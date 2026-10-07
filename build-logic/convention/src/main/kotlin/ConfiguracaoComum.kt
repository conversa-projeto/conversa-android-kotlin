import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// Configuração compartilhada pelos convention plugins: SDKs, Java 17 e as
// dependências de teste que todo módulo usa.

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versao(nome: String): String = findVersion(nome).get().requiredVersion

internal fun VersionCatalog.biblioteca(nome: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(nome).get()

internal fun Project.configurarAplicacao(extensao: ApplicationExtension) {
    extensao.apply {
        compileSdk = libs.versao("compileSdk").toInt()
        defaultConfig {
            minSdk = libs.versao("minSdk").toInt()
            targetSdk = libs.versao("targetSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            unitTests.isIncludeAndroidResources = true
            unitTests.isReturnDefaultValues = true
        }
    }
}

internal fun Project.configurarBiblioteca(extensao: LibraryExtension) {
    extensao.apply {
        compileSdk = libs.versao("compileSdk").toInt()
        defaultConfig {
            minSdk = libs.versao("minSdk").toInt()
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        testOptions {
            unitTests.isIncludeAndroidResources = true
            unitTests.isReturnDefaultValues = true
        }
    }
}

internal fun Project.configurarKotlin() {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

internal fun Project.dependenciasDeTeste() {
    dependencies {
        add("testImplementation", libs.biblioteca("junit"))
        add("testImplementation", libs.biblioteca("truth"))
        add("testImplementation", libs.biblioteca("kotlinx-coroutines-test"))
        add("testImplementation", libs.biblioteca("turbine"))
        add("testImplementation", libs.biblioteca("mockk"))
    }
}
