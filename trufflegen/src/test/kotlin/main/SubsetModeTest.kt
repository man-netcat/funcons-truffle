package main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/**
 * A run can emit only a subset of the parsed files and skip the metadata files.
 *
 * The stable 44-file baseline owns `Aliases.kt` and `NodeMetas.kt`; a second
 * module that re-renders part of the corpus must leave those to the module that
 * owns the whole corpus, or the top-level symbols are declared twice.
 */
class SubsetModeTest {
    private fun corpusDir(): File {
        val dir = File.createTempFile("cbs-subset", "").apply { delete() }
        dir.mkdirs()
        File(dir, "A.cbs").writeText(
            """
            Funcon f : value-types
              ~> g

            """.trimIndent()
        )
        File(dir, "B.cbs").writeText(
            """
            Funcon g : value-types
              ~> f

            """.trimIndent()
        )
        return dir
    }

    @Test
    fun renderSubsetTest() {
        val out = File.createTempFile("gen-subset", "").apply { delete() }

        TruffleGen(corpusDir(), out, renderFiles = listOf("A.cbs"), emitMetadata = false).process()

        assertEquals(setOf("A.kt"), out.listFiles()!!.map { it.name }.toSet())
    }

    @Test
    fun renderSubsetWithMetadataTest() {
        val out = File.createTempFile("gen-subset-meta", "").apply { delete() }

        TruffleGen(corpusDir(), out, renderFiles = listOf("B.cbs"), emitMetadata = true).process()

        assertEquals(
            setOf("B.kt", "Aliases.kt", "NodeMetas.kt"),
            out.listFiles()!!.map { it.name }.toSet(),
        )
    }
}