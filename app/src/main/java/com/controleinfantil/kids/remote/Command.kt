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

    /** Pacotes liberados (comando set_allowed_apps). */
    val packages: List<String>
        get() = payload.optJSONArray("packages")?.let { a ->
            (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }
        } ?: emptyList()

    /** Pacote a instalar (comando install_app). */
    val appPackage: String get() = payload.optString("package")

    /** Link do APK para instalação silenciosa (Device Owner). */
    val apkUrl: String get() = payload.optString("apk_url")

    enum class Type(val wire: String) {
        LOCK_SCREEN("lock_screen"),
        REBOOT("reboot"),
        ENABLE_LOCATION("enable_location"),
        REQUEST_LOCATION("request_location"),
        // Vídeo/tela (WebRTC): precisam de teste em aparelho real
        START_SCREEN_VIEW("start_screen_view"),
        START_CHECKIN("start_checkin"),
        STOP_SCREEN_VIEW("stop_screen_view"),
        STOP_CHECKIN("stop_checkin"),
        // Gestão remota de apps (painel)
        SET_ALLOWED_APPS("set_allowed_apps"),
        INSTALL_APP("install_app"),
        SET_TIME_RULES("set_time_rules"),
        GRANT_TIME("grant_time"),
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
