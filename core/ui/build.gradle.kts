plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.android.compose)
}

android {
    namespace = "com.conversa.app.core.ui"
}

dependencies {
    implementation(projects.core.model)
    api(libs.compose.material.icons.extended)
    api(libs.coil.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
}
