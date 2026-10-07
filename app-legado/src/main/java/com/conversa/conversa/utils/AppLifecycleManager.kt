package com.conversa.conversa.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log

/**
 * Gerencia o ciclo de vida do app para detectar se está em foreground/background
 */
object AppLifecycleManager : Application.ActivityLifecycleCallbacks {
    
    private const val TAG = "AppLifecycleManager"
    
    private var activityReferences = 0
    private var isActivityChangingConfigurations = false
    
    var isAppInForeground = false
        private set
    
    var currentActivity: Activity? = null
        private set
    
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        currentActivity = activity
    }
    
    override fun onActivityStarted(activity: Activity) {
        if (++activityReferences == 1 && !isActivityChangingConfigurations) {
            isAppInForeground = true
            Log.d(TAG, "✅ App entrou em FOREGROUND")
        }
        currentActivity = activity
    }
    
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    
    override fun onActivityPaused(activity: Activity) {
        // Mantém referência
    }
    
    override fun onActivityStopped(activity: Activity) {
        isActivityChangingConfigurations = activity.isChangingConfigurations
        if (--activityReferences == 0 && !isActivityChangingConfigurations) {
            isAppInForeground = false
            Log.d(TAG, "❌ App entrou em BACKGROUND")
        }
    }
    
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        // Não utilizado
    }
    
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
