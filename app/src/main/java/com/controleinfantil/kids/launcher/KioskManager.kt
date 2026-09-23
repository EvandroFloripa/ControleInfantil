package com.controleinfantil.kids.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.SharedPreferences
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
        } catch (e: SecurityException) {
            Log.e(TAG, "setLockTaskPackages falhou", e)
        }
    }

    /** Entra no modo de tarefa fixada (kiosk real) — usado pelo launcher. */
    fun startLockTask(activity: Activity) {
        if (!isOwner) return
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
