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
        // Dépôt pour les bibliothèques GitHub
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "FondabecBattage"
include(":app")
