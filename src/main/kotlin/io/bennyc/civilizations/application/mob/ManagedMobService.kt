package io.bennyc.civilizations.application.mob

import io.bennyc.civilizations.application.*
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock
import java.util.UUID

data class MobRejected(override val description: String) : ApplicationFailure

/** Worker-side durable operations. Only Applied from begin* grants a fresh world apply attempt. */
class ManagedMobService(private val repository: CivilizationsRepository, private val clock: Clock) {
    fun prepare(request: MobCreation): ApplicationResult<ManagedMob> = repository.transaction {
        findMobCreation(request.operationId)?.let {
            return@transaction if (it.creation == request) ApplicationResult.Unchanged(it) else reject("creation operation reused with different request")
        }
        if (findManagedMob(request.mobId) != null) return@transaction reject("mob identity already exists")
        val season = findSeason(request.seasonId) ?: return@transaction reject("season not found")
        if (findActiveSeasonId() != request.seasonId || season.status == SeasonStatus.ARCHIVED) return@transaction reject("season not active")
        if (listWorldManifests().none { it.manifest.seasonId == request.seasonId && it.manifest.worldUuid == request.worldUuid }) return@transaction reject("world not registered to season")
        if (!request.isBirth && season.status != SeasonStatus.SETUP) return@transaction reject("seeding requires SETUP")
        val now = clock.instant()
        if (request.isBirth) {
            for (id in listOf(request.parentA!!, request.parentB!!)) {
                val parent = findManagedMob(id) ?: return@transaction reject("parent not registered")
                if (parent.life != MobLife.ALIVE || parent.creation.seasonId != request.seasonId || parent.creation.species != request.species) return@transaction reject("parent not eligible")
                if (parent.matureAt > now || parent.breedAfter > now || hasMobReservation(id)) return@transaction reject("parent immature, cooling down or reserved")
            }
        }
        val maturity = if (request.isBirth) now.plusMillis(request.rules.maturityMillis) else now
        val mob = ManagedMob(request, now, maturity, maturity, MobLife.PREPARED)
        insertManagedMob(mob)
        ApplicationResult.Applied(mob)
    }

    fun beginSpawn(operationId: UUID): ApplicationResult<ManagedMob> = repository.transaction {
        val mob = findMobCreation(operationId) ?: return@transaction reject("creation not found")
        if (mob.life == MobLife.CANCELLED) return@transaction reject("creation cancelled")
        if (mob.life != MobLife.PREPARED) return@transaction ApplicationResult.Unchanged(mob)
        if (findActiveSeasonId() != mob.creation.seasonId || findSeason(mob.creation.seasonId)?.status == SeasonStatus.ARCHIVED) return@transaction reject("season not active")
        if (!mob.creation.isBirth && findSeason(mob.creation.seasonId)?.status != SeasonStatus.SETUP) return@transaction reject("seeding requires SETUP")
        val next = mob.copy(life = MobLife.APPLYING)
        updateManagedMob(next)
        ApplicationResult.Applied(next)
    }

    /** Called only with an observed marked entity, including reconciliation after restart. */
    fun acknowledgeSpawn(operationId: UUID, entityUuid: UUID, species: MobSpecies, worldUuid: UUID): ApplicationResult<ManagedMob> = repository.transaction {
        val mob = findMobCreation(operationId) ?: return@transaction reject("creation not found")
        if (mob.creation.species != species || mob.creation.worldUuid != worldUuid) return@transaction reject("spawn identity mismatch")
        if (mob.entityUuid != null) return@transaction if (mob.entityUuid == entityUuid) ApplicationResult.Unchanged(mob) else reject("entity binding conflict")
        if (mob.life != MobLife.APPLYING) return@transaction reject("creation not applying")
        if (findManagedMobByEntity(entityUuid) != null) return@transaction reject("entity already bound")
        val next = mob.copy(life = MobLife.ALIVE, entityUuid = entityUuid)
        updateManagedMob(next)
        // The reservation remains held until acknowledgment. Delay cannot shorten cooldown.
        for (id in listOfNotNull(mob.creation.parentA, mob.creation.parentB)) {
            val parent = requireNotNull(findManagedMob(id))
            updateManagedMob(parent.copy(breedAfter = maxOf(parent.breedAfter, clock.instant().plusMillis(mob.creation.rules.breedingCooldownMillis))))
        }
        ApplicationResult.Applied(next)
    }

    /** Only pre-apply cancellation is definite. An interrupted APPLYING row is never retried. */
    fun cancelPrepared(operationId: UUID): ApplicationResult<ManagedMob> = repository.transaction {
        val mob = findMobCreation(operationId) ?: return@transaction reject("creation not found")
        if (mob.life == MobLife.CANCELLED) return@transaction ApplicationResult.Unchanged(mob)
        if (mob.life != MobLife.PREPARED) return@transaction reject("ambiguous or completed creation cannot cancel")
        val next = mob.copy(life = MobLife.CANCELLED)
        updateManagedMob(next)
        ApplicationResult.Applied(next)
    }

    fun prepareDeath(operationId: UUID, mobId: UUID, actor: String, reason: String): ApplicationResult<MobDeath> = repository.transaction {
        findMobDeath(operationId)?.let {
            return@transaction if (it.mobId == mobId && it.actor == actor && it.reason == reason) ApplicationResult.Unchanged(it) else reject("death operation reused")
        }
        val mob = findManagedMob(mobId) ?: return@transaction reject("mob not registered")
        if (mob.life != MobLife.ALIVE || hasMobReservation(mobId)) return@transaction reject("mob unavailable or reserved")
        val death = MobDeath(operationId, mobId, actor, reason, clock.instant(), DeathStage.PREPARED)
        insertMobDeath(death)
        updateManagedMob(mob.copy(life = MobLife.DEATH_PENDING))
        ApplicationResult.Applied(death)
    }

    fun beginDeath(operationId: UUID): ApplicationResult<MobDeath> = repository.transaction {
        val death = findMobDeath(operationId) ?: return@transaction reject("death not found")
        if (death.stage != DeathStage.PREPARED) return@transaction ApplicationResult.Unchanged(death)
        val next = death.copy(stage = DeathStage.APPLYING)
        updateMobDeath(next)
        ApplicationResult.Applied(next)
    }

    /** Recovery must suppress rewards; acknowledging disappearance never grants a payout. */
    fun acknowledgeDeath(operationId: UUID): ApplicationResult<MobDeath> = repository.transaction {
        val death = findMobDeath(operationId) ?: return@transaction reject("death not found")
        if (death.stage == DeathStage.COMPLETED) return@transaction ApplicationResult.Unchanged(death)
        if (death.stage != DeathStage.APPLYING) return@transaction reject("death not applying")
        val next = death.copy(stage = DeathStage.COMPLETED)
        updateMobDeath(next)
        updateManagedMob(requireNotNull(findManagedMob(death.mobId)).copy(life = MobLife.DEAD))
        ApplicationResult.Applied(next)
    }

    private fun reject(reason: String) = ApplicationResult.Rejected(MobRejected(reason))
}
