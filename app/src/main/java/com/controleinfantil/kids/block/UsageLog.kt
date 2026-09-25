package com.controleinfantil.kids.block

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Fila local dos apps que a criança abriu (histórico de uso). O [AppBlockerService]
 * grava cada abertura; o CommandService drena e envia ao painel em lotes.
 *
 * Guardado localmente até o envio para não perder eventos sem rede. Tem teto para
 * não crescer sem fim.
 */
object UsageLog {

    private const val PREFS = "usage_log"
    private const val KEY = "events"
    private const val MAX = 300
    private const val DEDUP_MS = 3_000L

    private var lastPkg: String? = null
    private var lastAt = 0L

    /** Registra a abertura de um app (dedup de repetições em sequência). */
    @Synchronized
    fun record(context: Context, pkg: String, label: String, blocked: Boolean) {
        val now = System.currentTimeMillis()
        if (pkg == lastPkg && now - lastAt < DEDUP_MS) return
        lastPkg = pkg
        lastAt = now

        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        arr.put(
            JSONObject()
                .put("package", pkg)
                .put("label", label)
                .put("blocked", blocked)
                .put("at", now)
        )
        // Mantém só os últimos MAX.
        val trimmed = if (arr.length() > MAX) {
            JSONArray().also { out -> for (i in arr.length() - MAX until arr.length()) out.put(arr.get(i)) }
        } else arr
        p.edit().putString(KEY, trimmed.toString()).apply()
    }

    /** Devolve os eventos acumulados e limpa a fila. */
    @Synchronized
    fun drain(context: Context): JSONArray {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(p.getString(KEY, "[]")) }.getOrDefault(JSONArray())
        if (arr.length() > 0) p.edit().remove(KEY).apply()
        return arr
    }
}
