package io.bennyc.civilizations.application.claim

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.civilization.CivilizationNotFound
import io.bennyc.civilizations.application.economy.EconomyLedger
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsReadContext
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.application.season.SeasonNotFound
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimGroup
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Clock

/** Atomic claim-group selection, purchase, persistence, and later index publication. */
class ClaimService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val rules: ClaimRules,
    private val phaseRules: GameplayPhaseRules = GameplayPhaseRules(),
    private val clock: Clock = Clock.systemUTC(),
) {
    private val ledger = EconomyLedger(idGenerator, clock)

    fun quote(request: PlaceClaim): ApplicationResult<ClaimPurchaseQuote> = repository.read {
        validateAndPlan(request).map { it.quote }
    }

    fun place(request: PlaceClaim): ApplicationResult<Claim> = repository.transaction {
        when (val planned = validateAndPlan(request)) {
            is ApplicationResult.Rejected -> planned
            is ApplicationResult.Unchanged -> error("Claim planning cannot be unchanged")
            is ApplicationResult.Applied -> {
                val plan = planned.value
                val claim = Claim(
                    id = plan.claimId,
                    seasonId = plan.seasonId,
                    civilizationId = request.civilizationId,
                    bounds = request.bounds,
                    groupId = plan.group.id,
                )
                if (!request.adminSponsored && plan.quote.totalPrice.minorUnits > 0) {
                    when (val payment = ledger.post(
                        this,
                        LedgerTransactionRequest(
                            seasonId = plan.seasonId,
                            idempotencyKey = request.idempotencyKey ?: "claim:${claim.id}",
                            kind = LedgerTransactionKind.CLAIM_PURCHASE,
                            postings = listOf(
                                LedgerPosting(
                                    request.civilizationId,
                                    plan.quote.totalPrice.negate(),
                                ),
                            ),
                            referenceType = "CLAIM",
                            referenceId = claim.id.toString(),
                            actorPlayerId = request.actorPlayerId,
                            description = "Civilization land claim purchase",
                        ),
                    )) {
                        is ApplicationResult.Rejected -> return@transaction payment
                        is ApplicationResult.Applied,
                        is ApplicationResult.Unchanged,
                        -> Unit
                    }
                }
                if (plan.isNewGroup) insertClaimGroup(plan.group)
                plan.mergedGroups.forEach { merged ->
                    reassignClaimsToGroup(merged.id, plan.group.id)
                    check(deleteClaimGroup(merged.id)) { "Claim group ${merged.id} disappeared" }
                }
                insertClaim(claim)
                ApplicationResult.Applied(claim)
            }
        }
    }

    private fun CivilizationsReadContext.validateAndPlan(
        request: PlaceClaim,
    ): ApplicationResult<ClaimPlacementPlan> {
        val civilization = findCivilization(request.civilizationId)
            ?: return ApplicationResult.Rejected(CivilizationNotFound(request.civilizationId))
        val season = findSeason(civilization.seasonId)
            ?: return ApplicationResult.Rejected(SeasonNotFound(civilization.seasonId))
        if (civilization.status != CivilizationStatus.ACTIVE) {
            return ApplicationResult.Rejected(
                ClaimCivilizationNotActive(civilization.id, civilization.status),
            )
        }
        if (season.status !in phaseRules.claimCreationAllowedIn) {
            return ApplicationResult.Rejected(ClaimingClosed(season.id, season.status))
        }
        if (request.bounds.area > rules.maxArea) {
            return ApplicationResult.Rejected(ClaimAreaExceeded(request.bounds.area, rules.maxArea))
        }
        if (!request.adminSponsored) {
            val actor = request.actorPlayerId
                ?: return ApplicationResult.Rejected(ClaimActorRequired)
            val membership = findMembership(season.id, actor)
            if (membership?.civilizationId != civilization.id ||
                membership.role !in rules.ordinaryInitiatorRoles
            ) {
                return ApplicationResult.Rejected(ClaimAuthorityRequired(actor, civilization.id))
            }
        }

        val seasonClaims = listClaimsForSeason(season.id)
        val civilizationClaims = seasonClaims.filter { it.civilizationId == civilization.id }
        if (civilizationClaims.size >= rules.maxClaimsPerCivilization) {
            return ApplicationResult.Rejected(
                ClaimCountExceeded(civilization.id, rules.maxClaimsPerCivilization),
            )
        }
        val index = ClaimSpatialIndex(season.id, seasonClaims)
        index.findIntersecting(request.bounds).firstOrNull()?.let { overlap ->
            return ApplicationResult.Rejected(
                ClaimOverlapsExisting(overlap.id, overlap.civilizationId),
            )
        }

        val groups = listClaimGroups(civilization.id)
        val groupsById = groups.associateBy(ClaimGroup::id)
        val adjacentGroups = index.findEdgeAdjacent(request.bounds, civilization.id)
            .mapNotNull { groupsById[it.groupId] }
            .distinctBy(ClaimGroup::id)
            .sortedBy(ClaimGroup::ordinal)
        val isNewGroup = adjacentGroups.isEmpty()
        val targetGroupNumber = if (isNewGroup) groups.size + 1 else groups.size
        val tier = rules.tierForGroupCount(targetGroupNumber)
            ?: return ApplicationResult.Rejected(
                if (rules.requireEdgeConnection && rules.groupTiers.size == 1) {
                    ClaimIsDisconnected(civilization.id)
                } else {
                    ClaimGroupLimitExceeded(civilization.id, rules.groupTiers.size)
                },
            )

        val memberCount = listMemberships(civilization.id).size
        val account = findCivilizationAccount(civilization.id)
        if (!request.adminSponsored && isNewGroup) {
            if (memberCount < tier.minimumMembers) {
                return ApplicationResult.Rejected(
                    ClaimGroupMembersRequired(tier.minimumMembers, memberCount),
                )
            }
            val balance = account?.balance ?: MoneyAmount.ZERO
            if (balance.minorUnits < tier.minimumTreasuryBalance.minorUnits) {
                return ApplicationResult.Rejected(
                    ClaimGroupTreasuryRequired(tier.minimumTreasuryBalance, balance),
                )
            }
        }

        val establishmentCost = if (isNewGroup) tier.establishmentCost else MoneyAmount.ZERO
        val landPrice = try {
            rules.baseClaimPrice.plus(rules.pricePerBlock.times(request.bounds.area))
        } catch (_: RuntimeException) {
            return ApplicationResult.Rejected(ClaimPriceOverflow)
        }
        val totalPrice = if (request.adminSponsored) MoneyAmount.ZERO else try {
            landPrice.plus(establishmentCost)
        } catch (_: RuntimeException) {
            return ApplicationResult.Rejected(ClaimPriceOverflow)
        }
        if (!request.adminSponsored &&
            (account == null || account.balance.minorUnits < totalPrice.minorUnits)
        ) {
            return ApplicationResult.Rejected(
                ClaimPurchaseFundsRequired(totalPrice, account?.balance ?: MoneyAmount.ZERO),
            )
        }

        val group = adjacentGroups.firstOrNull() ?: ClaimGroup(
            id = idGenerator.newClaimGroupId(),
            seasonId = season.id,
            civilizationId = civilization.id,
            ordinal = (groups.maxOfOrNull(ClaimGroup::ordinal) ?: 0) + 1,
            foundedByPlayerId = request.actorPlayerId,
            establishmentCost = if (request.adminSponsored) MoneyAmount.ZERO else establishmentCost,
            requiredMemberCount = if (request.adminSponsored) 0 else tier.minimumMembers,
            requiredTreasuryBalance = if (request.adminSponsored) {
                MoneyAmount.ZERO
            } else {
                tier.minimumTreasuryBalance
            },
            createdAt = clock.instant(),
        )
        return ApplicationResult.Applied(
            ClaimPlacementPlan(
                claimId = idGenerator.newClaimId(),
                seasonId = season.id,
                group = group,
                isNewGroup = isNewGroup,
                mergedGroups = adjacentGroups.drop(1),
                quote = ClaimPurchaseQuote(
                    area = request.bounds.area,
                    landPrice = if (request.adminSponsored) MoneyAmount.ZERO else landPrice,
                    establishmentPrice = if (request.adminSponsored) {
                        MoneyAmount.ZERO
                    } else {
                        establishmentCost
                    },
                    totalPrice = totalPrice,
                    createsGroup = isNewGroup,
                    resultingGroupCount = if (isNewGroup) groups.size + 1
                    else groups.size - adjacentGroups.drop(1).size,
                ),
            ),
        )
    }
}

