package com.controleinfantil.kids.remote

import android.content.Context
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente REST mínimo do Supabase para o APP DA CRIANÇA.
 *
 * Toda ação vai pelas funções `device_*` (endpoint /rest/v1/rpc/...), que exigem o
 * token do aparelho. O app não lê nem escreve tabelas diretamente — o RLS bloqueia
 * isso de propósito.
 *
 * Chamadas são síncronas; use dentro de uma corrotina em Dispatchers.IO.
 */
class SupabaseClient(context: Context) {

    private val appContext = context.applicationContext

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // --- Chamada base -------------------------------------------------------

    /** Motivo da última falha, para distinguir "sem rede" de "servidor recusou". */
    @Volatile
    private var lastFailureCode: Int = 0

    private fun rpc(fn: String, args: JSONObject): Response {
        if (!isConfigured) {
            Log.w(TAG, "SupabaseConfig não preenchido")
            lastFailureCode = CODE_NOT_CONFIGURED
            return Response(false, CODE_NOT_CONFIGURED, null)
        }
        val request = Request.Builder()
            .url("${SupabaseConfig.URL}/rest/v1/rpc/$fn")
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")
            .header("Content-Type", "application/json")
            .post(args.toString().toRequestBody(JSON))
            .build()
        return try {
            http.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    Log.w(TAG, "rpc $fn HTTP ${resp.code}: $body")
                    lastFailureCode = resp.code
                }
                Response(resp.isSuccessful, resp.code, body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "rpc $fn falhou", e)
            lastFailureCode = CODE_NETWORK
            Response(false, CODE_NETWORK, null)
        }
    }

    /**
     * Chamada que usa o token do aparelho. Se o servidor recusar o token, esquece
     * o registro para que o próximo ciclo registre de novo — sem isso, um backend
     * recriado deixaria o app falhando para sempre.
     */
    private fun deviceRpc(fn: String, extra: JSONObject.() -> Unit = {}): Response {
        val args = JSONObject()
            .put("p_device", DeviceIdentity.id(appContext))
            .put("p_token", DeviceIdentity.token(appContext))
            .apply(extra)
        val resp = rpc(fn, args)
        if (resp.isDeviceAuthFailure) {
            Log.w(TAG, "Servidor recusou o token do aparelho; registrando de novo")
            DeviceIdentity.clear(appContext)
        }
        return resp
    }

    // --- Registro -----------------------------------------------------------

    /**
     * Registra o aparelho, se ainda não estiver, e devolve se está registrado.
     *
     * A trava evita que o serviço de comandos e a tela de configuração — que têm
     * instâncias separadas deste cliente — registrem o aparelho duas vezes na
     * primeira abertura. Dois registros fariam o código de pareamento sair para um
     * aparelho enquanto o app usaria o outro, e os comandos expirariam calados.
     */
    fun ensureRegistered(): Boolean {
        if (DeviceIdentity.isRegistered(appContext)) return true
        synchronized(REGISTRATION_LOCK) {
            // Outra thread pode ter registrado enquanto esperávamos a trava.
            if (DeviceIdentity.isRegistered(appContext)) return true

            val args = JSONObject().put("p_label", DeviceIdentity.label(appContext))
            val resp = rpc("device_register", args)
            if (!resp.ok || resp.body == null) return false
            return try {
                // Funções que retornam TABLE vêm como array de linhas.
                val row = JSONArray(resp.body).optJSONObject(0) ?: return false
                DeviceIdentity.save(
                    appContext,
                    row.getString("device_id"),
                    row.getString("device_token"),
                )
                Log.i(TAG, "Aparelho registrado")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao ler device_register", e)
                false
            }
        }
    }

    // --- Operações do aparelho ---------------------------------------------

    fun heartbeat() {
        if (!DeviceIdentity.isRegistered(appContext)) return
        deviceRpc("device_heartbeat")
    }

    fun fetchPendingCommands(): List<Command> {
        if (!DeviceIdentity.isRegistered(appContext)) return emptyList()
        val resp = deviceRpc("device_fetch_commands")
        if (!resp.ok || resp.body == null) return emptyList()
        return try {
            val arr = JSONArray(resp.body)
            (0 until arr.length()).map { Command.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler comandos", e)
            emptyList()
        }
    }

    fun reportCommandResult(commandId: String, status: String, detail: String? = null) {
        if (!DeviceIdentity.isRegistered(appContext)) return
        deviceRpc("device_report_result") {
            put("p_command", commandId)
            put("p_status", status)
            put("p_detail", detail ?: JSONObject.NULL)
        }
    }

    fun postLocation(lat: Double, lon: Double, accuracy: Float) {
        if (!DeviceIdentity.isRegistered(appContext)) return
        deviceRpc("device_post_location") {
            put("p_lat", lat)
            put("p_lon", lon)
            put("p_accuracy", accuracy.toDouble())
        }
    }

    /** Gera o código de pareamento do PRIMEIRO responsável. */
    fun createPairingCode(): PairingResult {
        if (!isConfigured) return PairingResult.NotConfigured
        if (!ensureRegistered()) {
            return if (lastFailureCode == CODE_NETWORK) PairingResult.NoNetwork
            else PairingResult.Failed
        }
        val resp = deviceRpc("device_create_pairing_code")
        return when {
            resp.code == CODE_NETWORK -> PairingResult.NoNetwork
            resp.body?.contains("device_already_paired") == true -> PairingResult.AlreadyPaired
            !resp.ok || resp.body == null -> PairingResult.Failed
            else -> try {
                val row = JSONArray(resp.body).optJSONObject(0) ?: return PairingResult.Failed
                PairingResult.Success(row.getString("code"), row.getString("expires_at"))
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao ler código de pareamento", e)
                PairingResult.Failed
            }
        }
    }

    // --- Tipos --------------------------------------------------------------

    sealed interface PairingResult {
        data class Success(val code: String, val expiresAt: String) : PairingResult
        data object NotConfigured : PairingResult
        data object NoNetwork : PairingResult
        data object AlreadyPaired : PairingResult
        data object Failed : PairingResult
    }

    private data class Response(val ok: Boolean, val code: Int, val body: String?) {
        /** O servidor recusou o token deste aparelho (não é falha de rede). */
        val isDeviceAuthFailure: Boolean
            get() = body?.contains("device_auth_failed") == true ||
                (!ok && code in AUTH_FAILURE_CODES)
    }

    private val isConfigured: Boolean
        get() = !SupabaseConfig.URL.contains("SEU-PROJETO") &&
            !SupabaseConfig.ANON_KEY.startsWith("COLE_SUA")

    companion object {
        private const val TAG = "SupabaseClient"
        private val JSON = "application/json".toMediaType()

        /** Códigos internos (não são HTTP). */
        private const val CODE_NETWORK = -1
        private const val CODE_NOT_CONFIGURED = -2

        /** SQLSTATE 28000 chega como 401 ou 403, conforme a versão do PostgREST. */
        private val AUTH_FAILURE_CODES = setOf(401, 403)

        /** Compartilhada por todas as instâncias no processo. */
        private val REGISTRATION_LOCK = Any()
    }
}
