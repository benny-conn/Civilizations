package io.bennyc.civilizations.application.civilization

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.season.SeasonNotFound
import io.bennyc.civilizations.domain.civilization.Civilization
import io.bennyc.civilizations.domain.civilization.CivilizationName
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock

class CivilizationService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
    private val phaseRules: GameplayPhaseRules = GameplayPhaseRules(),
) {
    /** Creates an intentionally landless, leaderless draft. */
    fun createDraft(
        seasonId: SeasonId,
        rawName: String,
    ): ApplicationResult<Civilization> {
        val name = parseName(rawName) ?: return invalidName(rawName)
        return repository.transaction {
            validateRosterPhase(seasonId)?.let { return@transaction ApplicationResult.Rejected(it) }
            findCivilizationByName(seasonId, name)?.let {
                return@transaction ApplicationResult.Rejected(
                    CivilizationNameAlreadyExists(seasonId, it.id, it.name.value),
                )
            }

            val now = clock.instant()
            val civilization = Civilization(
                id = idGenerator.newCivilizationId(),
                seasonId = seasonId,
                name = name,
                status = CivilizationStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            )
            insertCivilization(civilization)
            ApplicationResult.Applied(civilization)
        }
    }

    /**
     * Atomically creates or completes a preselected roster. Existing members
     * not named in the request are retained, making repeated setup safe.
     */
    fun provision(request: ProvisionCivilization): ApplicationResult<CivilizationRoster> {
        val name = parseName(request.rawName) ?: return invalidName(request.rawName)
        val requestedPlayers = linkedSetOf(request.leaderId).apply {
            addAll(request.memberIds)
        }

        return repository.transaction {
            validateRosterPhase(request.seasonId)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }

            val now = clock.instant()
            val existing = findCivilizationByName(request.seasonId, name)
            if (existing?.status == CivilizationStatus.DISSOLVED) {
                return@transaction ApplicationResult.Rejected(
                    CivilizationUnavailable(existing.id, existing.status),
                )
            }
            val civilization = existing ?: Civilization(
                id = idGenerator.newCivilizationId(),
                seasonId = request.seasonId,
                name = name,
                status = CivilizationStatus.DRAFT,
                createdAt = now,
                updatedAt = now,
            )

            for (playerId in requestedPlayers) {
                val assignment = findMembership(request.seasonId, playerId)
                if (assignment != null && assignment.civilizationId != civilization.id) {
                    return@transaction ApplicationResult.Rejected(
                        PlayerAlreadyAssigned(
                            seasonId = request.seasonId,
                            playerId = playerId,
                            civilizationId = assignment.civilizationId,
                        ),
                    )
                }
            }

            var changed = false
            if (existing == null) {
                insertCivilization(civilization)
                changed = true
            }

            val memberships = listMemberships(civilization.id).associateBy { it.playerId }.toMutableMap()
            for (playerId in requestedPlayers) {
                if (playerId !in memberships) {
                    val membership = Membership(
                        seasonId = request.seasonId,
                        civilizationId = civilization.id,
                        playerId = playerId,
                        role = MembershipRole.MEMBER,
                        joinedAt = now,
                    )
                    insertMembership(membership)
                    memberships[playerId] = membership
                    changed = true
                }
            }

            val oldLeader = memberships.values.singleOrNull { it.role == MembershipRole.LEADER }
            if (oldLeader?.playerId != request.leaderId) {
                if (oldLeader != null) {
                    val demoted = oldLeader.copy(role = MembershipRole.MEMBER)
                    updateMembership(demoted)
                    memberships[demoted.playerId] = demoted
                }
                val promoted = memberships.getValue(request.leaderId).copy(role = MembershipRole.LEADER)
                updateMembership(promoted)
                memberships[promoted.playerId] = promoted
                changed = true
            }

            val finalCivilization = if (
                request.activate && civilization.status == CivilizationStatus.DRAFT
            ) {
                civilization.copy(status = CivilizationStatus.ACTIVE, updatedAt = now).also {
                    updateCivilization(it)
                    changed = true
                }
            } else {
                civilization
            }

            val roster = CivilizationRoster(
                civilization = finalCivilization,
                memberships = memberships.values.sortedWith(membershipOrder),
            )
            if (changed) ApplicationResult.Applied(roster) else ApplicationResult.Unchanged(roster)
        }
    }

    fun assignMember(
        civilizationId: CivilizationId,
        playerId: PlayerId,
    ): ApplicationResult<Membership> = repository.transaction {
        val civilization = findCivilization(civilizationId)
            ?: return@transaction ApplicationResult.Rejected(CivilizationNotFound(civilizationId))
        validateMutable(civilization)?.let {
            return@transaction ApplicationResult.Rejected(it)
        }
        validateRosterPhase(civilization.seasonId)?.let {
            return@transaction ApplicationResult.Rejected(it)
        }

        val existing = findMembership(civilization.seasonId, playerId)
        if (existing != null) {
            return@transaction if (existing.civilizationId == civilizationId) {
                ApplicationResult.Unchanged(existing)
            } else {
                ApplicationResult.Rejected(
                    PlayerAlreadyAssigned(civilization.seasonId, playerId, existing.civilizationId),
                )
            }
        }

        val membership = Membership(
            seasonId = civilization.seasonId,
            civilizationId = civilizationId,
            playerId = playerId,
            role = MembershipRole.MEMBER,
            joinedAt = clock.instant(),
        )
        insertMembership(membership)
        ApplicationResult.Applied(membership)
    }

    /** Explicitly moves a non-leader; leaders must transfer leadership first. */
    fun moveMember(
        seasonId: SeasonId,
        playerId: PlayerId,
        targetCivilizationId: CivilizationId,
    ): ApplicationResult<Membership> = repository.transaction {
        validateRosterPhase(seasonId)?.let { return@transaction ApplicationResult.Rejected(it) }
        val target = findCivilization(targetCivilizationId)
            ?: return@transaction ApplicationResult.Rejected(
                CivilizationNotFound(targetCivilizationId),
            )
        if (target.seasonId != seasonId) {
            return@transaction ApplicationResult.Rejected(
                CivilizationSeasonMismatch(targetCivilizationId, seasonId, target.seasonId),
            )
        }
        validateMutable(target)?.let { return@transaction ApplicationResult.Rejected(it) }

        val current = findMembership(seasonId, playerId)
            ?: return@transaction ApplicationResult.Rejected(PlayerNotAssigned(seasonId, playerId))
        if (current.civilizationId == targetCivilizationId) {
            return@transaction ApplicationResult.Unchanged(current)
        }
        if (current.role == MembershipRole.LEADER) {
            return@transaction ApplicationResult.Rejected(
                LeaderCannotMove(playerId, current.civilizationId),
            )
        }

        val moved = current.copy(
            civilizationId = targetCivilizationId,
            joinedAt = clock.instant(),
        )
        updateMembership(moved)
        ApplicationResult.Applied(moved)
    }

    fun transferLeadership(
        civilizationId: CivilizationId,
        newLeaderId: PlayerId,
    ): ApplicationResult<CivilizationRoster> = repository.transaction {
        val civilization = findCivilization(civilizationId)
            ?: return@transaction ApplicationResult.Rejected(CivilizationNotFound(civilizationId))
        validateMutable(civilization)?.let { return@transaction ApplicationResult.Rejected(it) }
        validateRosterPhase(civilization.seasonId)?.let {
            return@transaction ApplicationResult.Rejected(it)
        }
        val memberships = listMemberships(civilizationId).toMutableList()
        val newLeaderIndex = memberships.indexOfFirst { it.playerId == newLeaderId }
        if (newLeaderIndex < 0) {
            return@transaction ApplicationResult.Rejected(
                PlayerNotInCivilization(newLeaderId, civilizationId),
            )
        }
        val oldLeaderIndex = memberships.indexOfFirst { it.role == MembershipRole.LEADER }
        if (oldLeaderIndex == newLeaderIndex) {
            return@transaction ApplicationResult.Unchanged(
                CivilizationRoster(civilization, memberships.sortedWith(membershipOrder)),
            )
        }

        if (oldLeaderIndex >= 0) {
            val demoted = memberships[oldLeaderIndex].copy(role = MembershipRole.MEMBER)
            updateMembership(demoted)
            memberships[oldLeaderIndex] = demoted
        }
        val promoted = memberships[newLeaderIndex].copy(role = MembershipRole.LEADER)
        updateMembership(promoted)
        memberships[newLeaderIndex] = promoted

        ApplicationResult.Applied(
            CivilizationRoster(civilization, memberships.sortedWith(membershipOrder)),
        )
    }

    fun activate(civilizationId: CivilizationId): ApplicationResult<Civilization> =
        repository.transaction {
            val civilization = findCivilization(civilizationId)
                ?: return@transaction ApplicationResult.Rejected(
                    CivilizationNotFound(civilizationId),
                )
            if (civilization.status == CivilizationStatus.ACTIVE) {
                return@transaction ApplicationResult.Unchanged(civilization)
            }
            validateMutable(civilization)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }
            validateRosterPhase(civilization.seasonId)?.let {
                return@transaction ApplicationResult.Rejected(it)
            }
            if (listMemberships(civilizationId).none { it.role == MembershipRole.LEADER }) {
                return@transaction ApplicationResult.Rejected(
                    CivilizationHasNoLeader(civilizationId),
                )
            }

            // Land is intentionally not an activation prerequisite.
            val activated = civilization.copy(
                status = CivilizationStatus.ACTIVE,
                updatedAt = clock.instant(),
            )
            updateCivilization(activated)
            ApplicationResult.Applied(activated)
        }

    private fun CivilizationsWriteContext.validateRosterPhase(
        seasonId: SeasonId,
    ): ApplicationFailure? {
        val season = findSeason(seasonId) ?: return SeasonNotFound(seasonId)
        return if (season.status in phaseRules.rosterChangesAllowedIn) {
            null
        } else {
            RosterChangesClosed(seasonId, season.status)
        }
    }

    private fun validateMutable(civilization: Civilization): ApplicationFailure? =
        if (civilization.status == CivilizationStatus.DISSOLVED) {
            CivilizationUnavailable(civilization.id, civilization.status)
        } else {
            null
        }

    private fun parseName(rawName: String): CivilizationName? =
        try {
            CivilizationName.from(rawName)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun <T> invalidName(rawName: String): ApplicationResult<T> =
        ApplicationResult.Rejected(InvalidCivilizationName(rawName))

    private companion object {
        val membershipOrder = compareBy<Membership>({ it.role }, { it.joinedAt }, { it.playerId.toString() })
    }
}

