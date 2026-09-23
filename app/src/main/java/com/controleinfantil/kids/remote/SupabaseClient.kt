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

    private fun rpc(fn: String, args: JSONObject): Response {
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
                if (!resp.isSuccessful) Log.w(TAG, "rpc $fn HTTP ${resp.code}: $body")
                Response(resp.isSuccessful, resp.code, body)
            }
        } catch (e: Exception) {
            Log.e(TAG, "rpc $fn falhou", e)
            Response(false, -1, null)
        }
    }

    private fun deviceArgs(): JSONObject = JSONObject()
        .put("p_device", DeviceIdentity.id(appContext))
        .put("p_token", DeviceIdentity.token(appContext))

    /**
     * Registra o aparelho, se ainda não estiver. Guarda id e token localmente.
     * Retorna true se já está registrado ao final.
     */
    fun ensureRegistered(): Boolean {
        if (DeviceIdentity.isRegistered(appContext)) return true
        val args = JSONObject().put("p_label", DeviceIdentity.label(appContext))
        val resp = rpc("device_register", args)
        if (!resp.ok || resp.body == null) return false
        return try {
            // Funções que retornam TABLE vêm como array de linhas.
            val row = JSONArray(resp.body).optJSONObject(0) ?: return false
            val id = row.getString("device_id")
            val token = row.getString("device_token")
            DeviceIdentity.save(appContext, id, token)
            Log.i(TAG, "Aparelho registrado: $id")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler device_register", e)
            false
        }
    }

    fun heartbeat() {
        if (!DeviceIdentity.isRegistered(appContext)) return
        rpc("device_heartbeat", deviceArgs())
    }

    fun fetchPendingCommands(): List<Command> {
        if (!DeviceIdentity.isRegistered(appContext)) return emptyList()
        val resp = rpc("device_fetch_commands", deviceArgs())
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
        val args = deviceArgs()
            .put("p_command", commandId)
            .put("p_status", status)
            .put("p_detail", detail ?: JSONObject.NULL)
        rpc("device_report_result", args)
    }

    fun postLocation(lat: Double, lon: Double, accuracy: Float) {
        val args = deviceArgs()
            .put("p_lat", lat)
            .put("p_lon", lon)
            .put("p_accuracy", accuracy.toDouble())
        rpc("device_post_location", args)
    }

    /**
     * Gera o código de pareamento do PRIMEIRO responsável. Retorna o código, ou
     * null se falhar (ex.: aparelho já tem controlador).
     */
    fun createPairingCode(): PairingCode? {
        if (!DeviceIdentity.isRegistered(appContext)) return null
        val resp = rpc("device_create_pairing_code", deviceArgs())
        if (!resp.ok || resp.body == null) return null
        return try {
            val row = JSONArray(resp.body).optJSONObject(0) ?: return null
            PairingCode(row.getString("code"), row.getString("expires_at"))
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler código de pareamento", e)
            null
        }
    }

    data class PairingCode(val code: String, val expiresAt: String)

    private data class Response(val ok: Boolean, val code: Int, val body: String?)

    companion object {
        private const val TAG = "SupabaseClient"
        private val JSON = "application/json".toMediaType()
    }
}
