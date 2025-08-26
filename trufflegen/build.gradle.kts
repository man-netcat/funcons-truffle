plugins {
    kotlin("jvm") version "2.1.0"
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
}

tasks.compileKotlin {
    dependsOn(":antlr:generateFCTGrammar")
    dependsOn(":antlr:generateCBSGrammar")
    dependsOn(":antlr:compileJava")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("main.TruffleGen")
}

tasks.named<JavaExec>("run") {
    inputs.dir("../CBS-beta/Funcons-beta")
    outputs.dir("../fctlang/src/main/kotlin/generated")
    args = listOf("../CBS-beta/Funcons-beta/")
}
