package trufflegen.main

import java.nio.file.Path

fun main() {
    val directoryPath = Path.of("/home/rick/workspace/thesis/CBS-beta/Funcons-beta")
    val truffleGen = TruffleGen(directoryPath)
    truffleGen.process()
}


