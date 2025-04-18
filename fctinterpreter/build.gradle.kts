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
    args =
        listOf("/home/rick/workspace/thesis/funcons-truffle/fctinterpreter/src/test/resources/CustomTests/sandbox.config")
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
