plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.hilt)
}

android {
    namespace = "com.conversa.app.core.registro"
}

dependencies {
    // Quem registra usa o Timber direto; a árvore deste módulo grava em arquivo.
    api(libs.timber)
}
