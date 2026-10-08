plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.android.compose)
    alias(libs.plugins.conversa.hilt)
}

android {
    namespace = "com.conversa.app.core.media"
}

dependencies {
    implementation(libs.media3.exoplayer)
    // PlayerView (controles prontos) para os vídeos do visualizador.
    implementation(libs.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
