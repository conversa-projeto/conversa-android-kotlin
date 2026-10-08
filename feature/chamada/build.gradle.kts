plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.chamada"
}

dependencies {
    // Mídia da chamada (WHIP/WHEP).
    implementation(projects.core.webrtc)
    // ComponentActivity da tela da chamada e os pedidos de permissão.
    implementation(libs.androidx.activity.compose)
    implementation(libs.timber)
}
