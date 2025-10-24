package com.example.conversaandroid

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ConversaApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Inicialização global pode ser adicionada aqui
    }
}