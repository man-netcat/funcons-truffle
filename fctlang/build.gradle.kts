plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.kotlin.kapt")
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlin.reflect)
    kapt(libs.truffle.dsl.processor)
    testImplementation(libs.junit.jupiter)
}

sourceSets["main"].kotlin {
    srcDirs(
        "src/main/kotlin",
        "src/main/kotlin/generated",
        "build/generated/sources/kapt/main"
    )
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("compileKotlin") {
    dependsOn(":antlr:generateCBSGrammar")
    dependsOn(":antlr:generateFCTGrammar")
    dependsOn(":trufflegen:run")
}

tasks.jar {
    from(sourceSets.main.get().output)
    from(sourceSets.main.get().resources) {
        include("META-INF/services/**")
    }

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.compileJava {
    dependsOn(tasks.compileKotlin)
}
