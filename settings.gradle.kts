pluginManagement {
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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content {
                includeGroup("com.github.philburk")
                includeGroup("com.github.racra")
                includeGroup("com.github.TeamNewPipe")
                // Regex, not exact: the fork is multi-module, so its transitive artifacts
                // live under the sub-group com.github.MetrolistGroup.MetrolistExtractor.
                includeGroupByRegex("com\\.github\\.MetrolistGroup.*")
            }
        }
    }
}

rootProject.name = "PixelPlayerOSS"
include(":app")
include(":baselineprofile")
