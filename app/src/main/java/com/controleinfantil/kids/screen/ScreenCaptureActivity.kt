package com.controleinfantil.kids.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Pega o consentimento de captura de tela e repassa ao [ScreenShareService].
 *
 * O Android **obriga** este toque de "Iniciar agora" no próprio aparelho antes de
 * qualquer captura de tela — é uma proteção do sistema, e eu não a contorno. Depois
 * de iniciada, o ícone de transmissão fica visível na barra de status.
 *
 * A tela é transparente e some assim que a decisão é tomada.
 */
class ScreenCaptureActivity : AppCompatActivity() {

    private val projectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val consent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            ScreenShareService.start(this, result.resultCode, result.data!!, sessionId)
        } else {
            Log.i(TAG, "Consentimento de captura de tela negado")
        }
        finish()
    }

    private val sessionId: String
        get() = intent?.getStringExtra(EXTRA_SESSION).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (sessionId.isEmpty()) {
            Log.e(TAG, "Sem session_id; nada a compartilhar")
            finish()
            return
        }
        consent.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private const val TAG = "ScreenCaptureActivity"
        private const val EXTRA_SESSION = "session_id"

        /** Abre a tela de consentimento (chamada a partir do serviço de comandos). */
        fun launch(context: Context, sessionId: String) {
            val intent = Intent(context, ScreenCaptureActivity::class.java)
                .putExtra(EXTRA_SESSION, sessionId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
