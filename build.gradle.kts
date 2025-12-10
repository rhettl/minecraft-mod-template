plugins {
    id("dev.architectury.loom") apply false
    id("architectury-plugin")
    id("com.github.johnrengelman.shadow") apply false
    java
}

// Get properties
val minecraftVersion: String by project
val modVersion: String by project
val mavenGroup: String by project

architectury {
    minecraft = minecraftVersion
}

allprojects {
    apply(plugin = "java")

    group = mavenGroup
    version = modVersion

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    repositories {
        mavenCentral()
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
    }
}
