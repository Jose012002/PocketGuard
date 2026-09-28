package com.equipo.pocketguard.decision

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** RNF-10: el paquete `decision` es Kotlin puro y no depende de `android.*` ni `androidx.*`. */
class DecisionArchitectureTest {

    private val decisionDir = File("src/main/java/com/equipo/pocketguard/decision")

    private fun sources(): List<File> =
        decisionDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `el paquete decision existe y tiene fuentes`() {
        assertTrue("No se encontró ${decisionDir.absolutePath}", decisionDir.isDirectory)
        assertTrue("decision no tiene archivos .kt", sources().isNotEmpty())
    }

    @Test
    fun `ningun archivo de decision importa android ni androidx`() {
        val forbidden = Regex("""^\s*import\s+(android|androidx)\.""")
        val offenders = sources().flatMap { file ->
            file.readLines().withIndex()
                .filter { (_, line) -> forbidden.containsMatchIn(line) }
                .map { (i, line) -> "${file.name}:${i + 1}: ${line.trim()}" }
        }
        assertTrue("Imports prohibidos en decision:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }

    @Test
    fun `decision solo depende de processing_SensorSnapshot dentro del proyecto`() {
        val projectImport = Regex("""^\s*import\s+com\.equipo\.pocketguard\.(\w+)\.(\w+)""")
        val offenders = sources().flatMap { file ->
            file.readLines().mapNotNull { line ->
                val m = projectImport.find(line) ?: return@mapNotNull null
                val (pkg, type) = m.destructured
                if (pkg == "decision" || (pkg == "processing" && type == "SensorSnapshot")) null
                else "${file.name}: ${line.trim()}"
            }
        }
        assertTrue("Dependencias no permitidas:\n${offenders.joinToString("\n")}", offenders.isEmpty())
    }
}
