plugins {
    alias(libs.plugins.conversa.android.library)
}

android {
    namespace = "com.conversa.app.core.webrtc"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
