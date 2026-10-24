package com.auraline.app.di

import android.content.Context
import com.auraline.core.data.AudioRepositoryImpl
import com.auraline.core.domain.AudioRepository

/**
 * Contenedor mínimo. Una única instancia del repositorio para que la actividad, el
 * servicio y los receivers compartan el mismo motor de audio.
 */
object AuraLineModule {

    @Volatile
    private var instance: AudioRepository? = null

    fun repository(context: Context): AudioRepository {
        return instance ?: synchronized(this) {
            instance ?: AudioRepositoryImpl(context.applicationContext).also { instance = it }
        }
    }
}
