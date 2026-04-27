plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // IMPORTANTE: para FCM funcionar, adicionar plugin google-services:
    // 1. Adicionar em libs.versions.toml: googleServices = { id = "com.google.gms.google-services", version = "4.4.2" }
    // 2. Aplicar aqui: alias(libs.plugins.googleServices)
    // 3. Colocar google-services.json (do Firebase Console) em app/
    // Sem isso as chamadas a FirebaseApp.initializeApp falham em runtime.
}

android {
    namespace = "com.conversa.conversa"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.conversa.conversa"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Configurações da API (alterar conforme seu servidor)
        buildConfigField("String", "API_URL", "\"https://192.168.2.5:4430/api/\"")
        buildConfigField("String", "MEDIAMTX_URL", "\"https://192.168.2.5:4430/webrtc\"")
        buildConfigField("String", "STUN_URL", "\"stun:stun.l.google.com:19302\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    // Networking - Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    
    // WebSocket
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // WebRTC (fork ativo do google-webrtc com AARs pré-compilados)
    // Substitui o antigo org.webrtc:google-webrtc:1.0.32006 descontinuado
    implementation("io.github.webrtc-sdk:android:125.6422.07")

    // Firebase Cloud Messaging (push notifications)
    // Requer google-services.json em app/ e plugin com.google.gms.google-services aplicado
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-messaging-ktx")

    // DataStore para salvar preferências
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    
    // Glide para carregamento de imagens
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // Jetpack Compose
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
