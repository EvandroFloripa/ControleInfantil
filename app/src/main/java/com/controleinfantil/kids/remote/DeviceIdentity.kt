package com.controleinfantil.kids.remote

import android.content.Context

/**
 * Identidade do aparelho no backend.
 *
 * Diferente da versão inicial, o ID agora vem do servidor (função `device_register`),
 * junto com um TOKEN secreto. Guardamos os dois localmente; o token nunca sai do
 * aparelho a não ser nas chamadas às funções `device_*`.
 */
object DeviceIdentity {
    private const val PREFS = "controle_infantil"
    private const val KEY_ID = "device_id"
    private const val KEY_TOKEN = "device_token"
    private const val KEY_LABEL = "device_label"

    /** ID do aparelho, ou null se ainda não foi registrado. */
    fun id(context: Context): String? =
        prefs(context).getString(KEY_ID, null)

    /** Token secreto do aparelho, ou null se ainda não foi registrado. */
    fun token(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    fun isRegistered(context: Context): Boolean =
        id(context) != null && token(context) != null

    fun save(context: Context, id: String, token: String) {
        prefs(context).edit()
            .putString(KEY_ID, id)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    /**
     * Esquece o registro. Usado quando o servidor recusa o token (backend
     * recriado, aparelho apagado). O app se registra de novo no próximo ciclo —
     * como vira um aparelho novo, o pareamento precisa ser refeito.
     */
    fun clear(context: Context) {
        prefs(context).edit()
            .remove(KEY_ID)
            .remove(KEY_TOKEN)
            .apply()
    }

    fun label(context: Context): String =
        prefs(context).getString(KEY_LABEL, null) ?: defaultLabel()

    /** Sem nome definido, usa o modelo do aparelho para dar para diferenciar. */
    private fun defaultLabel(): String {
        val model = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
            .filter { !it.isNullOrBlank() }
            .joinToString(" ") { it.trim() }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        return model.ifBlank { "Celular da criança" }.take(60)
    }

    fun setLabel(context: Context, label: String) {
        prefs(context).edit().putString(KEY_LABEL, label).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
