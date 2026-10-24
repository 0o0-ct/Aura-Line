package com.auraline.app.di

import android.content.Context
import com.auraline.core.data.AudioRepositoryImpl
import com.auraline.core.domain.AudioRepository

object AuraLineModule {

    fun provideRepository(context: Context): AudioRepository {
        return AudioRepositoryImpl(context)
    }
}
