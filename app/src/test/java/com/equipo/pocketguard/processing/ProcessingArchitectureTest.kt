package com.equipo.pocketguard.processing

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** El paquete `processing` es Kotlin puro (sin Android) y no depende de otras capas del proyecto. */
class ProcessingArchitectureTest {

    private val dir = File("src/main/java/com/equipo/pocketguard/processing")

    private fun importLines(): List<Pair<String, String>> =
        dir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .flatMap { f -> f.readLines().filter { it.trimStart().startsWith("import ") }.map { f.name to it.trim() } }
            .toList()

    @Test
    fun `processing tiene fuentes`() {
        assertTrue("No se encontró ${dir.absolutePath}", dir.isDirectory)
        assertTrue(importLines().isNotEmpty())
    }

    @Test
    fun `processing no importa android ni androidx`() {
        val offenders = importLines().filter { (_, l) -> Regex("""^import\s+(android|androidx)\.""").containsMatchIn(l) }
        assertTrue("Imports prohibidos:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `processing no depende de otras capas del proyecto`() {
        val offenders = importLines().filter { (_, l) ->
            l.startsWith("import com.equipo.pocketguard.") && !l.startsWith("import com.equipo.pocketguard.processing.")
        }
        assertTrue("Dependencias no permitidas:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
