@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    val localProps = java.util.Properties().apply {
        val f = file("local.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }
    val gprUser = settings.providers.gradleProperty("gpr.user").orNull
        ?: localProps.getProperty("gpr.user")
        ?: System.getenv("GITHUB_ACTOR")
        ?: "x-access-token"
    val gprKey = settings.providers.gradleProperty("gpr.key").orNull
        ?: localProps.getProperty("gpr.key")
        ?: System.getenv("GITHUB_TOKEN")
        ?: System.getenv("GH_TOKEN")
    repositories {
        mavenCentral()
        mavenLocal()
        google()
        maven { url = uri("https://jitpack.io") }
        maven {
            name = "zjnsRegistry"
            url = uri("https://maven.pkg.github.com/zjns/registry")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
        maven {
            name = "revancedRegistry"
            url = uri("https://maven.pkg.github.com/revanced/registry")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
        maven {
            name = "ReVancedPatcher"
            url = uri("https://maven.pkg.github.com/ReVanced/revanced-patcher")
            credentials {
                username = gprUser
                password = gprKey
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

include(
    ":integrations:app",
    ":integrations:dummy",
    ":integrations:extend",
    ":integrations:ksp",
    ":integrations",
    ":patches"
)
rootProject.name = "BiliRoamingZQ"
