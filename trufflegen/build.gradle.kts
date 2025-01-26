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
    val cbsPathStr = "../../CBS-beta/Funcons-beta/"
    val generatedPathStr = "../fctruffle/src/main/kotlin/generated"
    val generated = Paths.get(generatedPathStr)
    if (Files.notExists(generated)) {
        Files.createDirectories(generated)
    }
    args = listOf(
        cbsPathStr,
        generatedPathStr
    )
}