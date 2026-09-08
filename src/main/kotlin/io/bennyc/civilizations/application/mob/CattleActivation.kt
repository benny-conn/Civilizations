package io.bennyc.civilizations.application.mob

import io.bennyc.civilizations.application.*
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.scarcity.ResourceKind
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CattleSeed(val id: String, val worldUuid: UUID, val x: Int, val y: Int, val z: Int) {
    init {
        require(x in -29_999_984..29_999_984) { "seeds.x: outside world envelope" }
        require(z in -29_999_984..29_999_984) { "seeds.z: outside world envelope" }
        require(y in -2048..2046) { "seeds.y: outside build envelope" }
        require(id.matches(Regex("[a-z0-9_-]{1,32}"))) { "seeds.id: invalid ID" } }
}
class CattleActivation(val seasonId: SeasonId, val rules: MobRules, seeds: List<CattleSeed>, val actor: String, val activatedAt: Instant) {
    val seeds: List<CattleSeed> = java.util.List.copyOf(seeds)
    init {
        require(seeds.size in 2..128) { "seeds: expected 2..128 entries" }
        require(seeds.map { it.id }.distinct().size == seeds.size) { "seeds.id: duplicates" }
        require(seeds.map { listOf(it.worldUuid,it.x,it.y,it.z) }.distinct().size == seeds.size) { "seeds: duplicate positions" }
        require(actor.isNotBlank() && actor.length<=128)
    }
    fun request(seed: CattleSeed) = MobCreation(
        UUID.nameUUIDFromBytes("cattle:$seasonId:${seed.id}:operation".toByteArray()),
        UUID.nameUUIDFromBytes("cattle:$seasonId:${seed.id}:mob".toByteArray()),
        seasonId,seed.worldUuid,MobSpecies("minecraft:cow"),rules,actor,"Initial cattle seed ${seed.id}")
}
class CattleActivationService(private val repository: CivilizationsRepository, private val clock: Clock) {
    fun install(seasonId: SeasonId, rules: MobRules, seeds: List<CattleSeed>, actor: String): ApplicationResult<CattleActivation> = repository.transaction {
        val proposed = try { CattleActivation(seasonId,rules,seeds,actor,clock.instant()) }
        catch(e: IllegalArgumentException) { return@transaction ApplicationResult.Rejected(MobRejected(e.message ?: "Invalid cattle activation")) }
        findCattleActivation()?.let {
            return@transaction if(it.seasonId==seasonId && it.rules==rules && it.seeds==seeds) ApplicationResult.Unchanged(it)
            else ApplicationResult.Rejected(MobRejected("Cattle already activated; definition is immutable"))
        }
        if(findActiveSeasonId()!=seasonId || findSeason(seasonId)?.status!=SeasonStatus.SETUP) return@transaction ApplicationResult.Rejected(MobRejected("Cattle activation requires active SETUP"))
        val manifests=listWorldManifests().map { it.manifest }.filter { it.seasonId==seasonId }
        if(seeds.any { seed -> manifests.none { m -> m.worldUuid==seed.worldUuid && m.zones.any { it.resource==ResourceKind.CATTLE && it.bounds.contains(seed.x,seed.y,seed.z) && it.bounds.contains(seed.x,seed.y+1,seed.z) } } }) return@transaction ApplicationResult.Rejected(MobRejected("Every seed must fit a registered CATTLE habitat"))
        insertCattleActivation(proposed)
        ApplicationResult.Applied(proposed)
    }
}
