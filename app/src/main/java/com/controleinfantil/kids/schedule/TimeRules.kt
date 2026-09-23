package com.controleinfantil.kids.schedule

import android.content.Context
import java.util.Calendar

/**
 * Limites de uso definidos pelo responsável.
 *
 * @param enabled se false, nada é limitado.
 * @param startMinute início da janela liberada, em minutos desde a meia-noite.
 * @param endMinute fim da janela. Se for menor que [startMinute], a janela cruza a
 *   meia-noite (ex.: 22:00 às 06:00).
 * @param dailyLimitMinutes teto de uso por dia; 0 significa sem teto.
 */
data class TimeRules(
    val enabled: Boolean = false,
    val startMinute: Int = 8 * 60,
    val endMinute: Int = 20 * 60,
    val dailyLimitMinutes: Int = 0,
) {
    val startText: String get() = format(startMinute)
    val endText: String get() = format(endMinute)

    private fun format(minute: Int): String =
        "%02d:%02d".format(minute / 60, minute % 60)

    /** True se [minuteOfDay] está dentro da janela liberada. */
    fun isWithinWindow(minuteOfDay: Int): Boolean = when {
        startMinute == endMinute -> true          // janela cheia
        startMinute < endMinute -> minuteOfDay >= startMinute && minuteOfDay < endMinute
        else -> minuteOfDay >= startMinute || minuteOfDay < endMinute  // cruza meia-noite
    }

    companion object {
        private const val PREFS = "schedule"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_START = "start_minute"
        private const val KEY_END = "end_minute"
        private const val KEY_LIMIT = "daily_limit"
        private const val KEY_OVERRIDE = "override_until"

        fun load(context: Context): TimeRules {
            val p = prefs(context)
            val padrao = TimeRules()
            return TimeRules(
                enabled = p.getBoolean(KEY_ENABLED, padrao.enabled),
                startMinute = p.getInt(KEY_START, padrao.startMinute),
                endMinute = p.getInt(KEY_END, padrao.endMinute),
                dailyLimitMinutes = p.getInt(KEY_LIMIT, padrao.dailyLimitMinutes),
            )
        }

        fun save(context: Context, rules: TimeRules) {
            prefs(context).edit()
                .putBoolean(KEY_ENABLED, rules.enabled)
                .putInt(KEY_START, rules.startMinute)
                .putInt(KEY_END, rules.endMinute)
                .putInt(KEY_LIMIT, rules.dailyLimitMinutes)
                .apply()
        }

        /**
         * Libera o aparelho por [minutes] minutos, ignorando as regras. Serve para o
         * responsável não ficar trancado fora do próprio aparelho ao configurá-lo,
         * e para exceções ("hoje pode mais um pouquinho").
         */
        fun grantOverride(context: Context, minutes: Int) {
            prefs(context).edit()
                .putLong(KEY_OVERRIDE, System.currentTimeMillis() + minutes * 60_000L)
                .apply()
        }

        fun clearOverride(context: Context) {
            prefs(context).edit().remove(KEY_OVERRIDE).apply()
        }

        fun overrideUntil(context: Context): Long =
            prefs(context).getLong(KEY_OVERRIDE, 0L)

        private fun prefs(context: Context) =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}

/** Resultado da checagem das regras num dado instante. */
sealed interface Verdict {
    /** Pode usar. */
    data object Allowed : Verdict

    /** Fora do horário permitido. */
    data class OutsideWindow(val rules: TimeRules) : Verdict

    /** O tempo do dia acabou. */
    data class BudgetSpent(val limitMinutes: Int) : Verdict

    val blocked: Boolean get() = this !is Allowed
}

/**
 * Avalia as regras agora. Usa o relógio do aparelho — se a criança mudar a hora,
 * o limite muda junto; como Device Owner dá para impedir isso (melhoria futura).
 */
fun checkRules(context: Context, nowMillis: Long = System.currentTimeMillis()): Verdict {
    val rules = TimeRules.load(context)
    if (!rules.enabled) return Verdict.Allowed
    if (nowMillis < TimeRules.overrideUntil(context)) return Verdict.Allowed

    val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    if (!rules.isWithinWindow(minuteOfDay)) return Verdict.OutsideWindow(rules)

    val limit = rules.dailyLimitMinutes
    if (limit > 0 && UsageTracker.usedMinutesToday(context) >= limit) {
        return Verdict.BudgetSpent(limit)
    }
    return Verdict.Allowed
}
