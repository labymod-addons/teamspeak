rootProject.name = "teamspeak"

pluginManagement {
    val labyGradlePluginVersion = "0.5.7"

    buildscript {
        repositories {
            maven("https://dist.labymod.net/api/v1/maven/release/")
            maven("https://repo.spongepowered.org/repository/maven-public")
            gradlePluginPortal()
            mavenCentral()
            mavenLocal()
        }

        dependencies {
            classpath("net.labymod.gradle", "common", labyGradlePluginVersion)
        }
    }
}

plugins.apply("net.labymod.labygradle.settings")

include(":api")
include(":core")
