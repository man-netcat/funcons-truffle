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
    testImplementation(libs.junit.jupiter)
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
