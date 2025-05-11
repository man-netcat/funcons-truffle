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
    implementation(libs.polyglot)
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

application {
    mainClass.set("interpreter.FCTInterpreterKt")
}

tasks.named<JavaExec>("run") {
    args = project.findProperty("args")?.toString()?.split(" ") ?: emptyList()
}

//tasks.compileKotlin {
//    dependsOn(":trufflegen:run")
//}

sourceSets.main {
    java.srcDir("fctlang/src/main/kotlin/generated")
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