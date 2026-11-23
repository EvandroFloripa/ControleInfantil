package com.controleinfantil.kids.block

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import com.controleinfantil.kids.remote.SupabaseClient

class UninstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val packageName = intent.getStringExtra(RemoteUninstaller.EXTRA_PACKAGE).orEmpty()
        val commandId = intent.getStringExtra(RemoteUninstaller.EXTRA_COMMAND_ID).orEmpty()
        val success = status == PackageInstaller.STATUS_SUCCESS
        val detail = if (success) null else intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            ?: "Falha ao desinstalar (código Android $status)"
        Log.i(TAG, "Desinstalação de $packageName terminou: ${if (success) "ok" else detail}")
        if (commandId.isNotBlank()) {
            SupabaseClient(context).reportCommandResult(
                commandId,
                if (success) "done" else "error",
                detail,
            )
        }
    }

    private companion object { const val TAG = "UninstallReceiver" }
}
