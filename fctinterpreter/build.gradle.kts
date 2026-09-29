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
    testImplementation(project(":antlr"))
    testRuntimeOnly(libs.junit.platform.launcher)
}


application {
    mainClass.set("interpreter.FCTInterpreter")
}

/*
  A second entry point, so the benchmark does not take over `run`. The working
  directory is the module's, which is what makes the `../CBS-beta` in the default
  corpus path resolve: Gradle already does this for `run`, and the benchmark is
  easier to read for it being stated.
*/
tasks.register<JavaExec>("benchmark") {
    group = "application"
    description = "Times the evaluator on the config corpus, cold and with a shared Context"
    mainClass.set("interpreter.Benchmark")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = projectDir
    jvmArgs = listOf("-Xmx2g")
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