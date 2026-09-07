package io.bennyc.civilizations.infrastructure.paper.scarcity

import java.nio.file.Files
import kotlin.test.*

class WorldManifestYamlTest {
    private val valid = """
        format: 1
        id: 00000000-0000-0000-0000-000000000020
        season: 00000000-0000-0000-0000-000000000001
        revision: 1
        world:
          key: minecraft:scarcity
          uuid: 00000000-0000-0000-0000-000000000030
        bounds: {min-x: 0, min-y: -64, min-z: 0, max-x: 2047, max-y: 319, max-z: 2047}
        zones:
          - id: diamonds
            resource: DIAMOND
            bounds: {min-x: 0, min-y: -60, min-z: 0, max-x: 100, max-y: 0, max-z: 100}
    """.trimIndent()
    @Test fun `strict authoring format accepts valid source and hashes original bytes`() {
        val m = WorldManifestYaml.parse(valid.toByteArray())
        assertEquals(1, m.zones.size)
        assertEquals(2047, m.bounds.maxX)
        assertEquals(64, m.sourceSha256.length)
        assertNotEquals(m.sourceSha256, WorldManifestYaml.parse((valid + "\n# provenance").toByteArray()).sourceSha256)
    }
    @Test fun `rejects unknown missing malformed out of range and ambiguous inputs`() {
        listOf(valid.replace("format: 1", "format: 2"), valid + "\nextra: true",
            valid.replace("revision: 1", "revision: 1.5"), valid.replace("revision: 1", "revision: 2147483648"),
            valid.replace("revision: 1", "revision: -1"), valid.replace("revision: 1", "revision: '1'"),
            valid.replace("resource: DIAMOND", "resource: GOLD"), valid.replace("minecraft:scarcity", "Scarcity"),
            valid.replace("00000000-0000-0000-0000-000000000020", "1-1-1-1-1"),
            valid.replace("revision: 1\n", ""), valid.replace("max-x: 100", "max-x: 2048"),
            valid + "\nformat: 1").forEach { input ->
            assertFails("Should reject: $input") { WorldManifestYaml.parse(input.toByteArray()) }
        }
        assertFailsWith<IllegalArgumentException> { WorldManifestYaml.parse(ByteArray(WorldManifestYaml.MAX_BYTES + 1)) }
    }
    @Test fun `file reader rejects traversal and external symlinks`() {
        val parent = Files.createTempDirectory("scarcity-manifest-test")
        try {
            val root = Files.createDirectory(parent.resolve("manifests"))
            val outside = Files.writeString(parent.resolve("outside.yml"), valid)
            Files.writeString(root.resolve("valid.yml"), valid)
            Files.createSymbolicLink(root.resolve("escape.yml"), outside)
            val parser = WorldManifestYaml(root)
            assertEquals(1, parser.read("valid.yml").zones.size)
            assertFailsWith<IllegalArgumentException> { parser.read("../outside.yml") }
            assertFailsWith<IllegalArgumentException> { parser.read("escape.yml") }
        } finally { Files.walk(parent).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
}
