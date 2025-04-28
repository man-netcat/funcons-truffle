plugins {
    kotlin("jvm") version "2.1.0"
    java
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    testImplementation(libs.junit.jupiter)
    implementation(kotlin("stdlib-jdk8"))
}

tasks.test {
    useJUnitPlatform()
}

subprojects {
    apply(plugin = "kotlin")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    kotlin {
        jvmToolchain(21)
    }


    repositories {
        mavenCentral()
    }

    tasks.test {
        useJUnitPlatform()
    }
}
