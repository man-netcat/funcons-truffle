import dependencies.Deps

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("kapt")
    java
}

dependencies {
    implementation(project(":antlr"))
    implementation(Deps.graalSdk)
    implementation(Deps.truffleApi)
    kapt(Deps.truffleDslProcessor)
    implementation(Deps.antlrRuntime)
    implementation(Deps.kotlinReflect)
    testImplementation(Deps.junitJupiter)
}

sourceSets.main {
    java.srcDirs("build/generated/source/kapt/main")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    dependsOn(":antlr:generateGrammarSource")
    // Add resources/META-INF to JAR
    from(sourceSets.main.get().output)
    from(sourceSets.main.get().resources) {
        include("META-INF/services/**")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes(
            "Specification-Title" to "FCT Language",
            "Implementation-Version" to project.version
        )
    }
}

tasks.compileJava {
    dependsOn(tasks.compileKotlin)
}
