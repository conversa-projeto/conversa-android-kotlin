// com.example.conversa.main/MyApp.kt
package com.example.conversa.main

import android.app.Application
import com.example.conversa.utils.ApiConfig // Importe o novo ApiConfig

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Inicializa o ApiConfig com o Context da aplicação
        ApiConfig.init(this)
    }
}