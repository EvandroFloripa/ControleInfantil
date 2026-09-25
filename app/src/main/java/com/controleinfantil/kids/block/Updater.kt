package com.controleinfantil.kids.block

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.util.Log
import com.controleinfantil.kids.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Atualização automática: como **Device Owner**, o app consulta a última Release no
 * GitHub e, se houver versão nova, baixa o APK e instala em silêncio (via
 * [SilentInstaller]) — sem ninguém precisar mexer no celular.
 *
 * Sem Device Owner isto não roda (a instalação pediria confirmação); nesse caso a
 * atualização é manual.
 */
object Updater {

    private const val TAG = "Updater"
    private const val LATEST =
        "https://api.github.com/repos/EvandroFloripa/ControleInfantil/releases/latest"

    fun checkAndUpdate(context: Context) {
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(context.packageName)) return

        try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val resp = client.newCall(
                Request.Builder().url(LATEST)
                    .header("Accept", "application/vnd.github+json").build()
            ).execute()
            val body = resp.use { if (it.isSuccessful) it.body?.string() else null } ?: return

            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            if (tag.isEmpty() || !isNewer(tag, BuildConfig.VERSION_NAME)) return

            val assets = json.optJSONArray("assets") ?: return
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url"); break
                }
            }
            val url = apkUrl ?: return
            Log.i(TAG, "Atualizando para $tag")
            SilentInstaller.install(context, url)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao checar/atualizar", e)
        }
    }

    /** Compara "1.2.3" ignorando sufixos; true se [remote] for maior que [local]. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    private fun parts(v: String): List<Int> =
        v.substringBefore('-').substringBefore('+')
            .split('.').map { it.toIntOrNull() ?: 0 }
}
