@file:OptIn(ExperimentalPathApi::class)

import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

plugins {
    kotlin("jvm") version "2.1.0"
    java
    application
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
    val generated = Paths.get("fctlang/src/main/kotlin/generated")

    generated.deleteRecursively()
    Files.createDirectories(generated)
    val generatedDir = file(rootDir.resolve("fctlang/src/main/kotlin/generated"))
    val cbsDir = file(rootDir.resolve("CBS-beta/Funcons-beta/"))

    args = listOf(
        cbsDir.path,
        generatedDir.path,
//        "--index",
//        "../CBS-beta/Languages-beta/IMP/IMP-cbs/IMP/IMP-Funcons-Index/IMP-Funcons-Index.cbs",
//        "../CBS-beta/Languages-beta/SL/SL-cbs/SL/SL-Funcons-Index/SL-Funcons-Index.cbs"
    )
}