package com.controleinfantil.kids.setup

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.controleinfantil.kids.R

/**
 * Base das telas do responsável (configuração, escolha de apps, limites).
 *
 * Faz duas coisas:
 *
 * 1. **Pede o PIN.** Sem isso a criança abriria a configuração pelo toque longo no
 *    launcher e poderia liberar qualquer app. Na primeira vez, pede para criar.
 * 2. **Suspende os limites de horário** enquanto está aberta, para o responsável não
 *    ser interrompido justamente ao ajustar as regras que bloqueiam o aparelho.
 */
abstract class GuardianActivity : AppCompatActivity() {

    private var pinDialog: AlertDialog? = null

    override fun onResume() {
        super.onResume()
        GuardianArea.inForeground = true
        if (GuardianArea.isUnlocked()) {
            GuardianArea.refresh()
        } else if (pinDialog?.isShowing != true) {
            askForPin()
        }
    }

    override fun onPause() {
        super.onPause()
        GuardianArea.inForeground = false
    }

    override fun onDestroy() {
        pinDialog?.dismiss()
        pinDialog = null
        super.onDestroy()
    }

    private fun askForPin() {
        val creating = !GuardianPin.isSet(this)
        val locked = GuardianPin.lockoutRemainingMs(this)
        if (!creating && locked > 0) {
            toast(getString(R.string.pin_too_many, waitText(locked)))
            finish()
            return
        }
        val view = layoutInflater.inflate(R.layout.dialog_pin, null)
        val input = view.findViewById<EditText>(R.id.pinInput)
        val confirm = view.findViewById<EditText>(R.id.pinConfirm)
        confirm.visibility = if (creating) View.VISIBLE else View.GONE
        view.findViewById<TextView>(R.id.pinMessage)
            .setText(if (creating) R.string.pin_create_message else R.string.pin_enter_message)

        pinDialog = AlertDialog.Builder(this)
            .setTitle(if (creating) R.string.pin_create_title else R.string.pin_enter_title)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok, null)   // ligado abaixo
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .create()
            .apply {
                // Sem isto o diálogo fecharia mesmo com o PIN errado.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (creating) submitNewPin(input, confirm)
                        else submitPin(input)
                    }
                }
                show()
            }
    }

    private fun submitNewPin(input: EditText, confirm: EditText) {
        val pin = input.text.toString()
        when {
            pin.length < GuardianPin.MIN_LENGTH ->
                toast(getString(R.string.pin_too_short, GuardianPin.MIN_LENGTH))
            pin != confirm.text.toString() -> {
                toast(getString(R.string.pin_mismatch))
                confirm.text.clear()
            }
            else -> {
                GuardianPin.set(this, pin)
                unlock()
            }
        }
    }

    private fun submitPin(input: EditText) {
        // Pode ter travado enquanto o diálogo estava aberto (outra tela errou antes).
        val locked = GuardianPin.lockoutRemainingMs(this)
        if (locked > 0) {
            toast(getString(R.string.pin_too_many, waitText(locked)))
            finish()
            return
        }
        if (GuardianPin.verify(this, input.text.toString())) {
            GuardianPin.clearFailures(this)
            unlock()
            return
        }
        input.text.clear()
        val lockout = GuardianPin.registerFailure(this)
        if (lockout > 0) {
            toast(getString(R.string.pin_too_many, waitText(lockout)))
            finish()
        } else {
            toast(getString(R.string.pin_wrong))
        }
    }

    private fun waitText(ms: Long): String {
        val seconds = (ms + 999) / 1000
        return if (seconds < 60) getString(R.string.duration_seconds, seconds.toInt())
        else getString(R.string.duration_minutes, ((seconds + 59) / 60).toInt())
    }

    private fun unlock() {
        GuardianArea.markUnlocked()
        pinDialog?.dismiss()
        pinDialog = null
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * Estado compartilhado da área do responsável: se está aberta (para os limites de
 * horário não bloquearem a tela) e até quando o PIN vale.
 */
object GuardianArea {

    /** Alguma tela do responsável está em primeiro plano. */
    @Volatile
    var inForeground: Boolean = false

    @Volatile
    private var unlockedAt: Long = 0L

    /** Tempo de folga para navegar entre as telas sem redigitar o PIN. */
    private const val GRACE_MS = 3 * 60_000L

    fun isUnlocked(): Boolean =
        System.currentTimeMillis() - unlockedAt < GRACE_MS

    fun markUnlocked() {
        unlockedAt = System.currentTimeMillis()
    }

    /** Estende a folga enquanto o responsável continua na área. */
    fun refresh() {
        if (isUnlocked()) markUnlocked()
    }

    /** Volta a exigir o PIN (a criança recebeu o aparelho de volta). */
    fun lock() {
        unlockedAt = 0L
    }

    // --- Modo manutenção: o responsável libera o aparelho por um tempo ----------
    @Volatile
    private var pausedUntil: Long = 0L

    /** Libera o aparelho (sem quiosque nem bloqueio) por [ms]. */
    fun pauseBlocking(ms: Long) {
        pausedUntil = System.currentTimeMillis() + ms
        markUnlocked()
    }

    /** True enquanto o aparelho está liberado pelo responsável. */
    fun isPaused(): Boolean = System.currentTimeMillis() < pausedUntil

    /** Religa o quiosque/bloqueio na hora. */
    fun resumeBlocking() {
        pausedUntil = 0L
    }
}
