plugins {
    kotlin("jvm") version "2.0.21"
    java
}

kotlin {
    jvmToolchain(22)
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

tasks.test {
    useJUnitPlatform()
}

subprojects {
    apply(plugin = "kotlin")

    repositories {
        mavenCentral()
    }

    tasks.test {
        useJUnitPlatform()
    }
}
