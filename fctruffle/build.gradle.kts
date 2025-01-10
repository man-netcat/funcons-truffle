plugins {
    kotlin("jvm") version "2.0.21"
    java
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
    implementation("org.graalvm.truffle:truffle-api:24.1.1")
}

tasks.compileKotlin {
    dependsOn(":antlr:generateGrammarSource")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("main.FCTInterpreter")
}

tasks.named<JavaExec>("run") {
    args = listOf(
        "../CBS-beta/Funcons-beta/Values/Primitive/Booleans/tests/and.config"
    )
}


