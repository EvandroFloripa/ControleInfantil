package com.controleinfantil.kids.block

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.controleinfantil.kids.launcher.KioskManager
import com.controleinfantil.kids.launcher.LauncherActivity
import com.controleinfantil.kids.schedule.checkRules
import com.controleinfantil.kids.setup.GuardianArea

/**
 * Bloqueia, sem Device Owner, os apps que não estão liberados: quando um app fora da
 * lista chega ao primeiro plano, o serviço traz o launcher da criança de volta.
 *
 * É o que os apps de controle parental usam quando não são Device Owner. Depende do
 * responsável ligar a Acessibilidade nas Configurações (a tela é aberta pelo setup).
 *
 * Só usa o **pacote** do app em primeiro plano — não lê o conteúdo da tela
 * (canRetrieveWindowContent=false).
 */
class AppBlockerService : AccessibilityService() {

    /**
     * App que está na frente. O evento só chega quando a janela muda — se a criança
     * já estava num app quando o horário fechou, nenhum evento vem e ela seguia
     * usando. Guardamos o pacote para reavaliar periodicamente e ao desbloquear a tela.
     */
    private var foregroundPkg: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val recheck = object : Runnable {
        override fun run() {
            enforceForeground()
            handler.postDelayed(this, RECHECK_MS)
        }
    }

    /** Tela ligou/desbloqueou: o app que ficou aberto volta sem gerar evento de janela. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = enforceForeground()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )
        handler.postDelayed(recheck, RECHECK_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(recheck)
        runCatching { unregisterReceiver(screenReceiver) }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event?.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) return
        val pkg = event.packageName?.toString() ?: return
        // Barra, diálogos do sistema e o teclado aparecem por cima do app sem tirá-lo
        // da frente: não contam como troca de app.
        if (pkg !in overlays()) foregroundPkg = pkg
        val blocked = shouldBlock(pkg)
        logUsage(pkg, blocked)
        if (blocked) goHome(pkg)
    }

    /** Reavalia o app que ficou na frente (horário pode ter fechado com ele aberto). */
    private fun enforceForeground() {
        val pkg = foregroundPkg ?: return
        if (shouldBlock(pkg)) goHome(pkg)
    }

    /** Registra no histórico a abertura de um app "de verdade" (não o sistema/nós). */
    private fun logUsage(pkg: String, blocked: Boolean) {
        if (pkg == packageName || pkg in essentials()) return
        if (isInstaller(pkg)) return
        val label = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
        UsageLog.record(this, pkg, label, blocked)
    }

    override fun onInterrupt() {}

    private fun shouldBlock(pkg: String): Boolean {
        // O próprio app (launcher, setup, bloqueio) nunca é bloqueado.
        if (pkg == packageName) return false
        // Responsável autenticado (PIN há pouco): libera tudo, inclusive as
        // Configurações e a instalação — o pai acessa o sistema, a criança não.
        if (GuardianArea.isUnlocked()) return false
        // Modo manutenção: o responsável liberou o aparelho por um tempo.
        if (GuardianArea.isPaused()) return false
        // Alguma tela do responsável está aberta (ex.: o diálogo do PIN): não bloqueia
        // nada, senão o teclado — que é outro pacote — seria expulso e fecharia o PIN.
        if (GuardianArea.inForeground) return false

        // Instalar apps (Play e a tela "deseja instalar?") é sempre bloqueado, para a
        // criança não instalar por um link/APK. Só libera na janela em que o PAI manda
        // instalar pelo painel (InstallAllow) — aí a instalação dele conclui.
        if (isInstaller(pkg)) return !InstallAllow.isPlayAllowed(this)

        // Essenciais do sistema: barra/diálogos e o discador (receber chamadas).
        if (pkg in essentials()) return false

        // Fora do horário permitido, só o nosso app (que mostra o aviso de descanso).
        if (checkRules(this).blocked) return true
        // No horário: bloqueia o que não está na lista de liberados.
        return pkg !in KioskManager(this).allowedPackages
    }

    /** Play Store ou qualquer instalador de pacotes (inclusive variações das fabricantes). */
    private fun isInstaller(pkg: String): Boolean =
        pkg == PLAY_PACKAGE || pkg.contains("packageinstaller") || pkg == "com.android.packageinstaller"

    private fun goHome(pkg: String) {
        // O HOME global sempre funciona (não sofre a restrição de abrir tela em segundo
        // plano); em seguida abrimos o nosso launcher, que mostra o aviso de descanso.
        performGlobalAction(GLOBAL_ACTION_HOME)
        runCatching {
            startActivity(
                Intent(this, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
        // Tirar da frente não encerra o app: ele ficaria em segundo plano e voltaria ao
        // primeiro toque. Fora do horário, encerra todos os apps da criança; senão, só o bloqueado.
        if (checkRules(this).blocked) AppCloser.closeAllSoon(this)
        else AppCloser.closeOneSoon(this, pkg)
    }

    private fun essentials(): Set<String> {
        val set = overlays().toMutableSet()
        runCatching {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                ?.let { set.add(it) }
        }
        return set
    }

    /** Sistema (barra/diálogos) e teclado: janelas que sobrepõem o app da frente. */
    private fun overlays(): Set<String> {
        val set = mutableSetOf("com.android.systemui", "android")
        // O teclado (IME) atual nunca pode ser bloqueado, senão nenhum campo de texto
        // (PIN, busca, login) funciona — a tela fecharia ao abrir o teclado.
        runCatching {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        }
        return set
    }

    companion object {
        const val PLAY_PACKAGE = "com.android.vending"

        /** Intervalo da reavaliação do app em primeiro plano. */
        private const val RECHECK_MS = 5_000L

        /** O serviço de bloqueio está ligado pelo responsável? */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${AppBlockerService::class.java.name}"
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabled)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            return false
        }
    }
}
