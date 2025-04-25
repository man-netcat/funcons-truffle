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
    testImplementation(Deps.junitJupiter)
}

application {
    mainClass.set("interpreter.FCTInterpreterKt")
}

tasks.named<JavaExec>("run") {
    args = project.findProperty("args")?.toString()?.split(" ") ?: emptyList()
}

tasks.register<Test>("runTests") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("interpreter.InterpreterFilesTest.testFiles")
    }
    testLogging {
        events("passed", "skipped", "failed")
    }
}
