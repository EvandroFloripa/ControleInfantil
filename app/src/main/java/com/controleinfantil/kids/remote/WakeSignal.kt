package com.controleinfantil.kids.remote

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sinal que acorda o ciclo de busca de comandos antes da hora.
 *
 * O canal em tempo real avisa que há comando novo; o loop de [CommandService] espera
 * este sinal no lugar de dormir o intervalo fixo. Se o aviso não chegar, o intervalo
 * continua valendo como reserva. Fila de um elemento: vários avisos viram um só.
 */
object WakeSignal {

    private val channel = Channel<Unit>(Channel.CONFLATED)

    /** Acorda o loop agora. Chamável de qualquer thread. */
    fun signal() {
        channel.trySend(Unit)
    }

    /** Espera um aviso por até [timeoutMs]. Retorna quando há aviso ou o tempo acaba. */
    suspend fun await(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) { channel.receive() }
    }
}
