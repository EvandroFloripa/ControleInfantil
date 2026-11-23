package com.controleinfantil.kids.block

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager

/**
 * Encerra os apps da criança que estavam abertos quando o bloqueio entra em vigor.
 *
 * Tirar o app da frente (HOME) não basta: os outros continuariam vivos em segundo
 * plano e voltariam ao primeiro toque. Aqui o processo de cada app em segundo plano é
 * encerrado com [ActivityManager.killBackgroundProcesses], que exige só a permissão
 * KILL_BACKGROUND_PROCESSES.
 *
 * Limites conhecidos (verificar no aparelho real):
 * - Apps com serviço em primeiro plano (ex.: chamada, gravação) podem continuar vivos.
 * - Não remove as miniaturas da lista de apps recentes.
 * - Sistema, teclado, discadora e o próprio Controle Infantil nunca são encerrados.
 */
object AppCloser {

    /** Dá tempo para o HOME tirar o app da frente antes de encerrá-lo. */
    private const val DELAY_MS = 800L

    private val handler = Handler(Looper.getMainLooper())

    /** Encerra todos os apps da criança em segundo plano (bloqueio por horário/tempo). */
    fun closeAllSoon(context: Context) {
        val app = context.applicationContext
        handler.postDelayed({ closeAll(app) }, DELAY_MS)
    }

    /** Encerra um app específico (app não liberado aberto dentro do horário). */
    fun closeOneSoon(context: Context, pkg: String) {
        val app = context.applicationContext
        handler.postDelayed({ closeOne(app, pkg) }, DELAY_MS)
    }

    private fun closeAll(context: Context) {
        val keep = systemPackages(context)
        launchablePackages(context)
            .filter { it !in keep }
            .forEach { closeOne(context, it) }
    }

    private fun closeOne(context: Context, pkg: String) {
        if (pkg == context.packageName) return
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(pkg)
    }

    /** Apps que aparecem na gaveta de apps: são os que a criança pode abrir. */
    private fun launchablePackages(context: Context): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    /** O que nunca pode ser encerrado: o próprio app, o sistema, o teclado e a discadora. */
    private fun systemPackages(context: Context): Set<String> {
        val set = mutableSetOf(context.packageName, "com.android.systemui", "android")
        runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        }
        runCatching {
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                ?.let { set.add(it) }
        }
        return set
    }
}
