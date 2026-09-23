package com.controleinfantil.kids.media

import android.content.Context
import android.content.Intent

/**
 * Fronteira entre a captura (câmera, microfone, tela) e o envio ao responsável.
 *
 * O envio em tempo real é WebRTC, com sinalização pela tabela `signals` do Supabase
 * (ver supabase/signaling.sql) e STUN/TURN configurados em [WebRtcConfig]. A
 * implementação real é [WebRtcTransport]; ela só se valida em aparelho de verdade.
 *
 * A camada visível (notificação, serviços em primeiro plano, bolinha do Android)
 * vive nos serviços que chamam esta interface, não aqui.
 */
interface MediaTransport {

    /** Abre a conexão e começa a enviar a mídia indicada. */
    fun start(session: SessionConfig)

    /** Encerra a conexão e libera câmera/microfone/tela. */
    fun stop()

    enum class Kind { CAMERA, SCREEN }

    /**
     * @param screenResultCode / [screenData] só usados quando [kind] é SCREEN — são
     *   o resultado do consentimento de captura pego pela ScreenCaptureActivity.
     */
    data class SessionConfig(
        val deviceId: String,
        val sessionId: String,
        val kind: Kind,
        val screenResultCode: Int = 0,
        val screenData: Intent? = null,
    )

    companion object {
        fun create(context: Context): MediaTransport = WebRtcTransport(context)
    }
}