data class ProvisionCivilization(
    val seasonId: SeasonId,
    val rawName: String,
    val leaderId: PlayerId,
    val memberIds: Set<PlayerId> = emptySet(),
    val activate: Boolean = true,
)

data class CivilizationRoster(
    val civilization: Civilization,
    val memberships: List<Membership>,
)

data class InvalidCivilizationName(
    val suppliedName: String,
) : ApplicationFailure {
    override val description: String = "'$suppliedName' is not a valid civilization name"
}

data class CivilizationNameAlreadyExists(
    val seasonId: SeasonId,
    val civilizationId: CivilizationId,
    val name: String,
) : ApplicationFailure {
    override val description: String = "A civilization named '$name' already exists in season $seasonId"
}

data class CivilizationNotFound(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId does not exist"
}

data class CivilizationUnavailable(
    val civilizationId: CivilizationId,
    val status: CivilizationStatus,
) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId is $status"
}

data class CivilizationSeasonMismatch(
    val civilizationId: CivilizationId,
    val expectedSeasonId: SeasonId,
    val actualSeasonId: SeasonId,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId belongs to season $actualSeasonId, not $expectedSeasonId"
}

data class PlayerAlreadyAssigned(
    val seasonId: SeasonId,
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Player $playerId is already assigned to civilization $civilizationId in season $seasonId"
}

data class PlayerNotAssigned(
    val seasonId: SeasonId,
    val playerId: PlayerId,
) : ApplicationFailure {
    override val description: String = "Player $playerId is not assigned in season $seasonId"
}

data class PlayerNotInCivilization(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String = "Player $playerId is not a member of civilization $civilizationId"
}

data class LeaderCannotMove(
    val playerId: PlayerId,
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "Leader $playerId must transfer leadership before leaving civilization $civilizationId"
}

data class CivilizationHasNoLeader(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String = "Civilization $civilizationId cannot activate without a leader"
}

data class RosterChangesClosed(
    val seasonId: SeasonId,
    val seasonStatus: SeasonStatus,
) : ApplicationFailure {
    override val description: String =
        "Roster changes are closed while season $seasonId is $seasonStatus"
}
