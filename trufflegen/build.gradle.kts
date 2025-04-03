@file:OptIn(ExperimentalPathApi::class)

import dependencies.Vars
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi

plugins {
    kotlin("jvm") version "2.1.0"
    java
    application
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
    val generated = Paths.get(Vars.GENERATEDPATHSTR)

    Files.createDirectories(generated)

    args = listOf(
        Vars.CBSFILEPATH,
        Vars.GENERATEDPATHSTR,
        *Vars.configFiles
    )
}