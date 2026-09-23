package com.controleinfantil.kids.remote

import org.json.JSONObject

/**
 * Um comando remoto enviado pelo responsável (linha da tabela `commands`).
 *
 * Os tipos suportados hoje pelo app estão em [Type]. O painel/responsável insere uma
 * linha em `commands` com `status = 'pending'`; o app pega, executa e marca o
 * resultado.
 */
data class Command(
    val id: String,
    val type: Type,
    val payload: JSONObject,
) {
    /** Sessão WebRTC do check-in / ver a tela, definida pelo painel. */
    val sessionId: String get() = payload.optString("session_id")

    enum class Type(val wire: String) {
        LOCK_SCREEN("lock_screen"),
        REBOOT("reboot"),
        ENABLE_LOCATION("enable_location"),
        REQUEST_LOCATION("request_location"),
        // Estruturados (ver README): precisam de teste em aparelho real
        START_SCREEN_VIEW("start_screen_view"),
        START_CHECKIN("start_checkin"),
        UNKNOWN("unknown");

        companion object {
            fun from(wire: String?): Type =
                entries.firstOrNull { it.wire == wire } ?: UNKNOWN
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): Command = Command(
            id = obj.getString("id"),
            type = Type.from(obj.optString("type")),
            payload = obj.optJSONObject("payload") ?: JSONObject(),
        )
    }
}
