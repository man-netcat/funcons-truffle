import java.nio.file.Files
import java.nio.file.Paths

plugins {
    kotlin("jvm") version "2.0.21"
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
}

tasks.compileKotlin {
    dependsOn(":antlr:generateGrammarSource")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("main.TruffleGen")
}

tasks.named<JavaExec>("run") {
    val generated = "/fctruffle/src/main/kotlin/generated"
    val path = Paths.get(generated)
    if (Files.notExists(path)) {
        Files.createDirectories(path)
    }
    args = listOf(
        "../../CBS-beta/Funcons-beta/",
        "/fctruffle/src/main/kotlin/generated"
    )
}