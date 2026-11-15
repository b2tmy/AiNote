pluginManagement {
    repositories {
        google()
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

rootProject.name = "AiNote"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:data")
include(":core:security")
include(":core:ai")
include(":service")
