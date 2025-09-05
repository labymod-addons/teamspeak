import net.labymod.labygradle.common.extension.model.labymod.ReleaseChannels

plugins {
    id("java-library")
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
    id("org.cadixdev.licenser") version ("0.6.1")
}

group = "org.example"
version = "1.0.0"

java.toolchain.languageVersion.set(JavaLanguageVersion.of(17))

val versions = providers.gradleProperty("net.labymod.minecraft-versions").get().split(";")

labyMod {
    defaultPackageName = "net.labymod.addons.teamspeak" //change this to your main package name (used by all modules)

    minecraft {
        registerVersion(versions.toTypedArray()) {
            runs {
                getByName("client") {
                    devLogin = false
                }
            }
        }
    }

    addonInfo {
        namespace = "teamspeak"
        displayName = "TeamSpeak"
        author = "LabyMedia GmbH"
        minecraftVersion = "*"
        version = System.getenv().getOrDefault("VERSION", "0.0.1")
        releaseChannel = ReleaseChannels.INTERNAL
    }
}

subprojects {
    plugins.apply("java-library")
    plugins.apply("net.labymod.labygradle")
    plugins.apply("net.labymod.labygradle.addon")
    plugins.apply("org.cadixdev.licenser")

    repositories {
        maven("https://libraries.minecraft.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        mavenLocal()
    }

    license {
        header(rootProject.file("gradle/LICENSE-HEADER.txt"))
        newLine.set(true)
    }
}
