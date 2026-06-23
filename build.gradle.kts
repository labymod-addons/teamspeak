import com.diffplug.spotless.LineEnding
import net.labymod.labygradle.common.extension.model.labymod.ReleaseChannels

plugins {
    id("java-library")
    id("net.labymod.labygradle")
    id("net.labymod.labygradle.addon")
    id("com.diffplug.spotless") version ("8.0.0")
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
    plugins.apply("com.diffplug.spotless")

    repositories {
        maven("https://libraries.minecraft.net/")
        maven("https://repo.spongepowered.org/repository/maven-public/")
        mavenLocal()
    }

    spotless {
        lineEndings = LineEnding.UNIX

        java {
            licenseHeaderFile(rootProject.file("gradle/LICENSE-HEADER.txt"))
        }
    }

    extensions.findByType(JavaPluginExtension::class.java)?.apply {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