data class ClaimRules(
    val maxArea: Long,
    val maxClaimsPerCivilization: Int,
    val requireEdgeConnection: Boolean = true,
    val baseClaimPrice: MoneyAmount = MoneyAmount.ZERO,
    val pricePerBlock: MoneyAmount = MoneyAmount.ZERO,
    val ordinaryInitiatorRoles: Set<MembershipRole> = setOf(MembershipRole.LEADER),
    val groupTiers: List<ClaimGroupTier> = listOf(ClaimGroupTier(1)),
) {
    init {
        require(maxArea > 0) { "Maximum claim area must be positive" }
        require(maxClaimsPerCivilization > 0) { "Maximum claims per civilization must be positive" }
        require(baseClaimPrice.minorUnits >= 0 && pricePerBlock.minorUnits >= 0)
        require(ordinaryInitiatorRoles.isNotEmpty())
        require(groupTiers.isNotEmpty())
        require(groupTiers.map(ClaimGroupTier::maxGroups) == (1..groupTiers.size).toList()) {
            "Claim-group tiers must be contiguous from one"
        }
    }

    fun tierForGroupCount(count: Int): ClaimGroupTier? = groupTiers.getOrNull(count - 1)
}

data class ClaimGroupTier(
    val maxGroups: Int,
    val minimumMembers: Int = 0,
    val minimumTreasuryBalance: MoneyAmount = MoneyAmount.ZERO,
    val establishmentCost: MoneyAmount = MoneyAmount.ZERO,
) {
    init {
        require(maxGroups > 0 && minimumMembers >= 0)
        require(minimumTreasuryBalance.minorUnits >= 0 && establishmentCost.minorUnits >= 0)
    }
}

