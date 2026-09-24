package com.controleinfantil.kids.block

import android.content.Context

/**
 * Libera a Play Store por uma janela curta, para uma instalação pedida pelo painel
 * poder acontecer sem o [AppBlockerService] chutar a criança de volta ao launcher.
 * Passada a janela, a Play volta a ser bloqueada.
 */
object InstallAllow {

    private const val PREFS = "install_allow"
    private const val KEY_UNTIL = "play_until"
    private const val WINDOW_MS = 3 * 60_000L

    fun allowPlay(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_UNTIL, System.currentTimeMillis() + WINDOW_MS).apply()
    }

    fun isPlayAllowed(context: Context): Boolean =
        System.currentTimeMillis() <
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_UNTIL, 0L)
}
