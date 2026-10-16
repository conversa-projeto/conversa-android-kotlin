plugins {
    alias(libs.plugins.conversa.android.library)
    alias(libs.plugins.conversa.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.conversa.app.core.database"
}

androidComponents {
    onVariants { variante ->
        // Os esquemas como assets dos testes unitários: o MigrationTestHelper os lê (MigracoesTest).
        variante.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]
            ?.sources?.assets?.addStaticSourceDirectory("$projectDir/schemas")
    }
}

room {
    // Schemas versionados para testar migrações (docs: FC-110).
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
}
