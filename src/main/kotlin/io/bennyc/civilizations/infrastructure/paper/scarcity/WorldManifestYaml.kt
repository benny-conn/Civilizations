package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID

/** Explicit authoring import, not live configuration. Call only on the storage worker. */
class WorldManifestYaml(private val directory: Path) {
    fun read(filename: String): WorldManifest {
        require(filename.matches(Regex("[a-zA-Z0-9_-]{1,64}\\.yml"))) { "file: expected a simple .yml filename" }
        val root = directory.toRealPath()
        val file = root.resolve(filename).toRealPath()
        require(file.startsWith(root)) { "file: must stay inside manifests directory" }
        val bytes = Files.newInputStream(file).use { it.readNBytes(MAX_BYTES + 1) }
        require(bytes.size <= MAX_BYTES) { "file: maximum size is 1048576 bytes" }
        return parse(bytes)
    }
    companion object {
        const val MAX_BYTES = 1_048_576
        fun parse(bytes: ByteArray): WorldManifest {
            require(bytes.size <= MAX_BYTES) { "file: maximum size is 1048576 bytes" }
            val options = LoaderOptions().apply {
                isAllowDuplicateKeys = false
                maxAliasesForCollections = 0
                nestingDepthLimit = 32
                codePointLimit = MAX_BYTES
            }
            val data = try {
                Yaml(SafeConstructor(options)).load<Any?>(bytes.toString(Charsets.UTF_8)) as? Map<*, *>
                    ?: errorValue("manifest: expected a mapping")
            } catch (e: org.yaml.snakeyaml.error.YAMLException) {
                errorValue("YAML: ${e.message}")
            }
            return decode(data, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        }
        private fun decode(root: Map<*, *>, hash: String): WorldManifest {
            keys(root, setOf("format", "id", "season", "revision", "world", "bounds", "zones"), "manifest")
            require(integer(root, "format", "format") == 1) { "format: only version 1 is supported" }
            val world = map(root["world"], "world")
            keys(world, setOf("key", "uuid"), "world")
            val zones = root["zones"] as? List<*> ?: errorValue("zones: expected a list")
            require(zones.size in 1..256) { "zones: must contain 1–256 zones" }
            return WorldManifest(
                uuid(root, "id", "id"), SeasonId(uuid(root, "season", "season")),
                WorldId(string(world, "key", "world.key")), uuid(world, "uuid", "world.uuid"),
                integer(root, "revision", "revision"), hash, bounds(root["bounds"], "bounds"),
                zones.mapIndexed { i, raw ->
                    val z = map(raw, "zones[$i]")
                    keys(z, setOf("id", "resource", "bounds"), "zones[$i]")
                    val kind = string(z, "resource", "zones[$i].resource")
                    ResourceZone(string(z, "id", "zones[$i].id"),
                        ResourceKind.entries.singleOrNull { it.name == kind } ?: errorValue("zones[$i].resource: expected DIAMOND, CATTLE or SUGAR_CANE"),
                        bounds(z["bounds"], "zones[$i].bounds"))
                },
            )
        }
        private fun bounds(value: Any?, path: String): ResourceBounds {
            val b = map(value, path)
            val names = listOf("min-x", "min-y", "min-z", "max-x", "max-y", "max-z")
            keys(b, names.toSet(), path)
            val n = names.map { integer(b, it, "$path.$it") }
            return try { ResourceBounds(n[0], n[1], n[2], n[3], n[4], n[5]) }
            catch (e: IllegalArgumentException) { errorValue("$path: ${e.message}") }
        }
        private fun map(value: Any?, path: String): Map<*, *> = when (value) {
            is org.bukkit.configuration.ConfigurationSection -> value.getValues(false)
            is Map<*, *> -> value
            else -> errorValue("$path: expected a mapping")
        }
        private fun keys(map: Map<*, *>, expected: Set<String>, path: String) {
            require(map.keys == expected) { "$path: expected keys $expected; missing=${expected - map.keys}, unknown=${map.keys - expected}" }
        }
        private fun string(map: Map<*, *>, key: String, path: String) = (map[key] as? String)?.takeIf { it.isNotBlank() }
            ?: errorValue("$path: expected a nonempty string")
        private fun uuid(map: Map<*, *>, key: String, path: String): UUID {
            val raw = string(map, key, path)
            return try { UUID.fromString(raw).also { require(it.toString() == raw) } }
            catch (_: IllegalArgumentException) { errorValue("$path: expected a canonical UUID") }
        }
        private fun integer(map: Map<*, *>, key: String, path: String): Int {
            val value = map[key]
            return when {
                value is Int -> value
                value is Long && value in Int.MIN_VALUE..Int.MAX_VALUE -> value.toInt()
                else -> errorValue("$path: expected a 32-bit integer")
            }
        }
        private fun errorValue(message: String): Nothing = throw IllegalArgumentException(message)
    }
}
