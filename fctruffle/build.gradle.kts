plugins {
    kotlin("jvm") version "2.0.21"
    java
    antlr
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
    antlr("org.antlr:antlr4-runtime:4.13.2")
    antlr("org.antlr:antlr4:4.13.2")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java", "src/main/java/antlr")
        }
    }
}

tasks.generateGrammarSource {
    maxHeapSize = "64m"

    outputDirectory = file("src/main/java/antlr")

    arguments.add("-visitor")
    arguments.add("-long-messages")
}

tasks.compileKotlin {
    dependsOn(tasks.generateGrammarSource)
}

tasks.test {
    useJUnitPlatform()
}


