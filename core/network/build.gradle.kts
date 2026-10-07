plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.conversa.app.core.network"
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
}
