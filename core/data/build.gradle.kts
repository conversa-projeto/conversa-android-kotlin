plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.conversa.app.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.network)
    api(projects.core.database)
    api(projects.core.datastore)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)

    testImplementation(projects.core.testing)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
