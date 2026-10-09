pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Conversa"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// O app legado (servidor Delphi) fica em app-legado/, fora do build.
include(":app")
include(":core:model")
include(":core:network")
include(":core:datastore")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:media")
include(":core:webrtc")
include(":core:testing")
include(":feature:auth")
include(":feature:conversas")
include(":feature:chat")
include(":feature:chamada")
include(":feature:atividades")
include(":feature:pesquisa")
