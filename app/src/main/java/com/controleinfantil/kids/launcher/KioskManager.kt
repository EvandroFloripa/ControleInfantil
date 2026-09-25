package com.controleinfantil.kids.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.SharedPreferences
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.controleinfantil.kids.admin.DeviceAdmin

/**
 * Controla o "modo quiosque": a criança só enxerga os apps que o responsável
 * liberou, e não consegue sair da tela inicial nem abrir outros apps.
 *
 * A lista de apps liberados é guardada localmente (e pode ser sincronizada com o
 * backend depois). Em Device Owner conseguimos travar de verdade (Lock Task);
 * sem Device Owner, o launcher ainda limita o que aparece, mas a proteção é mais
 * fraca.
 */
class KioskManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("kiosk", Context.MODE_PRIVATE)
    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = DeviceAdmin.component(context)

    private val isOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    /**
     * Pacotes que a criança pode abrir, escolhidos pelo responsável. Não inclui o
     * próprio Controle Infantil — ele é acrescentado só no Lock Task, que precisa
     * dele para fixar o launcher.
     */
    var allowedPackages: Set<String>
        get() = prefs.getStringSet(KEY_ALLOWED, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_ALLOWED, value).apply()
            applyLockTaskAllowlist()
        }

    fun allow(pkg: String) {
        allowedPackages = allowedPackages + pkg
    }

    fun disallow(pkg: String) {
        allowedPackages = allowedPackages - pkg
    }

    /**
     * Aplica a lista de apps permitidos ao Lock Task (só funciona como Device
     * Owner). Chame ao configurar o aparelho e sempre que a lista mudar.
     */
    fun applyLockTaskAllowlist() {
        if (!isOwner) return
        try {
            // O nosso pacote precisa entrar para o launcher poder ser fixado.
            val packages = allowedPackages + context.packageName
            dpm.setLockTaskPackages(admin, packages.toTypedArray())
            applyLockTaskFeatures()
        } catch (e: SecurityException) {
            Log.e(TAG, "setLockTaskPackages falhou", e)
        }
    }

    /**
     * Por padrão o Lock Task esconde a barra de status e desliga Início/Recentes: o
     * launcher pareceria um app em tela cheia e a criança não veria as notificações
     * — inclusive a do check-in, que precisa estar sempre visível na barra.
     *
     * Liberamos o que um launcher normal tem. As Configurações rápidas continuam
     * bloqueadas pelo próprio Lock Task (a criança não desliga Wi-Fi nem dados), e
     * Início/Recentes só alcançam os apps liberados.
     */
    private fun applyLockTaskFeatures() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        dpm.setLockTaskFeatures(
            admin,
            DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or      // relógio, bateria, sinal
                DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or // barra de notificações
                DevicePolicyManager.LOCK_TASK_FEATURE_HOME or          // botão Início
                DevicePolicyManager.LOCK_TASK_FEATURE_OVERVIEW or      // Recentes
                DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS or // menu de desligar
                DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD,        // tela de bloqueio
        )
    }

    /**
     * Como Device Owner, torna o launcher impossível de trocar ou fechar:
     * - fixa-o como tela inicial (Início sempre volta para ele, sem escolher outro);
     * - bloqueia a desinstalação;
     * - bloqueia, nas Configurações, forçar parada / limpar dados / desativar apps;
     * - bloqueia o modo de segurança (que desliga launchers de terceiros), a
     *   restauração de fábrica pelas Configurações e a criação de outros usuários.
     *
     * Sem Device Owner nada disso é possível: o Android sempre deixa a pessoa trocar
     * o launcher. Por isso o celular da criança precisa ser provisionado.
     */
    fun enforceLauncherLockdown() {
        if (!isOwner) return
        try {
            dpm.setUninstallBlocked(admin, context.packageName, true)
            listOf(
                UserManager.DISALLOW_APPS_CONTROL,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_ADD_USER,
            ).forEach { dpm.addUserRestriction(admin, it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "Falha ao aplicar as restrições do aparelho", e)
        }
        try {
            val filter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            dpm.addPersistentPreferredActivity(
                admin, filter, ComponentName(context, LauncherActivity::class.java)
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "addPersistentPreferredActivity falhou", e)
        }
    }

    /**
     * Desfaz tudo o que [enforceLauncherLockdown] e o quiosque aplicaram e deixa de
     * ser Device Owner. Depois disso o app volta a ser um app comum: dá para trocar
     * o launcher e desinstalar. Só a área do responsável (com PIN) chama isto.
     */
    fun releaseDevice(activity: Activity): Boolean {
        if (!isOwner) return false
        return try {
            runCatching { activity.stopLockTask() }
            dpm.setLockTaskPackages(admin, emptyArray())
            dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
            dpm.setUninstallBlocked(admin, context.packageName, false)
            listOf(
                UserManager.DISALLOW_APPS_CONTROL,
                UserManager.DISALLOW_SAFE_BOOT,
                UserManager.DISALLOW_FACTORY_RESET,
                UserManager.DISALLOW_ADD_USER,
            ).forEach { dpm.clearUserRestriction(admin, it) }
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(context.packageName)
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Falha ao liberar o aparelho", e)
            false
        }
    }

    /** O nosso launcher é a tela inicial padrão do aparelho? */
    fun isDefaultHome(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == context.packageName
    }

    /**
     * Pedido para virar a tela inicial, quando não somos Device Owner. No Android
     * 10+ é o diálogo do sistema; antes disso, a tela de "App de início".
     */
    fun homeRequestIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roles = context.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    val canForceHome: Boolean get() = isOwner

    /** Entra no modo de tarefa fixada (kiosk real) — usado pelo launcher. */
    fun startLockTask(activity: Activity) {
        if (!isOwner) return
        // Modo manutenção: não fixa (e sai do quiosque) enquanto o responsável liberou.
        if (com.controleinfantil.kids.setup.GuardianArea.isPaused()) {
            runCatching { activity.stopLockTask() }
            return
        }
        try {
            // Sempre reaplica antes de fixar: depois de um reboot, ou se o app nunca
            // passou pela tela de escolha, a lista estaria vazia e o quiosque viraria
            // uma fixação de tela comum, da qual a criança consegue sair.
            applyLockTaskAllowlist()
            activity.startLockTask()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "startLockTask falhou", e)
        }
    }

    companion object {
        private const val TAG = "KioskManager"
        private const val KEY_ALLOWED = "allowed_packages"
    }
}
