plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.chat"
}

dependencies {
    // Seletores do sistema (galeria, documento, câmera) para os anexos.
    implementation(libs.androidx.activity.compose)
    // Player de áudio único (4.5).
    implementation(projects.core.media)
    // Ligar a partir da conversa (6.4).
    implementation(projects.feature.chamada)
}
