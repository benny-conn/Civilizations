package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** One server-wide experiment; persisted once so phase/season switches cannot bypass it. */
data class CaneActivation(val seasonId: SeasonId, val maxHeight: Int, val actor: String, val reason: String, val activatedAt: Instant) {
    init {
        require(maxHeight in 1..3) { "max-height: expected 1..3" }
        require(actor.isNotBlank() && actor.length <= 128) { "actor: expected 1..128 characters" }
        require(reason.isNotBlank() && reason.length <= 256) { "reason: expected 1..256 characters" }
        require(activatedAt.toEpochMilli() >= 0) { "activatedAt: must not precede epoch" }
    }
}

class CaneActivationService(private val repository: CivilizationsRepository, private val clock: Clock) {
    fun enable(seasonId: SeasonId, maxHeight: Int, actor: String, reason: String, worlds: List<LoadedResourceWorld>): ApplicationResult<CaneActivation> = repository.transaction {
        val proposed = try { CaneActivation(seasonId, maxHeight, actor, reason.trim(), clock.instant()) }
        catch (e: IllegalArgumentException) { return@transaction rejected(e.message ?: "Invalid activation") }
        findCaneActivation()?.let {
            return@transaction if (it.seasonId == seasonId && it.maxHeight == maxHeight) ApplicationResult.Unchanged(it)
            else rejected("Cane policy already enabled; replacement requires a future audited reset")
        }
        if (findActiveSeasonId() != seasonId || findSeason(seasonId)?.status != SeasonStatus.SETUP) {
            return@transaction rejected("Cane activation requires the active season in SETUP")
        }
        val manifests = listWorldManifests().map { it.manifest }.filter { it.seasonId == seasonId }
        if (manifests.none { m -> m.zones.any { it.resource == ResourceKind.SUGAR_CANE } }) {
            return@transaction rejected("Register at least one SUGAR_CANE zone first")
        }
        if (manifests.any { m -> worlds.none { it.id == m.worldId && it.uuid == m.worldUuid && m.bounds.minY >= it.minHeight && m.bounds.maxY < it.maxHeightExclusive } }) {
            return@transaction rejected("All season manifests must match loaded world identities and build heights")
        }
        insertCaneActivation(proposed)
        ApplicationResult.Applied(proposed)
    }
    private fun rejected(message: String) = ApplicationResult.Rejected(WorldManifestRejected(message))
}

/** Read-only policy: a complete cane column must fit in ONE immutable 3D cane zone. */
class CaneGrowthPolicy(val activation: CaneActivation?, private val index: ResourceZoneIndex) {
    fun permits(world: WorldId, uuid: UUID, x: Int, baseY: Int, z: Int, topY: Int): Boolean {
        val rules = activation ?: return true
        if (topY < baseY || topY.toLong() - baseY + 1 > rules.maxHeight) return false
        return index.at(rules.seasonId, world, uuid, x, baseY, z).any {
            it.resource == ResourceKind.SUGAR_CANE && it.bounds.contains(x, topY, z)
        }
    }
}
