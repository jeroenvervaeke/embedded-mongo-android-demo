import java.io.File

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

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "coffee-finder"

include(":data")
include(":app")

// The library publishes no Maven artifact yet, so the application consumes the module itself.
// An included build rather than a copied module: the sources stay in their own repository, and
// `:app` sees whatever is checked out there without anything being copied in.
//
// The path is a property so the two repositories do not have to be siblings, and a missing one
// is reported here rather than as an unresolved dependency several hundred lines of Gradle log
// later. `:data` does not depend on the library at all, so `:data:test` still runs without it.
val libraryPath = providers.gradleProperty("embeddedMongoAndroidDir").get()
val libraryDir = File(libraryPath).takeIf(File::isAbsolute) ?: rootDir.resolve(libraryPath)

if (libraryDir.resolve("settings.gradle.kts").isFile) {
    includeBuild(libraryDir) {
        dependencySubstitution {
            substitute(module("io.github.jeroenvervaeke:embedded-mongodb"))
                .using(project(":embedded-mongodb"))
            // `:data` depends on this one alone: it is plain Kotlin, so the whole data layer is
            // still compiled and tested without the Android SDK or a built engine.
            substitute(module("io.github.jeroenvervaeke:embedded-mongodb-core"))
                .using(project(":embedded-mongodb-core"))
        }
    }
} else {
    logger.warn(
        "embedded-mongodb is not at $libraryDir, so nothing here can be built. Set " +
            "embeddedMongoAndroidDir to the `android` directory of a checkout of " +
            "github.com/jeroenvervaeke/embedded-mongo.",
    )
}
