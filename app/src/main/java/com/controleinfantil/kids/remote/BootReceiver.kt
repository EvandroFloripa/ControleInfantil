package com.controleinfantil.kids.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reinicia o serviço de comandos após o aparelho ligar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            CommandService.start(context)
        }
    }
}
