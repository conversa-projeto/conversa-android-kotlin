import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

// Convention plugins do projeto. Cada módulo aplica um deles em vez de repetir
// a configuração do Android/Kotlin.

/** App Android (módulo :app). */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            extensions.configure<ApplicationExtension> { configurarAplicacao(this) }
            configurarKotlin()
            dependenciasDeTeste()
        }
    }
}

/** Biblioteca Android (módulos :core:*). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> { configurarBiblioteca(this) }
            configurarKotlin()
            dependenciasDeTeste()
        }
    }
}

/** Liga o Compose num módulo que já aplicou application ou library. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.findByType(ApplicationExtension::class.java)?.buildFeatures?.compose = true
            extensions.findByType(LibraryExtension::class.java)?.buildFeatures?.compose = true
            dependencies {
                val bom = platform(libs.biblioteca("compose-bom"))
                add("implementation", bom)
                add("androidTestImplementation", bom)
                add("implementation", libs.biblioteca("compose-ui"))
                add("implementation", libs.biblioteca("compose-ui-graphics"))
                add("implementation", libs.biblioteca("compose-ui-tooling-preview"))
                add("implementation", libs.biblioteca("compose-material3"))
                add("debugImplementation", libs.biblioteca("compose-ui-tooling"))
                add("debugImplementation", libs.biblioteca("compose-ui-test-manifest"))
            }
        }
    }
}

/** Hilt com KSP. */
class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("com.google.dagger.hilt.android")
            dependencies {
                add("implementation", libs.biblioteca("hilt-android"))
                add("ksp", libs.biblioteca("hilt-compiler"))
            }
        }
    }
}

/**
 * Módulo de funcionalidade: biblioteca Android + Compose + Hilt + navegação,
 * já dependendo do design system, dos dados e dos modelos.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("conversa.android.library")
            pluginManager.apply("conversa.android.compose")
            pluginManager.apply("conversa.hilt")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            dependencies {
                add("implementation", project(":core:ui"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:model"))
                add("implementation", libs.biblioteca("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.biblioteca("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.biblioteca("androidx-navigation-compose"))
                add("implementation", libs.biblioteca("androidx-hilt-navigation-compose"))
                add("implementation", libs.biblioteca("kotlinx-serialization-json"))
                add("testImplementation", project(":core:testing"))
            }
        }
    }
}

/** Módulo Kotlin puro (sem Android): modelos de domínio e utilitários de teste. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            extensions.configure<JavaPluginExtension> {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
            configurarKotlin()
            dependenciasDeTeste()
        }
    }
}
