package com.controleinfantil.kids.lock

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.controleinfantil.kids.R
import com.controleinfantil.kids.setup.GuardianPin

/**
 * Bloqueio real da tela, sem depender de Device Owner: uma janela de sobreposição
 * (overlay) por cima de qualquer app, que só sai com o PIN do responsável.
 *
 * O `dpm.lockNow()` sozinho apenas apaga a tela — se o aparelho não tem senha, um
 * toque volta tudo. Este overlay cobre a tela e captura os toques até o PIN certo.
 *
 * Precisa da permissão "sobrepor a outros apps" (SYSTEM_ALERT_WINDOW), pedida no
 * setup. Sem ela, [show] devolve false e o chamador cai para o lockNow().
 */
object LockScreen {

    private const val TAG = "LockScreen"
    private const val PREFS = "lock"
    private const val KEY_LOCKED = "locked"
    private var view: View? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    val isShowing: Boolean get() = view != null

    /**
     * Ficou bloqueado? Persistido para o bloqueio sobreviver ao app ser morto (a
     * criança fechar pelos Recentes): ao reiniciar, o serviço reaplica o bloqueio.
     */
    fun shouldRestore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_LOCKED, false)

    private fun setLocked(context: Context, locked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LOCKED, locked).apply()
    }

    /** Mostra o bloqueio. Devolve false se falta a permissão de sobreposição. */
    fun show(context: Context): Boolean {
        if (!canShow(context)) return false
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            if (view != null) return@post
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val v = LayoutInflater.from(app).inflate(R.layout.overlay_lock, null)

            val pin = v.findViewById<EditText>(R.id.lockPin)
            val error = v.findViewById<TextView>(R.id.lockError)
            v.findViewById<Button>(R.id.lockUnlock).setOnClickListener {
                // Só o PIP do responsável tira o bloqueio, com o mesmo limite de
                // tentativas da área do responsável (contagem gravada, trava crescente)
                // para o PIN de 4 dígitos não poder ser tentado à vontade.
                val remaining = GuardianPin.lockoutRemainingMs(app)
                if (remaining > 0) {
                    error.text = app.getString(R.string.pin_too_many, waitText(app, remaining))
                    error.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (GuardianPin.verify(app, pin.text.toString())) {
                    GuardianPin.clearFailures(app)
                    hide(app)
                } else {
                    pin.text.clear()
                    val lock = GuardianPin.registerFailure(app)
                    error.text =
                        if (lock > 0) app.getString(R.string.pin_too_many, waitText(app, lock))
                        else app.getString(R.string.pin_wrong)
                    error.visibility = View.VISIBLE
                }
            }

            val type =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                // Focável (para digitar o PIN) e cobrindo a tela toda.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE,
            )
            try {
                wm.addView(v, params)
                view = v
                setLocked(app, true)
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao mostrar o bloqueio", e)
            }
        }
        return true
    }

    private fun waitText(context: Context, ms: Long): String {
        val seconds = (ms + 999) / 1000
        return if (seconds < 60) context.getString(R.string.duration_seconds, seconds.toInt())
        else context.getString(R.string.duration_minutes, ((seconds + 59) / 60).toInt())
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        setLocked(app, false)
        Handler(Looper.getMainLooper()).post {
            val v = view ?: return@post
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            runCatching { wm.removeView(v) }
            view = null
        }
    }
}
