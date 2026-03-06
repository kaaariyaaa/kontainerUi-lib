group = "com.github.kaaariyaaa"
version = "0.1.0"

plugins {
    kotlin("jvm") version "2.2.21"
    id("de.eldoria.plugin-yml.paper") version "0.8.0"
    id("com.gradleup.shadow") version "9.2.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<Jar> {
        archiveBaseName.set("kontainer-ui-lib")
    }

    shadowJar {
        archiveClassifier.set("min")
    }
}

paper {
    main = "com.github.kaaariyaaa.kontainer_ui_lib.plugin.Plugin"
    name = "kontainer-ui-lib"
    description = "Container UI library with Kotlin DSL"
    version = getVersion().toString()
    apiVersion = "1.21"
    authors =
        listOf(
            "kaaariyaaa",
        )
}
