import dependencies.Deps

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
    implementation(Deps.kotlinStdLib)
    testImplementation(Deps.junitJupiter)
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

    dependencies {
        implementation(Deps.kotlinStdLib)
        testImplementation(Deps.junitJupiter)
    }

    tasks.test {
        useJUnitPlatform()
    }
}
