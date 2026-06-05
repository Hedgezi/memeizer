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
        maven("https://jitpack.io")
    }
}

rootProject.name = "Memeizer"
include(":app")
include(":ncnnAndroidPPOCR")
project(":ncnnAndroidPPOCR").projectDir = file("third_party/ncnnAndroidPPOCR")
