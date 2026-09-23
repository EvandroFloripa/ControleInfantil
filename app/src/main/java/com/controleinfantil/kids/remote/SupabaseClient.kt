package com.controleinfantil.kids.remote

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente REST mínimo para o Supabase (PostgREST), sem depender do SDK completo.
 * Faz apenas o que o app precisa: buscar comandos pendentes, marcar resultado e
 * enviar localização.
 *
 * Todas as chamadas são síncronas (bloqueantes) — chame-as de dentro de uma
 * corrotina em Dispatchers.IO, como faz o [CommandService].
 */
class SupabaseClient(private val deviceId: String) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun baseRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("apikey", SupabaseConfig.ANON_KEY)
            .header("Authorization", "Bearer ${SupabaseConfig.ANON_KEY}")

    /** Busca comandos com status 'pending' para este aparelho. */
    fun fetchPendingCommands(): List<Command> {
        val url = "${SupabaseConfig.restUrl}/commands" +
            "?device_id=eq.$deviceId" +
            "&status=eq.pending" +
            "&order=created_at.asc" +
            "&select=id,type,payload"
        return try {
            http.newCall(baseRequest(url).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "fetchPendingCommands HTTP ${resp.code}")
                    return emptyList()
                }
                val body = resp.body?.string().orEmpty()
                val arr = JSONArray(body)
                (0 until arr.length()).map { Command.fromJson(arr.getJSONObject(it)) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPendingCommands falhou", e)
            emptyList()
        }
    }

    /** Marca o resultado de um comando (done / error). */
    fun reportCommandResult(commandId: String, status: String, detail: String? = null) {
        val url = "${SupabaseConfig.restUrl}/commands?id=eq.$commandId"
        val body = JSONObject()
            .put("status", status)
            .put("result_detail", detail ?: JSONObject.NULL)
            .put("executed_at", nowIso())
            .toString()
            .toRequestBody(JSON)
        try {
            http.newCall(
                baseRequest(url)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .patch(body)
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) Log.w(TAG, "reportCommandResult HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "reportCommandResult falhou", e)
        }
    }

    /** Envia uma posição para a tabela `locations`. */
    fun postLocation(lat: Double, lon: Double, accuracy: Float) {
        val url = "${SupabaseConfig.restUrl}/locations"
        val body = JSONObject()
            .put("device_id", deviceId)
            .put("lat", lat)
            .put("lon", lon)
            .put("accuracy", accuracy.toDouble())
            .toString()
            .toRequestBody(JSON)
        try {
            http.newCall(
                baseRequest(url)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "return=minimal")
                    .post(body)
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) Log.w(TAG, "postLocation HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "postLocation falhou", e)
        }
    }

    /** Registra/atualiza o aparelho (heartbeat). */
    fun upsertDevice(label: String) {
        val url = "${SupabaseConfig.restUrl}/devices"
        val body = JSONObject()
            .put("id", deviceId)
            .put("label", label)
            .put("last_seen", nowIso())
            .toString()
            .toRequestBody(JSON)
        try {
            http.newCall(
                baseRequest(url)
                    .header("Content-Type", "application/json")
                    .header("Prefer", "resolution=merge-duplicates,return=minimal")
                    .post(body)
                    .build()
            ).execute().use { resp ->
                if (!resp.isSuccessful) Log.w(TAG, "upsertDevice HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "upsertDevice falhou", e)
        }
    }

    /** Instante atual em ISO-8601 (UTC), aceito pelo Postgres como timestamptz. */
    private fun nowIso(): String = java.time.Instant.now().toString()

    companion object {
        private const val TAG = "SupabaseClient"
        private val JSON = "application/json".toMediaType()
    }
}
