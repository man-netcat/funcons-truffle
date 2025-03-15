import dependencies.Deps
import dependencies.Vars

plugins {
    kotlin("jvm")
    application
}

group = "org.rickteuthof"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(project(":fctlang"))
    implementation(Deps.polyglot)
    implementation(Deps.graalSdk)
    implementation(Deps.truffleApi)
}
//
//tasks.named<JavaExec>("run") {
//    dependsOn(":fctlang:jar")
//
//    mainClass.set("interpreter.FCTInterpreterKt")
//    args = listOf("../../CBS-beta/Funcons-beta/Computations/Normal/Flowing/tests/do-while.config")
//
//    // Get reference to fctlang JAR
//    val fctlangJar = project(":fctlang").tasks.jar.flatMap { it.archiveFile }
//
//    // Set classpath and JVM args
//    classpath = files(fctlangJar) + sourceSets.main.get().runtimeClasspath
//    jvmArgs = listOf("-Dpolyglot.engine.WarnInterpreterOnly=false")
//}

tasks.register("testFilesRun") {
    group = "application"
    description = "Runs the interpreter for a list of test files"
    dependsOn(":fctlang:jar", ":fctinterpreter:build") // Ensure both modules are built
    doLast {
        // List of file names containing your fctlang code.
        val testFiles = Vars.configFiles

        // Get reference to the fctlang JAR.
        val fctlangJar = project(":fctlang").tasks.jar.get().archiveFile.get().asFile

        // Iterate over each file and run the interpreter.
        testFiles.forEach { fileName ->
            println("Executing file: $fileName")
            javaexec {
                mainClass.set("interpreter.FCTInterpreterKt")
                args = listOf(fileName, "test")
                classpath = sourceSets["main"].runtimeClasspath + files(fctlangJar)
                jvmArgs = listOf("-Dpolyglot.engine.WarnInterpreterOnly=false")
            }
            println("-----------------------")
        }
    }
}
