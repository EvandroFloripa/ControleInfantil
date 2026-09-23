package com.controleinfantil.kids.admin

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log

/**
 * Camada única sobre o [DevicePolicyManager]. Todas as ações de controle passam por
 * aqui, para deixar claro o que exige apenas Device Admin e o que exige Device Owner.
 *
 * - **Device Admin** (o responsável ativa nas configurações): bloquear tela.
 * - **Device Owner** (instalado via ADB em aparelho resetado): reiniciar, forçar GPS,
 *   modo quiosque completo. Veja `docs/PROVISIONAMENTO.md`.
 */
class PolicyManager(private val context: Context) {

    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin = DeviceAdmin.component(context)

    val isAdminActive: Boolean get() = dpm.isAdminActive(admin)
    val isDeviceOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    /** Bloqueia a tela imediatamente. Requer Device Admin. */
    fun lockNow(): Result {
        if (!isAdminActive) return Result.NeedsAdmin
        return try {
            dpm.lockNow()
            Result.Ok
        } catch (e: SecurityException) {
            Log.e(TAG, "lockNow falhou", e)
            Result.Error(e.message)
        }
    }

    /** Reinicia o aparelho. Requer Device Owner (API 24+). */
    fun reboot(): Result {
        if (!isDeviceOwner) return Result.NeedsOwner
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                dpm.reboot(admin)
                Result.Ok
            } else {
                Result.Error("Reboot exige Android 7+")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "reboot falhou", e)
            Result.Error(e.message)
        }
    }

    /**
     * Garante que a localização esteja ligada. Em Device Owner (API 30+) dá para
     * forçar; abaixo disso, o melhor que um app pode fazer é abrir a tela de
     * configurações de localização para o responsável ligar.
     */
    fun ensureLocationEnabled(): Result {
        return try {
            if (isDeviceOwner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                dpm.setLocationEnabled(admin, true)
                Result.Ok
            } else {
                Result.NeedsOwner
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "setLocationEnabled falhou", e)
            Result.Error(e.message)
        }
    }

    /**
     * Como Device Owner, concede câmera, microfone e localização ao próprio app, para
     * o check-in conectar sem ninguém tocar em "permitir". Sem Device Owner, não faz
     * nada — aí a permissão é pedida à criança na primeira vez (e pode não ser aceita).
     */
    fun grantMediaPermissions() {
        if (!isDeviceOwner || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val permissions = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
        )
        permissions.forEach { permission ->
            try {
                dpm.setPermissionGrantState(
                    admin, context.packageName, permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED,
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "setPermissionGrantState falhou para $permission", e)
            }
        }
    }

    fun isLocationEnabled(): Boolean {
        val lm =
            context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            ) != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    sealed interface Result {
        data object Ok : Result
        /** Falta ativar Device Admin. */
        data object NeedsAdmin : Result
        /** Falta ser Device Owner (provisionar por ADB). */
        data object NeedsOwner : Result
        data class Error(val message: String?) : Result
    }

    companion object {
        private const val TAG = "PolicyManager"
    }
}
