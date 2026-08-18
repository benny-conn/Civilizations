package io.bennyc.civilizations.application.claim

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationNotFound
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.season.SeasonNotFound
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus

/**
 * Validates and persists infrequent claim mutations against authoritative data.
 * This service performs blocking persistence and must run on plugin-owned
 * background execution. A Paper adapter installs an applied claim into the live
 * [ClaimSpatialIndex] on the server thread before exposing success to players.
 */
class ClaimService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val rules: ClaimRules,
) {
    fun place(request: PlaceClaim): ApplicationResult<Claim> = repository.transaction {
        val civilization = findCivilization(request.civilizationId)
            ?: return@transaction ApplicationResult.Rejected(
                CivilizationNotFound(request.civilizationId),
            )
        val season = findSeason(civilization.seasonId)
            ?: return@transaction ApplicationResult.Rejected(
                SeasonNotFound(civilization.seasonId),
            )
        if (civilization.status != CivilizationStatus.ACTIVE) {
            return@transaction ApplicationResult.Rejected(
                ClaimCivilizationNotActive(civilization.id, civilization.status),
            )
        }
        if (season.status !in claimableSeasonStatuses) {
            return@transaction ApplicationResult.Rejected(
                ClaimingClosed(season.id, season.status),
            )
        }
        if (request.bounds.area > rules.maxArea) {
            return@transaction ApplicationResult.Rejected(
                ClaimAreaExceeded(request.bounds.area, rules.maxArea),
            )
        }

        val seasonClaims = listClaimsForSeason(season.id)
        val civilizationClaims = seasonClaims.filter {
            it.civilizationId == civilization.id
        }
        if (civilizationClaims.size >= rules.maxClaimsPerCivilization) {
            return@transaction ApplicationResult.Rejected(
                ClaimCountExceeded(civilization.id, rules.maxClaimsPerCivilization),
            )
        }

        // Claim creation is not an event hot path. Building this transaction-local
        // index avoids trusting a cross-thread live cache while retaining exact,
        // chunk-bucketed geometry checks for arbitrary rectangle arrangements.
        val index = ClaimSpatialIndex(season.id, seasonClaims)
        val overlap = index.findIntersecting(request.bounds).firstOrNull()
        if (overlap != null) {
            return@transaction ApplicationResult.Rejected(
                ClaimOverlapsExisting(overlap.id, overlap.civilizationId),
            )
        }
        if (
            rules.requireEdgeConnection &&
            civilizationClaims.isNotEmpty() &&
            index.findEdgeAdjacent(request.bounds, civilization.id).isEmpty()
        ) {
            return@transaction ApplicationResult.Rejected(
                ClaimIsDisconnected(civilization.id),
            )
        }

        val claim = Claim(
            id = idGenerator.newClaimId(),
            seasonId = season.id,
            civilizationId = civilization.id,
            bounds = request.bounds,
        )
        insertClaim(claim)
        ApplicationResult.Applied(claim)
    }

    private companion object {
        val claimableSeasonStatuses = setOf(SeasonStatus.SETUP, SeasonStatus.PEACE)
    }
}

data class ClaimRules(
    val maxArea: Long,
    val maxClaimsPerCivilization: Int,
    val requireEdgeConnection: Boolean = true,
) {
    init {
        require(maxArea > 0) { "Maximum claim area must be positive" }
        require(maxClaimsPerCivilization > 0) {
            "Maximum claims per civilization must be positive"
        }
    }
}

data class PlaceClaim(
    val civilizationId: CivilizationId,
    val bounds: ClaimBounds,
)

data class ClaimCivilizationNotActive(
    val civilizationId: CivilizationId,
    val status: CivilizationStatus,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId cannot claim land while $status"
}

data class ClaimingClosed(
    val seasonId: SeasonId,
    val seasonStatus: SeasonStatus,
) : ApplicationFailure {
    override val description: String =
        "Claims cannot change while season $seasonId is $seasonStatus"
}

data class ClaimAreaExceeded(
    val requestedArea: Long,
    val maximumArea: Long,
) : ApplicationFailure {
    override val description: String =
        "Claim area $requestedArea exceeds the maximum $maximumArea"
}

data class ClaimCountExceeded(
    val civilizationId: CivilizationId,
    val maximumClaims: Int,
) : ApplicationFailure {
    override val description: String =
        "Civilization $civilizationId already has the maximum $maximumClaims claims"
}

data class ClaimOverlapsExisting(
    val claimId: ClaimId,
    val ownerId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "The requested land overlaps claim $claimId owned by civilization $ownerId"
}

data class ClaimIsDisconnected(
    val civilizationId: CivilizationId,
) : ApplicationFailure {
    override val description: String =
        "The requested land must share a block edge with civilization $civilizationId"
}
