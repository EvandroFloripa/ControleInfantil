package com.controleinfantil.kids.block

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Instalação silenciosa de um APK a partir de um link — só funciona como **Device
 * Owner**, que pode instalar sem a tela de confirmação. Sem Device Owner, o resultado
 * pede a confirmação do usuário (tratado em [InstallResultReceiver]); nesse caso o
 * fluxo normal é abrir a Play (ver CommandService).
 *
 * Baixa o APK e entrega ao [PackageInstaller]; o resultado chega ao receiver.
 */
object SilentInstaller {

    private const val TAG = "SilentInstaller"
    const val ACTION_RESULT = "com.controleinfantil.kids.INSTALL_RESULT"

    /** Baixa e instala. Devolve false se o download/entrega falhou (não a instalação). */
    fun install(context: Context, url: String): Boolean {
        val app = context.applicationContext
        return try {
            val resp = OkHttpClient().newCall(Request.Builder().url(url).build()).execute()
            resp.use {
                val body = it.body
                if (!it.isSuccessful || body == null) {
                    Log.e(TAG, "Download do APK falhou: HTTP ${it.code}")
                    return false
                }
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("apk", 0, body.contentLength()).use { out ->
                        body.byteStream().copyTo(out)
                        session.fsync(out)
                    }
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_MUTABLE else 0
                    val pi = PendingIntent.getBroadcast(
                        app, sessionId,
                        Intent(app, InstallResultReceiver::class.java).setAction(ACTION_RESULT),
                        flags,
                    )
                    session.commit(pi.intentSender)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Instalação silenciosa falhou", e)
            false
        }
    }
}

/** Recebe o resultado da instalação. Como Device Owner, chega direto STATUS_SUCCESS. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Sem Device Owner: o sistema pede confirmação. Abre a tela.
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let {
                    runCatching { context.startActivity(it) }
                }
            }
            PackageInstaller.STATUS_SUCCESS ->
                Log.i("SilentInstaller", "App instalado")
            else -> Log.e(
                "SilentInstaller",
                "Falha na instalação: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}",
            )
        }
    }
}
