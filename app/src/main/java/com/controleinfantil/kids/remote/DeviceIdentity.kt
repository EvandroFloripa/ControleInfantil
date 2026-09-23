package com.controleinfantil.kids.remote

import android.content.Context
import java.util.UUID

/** Identidade estável do aparelho, guardada localmente. */
object DeviceIdentity {
    private const val PREFS = "controle_infantil"
    private const val KEY_ID = "device_id"
    private const val KEY_LABEL = "device_label"

    fun id(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_ID, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_ID, id).apply()
        return id
    }

    fun label(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LABEL, null) ?: "Celular da criança"

    fun setLabel(context: Context, label: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LABEL, label).apply()
    }
}
