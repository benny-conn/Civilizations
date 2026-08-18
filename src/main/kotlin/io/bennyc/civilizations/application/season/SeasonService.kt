package io.bennyc.civilizations.application.season

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.Season
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock

class SeasonService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
) {
    fun create(rawName: String): ApplicationResult<Season> {
        val name = rawName.trim()
        val now = clock.instant()
        val season = try {
            Season(
                id = idGenerator.newSeasonId(),
                name = name,
                status = SeasonStatus.SETUP,
                createdAt = now,
                updatedAt = now,
            )
        } catch (failure: IllegalArgumentException) {
            return ApplicationResult.Rejected(
                InvalidSeasonName(failure.message ?: "Invalid season name"),
            )
        }

        return repository.transaction {
            val existing = listSeasons().firstOrNull { it.name.equals(name, ignoreCase = true) }
            if (existing != null) {
                ApplicationResult.Rejected(SeasonNameAlreadyExists(existing.id, existing.name))
            } else {
                insertSeason(season)
                if (findActiveSeasonId() == null) {
                    setActiveSeasonId(season.id)
                }
                ApplicationResult.Applied(season)
            }
        }
    }

    fun selectActive(seasonId: SeasonId): ApplicationResult<Season> = repository.transaction {
        val season = findSeason(seasonId)
            ?: return@transaction ApplicationResult.Rejected(SeasonNotFound(seasonId))
        if (season.status == SeasonStatus.ARCHIVED) {
            return@transaction ApplicationResult.Rejected(ArchivedSeasonCannotBeActive(seasonId))
        }
        if (findActiveSeasonId() == seasonId) {
            return@transaction ApplicationResult.Unchanged(season)
        }

        setActiveSeasonId(seasonId)
        ApplicationResult.Applied(season)
    }

    /**
     * Changes the global gameplay gate. WAR -> PEACE is deliberately valid so
     * admins can disable war without ending a season; ARCHIVED is terminal.
     */
    fun transition(
        seasonId: SeasonId,
        target: SeasonStatus,
    ): ApplicationResult<Season> = repository.transaction {
        val current = findSeason(seasonId)
            ?: return@transaction ApplicationResult.Rejected(SeasonNotFound(seasonId))
        if (current.status == target) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (target !in allowedTargets.getValue(current.status)) {
            return@transaction ApplicationResult.Rejected(
                InvalidSeasonTransition(seasonId, current.status, target),
            )
        }

        val updated = current.copy(status = target, updatedAt = clock.instant())
        updateSeason(updated)
        if (target == SeasonStatus.ARCHIVED && findActiveSeasonId() == seasonId) {
            setActiveSeasonId(null)
        }
        ApplicationResult.Applied(updated)
    }

    private companion object {
        val allowedTargets = mapOf(
            SeasonStatus.SETUP to setOf(SeasonStatus.PEACE, SeasonStatus.ARCHIVED),
            SeasonStatus.PEACE to setOf(
                SeasonStatus.WAR,
                SeasonStatus.FINALE,
                SeasonStatus.ARCHIVED,
            ),
            SeasonStatus.WAR to setOf(SeasonStatus.PEACE, SeasonStatus.FINALE),
            SeasonStatus.FINALE to setOf(SeasonStatus.ARCHIVED),
            SeasonStatus.ARCHIVED to emptySet(),
        )
    }
}

data class InvalidSeasonName(
    override val description: String,
) : ApplicationFailure

data class SeasonNameAlreadyExists(
    val seasonId: SeasonId,
    val name: String,
) : ApplicationFailure {
    override val description: String = "A season named '$name' already exists"
}

data class SeasonNotFound(
    val seasonId: SeasonId,
) : ApplicationFailure {
    override val description: String = "Season $seasonId does not exist"
}

data class InvalidSeasonTransition(
    val seasonId: SeasonId,
    val current: SeasonStatus,
    val requested: SeasonStatus,
) : ApplicationFailure {
    override val description: String =
        "Season $seasonId cannot transition from $current to $requested"
}

data class ArchivedSeasonCannotBeActive(
    val seasonId: SeasonId,
) : ApplicationFailure {
    override val description: String = "Archived season $seasonId cannot become active"
}
