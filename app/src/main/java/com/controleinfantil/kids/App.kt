package com.controleinfantil.kids

import android.app.Application
import com.controleinfantil.kids.launcher.KioskManager
import com.controleinfantil.kids.remote.CommandService
import com.controleinfantil.kids.setup.GuardianArea

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Antes de tudo: o bloqueio e o quiosque consultam se o controle está desativado.
        GuardianArea.init(this)
        // Garante que o serviço de comandos suba junto com o app.
        CommandService.start(this)
        // Reaplica as travas do launcher a cada início (inclusive após reiniciar),
        // antes mesmo de a tela inicial abrir.
        KioskManager(this).enforceLauncherLockdown()
    }
}
