plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.conversas"
}

dependencies {
    // BackHandler do "Enviar para…" (voltar descarta o compartilhamento).
    implementation(libs.androidx.activity.compose)

    // Barra de título com a pesquisa (Compose no Robolectric).
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
