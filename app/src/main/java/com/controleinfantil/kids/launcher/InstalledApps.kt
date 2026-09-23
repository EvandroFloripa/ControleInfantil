package com.controleinfantil.kids.launcher

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable

/** Um app que a criança pode ver no launcher ou que aparece na tela de escolha. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)

/**
 * Lista os apps que têm ícone de abertura, já ordenados por nome.
 *
 * O próprio Controle Infantil fica de fora: ele é o launcher, não é uma escolha.
 * Carregar ícones é lento — chame fora da thread principal.
 */
fun Context.launchableApps(): List<AppInfo> {
    val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != packageName }
        .mapNotNull { pkg ->
            runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                AppInfo(
                    packageName = pkg,
                    label = packageManager.getApplicationLabel(info).toString(),
                    icon = packageManager.getApplicationIcon(info),
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}
