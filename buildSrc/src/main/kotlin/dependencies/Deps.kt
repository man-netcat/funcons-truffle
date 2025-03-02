package dependencies

object Deps {
    const val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib-jdk8:${Versions.kotlin}"
    const val kotlinReflect = "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlin}"
    const val junitJupiter = "org.junit.jupiter:junit-jupiter:${Versions.junit}"
    const val antlrRuntime = "org.antlr:antlr4-runtime:${Versions.antlr}"
    const val antlrTool = "org.antlr:antlr4:${Versions.antlr}"

    // GraalVM Dependencies
    const val graalSdk = "org.graalvm.sdk:graal-sdk:${Versions.graalvm}"
    const val truffleApi = "org.graalvm.truffle:truffle-api:${Versions.graalvm}"
    const val polyglot = "org.graalvm.polyglot:polyglot:${Versions.graalvm}"
    const val truffleDslProcessor = "org.graalvm.truffle:truffle-dsl-processor:${Versions.graalvm}"
}