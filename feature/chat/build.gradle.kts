plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.chat"
}

dependencies {
    // Seletores do sistema (galeria, documento, câmera) para os anexos.
    implementation(libs.androidx.activity.compose)
}
