package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock
import java.time.Instant

/** One server-wide experiment; persisted once so phase/season switches cannot bypass it. */
data class DiamondActivation(val seasonId: SeasonId, val includeEquipment: Boolean, val auditSha256: String, val actor: String, val reason: String, val activatedAt: Instant) {
    init {
        require(auditSha256.matches(Regex("[0-9a-f]{64}"))) { "audit-sha256: expected lowercase SHA256" }
        require(actor.isNotBlank() && actor.length <= 128) { "actor: expected 1..128 characters" }
        require(reason.isNotBlank() && reason.length <= 256) { "reason: expected 1..256 characters" }
        require(activatedAt.toEpochMilli() >= 0) { "activatedAt: must not precede epoch" }
    }
}

class DiamondActivationService(private val repository: CivilizationsRepository, private val clock: Clock) {
    fun enable(seasonId: SeasonId, includeEquipment: Boolean, auditSha256: String, actor: String, reason: String, worlds: List<LoadedResourceWorld>): ApplicationResult<DiamondActivation> = repository.transaction {
        val proposed = try { DiamondActivation(seasonId, includeEquipment, auditSha256, actor, reason.trim(), clock.instant()) }
        catch (e: IllegalArgumentException) { return@transaction rejected(e.message ?: "Invalid activation") }
        findDiamondActivation()?.let {
            return@transaction if (it.seasonId == seasonId && it.includeEquipment == includeEquipment && it.auditSha256 == auditSha256) ApplicationResult.Unchanged(it)
            else rejected("Diamond policy already enabled; replacement requires a future audited reset")
        }
        if (findActiveSeasonId() != seasonId || findSeason(seasonId)?.status != SeasonStatus.SETUP) {
            return@transaction rejected("Diamond activation requires the active season in SETUP")
        }
        val manifests = listWorldManifests().map { it.manifest }.filter { it.seasonId == seasonId }
        if (manifests.none { m -> m.zones.any { it.resource == ResourceKind.DIAMOND } }) {
            return@transaction rejected("Register at least one DIAMOND zone first")
        }
        if (manifests.any { m -> worlds.none { it.id == m.worldId && it.uuid == m.worldUuid && m.bounds.minY >= it.minHeight && m.bounds.maxY < it.maxHeightExclusive } }) {
            return@transaction rejected("All season manifests must match loaded world identities and build heights")
        }
        // Activation is for an unopened setup, never an implicit conversion of historical repair work.
        if (listSeasons().any { season ->
                listBattlesForSeason(season.id).isNotEmpty() || listCivilizations(season.id).any {
                    listUnresolvedExposureDamage(it.id, null, 1).isNotEmpty()
                }
            }) return@transaction rejected("Diamond activation requires an unopened experiment without battle history or unresolved exposure damage in any season")
        insertDiamondActivation(proposed)
        ApplicationResult.Applied(proposed)
    }
    private fun rejected(message: String) = ApplicationResult.Rejected(WorldManifestRejected(message))
}

/** Explicit selected outputs; crafting and transport of legitimate existing items remain legal. */
object DiamondSupplyPolicy {
    private val raw = setOf("diamond", "diamond_block", "diamond_ore", "deepslate_diamond_ore")
    fun excludes(materialKey: String, includeEquipment: Boolean): Boolean {
        if (!materialKey.startsWith("minecraft:")) return false
        val name = materialKey.removePrefix("minecraft:")
        return name in raw || (includeEquipment && name.startsWith("diamond_"))
    }
}
