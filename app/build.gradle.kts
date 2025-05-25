plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.conversa"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.conversa"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    }
}

dependencies {
    // OkHttp já está, vamos adicionar os módulos para Retrofit e Logging
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")

    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // ESTA É A LINHA CORRETA E ÚNICA PARA CONSTRAINTOUT
    implementation(libs.androidx.constraintlayout) {
        // Correção para Kotlin DSL: Use `because` para explicar o motivo,
        // e adicione `version { strictly("2.1.4") }` para forçar a versão.
        // A versão "2.1.4" deve vir do libs.versions.toml como reference.
        // Ou, se você quiser *realmente* sobrescrever, pode colocar a string da versão aqui.
        // Vamos usar a referência para manter a consistência com libs.versions.toml.
        version {
            // Este é o método para forçar uma versão específica
            // O nome "constraintlayout" aqui deve ser o mesmo usado no seu libs.versions.toml
            // Ex: if libs.versions.toml has `constraintlayout = "2.1.4"`, then use `versions.constraintlayout`
            strictly(libs.versions.constraintlayout.get()) // Pega a versão 2.1.4 do libs.versions.toml
        }
        // Opcional: Adicione um comentário para o Gradle para depuração
        because("Force ConstraintLayout version to 2.1.4 due to linking issues")
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)

    implementation(libs.androidx.annotation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}