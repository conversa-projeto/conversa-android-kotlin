plugins {
    alias(libs.plugins.conversa.jvm.library)
}

// Utilitários de teste compartilhados: regra do dispatcher principal e
// os JSONs reais do contrato (fixtures).
dependencies {
    api(libs.junit)
    api(libs.truth)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
    api(libs.mockk)
}
