pluginManagement {
    repositories {
        google() // Để mặc định như này cho thoáng, không cần includeGroupByRegex
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

rootProject.name = "App_AgePredict"
include(":app")
include(":sdk")