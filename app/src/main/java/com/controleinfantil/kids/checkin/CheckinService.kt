package com.controleinfantil.kids.checkin

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
 * Check-in de vídeo/áudio.
 *
 * Ao receber o comando do responsável, conecta câmera e microfone sem a criança
 * precisar aceitar — mas SEMPRE com uma notificação visível e persistente (como a de
 * um app de música tocando) enquanto dura, além da bolinha verde que o próprio
 * Android mostra. Não existe modo oculto: sem a notificação, o serviço não roda.
 *
 * A captura e o envio ficam por conta do [MediaTransport] (WebRTC, a implementar).
 * Enquanto ele é o placeholder, este serviço já faz toda a parte visível: sobe em
 * primeiro plano, mostra a notificação e encerra sob comando ou timeout (os prazos
 * ficam no [com.controleinfantil.kids.media.WebRtcTransport]).
 */
class CheckinService : Service() {

    private lateinit var transport: MediaTransport

    /** A notificação subiu. Sem ela, a câmera e o microfone nunca são ligados. */
    private var inForeground = false

    override fun onCreate() {
        super.onCreate()
        // Só aqui o Context já está pronto (após attachBaseContext).
        // onClosed encerra o serviço quando a conexão termina (ex.: "bye" do painel),
        // para a notificação não ficar dizendo "em uso" depois que parou.
        transport = MediaTransport.create(this) { stopSelf() }
        inForeground = startAsForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // stopSelf() não interrompe na hora: sem este retorno, o transporte ligaria
        // câmera e microfone mesmo com a notificação tendo falhado.
        if (!inForeground) return START_NOT_STICKY
        val sessionId = intent?.getStringExtra(EXTRA_SESSION)
        if (sessionId.isNullOrEmpty()) {
            Log.e(TAG, "Check-in sem session_id; encerrando")
            stopSelf()
            return START_NOT_STICKY
        }
        transport.start(
            MediaTransport.SessionConfig(
                deviceId = DeviceIdentity.id(this).orEmpty(),
                sessionId = sessionId,
                kind = MediaTransport.Kind.CAMERA,
            )
        )
        // Não reinicia sozinho: um check-in é pontual, não deve voltar após ser morto.
        return START_NOT_STICKY
    }

    /** Mostra a notificação persistente. Devolve false se não conseguiu. */
    private fun startAsForeground(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.checkin_channel),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.checkin_channel_desc) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.checkin_active_title))
            .setContentText(getString(R.string.checkin_active_text))
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Tipos câmera+microfone exigem as permissões concedidas (o responsável as
        // concede como Device Owner). Sem elas, não sobe — mas nunca roda oculto.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Não foi possível iniciar o check-in (permissões de câmera/mic?)", e)
            stopSelf()
            return false
        }
    }

    override fun onDestroy() {
        if (::transport.isInitialized) transport.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CheckinService"
        private const val CHANNEL_ID = "controle_infantil_checkin"
        private const val NOTIF_ID = 1002
        private const val EXTRA_SESSION = "session_id"
        const val ACTION_STOP = "com.controleinfantil.kids.STOP_CHECKIN"

        fun start(context: Context, sessionId: String) {
            val intent = Intent(context, CheckinService::class.java)
                .putExtra(EXTRA_SESSION, sessionId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, CheckinService::class.java).setAction(ACTION_STOP)
            )
        }
    }
}
