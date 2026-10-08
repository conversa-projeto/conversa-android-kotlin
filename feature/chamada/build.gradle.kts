plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.chamada"
}

dependencies {
    // Mídia da chamada (WHIP/WHEP).
    implementation(projects.core.webrtc)
    // Parar o áudio de mensagem ao entrar em chamada (6.8).
    implementation(projects.core.media)
    // ComponentActivity da tela da chamada e os pedidos de permissão.
    implementation(libs.androidx.activity.compose)
    // Chamadas pelo sistema (6.3): fone Bluetooth, chamada GSM concorrente, rotas de áudio.
    implementation(libs.androidx.core.telecom)
    implementation(libs.timber)
}
