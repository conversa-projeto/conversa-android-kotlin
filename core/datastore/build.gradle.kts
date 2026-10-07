plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.conversa.app.core.datastore"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.datastore)
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.tink.android)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
}
