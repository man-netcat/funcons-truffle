plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("kapt")
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":antlr"))
    implementation(libs.graal.sdk)
    implementation(libs.truffle.api)
    kapt(libs.truffle.dsl.processor)
    implementation(libs.antlr.runtime)
    implementation(libs.kotlin.reflect)
    testImplementation(libs.junit.jupiter)
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
}

tasks.compileJava {
    dependsOn(tasks.compileKotlin)
}
