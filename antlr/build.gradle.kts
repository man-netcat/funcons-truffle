plugins {
    kotlin("jvm") version "2.0.21"
    java
    antlr
    application
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    antlr("org.antlr:antlr4-runtime:4.13.2")
    antlr("org.antlr:antlr4:4.13.2")
    implementation(kotlin("stdlib-jdk8"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
}

sourceSets {
    main {
        java {
            srcDirs("src/main/java", "src/main/antlr") // Include both src dirs for compilation
        }
    }
}

// Define grammar generation tasks explicitly with fixed output paths
tasks.register<JavaExec>("generateFCTGrammar") {
    group = "antlr"
    description = "Generates ANTLR sources for FCT.g4"

    val grammarFile = file("src/main/antlr/FCT.g4")
    val outputDir = file("src/main/java/fct")

    inputs.file(grammarFile)
    outputs.dir(outputDir)

    classpath = configurations["antlr"]
    mainClass.set("org.antlr.v4.Tool")
    args = listOf(
        grammarFile.absolutePath,
        "-visitor", // Generate visitor classes
        "-long-messages", // Use long messages for errors
        "-o", outputDir.absolutePath // Explicit absolute output directory
    )
}

tasks.register<JavaExec>("generateCBSGrammar") {
    group = "antlr"
    description = "Generates ANTLR sources for CBS.g4"

    val grammarFile = file("src/main/antlr/CBS.g4")
    val outputDir = file("src/main/java/cbs")

    inputs.file(grammarFile)
    outputs.dir(outputDir)

    classpath = configurations["antlr"]
    mainClass.set("org.antlr.v4.Tool")
    args = listOf(
        grammarFile.absolutePath,
        "-visitor", // Generate visitor classes
        "-long-messages", // Use long messages for errors
        "-o", outputDir.absolutePath // Explicit absolute output directory
    )
}

// Aggregate task to run both generation tasks
tasks.register("generateAllGrammars") {
    dependsOn("generateFCTGrammar", "generateCBSGrammar")
    group = "antlr"
    description = "Generates ANTLR sources for all grammars"
}

// Ensure compilation depends on grammar generation
tasks.compileJava {
    dependsOn("generateAllGrammars")
}

// Disable the default ANTLR source generation
tasks.named("generateGrammarSource").configure {
    enabled = false
}