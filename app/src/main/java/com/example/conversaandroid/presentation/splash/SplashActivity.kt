package com.example.conversaandroid.presentation.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.seudominio.conversa.data.preferences.PreferencesManager
import com.seudominio.conversa.presentation.login.LoginActivity
import com.seudominio.conversa.presentation.main.MainActivity
import com.seudominio.conversa.service.WebSocketService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            delay(1500) // Splash screen por 1.5 segundos

            val isLoggedIn = preferencesManager.getToken() != null

            val intent = if (isLoggedIn) {
                // Iniciar serviço WebSocket
                WebSocketService.start(this@SplashActivity)
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, LoginActivity::class.java)
            }

            startActivity(intent)
            finish()
        }
    }
}

// ============== LAYOUT DA SPLASH ==============
// Arquivo: app/src/main/res/layout/activity_splash.xml
/*
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/colorPrimary">

    <ImageView
        android:id="@+id/imageViewLogo"
        android:layout_width="120dp"
        android:layout_height="120dp"
        android:src="@drawable/ic_launcher_foreground"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent"
        app:layout_constraintVertical_bias="0.4" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="@string/app_name"
        android:textColor="@android:color/white"
        android:textSize="32sp"
        android:textStyle="bold"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toBottomOf="@+id/imageViewLogo" />

    <ProgressBar
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="48dp"
        android:indeterminateTint="@android:color/white"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
*/

// ============== THEME DA SPLASH ==============
// Arquivo: app/src/main/res/values/themes.xml
/*
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="SplashTheme" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="android:statusBarColor">@color/colorPrimary</item>
        <item name="android:navigationBarColor">@color/colorPrimary</item>
    </style>
</resources>
*/