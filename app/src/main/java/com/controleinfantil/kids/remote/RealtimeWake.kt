package com.controleinfantil.kids.remote

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Canal em tempo real do Supabase (protocolo Phoenix, vsn 1.0.0).
 *
 * Assina o canal `device:<id>` do próprio aparelho. Quando o servidor envia o evento
 * `wake` (um comando foi criado), acorda o ciclo de busca via [WakeSignal]. O sinal
 * não contém comando: a busca continua pela RPC autenticada com o token do aparelho.
 *
 * Mantém a conexão com heartbeat e reconecta com espera crescente. A consulta
 * periódica do [CommandService] continua como reserva se o canal cair.
 */
object RealtimeWake {

    private const val TAG = "RealtimeWake"
    private const val HEARTBEAT_MS = 25_000L
    private const val MAX_BACKOFF_MS = 60_000L

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var started = false
    @Volatile private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var attempts = 0
    private var ref = 0

    /** Liga a assinatura do canal do aparelho. Chamar uma vez, ao iniciar o serviço. */
    fun start(context: Context) {
        Log.i(TAG, "start() chamado; ligado=$started")
        if (started) return
        val deviceId = DeviceIdentity.id(context)
        if (deviceId == null) {
            Log.w(TAG, "Sem identificador do aparelho; aviso em tempo real desligado")
            return
        }
        val base = SupabaseConfig.URL
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
        if (!base.startsWith("wss://") && !base.startsWith("ws://")) {
            Log.w(TAG, "URL do Supabase inválida; sem aviso em tempo real")
            return
        }
        started = true
        connect(base, deviceId)
    }

    private fun connect(base: String, deviceId: String) {
        Log.i(TAG, "Conectando em $base/realtime/v1/websocket")
        val url = "$base/realtime/v1/websocket?apikey=${SupabaseConfig.ANON_KEY}&vsn=1.0.0"
        val request = Request.Builder().url(url).build()
        socket = http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                attempts = 0
                Log.i(TAG, "Conectado; assinando device:$deviceId")
                webSocket.send(joinMessage(deviceId))
                heartbeat?.cancel()
                heartbeat = scope.launch {
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        webSocket.send(message("phoenix", "heartbeat", JSONObject()))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val msg = JSONObject(text)
                    if (msg.optString("event") != "broadcast") return@runCatching
                    val payload = msg.optJSONObject("payload") ?: return@runCatching
                    if (payload.optString("event") == "wake") WakeSignal.signal()
                }.onFailure { Log.w(TAG, "Mensagem inválida ignorada", it) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "Falha no canal; tentando de novo", t)
                reconnect(base, deviceId)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Canal fechado ($code); tentando de novo")
                reconnect(base, deviceId)
            }
        })
    }

    private fun reconnect(base: String, deviceId: String) {
        heartbeat?.cancel()
        heartbeat = null
        attempts++
        val espera = minOf(MAX_BACKOFF_MS, 1_000L shl minOf(attempts, 6))
        scope.launch {
            delay(espera)
            connect(base, deviceId)
        }
    }

    private fun nextRef(): String = (++ref).toString()

    private fun joinMessage(deviceId: String): String {
        val config = JSONObject()
            .put("broadcast", JSONObject().put("ack", false).put("self", false))
            .put("presence", JSONObject().put("key", ""))
            .put("private", false)
        val payload = JSONObject()
            .put("config", config)
            .put("access_token", SupabaseConfig.ANON_KEY)
        return message("realtime:device:$deviceId", "phx_join", payload)
    }

    private fun message(topic: String, event: String, payload: JSONObject): String =
        JSONObject()
            .put("topic", topic)
            .put("event", event)
            .put("payload", payload)
            .put("ref", nextRef())
            .toString()

    /** Encerra a assinatura. Hoje não é chamado; o serviço vive o tempo do app. */
    fun stop() {
        heartbeat?.cancel()
        socket?.close(1000, "parando")
        started = false
    }
}
