package com.controleinfantil.kids.schedule

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import java.util.Calendar

/**
 * Conta quanto tempo o aparelho foi realmente usado hoje.
 *
 * Contamos o tempo de tela ligada e desbloqueada, somado a cada ciclo do
 * [com.controleinfantil.kids.remote.CommandService]. É uma aproximação: mede "o
 * aparelho esteve em uso", não "qual app estava aberto". Medir por app exigiria a
 * permissão especial de Acesso ao Uso, que o responsável teria de conceder à mão —
 * para o objetivo aqui (limitar o tempo total da criança), isto basta e não depende
 * de permissão nenhuma.
 *
 * O contador zera sozinho na virada do dia.
 */
object UsageTracker {

    private const val PREFS = "usage"
    private const val KEY_DAY = "day"
    private const val KEY_SECONDS = "seconds"

    /** Ignora intervalos maiores que isto (aparelho dormiu, serviço foi morto). */
    private const val MAX_TICK_SECONDS = 120L

    /** True se a tela está ligada e desbloqueada. */
    fun isInUse(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return power.isInteractive && !keyguard.isKeyguardLocked
    }

    /** Soma [elapsedSeconds] ao total de hoje, virando o dia se preciso. */
    fun record(context: Context, elapsedSeconds: Long) {
        if (elapsedSeconds <= 0) return
        val p = prefs(context)
        val hoje = todayKey()
        val atual = if (p.getInt(KEY_DAY, -1) == hoje) p.getLong(KEY_SECONDS, 0L) else 0L
        p.edit()
            .putInt(KEY_DAY, hoje)
            .putLong(KEY_SECONDS, atual + elapsedSeconds.coerceAtMost(MAX_TICK_SECONDS))
            .apply()
    }

    fun usedMinutesToday(context: Context): Int {
        val p = prefs(context)
        if (p.getInt(KEY_DAY, -1) != todayKey()) return 0
        return (p.getLong(KEY_SECONDS, 0L) / 60L).toInt()
    }

    /** Zera o tempo de hoje (o responsável devolvendo tempo à criança). */
    fun resetToday(context: Context) {
        prefs(context).edit()
            .putInt(KEY_DAY, todayKey())
            .putLong(KEY_SECONDS, 0L)
            .apply()
    }

    private fun todayKey(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