data class PlaceClaim(
    val civilizationId: CivilizationId,
    val bounds: ClaimBounds,
    val actorPlayerId: PlayerId? = null,
    val adminSponsored: Boolean = true,
    val idempotencyKey: String? = null,
)

data class ClaimPurchaseQuote(
    val area: Long,
    val landPrice: MoneyAmount,
    val establishmentPrice: MoneyAmount,
    val totalPrice: MoneyAmount,
    val createsGroup: Boolean,
    val resultingGroupCount: Int,
)

private data class ClaimPlacementPlan(
    val claimId: ClaimId,
    val seasonId: SeasonId,
    val group: ClaimGroup,
    val isNewGroup: Boolean,
    val mergedGroups: List<ClaimGroup>,
    val quote: ClaimPurchaseQuote,
)

data class ClaimCivilizationNotActive(
    val civilizationId: CivilizationId,
    val status: CivilizationStatus,
) : ApplicationFailure {
    override val description = "Civilization $civilizationId cannot claim land while $status"
}

data class ClaimingClosed(val seasonId: SeasonId, val seasonStatus: SeasonStatus) :
    ApplicationFailure {
    override val description = "Claims cannot change while season $seasonId is $seasonStatus"
}

data class ClaimAreaExceeded(val requestedArea: Long, val maximumArea: Long) : ApplicationFailure {
    override val description = "Claim area $requestedArea exceeds the maximum $maximumArea"
}

data class ClaimCountExceeded(val civilizationId: CivilizationId, val maximumClaims: Int) :
    ApplicationFailure {
    override val description = "Civilization $civilizationId already has the maximum $maximumClaims claims"
}

data class ClaimOverlapsExisting(val claimId: ClaimId, val ownerId: CivilizationId) :
    ApplicationFailure {
    override val description = "The requested land overlaps claim $claimId owned by civilization $ownerId"
}

data class ClaimIsDisconnected(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description = "The requested land must share a block edge with civilization $civilizationId"
}

data object ClaimActorRequired : ApplicationFailure {
    override val description = "A player actor is required for an ordinary claim purchase"
}

data class ClaimAuthorityRequired(val playerId: PlayerId, val civilizationId: CivilizationId) :
    ApplicationFailure {
    override val description = "Player $playerId cannot purchase claims for civilization $civilizationId"
}

data class ClaimGroupLimitExceeded(val civilizationId: CivilizationId, val maximum: Int) :
    ApplicationFailure {
    override val description = "Civilization $civilizationId may own at most $maximum claim groups"
}

data class ClaimGroupMembersRequired(val required: Int, val actual: Int) : ApplicationFailure {
    override val description = "A new claim group requires $required members; this civilization has $actual"
}

data class ClaimGroupTreasuryRequired(val required: MoneyAmount, val actual: MoneyAmount) :
    ApplicationFailure {
    override val description = "A new claim group requires treasury ${required.minorUnits}; current ${actual.minorUnits}"
}

data class ClaimPurchaseFundsRequired(val required: MoneyAmount, val actual: MoneyAmount) :
    ApplicationFailure {
    override val description = "Claim purchase requires ${required.minorUnits}; treasury has ${actual.minorUnits}"
}

data object ClaimPriceOverflow : ApplicationFailure {
    override val description = "Claim price exceeds the supported money range"
}

private inline fun <T, R> ApplicationResult<T>.map(transform: (T) -> R): ApplicationResult<R> =
    when (this) {
        is ApplicationResult.Applied -> ApplicationResult.Applied(transform(value))
        is ApplicationResult.Unchanged -> ApplicationResult.Unchanged(transform(value))
        is ApplicationResult.Rejected -> this
    }
