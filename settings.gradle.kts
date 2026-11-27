rootProject.name = "morphe-patches"

pluginManagement {
    // Optional public-source build of the tooling; normal registry builds are unchanged.
    providers.environmentVariable("MORPHE_PUBLIC_BUILD_SOURCES").orNull?.let {
        includeBuild("$it/gradle-plugin")
    }
    repositories {
        gradlePluginPortal()
        google()
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/MorpheApp/registry")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("app.morphe.patches") version "1.3.4"
}

settings {
    extensions {
        defaultNamespace = "hoodles.morphe.extension"

        // Must resolve to an absolute path (not relative),
        // otherwise the extensions in subfolders will fail to find the proguard config.
        proguardFiles(rootProject.projectDir.resolve("extensions/proguard-rules.pro").toString())
    }
}

include(":patches:stub")

providers.environmentVariable("MORPHE_PUBLIC_BUILD_SOURCES").orNull?.let { sourceRoot ->
    val directory = file(sourceRoot)
    includeBuild(directory.resolve("patcher")) {
        dependencySubstitution {
            substitute(module("app.morphe:morphe-patcher")).using(project(":"))
        }
    }
    includeBuild(directory.resolve("patches-library")) {
        dependencySubstitution {
            substitute(module("app.morphe:morphe-patches-library")).using(project(":patch-library"))
            substitute(module("app.morphe:morphe-extensions-library")).using(project(":extension-library"))
        }
    }
}
