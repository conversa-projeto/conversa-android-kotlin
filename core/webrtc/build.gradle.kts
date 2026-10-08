plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.hilt)
}

android {
    namespace = "com.conversa.app.core.webrtc"
}

dependencies {
    // ConversaApi (ICE) e o OkHttp do app (WHIP/WHEP).
    implementation(projects.core.network)
    api(libs.webrtc.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
