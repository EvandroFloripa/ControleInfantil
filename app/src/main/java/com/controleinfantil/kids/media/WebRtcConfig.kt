package com.controleinfantil.kids.media

import org.webrtc.PeerConnection

/**
 * Servidores ICE (STUN/TURN).
 *
 * O STUN público do Google resolve a maioria dos casos em Wi-Fi. Em redes móveis
 * (operadora), quase sempre é preciso um **TURN** para o vídeo passar — coloque o seu
 * abaixo (host, usuário e senha). Sem TURN, o check-in pode não conectar no 4G/5G.
 */
object WebRtcConfig {

    fun iceServers(): List<PeerConnection.IceServer> = buildList {
        add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())

        // TURN — descomente e preencha com o seu servidor:
        // add(
        //     PeerConnection.IceServer.builder("turn:SEU-TURN:3478")
        //         .setUsername("usuario")
        //         .setPassword("senha")
        //         .createIceServer()
        // )
    }
}
