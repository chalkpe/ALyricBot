import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Properties

plugins {
    java
    id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
    maven {
        url = uri("https://jitpack.io")
    }
    mavenCentral()
}

dependencies {
    implementation(group = "be.zvz.alsong", name = "alsong-kt", version = "1.0.7")
    implementation(group = "com.github.rubenlagus", name = "TelegramBots", version = "5.6.0")
    implementation(group = "net.sf.javamusictag", name = "jid3lib", version = "0.5.4")
}

group = "pe.chalk.telegram.alyricbot"
version = "1.2"
description = "ALyricBot"

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_1_8.toString()
    targetCompatibility = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Jar> {
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "pe.chalk.telegram.alyricbot.ALyricBot",
                "Implementation-Title" to description,
                "Implementation-Version" to archiveVersion
            )
        )
    }
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveBaseName.set("app")
        mergeServiceFiles()
        manifest {
            attributes(mapOf("Main-Class" to "pe.chalk.telegram.alyricbot.ALyricBot"))
        }
    }
}
