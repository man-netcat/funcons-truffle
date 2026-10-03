// build.gradle.kts (:fctlang-unstable)

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.kapt")
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":fctlang"))
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlin.reflect)
    kapt(libs.truffle.dsl.processor)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The unstable corpus is generated from the same repository of funcon libraries
// vendored under `CBS-beta/Unstable-Funcons-beta`. That tree contains a symlink
// back to `../Funcons-beta` (the stable corpus), so walking it yields all 51
// source files: the registry resolves names as if every file were a corpus, but
// only the unstable declarations are written here. `Aliases.kt` and
// `NodeMetas.kt` stay in :fctlang, which owns the whole stable corpus; emitting
// them here instead would redeclare their top-level symbols on the classpath.
val generateUnstable = tasks.register<JavaExec>("generateUnstable") {
    group = "build"
    description = "Writes the unstable funcon node classes into the module's generated source set"
    classpath = project(":trufflegen").sourceSets["main"].runtimeClasspath
    mainClass.set("main.TruffleGen")
    workingDir = projectDir
    inputs.dir("../CBS-beta/Unstable-Funcons-beta")
    outputs.dir("src/main/kotlin/generated")
    args = listOf(
        "../CBS-beta/Unstable-Funcons-beta/",
        "src/main/kotlin/generated",
        "--render=Memos.cbs,Indexing.cbs,Postponing.cbs,Multithreading.cbs,Locks.cbs,Notifications.cbs,Synchronising.cbs",
        "--no-metadata",
    )
    dependsOn(":trufflegen:classes")
}

sourceSets["main"].kotlin {
    srcDirs("src/main/kotlin", "src/main/kotlin/generated")
}

tasks.compileKotlin {
    dependsOn(generateUnstable)
    dependsOn(":fctlang:compileKotlin")
}

tasks.matching { it.name == "kaptGenerateStubsKotlin" }.configureEach {
    dependsOn(generateUnstable)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    from(sourceSets.main.get().output)
}