// build.gradle.kts (:fctinterpreter)

import java.time.Duration

plugins {
    kotlin("jvm")
    application
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":fctlang"))
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)
    implementation(libs.polyglot)

    runtimeOnly(libs.truffle.runtime)
    runtimeOnly(libs.truffle.nfi)
    runtimeOnly(libs.polyglot)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}


application {
    mainClass.set("interpreter.FCTInterpreter")
}

tasks.compileKotlin {
    dependsOn(":fctlang:compileKotlin")
}

sourceSets["main"].kotlin {
    srcDirs("src/main/kotlin", "src/main/kotlin/generated")
}

tasks.withType<Test> {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showExceptions = true
        showCauses = true
        showStackTraces = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }

    failFast = false
    timeout.set(Duration.ofMinutes(5))
    jvmArgs = listOf("-Xmx2g")
}

tasks.register<Test>("runTests") {
    useJUnitPlatform()
    filter {
        includeTestsMatching("interpreter.InterpreterFilesTest.testFiles")
    }
    outputs.upToDateWhen { false }
}