package com.controleinfantil.kids.remote

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.controleinfantil.kids.R
import com.controleinfantil.kids.admin.PolicyManager
import com.controleinfantil.kids.checkin.CheckinService
import com.controleinfantil.kids.launcher.KioskManager
import com.controleinfantil.kids.launcher.LauncherActivity
import com.controleinfantil.kids.launcher.launchablePackages
import com.controleinfantil.kids.location.LocationReporter
import com.controleinfantil.kids.screen.ScreenCaptureActivity
import com.controleinfantil.kids.screen.ScreenShareService
import com.controleinfantil.kids.schedule.UsageTracker
import com.controleinfantil.kids.schedule.checkRules
import com.controleinfantil.kids.setup.GuardianArea
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Serviço em primeiro plano que mantém o aparelho "conectado": em intervalos
 * regulares busca comandos pendentes no Supabase, executa e reporta o resultado.
 *
 * É um modelo de *polling* (simples e robusto). Um passo futuro é trocar por push
 * (FCM) para reduzir a latência e o gasto de bateria.
 */
class CommandService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var client: SupabaseClient
    private lateinit var policy: PolicyManager
    private lateinit var location: LocationReporter
    private var lastAppsSig: String? = null

    override fun onCreate() {
        super.onCreate()
        client = SupabaseClient(this)
        policy = PolicyManager(this)
        location = LocationReporter(this)
        // Em Android 14+ iniciar um serviço em primeiro plano do tipo "location"
        // exige a permissão já concedida; se não estiver, evitamos derrubar o app.
        try {
            startForeground(NOTIF_ID, buildNotification())
        } catch (e: Exception) {
            Log.e(TAG, "startForeground falhou (permissão de localização pendente?)", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (loop == null || loop?.isActive != true) {
            loop = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        var lastTick = System.currentTimeMillis()
        while (scope.isActive) {
            try {
                // Conta o tempo de uso e aplica os limites de horário antes da rede:
                // sem internet, os limites continuam valendo.
                val now = System.currentTimeMillis()
                enforceTimeRules((now - lastTick) / 1000)
                lastTick = now

                if (client.ensureRegistered()) {
                    client.heartbeat()
                    syncApps()
                    client.fetchPendingCommands().forEach { execute(it) }
                } else {
                    Log.w(TAG, "Aparelho ainda não registrado (verifique SupabaseConfig)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no ciclo de polling", e)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    /**
     * Soma o tempo usado e, se as regras bloquearem agora, tira a criança do app em
     * que estiver: traz o launcher (que mostra o aviso de descanso) para a frente e
     * apaga a tela. Só apagar não bastava — ao desbloquear, ela voltava ao mesmo app.
     */
    private fun enforceTimeRules(elapsedSeconds: Long) {
        val emUso = UsageTracker.isInUse(this)
        if (emUso) UsageTracker.record(this, elapsedSeconds)

        // Não interrompe o responsável enquanto ele ajusta as próprias regras.
        if (emUso && !GuardianArea.inForeground && checkRules(this).blocked) {
            Log.i(TAG, "Limite de horário atingido; voltando ao launcher")
            showLauncher()
            policy.lockNow()
        }
    }

    /**
     * Abrir uma tela a partir de um serviço é restrito no Android 10+, mas o Device
     * Owner é isento. Sem Device Owner o sistema pode ignorar o pedido; aí resta o
     * lockNow(), e o quiosque também não é real (ver KioskManager).
     */
    private fun showLauncher() {
        try {
            startActivity(
                Intent(this, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Não consegui trazer o launcher para a frente", e)
        }
    }

    /**
     * Manda ao painel a lista de apps abríveis e quais estão liberados, mas só quando
     * muda (instalou/removeu app ou o responsável mudou a lista) — evita reenviar a
     * cada 15 s.
     */
    private fun syncApps() {
        val allowed = KioskManager(this).allowedPackages
        val apps = launchablePackages().map { (pkg, label) -> Triple(pkg, label, pkg in allowed) }
        val sig = apps.joinToString("|") { "${it.first}=${it.third}" }.hashCode().toString()
        if (sig == lastAppsSig) return
        if (client.reportApps(apps)) lastAppsSig = sig
    }

    private suspend fun execute(cmd: Command) {
        Log.i(TAG, "Executando comando ${cmd.type} (${cmd.id})")
        val result: String = when (cmd.type) {
            Command.Type.LOCK_SCREEN -> asStatus(policy.lockNow())
            Command.Type.REBOOT -> asStatus(policy.reboot())
            Command.Type.ENABLE_LOCATION -> asStatus(policy.ensureLocationEnabled())
            Command.Type.REQUEST_LOCATION ->
                if (location.reportOnce(client)) "done" else "error"
            Command.Type.START_CHECKIN ->
                if (cmd.sessionId.isEmpty()) "error"
                else {
                    // Conecta câmera/microfone com notificação visível (ver CheckinService).
                    CheckinService.start(this, cmd.sessionId)
                    "done"
                }
            Command.Type.START_SCREEN_VIEW ->
                if (cmd.sessionId.isEmpty()) "error"
                else {
                    // Abre o consentimento de captura de tela, exigido pelo Android.
                    ScreenCaptureActivity.launch(this, cmd.sessionId)
                    "started"
                }
            Command.Type.STOP_CHECKIN -> {
                CheckinService.stop(this)
                "done"
            }
            Command.Type.STOP_SCREEN_VIEW -> {
                ScreenShareService.stop(this)
                "done"
            }
            Command.Type.SET_ALLOWED_APPS -> {
                KioskManager(this).allowedPackages = cmd.packages.toSet()
                lastAppsSig = null      // força re-report para o painel refletir
                "done"
            }
            Command.Type.INSTALL_APP -> openPlayStore(cmd.appPackage)
            Command.Type.UNKNOWN -> "unsupported"
        }
        val detail = when (result) {
            "needs_admin" -> "Falta ativar o Device Admin no aparelho"
            "needs_owner" -> "Falta provisionar como Device Owner (ADB)"
            "unsupported" -> "Comando ainda não implementado nesta versão"
            "started" -> "Aguardando o consentimento de captura de tela no aparelho"
            else -> null
        }
        val status = if (result == "done" || result == "started") "done" else "error"
        client.reportCommandResult(cmd.id, status, detail ?: result.takeIf { it != "done" })
    }

    /**
     * Abre a página do app no Google Play. A instalação em si é feita pela loja e
     * pede um toque. Num aparelho travado em quiosque, o Google Play precisa estar
     * liberado (o responsável pode liberá-lo pelo painel na hora de instalar).
     */
    private fun openPlayStore(pkg: String): String {
        if (pkg.isBlank()) return "error"
        return try {
            val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(market)
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            "done"
        } catch (e: Exception) {
            Log.e(TAG, "Não consegui abrir o Google Play para $pkg", e)
            "error"
        }
    }

    private fun asStatus(r: PolicyManager.Result): String = when (r) {
        PolicyManager.Result.Ok -> "done"
        PolicyManager.Result.NeedsAdmin -> "needs_admin"
        PolicyManager.Result.NeedsOwner -> "needs_owner"
        is PolicyManager.Result.Error -> "error"
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Controle Infantil",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Mantém o controle parental ativo" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Controle Infantil ativo")
            .setContentText("Acompanhamento do responsável ligado")
            .setSmallIcon(R.drawable.ic_shield)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "CommandService"
        private const val CHANNEL_ID = "controle_infantil_svc"
        private const val NOTIF_ID = 1001
        private const val POLL_INTERVAL_MS = 15_000L
        private var loop: Job? = null

        fun start(context: Context) {
            val intent = Intent(context, CommandService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
