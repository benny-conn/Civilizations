package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock

data class WorldManifestRejected(override val description: String) : ApplicationFailure

class WorldManifestService(private val repository: CivilizationsRepository, private val clock: Clock) {
    fun register(manifest: WorldManifest, worlds: List<LoadedResourceWorld>, actor: String) = evaluate(manifest, worlds, actor, true)
    fun validate(manifest: WorldManifest, worlds: List<LoadedResourceWorld>, actor: String) = evaluate(manifest, worlds, actor, false)
    private fun evaluate(manifest: WorldManifest, worlds: List<LoadedResourceWorld>, actor: String, persist: Boolean): ApplicationResult<RegisteredWorldManifest> = repository.transaction {
        val world = worlds.singleOrNull { it.id == manifest.worldId && it.uuid == manifest.worldUuid }
            ?: return@transaction reject("world: loaded world key/UUID does not match manifest")
        if (manifest.bounds.minY < world.minHeight || manifest.bounds.maxY >= world.maxHeightExclusive) {
            return@transaction reject("bounds: outside loaded world's build height")
        }
        val season = findSeason(manifest.seasonId) ?: return@transaction reject("season: not found")
        val records = listWorldManifests()
        records.firstOrNull { it.manifest.id == manifest.id }?.let {
            return@transaction if (it.manifest.sameDefinition(manifest)) ApplicationResult.Unchanged(it)
            else reject("manifest: ID already registered with a different definition")
        }
        if (findCaneActivation()?.seasonId == manifest.seasonId) return@transaction reject("season: manifests are frozen by cane activation")
        if (season.status != SeasonStatus.SETUP) return@transaction reject("season: new world registration requires SETUP")
        if (records.any { it.manifest.worldId == manifest.worldId || it.manifest.worldUuid == manifest.worldUuid }) {
            return@transaction reject("world: already bound; replacement/reassignment requires a future audited lifecycle")
        }
        if (records.sumOf { it.manifest.chunkEntries } + manifest.chunkEntries > 262_144) {
            return@transaction reject("zones: global chunk index exceeds 262144 entries")
        }
        // Bound total published state as well as each individual manifest.
        if (records.size >= 32) return@transaction reject("manifest: registration limit of 32 reached")
        val record = RegisteredWorldManifest(manifest, clock.instant(), actor)
        if (persist) insertWorldManifest(record)
        ApplicationResult.Applied(record)
    }
    private fun reject(message: String) = ApplicationResult.Rejected(WorldManifestRejected(message))
}
