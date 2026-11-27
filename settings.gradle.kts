rootProject.name = "LocalRock"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Use the alternate official Maven Central endpoint for CI resolution.
        // Retain the standard endpoint for fallback; no third-party mirrors.
        // Prefer the documented Google Cloud Central mirror in CI only.
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        maven("https://repo1.maven.org/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        // Use the alternate official Maven Central endpoint for CI resolution.
        // Retain the standard endpoint for fallback; no third-party mirrors.
        // Prefer the documented Google Cloud Central mirror in CI only.
        if (System.getenv("GITHUB_ACTIONS") == "true") {
            maven("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        maven("https://repo1.maven.org/maven2/")
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":composeApp")