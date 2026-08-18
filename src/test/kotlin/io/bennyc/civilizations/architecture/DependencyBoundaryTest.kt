package io.bennyc.civilizations.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertTrue

class DependencyBoundaryTest {
    @Test
    fun `domain and application code do not import adapters or legacy frameworks`() {
        val sourceRoot = Path.of("src/main/kotlin/io/bennyc/civilizations")
        val inwardRoots = listOf(sourceRoot.resolve("domain"), sourceRoot.resolve("application"))
        val violations = buildList {
            for (root in inwardRoots) {
                Files.walk(root).use { paths ->
                    paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                        .forEach { source ->
                            source.readLines()
                                .filter { line -> forbiddenImports.any(line::startsWith) }
                                .forEach { line -> add("$source: $line") }
                        }
                }
            }
        }

        assertTrue(
            violations.isEmpty(),
            "Inward architecture dependency violations:\n${violations.joinToString("\n")}",
        )
    }

    private companion object {
        val forbiddenImports = listOf(
            "import io.bennyc.civilizations.command.",
            "import io.bennyc.civilizations.db.",
            "import io.bennyc.civilizations.infrastructure.",
            "import io.bennyc.civilizations.manager.",
            "import io.bennyc.civilizations.menu.",
            "import io.bennyc.civilizations.model.",
            "import io.bennyc.civilizations.settings.",
            "import io.papermc.",
            "import java.sql.",
            "import net.milkbowl.vault.",
            "import org.bukkit.",
            "import org.mineacademy.",
        )
    }
}
