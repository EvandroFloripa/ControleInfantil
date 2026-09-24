package com.controleinfantil.kids.block

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (shouldBlock(pkg)) goHome()
    }

    override fun onInterrupt() {}

    private fun shouldBlock(pkg: String): Boolean {
        // O próprio app (launcher, setup, bloqueio) nunca é bloqueado.
        if (pkg == packageName) return false
        // Responsável autenticado (PIN há pouco): libera tudo, inclusive as
        // Configurações — assim o pai acessa o sistema, mas a criança (sem PIN) não.
        if (GuardianArea.isUnlocked()) return false
        // Essenciais do sistema: barra/diálogos e o discador (receber chamadas).
        if (pkg in essentials()) return false
        // Janela curta liberada para instalar pela Play (ver InstallAllow).
        if (InstallAllow.isPlayAllowed(this) && pkg == PLAY_PACKAGE) return false

        // Fora do horário permitido, só o nosso app (que mostra o aviso de descanso).
        if (checkRules(this).blocked) return true
        // No horário: bloqueia o que não está na lista de liberados.
        return pkg !in KioskManager(this).allowedPackages
    }

    private fun goHome() {
        runCatching {
            startActivity(
                Intent(this, LauncherActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    private fun essentials(): Set<String> {
        val set = mutableSetOf("com.android.systemui", "android")
        runCatching {
            getSystemService(TelecomManager::class.java)?.defaultDialerPackage
                ?.let { set.add(it) }
        }
        return set
    }

    companion object {
        const val PLAY_PACKAGE = "com.android.vending"

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
