import dependencies.Deps

plugins {
    kotlin("jvm")
    application
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":fctlang"))
    implementation(Deps.polyglot)
    implementation(Deps.graalSdk)
    implementation(Deps.truffleApi)
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("interpreter.FCTInterpreterKt")
}

tasks.named<JavaExec>("run") {
    args = listOf("../CBS-beta/Funcons-beta/Computations/Normal/Binding/tests/bound-directly.config")
}