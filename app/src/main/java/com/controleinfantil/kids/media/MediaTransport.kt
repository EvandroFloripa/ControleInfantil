package com.controleinfantil.kids.media

import android.util.Log

/**
 * Fronteira entre a captura (câmera, microfone, tela) e o envio ao responsável.
 *
 * O envio em tempo real de vídeo/áudio é feito por WebRTC, que precisa de:
 *  - um servidor de sinalização para os dois lados trocarem a oferta/resposta SDP e
 *    os candidatos ICE (dá para usar o Supabase Realtime nas tabelas de sinalização);
 *  - servidores STUN/TURN para atravessar NAT (o TURN é praticamente obrigatório em
 *    redes móveis).
 *
 * Essa peça só faz sentido validada em aparelho real, então aqui fica só a fronteira.
 * A implementação de verdade entra em [WebRtcTransport] (a criar), trocando a
 * [NoopTransport] abaixo. O resto do app — comando, permissões, notificação visível,
 * serviços em primeiro plano — já funciona em volta desta interface.
 */
interface MediaTransport {

    /** Abre a conexão e começa a enviar as faixas indicadas. */
    fun start(session: SessionConfig)

    /** Encerra a conexão e libera câmera/microfone/tela. */
    fun stop()

    data class SessionConfig(
        val deviceId: String,
        val video: Boolean,
        val audio: Boolean,
        val screen: Boolean,
    )

    companion object {
        /**
         * Devolve o transporte a usar. Enquanto o WebRTC não estiver pronto e testado,
         * retorna a implementação vazia — assim a camada transparente pode ser
         * exercitada sem enviar mídia nenhuma.
         */
        fun create(): MediaTransport = NoopTransport()
    }
}

/**
 * Placeholder: registra o que faria, mas não abre câmera, microfone nem tela e não
 * envia nada. Existe para a camada transparente rodar de ponta a ponta enquanto o
 * WebRTC não é implementado. NÃO é uma captura silenciosa — não captura coisa nenhuma.
 */
class NoopTransport : MediaTransport {
    override fun start(session: MediaTransport.SessionConfig) {
        Log.i(TAG, "Transporte ainda não implementado; sessão solicitada: $session")
    }

    override fun stop() {
        Log.i(TAG, "Transporte encerrado (noop)")
    }

    private companion object {
        const val TAG = "MediaTransport"
    }
}
