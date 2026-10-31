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

rootProject.name = "bibi"

// The spike apps under spikes/ are separate Gradle builds and are not included here.
include(":app")
include(":core:common")
include(":core:designsystem")
include(":domain:model")
include(":domain:usecase")
