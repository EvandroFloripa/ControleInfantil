package com.controleinfantil.kids.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.controleinfantil.kids.R
import com.controleinfantil.kids.media.MediaTransport
import com.controleinfantil.kids.remote.DeviceIdentity

/**
 * Compartilha a tela com o responsável depois do consentimento pego pela
 * [ScreenCaptureActivity]. Roda em primeiro plano com notificação visível; o Android
 * ainda mostra o ícone de transmissão de tela. Sem modo oculto.
 *
 * A captura de frames e o envio ficam com o [MediaTransport] (WebRTC, a implementar).
 */
class ScreenShareService : Service() {

    private lateinit var transport: MediaTransport

    override fun onCreate() {
        super.onCreate()
        // Só aqui o Context já está pronto (após attachBaseContext).
        transport = MediaTransport.create(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Em primeiro plano ANTES de o WebRTC criar a projeção (exigência do Android 14).
        startAsForeground()

        val resultCode = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                it.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else @Suppress("DEPRECATION") it.getParcelableExtra(EXTRA_DATA)
        }
        val sessionId = intent?.getStringExtra(EXTRA_SESSION).orEmpty()
        if (resultCode == 0 || data == null || sessionId.isEmpty()) {
            Log.e(TAG, "Sem dados de consentimento ou sessão; encerrando")
            stopSelf()
            return START_NOT_STICKY
        }

        // O consentimento (code+data) vai direto ao WebRTC, que cria a projeção.
        // Criá-la aqui também a consumiria e o WebRTC falharia.
        transport.start(
            MediaTransport.SessionConfig(
                deviceId = DeviceIdentity.id(this).orEmpty(),
                sessionId = sessionId,
                kind = MediaTransport.Kind.SCREEN,
                screenResultCode = resultCode,
                screenData = data,
            )
        )
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.screen_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.screen_channel_desc) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.screen_active_title))
            .setContentText(getString(R.string.screen_active_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível iniciar o compartilhamento de tela", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        if (::transport.isInitialized) transport.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenShareService"
        private const val CHANNEL_ID = "controle_infantil_screen"
        private const val NOTIF_ID = 1003
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_SESSION = "session_id"
        const val ACTION_STOP = "com.controleinfantil.kids.STOP_SCREEN"

        fun start(context: Context, resultCode: Int, data: Intent, sessionId: String) {
            val intent = Intent(context, ScreenShareService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_SESSION, sessionId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, ScreenShareService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
