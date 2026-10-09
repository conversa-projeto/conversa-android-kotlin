plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.config"
}

dependencies {
    // Photo Picker da foto de perfil.
    implementation(libs.androidx.activity.compose)
}
