import dependencies.Deps

plugins {
    kotlin("jvm")
    application
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

dependencies {
    project(":fctlang")
    implementation(Deps.polyglot)
    implementation(project(":fctlang"))
    implementation(Deps.polyglot)
    implementation(Deps.graalSdk)
    implementation(Deps.truffleApi)
}

tasks.named<JavaExec>("run") {
    dependsOn(":fctlang:jar")

    mainClass.set("interpreter.FCTInterpreterKt")
    args = listOf("../../CBS-beta/Funcons-beta/Values/Primitive/Booleans/tests/and.config")

    // Get reference to fctlang JAR
    val fctlangJar = project(":fctlang").tasks.jar.flatMap { it.archiveFile }

    // Set classpath and JVM args
    classpath = files(fctlangJar) + sourceSets.main.get().runtimeClasspath
    jvmArgs = listOf("-Dpolyglot.engine.WarnInterpreterOnly=false")
}