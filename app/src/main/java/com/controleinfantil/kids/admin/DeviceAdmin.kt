package com.controleinfantil.kids.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.util.Log

/**
 * Receiver de administração do dispositivo.
 *
 * Quando o app é registrado como Device Admin (ou, melhor ainda, Device Owner via
 * provisionamento por ADB), este componente é o "ponto de entrada" das políticas.
 * Veja [PolicyManager] para as ações em si (bloquear, reiniciar, etc.).
 */
class DeviceAdmin : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: android.content.Intent) {
        Log.i(TAG, "Device Admin habilitado")
    }

    override fun onDisableRequested(
        context: Context,
        intent: android.content.Intent
    ): CharSequence {
        return "Desativar o Controle Infantil vai remover a proteção do aparelho."
    }

    companion object {
        private const val TAG = "DeviceAdmin"

        /** Componente usado em todas as chamadas de DevicePolicyManager. */
        fun component(context: Context): ComponentName =
            ComponentName(context.applicationContext, DeviceAdmin::class.java)
    }
}
