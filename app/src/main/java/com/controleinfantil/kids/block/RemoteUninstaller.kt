package com.controleinfantil.kids.block

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Solicita a desinstalação silenciosa de um app quando este app é Device Owner. */
object RemoteUninstaller {
    const val ACTION_RESULT = "com.controleinfantil.kids.UNINSTALL_RESULT"
    const val EXTRA_PACKAGE = "package_name"
    const val EXTRA_COMMAND_ID = "command_id"

    fun uninstall(context: Context, packageName: String, commandId: String): String {
        if (packageName.isBlank() || packageName == context.packageName) return "error"
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return "needs_owner"
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            val result = Intent(ACTION_RESULT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_PACKAGE, packageName)
                .putExtra(EXTRA_COMMAND_ID, commandId)
            val pending = PendingIntent.getBroadcast(
                context,
                packageName.hashCode(),
                result,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            context.packageManager.packageInstaller.uninstall(packageName, pending.intentSender)
            "started"
        } catch (_: PackageManager.NameNotFoundException) {
            "error"
        } catch (_: SecurityException) {
            "needs_owner"
        } catch (_: Exception) {
            "error"
        }
    }
}
