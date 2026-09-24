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

/** Um app abrível, sem ícone, com a categoria — para reportar ao painel. */
data class AppEntry(val packageName: String, val label: String, val category: String)

/**
 * Pacote, nome e categoria dos apps abríveis, sem carregar ícones — leve o bastante
 * para reportar a lista ao painel a cada ciclo, sem o custo de [launchableApps].
 */
fun Context.launchablePackages(): List<AppEntry> {
    val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, 0)
        .map { it.activityInfo.packageName }
        .distinct()
        .filter { it != packageName }
        .mapNotNull { pkg ->
            runCatching {
                val info = packageManager.getApplicationInfo(pkg, 0)
                AppEntry(
                    packageName = pkg,
                    label = packageManager.getApplicationLabel(info).toString(),
                    category = categoryKey(info.category),
                )
            }.getOrNull()
        }
        .sortedBy { it.label.lowercase() }
}

/** Categoria do Android para uma chave estável (o painel traduz para o rótulo). */
private fun categoryKey(category: Int): String = when (category) {
    android.content.pm.ApplicationInfo.CATEGORY_GAME -> "game"
    android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> "audio"
    android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> "video"
    android.content.pm.ApplicationInfo.CATEGORY_IMAGE -> "image"
    android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> "social"
    android.content.pm.ApplicationInfo.CATEGORY_NEWS -> "news"
    android.content.pm.ApplicationInfo.CATEGORY_MAPS -> "maps"
    android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> "productivity"
    else -> "other"
}
