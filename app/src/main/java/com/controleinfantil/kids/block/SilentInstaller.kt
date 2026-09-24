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

    /** Tamanho máximo do APK aceito (evita download abusivo). */
    private const val MAX_APK_BYTES = 300L * 1024 * 1024

    /** Baixa e instala. Devolve false se o download/entrega falhou (não a instalação). */
    fun install(context: Context, url: String): Boolean {
        val app = context.applicationContext
        // Só HTTPS: um link http poderia ser trocado por outro APK no caminho (MITM),
        // e a instalação silenciosa como Device Owner não pede confirmação.
        val clean = url.trim()
        if (!clean.startsWith("https://") || clean.length > 2048) {
            Log.e(TAG, "URL de APK recusada (precisa ser https e válida)")
            return false
        }
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(Request.Builder().url(clean).build()).execute()
            resp.use {
                val body = it.body
                if (!it.isSuccessful || body == null) {
                    Log.e(TAG, "Download do APK falhou: HTTP ${it.code}")
                    return false
                }
                if (body.contentLength() > MAX_APK_BYTES) {
                    Log.e(TAG, "APK grande demais: ${body.contentLength()} bytes")
                    return false
                }
                val installer = app.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL
                )
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    session.openWrite("apk", 0, body.contentLength()).use { out ->
                        // Copia com teto de tamanho, mesmo se o servidor não informar o length.
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        val input = body.byteStream()
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > MAX_APK_BYTES) {
                                Log.e(TAG, "APK excedeu o limite durante o download")
                                runCatching { session.abandon() }
                                return false
                            }
                            out.write(buffer, 0, n)
                        }
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
