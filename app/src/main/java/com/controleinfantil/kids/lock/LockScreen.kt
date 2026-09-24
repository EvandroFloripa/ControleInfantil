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
    private var view: View? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    val isShowing: Boolean get() = view != null

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
                // Sem PIN configurado, qualquer entrada tira o bloqueio (não trava o
                // aparelho para sempre); com PIN, exige o PIN certo.
                val ok = !GuardianPin.isSet(app) || GuardianPin.verify(app, pin.text.toString())
                if (ok) {
                    hide(app)
                } else {
                    pin.text.clear()
                    error.text = app.getString(R.string.pin_wrong)
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
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao mostrar o bloqueio", e)
            }
        }
        return true
    }

    fun hide(context: Context) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            val v = view ?: return@post
            val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            runCatching { wm.removeView(v) }
            view = null
        }
    }
}
