plugins {
    kotlin("jvm") version "2.1.0"
    java
    application
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // The generator has to be checked against the real ANTLR tool, not only against
    // the text it produces: a grammar that compiles as a file can still be rejected
    // for a name that collides with the generated code, and only the tool sees that.
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.antlr.tool)
    testImplementation(libs.antlr.runtime)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // The corpus tests read CBS-beta/Languages-beta, which is at the root.
    workingDir = rootProject.projectDir
}

application {
    mainClass.set("cbslang.MainKt")
}
