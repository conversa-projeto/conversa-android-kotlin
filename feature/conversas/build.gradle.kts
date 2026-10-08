plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.conversas"
}

dependencies {
    // BackHandler do "Enviar para…" (voltar descarta o compartilhamento).
    implementation(libs.androidx.activity.compose)
}
