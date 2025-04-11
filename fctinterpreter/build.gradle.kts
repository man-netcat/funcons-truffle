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

tasks.register("testFilesRun") {
    group = "application"
    description = "Runs the interpreter for a list of test files"
    dependsOn(":fctlang:jar", ":fctinterpreter:build")
    doLast {
        // List of file names containing your fctlang code.
        val testFiles = Vars.configFiles

        // Get reference to the fctlang JAR.
        val fctlangJar = project(":fctlang").tasks.jar.get().archiveFile.get().asFile

        var successCount = 0
        var failCount = 0
        val failed = mutableListOf<String>()

        // Iterate over each file and run the interpreter.
        testFiles.forEach { fileName ->
            println("Executing file: $fileName")

            val result = javaexec {
                mainClass.set("interpreter.FCTInterpreterKt")
                args = listOf(fileName, "test")
                classpath = sourceSets["main"].runtimeClasspath + files(fctlangJar)
                jvmArgs = listOf("-Dpolyglot.engine.WarnInterpreterOnly=false")
                isIgnoreExitValue = true
            }

            if (result.exitValue == 0) {
                println("Success")
                successCount++
            } else {
                println("Failed (exit code: ${result.exitValue})")
                failCount++
                failed.add(fileName)
            }
            println("-----------------------")
        }

        println("Total succeeded: $successCount / ${testFiles.size}")
        println("Total failed: $failCount / ${testFiles.size}")
        if (failed.isNotEmpty()) println("Failed files:")
        failed.forEach { println(it) }
    }
}

