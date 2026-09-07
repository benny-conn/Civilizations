package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant
import java.util.UUID

/** Inclusive block geometry; geography is independent of political claims. */
data class ResourceBounds(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) { "bounds: minimum must not exceed maximum" }
        require(minX >= -29_999_984 && maxX <= 29_999_984 && minZ >= -29_999_984 && maxZ <= 29_999_984) { "bounds: outside Minecraft coordinate envelope" }
        require(minY >= -2048 && maxY <= 2047) { "bounds: unsupported build height" }
        require(maxX.toLong() - minX < 8192 && maxZ.toLong() - minZ < 8192) { "bounds: each horizontal span must be at most 8192 blocks" }
    }
    fun contains(x: Int, y: Int, z: Int) = x in minX..maxX && y in minY..maxY && z in minZ..maxZ
    fun contains(other: ResourceBounds) = contains(other.minX, other.minY, other.minZ) && contains(other.maxX, other.maxY, other.maxZ)
    fun overlaps(other: ResourceBounds) = minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY && minZ <= other.maxZ && maxZ >= other.minZ
}

enum class ResourceKind { DIAMOND, CATTLE, SUGAR_CANE }
data class ResourceZone(val id: String, val resource: ResourceKind, val bounds: ResourceBounds) {
    init { require(id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))) { "zone.id: expected 1–64 lowercase letters, digits, hyphens or underscores" } }
}

/** Defensive copies prevent a parser or caller from changing accepted history. */
class WorldManifest(
    val id: UUID,
    val seasonId: SeasonId,
    val worldId: WorldId,
    val worldUuid: UUID,
    val revision: Int,
    val sourceSha256: String,
    val bounds: ResourceBounds,
    zones: List<ResourceZone>,
) {
    val zones: List<ResourceZone> = java.util.List.copyOf(zones.sortedBy { it.id })
    val chunkEntries: Long get() = this.zones.sumOf { z ->
        ((z.bounds.maxX shr 4) - (z.bounds.minX shr 4) + 1).toLong() * ((z.bounds.maxZ shr 4) - (z.bounds.minZ shr 4) + 1)
    }
    init {
        require(worldId.value.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "world.key: expected a namespaced key" }
        require(revision > 0) { "revision: must be positive" }
        require(sourceSha256.matches(Regex("[0-9a-f]{64}"))) { "sourceSha256: expected lowercase SHA256" }
        require(this.zones.size in 1..256) { "zones: must contain 1–256 zones" }
        require(this.zones.map { it.id }.distinct().size == this.zones.size) { "zones: duplicate IDs" }
        this.zones.forEach { require(bounds.contains(it.bounds)) { "zones.${it.id}.bounds: outside world bounds" } }
        this.zones.forEachIndexed { i, a -> this.zones.drop(i + 1).forEach { b ->
            require(a.resource != b.resource || !a.bounds.overlaps(b.bounds)) { "zones: same-resource zones ${a.id} and ${b.id} overlap" }
        } }
        require(this.zones.sumOf { z ->
            ((z.bounds.maxX shr 4) - (z.bounds.minX shr 4) + 1).toLong() * ((z.bounds.maxZ shr 4) - (z.bounds.minZ shr 4) + 1)
        } <= 262_144) { "zones: combined chunk index exceeds 262144 entries" }
    }
    fun sameDefinition(other: WorldManifest) = id == other.id && seasonId == other.seasonId && worldId == other.worldId &&
        worldUuid == other.worldUuid && revision == other.revision && sourceSha256 == other.sourceSha256 && bounds == other.bounds && zones == other.zones
}

data class RegisteredWorldManifest(val manifest: WorldManifest, val importedAt: Instant, val actor: String) {
    init {
        require(actor.isNotBlank() && actor.length <= 128) { "actor: must contain 1–128 characters" }
        require(importedAt.toEpochMilli() >= 0) { "importedAt: must not precede epoch" }
    }
}

data class LoadedResourceWorld(val id: WorldId, val uuid: UUID, val minHeight: Int, val maxHeightExclusive: Int)

/** Derived read model only. No SQL, Paper access, or gameplay authorization. */
class ResourceZoneIndex(registrations: List<RegisteredWorldManifest>) {
    init {
        require(registrations.size <= 32) { "manifest: registration limit of 32 exceeded" }
        require(registrations.sumOf { it.manifest.chunkEntries } <= 262_144) { "zones: global chunk index exceeds 262144 entries" }
    }
    private data class Key(val season: SeasonId, val world: WorldId, val uuid: UUID, val x: Int, val z: Int)
    private val chunks: Map<Key, List<ResourceZone>> = buildMap<Key, MutableList<ResourceZone>> {
        registrations.forEach { registration ->
            val m = registration.manifest
            m.zones.forEach { zone ->
                val b = zone.bounds
                for (x in (b.minX shr 4)..(b.maxX shr 4)) for (z in (b.minZ shr 4)..(b.maxZ shr 4)) {
                    getOrPut(Key(m.seasonId, m.worldId, m.worldUuid, x, z)) { mutableListOf() }.add(zone)
                }
            }
        }
    }.mapValues { java.util.List.copyOf(it.value) }
    fun at(season: SeasonId, world: WorldId, uuid: UUID, x: Int, y: Int, z: Int): List<ResourceZone> =
        chunks[Key(season, world, uuid, x shr 4, z shr 4)].orEmpty().filter { it.bounds.contains(x, y, z) }
}
