package io.bennyc.civilizations.application.mob

import io.bennyc.civilizations.domain.identity.SeasonId
import java.time.Instant
import java.util.UUID

/** A resource policy selects which species to manage; a valid key alone enables nothing. */
@JvmInline value class MobSpecies(val key: String) {
    init { require(key.length <= 128 && key.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) }
}

data class MobRules(val maturityMillis: Long, val breedingCooldownMillis: Long) {
    init {
        require(maturityMillis in 1..31_536_000_000L)
        require(breedingCooldownMillis in 1..31_536_000_000L)
    }
}

data class MobCreation(
    val operationId: UUID, val mobId: UUID, val seasonId: SeasonId, val worldUuid: UUID,
    val species: MobSpecies, val rules: MobRules, val actor: String, val reason: String,
    val parentA: UUID? = null, val parentB: UUID? = null,
) {
    init {
        require(actor.isNotBlank() && actor.length <= 128)
        require(reason.isNotBlank() && reason.length <= 512)
        require((parentA == null) == (parentB == null))
        require(parentA == null || (parentA != parentB && mobId != parentA && mobId != parentB))
    }
    val isBirth: Boolean get() = parentA != null
}

enum class MobLife { PREPARED, APPLYING, ALIVE, CANCELLED, DEATH_PENDING, DEAD }
enum class DeathStage { PREPARED, APPLYING, COMPLETED }

data class ManagedMob(
    val creation: MobCreation, val preparedAt: Instant, val matureAt: Instant,
    val breedAfter: Instant, val life: MobLife, val entityUuid: UUID? = null,
) {
    val id: UUID get() = creation.mobId
}

data class MobDeath(
    val operationId: UUID, val mobId: UUID, val actor: String, val reason: String,
    val preparedAt: Instant, val stage: DeathStage,
) {
    init {
        require(actor.isNotBlank() && actor.length <= 128)
        require(reason.isNotBlank() && reason.length <= 512)
    }
}

/** No SQL or world access: usable from a future published memory index on the Paper thread. */
enum class MobObservation { MANAGED, QUARANTINE, SUPPRESS_TOMBSTONE }
fun ManagedMob?.observe(entityUuid: UUID, species: MobSpecies): MobObservation = when {
    this == null -> MobObservation.QUARANTINE
    creation.species != species -> MobObservation.QUARANTINE
    life == MobLife.DEAD -> MobObservation.SUPPRESS_TOMBSTONE
    life != MobLife.ALIVE || this.entityUuid != entityUuid -> MobObservation.QUARANTINE
    else -> MobObservation.MANAGED
}
