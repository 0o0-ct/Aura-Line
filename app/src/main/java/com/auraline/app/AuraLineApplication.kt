package com.auraline.app

import android.app.Application
import com.auraline.app.di.AuraLineModule

class AuraLineApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Construye el repositorio (y con él el motor) antes de que la UI lo necesite.
        AuraLineModule.repository(this)
    }
}
