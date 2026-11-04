// Où Gradle trouve les plugins (kotlin jvm, kotlin serialization)
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

// Où Gradle trouve les bibliothèques (Retrofit, OkHttp, kotlinx…) : Maven Central
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "13-retrofit"
