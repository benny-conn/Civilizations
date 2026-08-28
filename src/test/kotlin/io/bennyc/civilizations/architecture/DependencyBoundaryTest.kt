package io.bennyc.civilizations.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertFalse
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

    @Test
    fun `production code and build are free of retired frameworks`() {
        val productionRoot = Path.of("src/main")
        val sourceViolations = buildList {
            Files.walk(productionRoot).use { paths ->
                paths.filter {
                    Files.isRegularFile(it) && (it.extension == "kt" || it.extension == "java")
                }.forEach { source ->
                    source.readLines()
                        .filter { line -> retiredImports.any(line::startsWith) }
                        .forEach { line -> add("$source: $line") }
                }
            }
        }
        assertTrue(
            sourceViolations.isEmpty(),
            "Retired framework imports remain:\n${sourceViolations.joinToString("\n")}",
        )

        val build = Path.of("build.gradle.kts").readText()
        retiredBuildMarkers.forEach { marker ->
            assertFalse(marker in build, "Retired build marker remains: $marker")
        }
    }

    @Test
    fun `Vault remains a narrow Paper economy adapter`() {
        val sourceRoot = Path.of("src/main/kotlin/io/bennyc/civilizations")
        val allowedRoot = sourceRoot.resolve("infrastructure/paper/economy").normalize()
        val violations = buildList {
            Files.walk(sourceRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.extension == "kt" }
                    .filter { source ->
                        source.readLines().any { it.startsWith("import net.milkbowl.vault.") } &&
                            !source.normalize().startsWith(allowedRoot)
                    }
                    .forEach { add(it.toString()) }
            }
        }
        assertTrue(
            violations.isEmpty(),
            "Vault imports escaped the Paper economy adapter: ${violations.joinToString()}",
        )

        val build = Path.of("build.gradle.kts").readText()
        assertTrue("compileOnly(\"com.github.MilkBowl:VaultAPI:" in build)
        assertFalse("implementation(\"com.github.MilkBowl:VaultAPI:" in build)
        assertTrue("content { includeGroup(\"com.github.MilkBowl\") }" in build)
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
        val retiredImports = listOf(
            "import kotlinx.coroutines.",
            "import org.mineacademy.",
        )
        val retiredBuildMarkers = listOf(
            "Foundation",
            "kotlinx-coroutines",
            "org.mineacademy",
        )
    }
}
