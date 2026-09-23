package com.controleinfantil.kids.setup

import androidx.appcompat.app.AppCompatActivity

/**
 * Base das telas do responsável (configuração, escolha de apps, limites).
 *
 * Enquanto uma delas está aberta, os limites de horário não apagam a tela — senão o
 * responsável seria interrompido a cada ciclo justamente quando tenta ajustar as
 * regras que estão bloqueando o aparelho.
 */
abstract class GuardianActivity : AppCompatActivity() {

    override fun onResume() {
        super.onResume()
        GuardianArea.inForeground = true
    }

    override fun onPause() {
        super.onPause()
        GuardianArea.inForeground = false
    }
}

/** Sinaliza se alguma tela do responsável está em primeiro plano. */
object GuardianArea {
    @Volatile
    var inForeground: Boolean = false
}
