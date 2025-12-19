package com.conversa.conversa

import android.app.Application
import com.conversa.conversa.utils.AppLifecycleManager

class ConversaApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Registra o gerenciador de lifecycle para detectar foreground/background
        registerActivityLifecycleCallbacks(AppLifecycleManager)
    }
}
