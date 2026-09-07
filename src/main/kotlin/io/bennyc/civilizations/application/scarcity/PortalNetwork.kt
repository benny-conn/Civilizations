package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Bounds describe the portal interior, excluding the obsidian frame. */
data class PortalSite(val world: WorldId, val uuid: UUID, val bounds: ResourceBounds) {
    val alongX: Boolean get() = bounds.minZ == bounds.maxZ
    val exitX: Int get() = if (alongX) bounds.minX + (bounds.maxX - bounds.minX + 1) / 2 else bounds.minX + 2
    val exitY: Int get() = bounds.minY
    val exitZ: Int get() = if (alongX) bounds.minZ + 2 else bounds.minZ + (bounds.maxZ - bounds.minZ + 1) / 2
    init {
        require(world.value.matches(Regex("[a-z0-9_.-]+:[a-z0-9/._-]+"))) { "world: expected namespaced key" }
        val width = if (alongX) bounds.maxX - bounds.minX + 1 else bounds.maxZ - bounds.minZ + 1
        require((alongX || bounds.minX == bounds.maxX) && width in 2..21 && bounds.maxY - bounds.minY + 1 in 3..21) { "portal: expected a vertical 2..21 by 3..21 interior, one block thick" }
        require(bounds.minY > -2048 && bounds.maxY < 2047 && bounds.minX > -29_999_980 && bounds.maxX < 29_999_980 && bounds.minZ > -29_999_980 && bounds.maxZ < 29_999_980) { "portal: frame/exit outside supported coordinates" }
    }
    fun near(x: Int, y: Int, z: Int) = y in bounds.minY..bounds.maxY &&
        x in (bounds.minX - if (alongX) 0 else 1)..(bounds.maxX + if (alongX) 0 else 1) &&
        z in (bounds.minZ - if (alongX) 1 else 0)..(bounds.maxZ + if (alongX) 1 else 0)
}
data class PortalPair(val id: String, val first: PortalSite, val second: PortalSite) {
    init {
        require(id.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}"))) { "pair.id: expected lowercase identifier" }
        require(first.world != second.world && first.uuid != second.uuid) { "pair: endpoints must be in different worlds" }
    }
}
class PortalNetwork(val seasonId: SeasonId, pairs: List<PortalPair>, val sourceSha256: String, val actor: String, val importedAt: Instant) {
    val pairs: List<PortalPair> = java.util.List.copyOf(pairs.sortedBy { it.id })
    init {
        require(this.pairs.size in 1..16 && this.pairs.map { it.id }.distinct().size == this.pairs.size) { "pairs: expected 1..16 unique pairs" }
        require(sourceSha256.matches(Regex("[0-9a-f]{64}"))) { "source: expected SHA256" }
        require(actor.isNotBlank() && actor.length <= 128 && importedAt.toEpochMilli() >= 0) { "invalid import audit" }
        val sites = this.pairs.flatMap { listOf(it.first, it.second) }
        sites.forEachIndexed { i, a -> sites.drop(i + 1).forEach { b ->
            if (a.world == b.world || a.uuid == b.uuid) {
                require(a.world == b.world && a.uuid == b.uuid) { "world identity is inconsistent" }
                val xGap = maxOf(a.bounds.minX - b.bounds.maxX, b.bounds.minX - a.bounds.maxX)
                val zGap = maxOf(a.bounds.minZ - b.bounds.maxZ, b.bounds.minZ - a.bounds.maxZ)
                require(xGap > 6 || zGap > 6) { "portal sites in one world must be separated by at least 6 blocks" }
            }
        } }
    }
    fun route(world: WorldId, uuid: UUID, x: Int, y: Int, z: Int): Pair<PortalSite, PortalSite>? = pairs.firstNotNullOfOrNull {
        when {
            it.first.world == world && it.first.uuid == uuid && it.first.near(x,y,z) -> it.first to it.second
            it.second.world == world && it.second.uuid == uuid && it.second.near(x,y,z) -> it.second to it.first
            else -> null
        }
    }
}
class PortalNetworkService(private val repository: CivilizationsRepository, private val clock: Clock) {
    fun install(season: SeasonId, pairs: List<PortalPair>, hash: String, actor: String): ApplicationResult<PortalNetwork> = repository.transaction {
        val network = PortalNetwork(season, pairs, hash, actor, Instant.ofEpochMilli(clock.millis()))
        findPortalNetwork()?.let {
            return@transaction if (it.seasonId == season && it.pairs == network.pairs && it.sourceSha256 == hash) ApplicationResult.Unchanged(it)
            else ApplicationResult.Rejected(WorldManifestRejected("Portal network already installed"))
        }
        if (findActiveSeasonId() != season || findSeason(season)?.status != SeasonStatus.SETUP) {
            return@transaction ApplicationResult.Rejected(WorldManifestRejected("Portal setup requires active SETUP"))
        }
        val registered = listWorldManifests().map { it.manifest }
        if (pairs.flatMap { listOf(it.first,it.second) }.any { site -> registered.any { m ->
            (m.worldId == site.world || m.worldUuid == site.uuid) &&
                (m.seasonId != season || m.worldId != site.world || m.worldUuid != site.uuid)
        } }) return@transaction ApplicationResult.Rejected(WorldManifestRejected("Portal world conflicts with an existing season/world registration"))
        insertPortalNetwork(network)
        ApplicationResult.Applied(network)
    }
}
