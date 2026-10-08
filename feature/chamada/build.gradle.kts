plugins {
    alias(libs.plugins.conversa.android.feature)
}

android {
    namespace = "com.conversa.app.feature.chamada"
}

dependencies {
    // Mídia da chamada (WHIP/WHEP).
    implementation(projects.core.webrtc)
    implementation(libs.timber)
}
