package com.controleinfantil.kids

import android.app.Application
import com.controleinfantil.kids.remote.CommandService

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // Garante que o serviço de comandos suba junto com o app.
        CommandService.start(this)
    }
}
