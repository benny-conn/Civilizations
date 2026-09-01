package io.bennyc.civilizations.application.protection

import io.bennyc.civilizations.application.ApplicationFailure
import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.economy.EconomyLedger
import io.bennyc.civilizations.application.economy.LedgerTransactionRequest
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.application.persistence.CivilizationsRepository
import io.bennyc.civilizations.domain.civilization.CivilizationStatus
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.economy.LedgerPosting
import io.bennyc.civilizations.domain.economy.LedgerTransactionId
import io.bennyc.civilizations.domain.economy.LedgerTransactionKind
import io.bennyc.civilizations.domain.economy.MoneyAmount
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.protection.ExposureDamageEvent
import io.bennyc.civilizations.domain.protection.ExposureDamageSite
import io.bennyc.civilizations.domain.protection.LandProtectionState
import io.bennyc.civilizations.domain.protection.LandProtectionStatus
import io.bennyc.civilizations.domain.protection.LandUpkeepAssessment
import io.bennyc.civilizations.domain.protection.LandUpkeepAssessmentStatus
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.war.BattleStatus
import java.time.Clock

data class LandProtectionRules(
    val enabled: Boolean,
    val intervalSeconds: Long,
    val graceSeconds: Long,
    val baseCharge: MoneyAmount,
    val perBlockCharge: MoneyAmount,
    val baseReserve: MoneyAmount,
    val perBlockReserve: MoneyAmount,
    val damageLimitPerExposure: Int,
    val assessmentIntervalSeconds: Long = 60,
) {
    init {
        require(intervalSeconds in 60..MAX_INTERVAL_SECONDS)
        require(graceSeconds in 60..MAX_GRACE_SECONDS)
        require(baseCharge.minorUnits >= 0 && perBlockCharge.minorUnits >= 0)
        require(baseReserve.minorUnits >= 0 && perBlockReserve.minorUnits >= 0)
        require(damageLimitPerExposure in 1..MAX_DAMAGE_LIMIT)
        require(assessmentIntervalSeconds in 10..3_600)
    }

    companion object {
        const val MAX_INTERVAL_SECONDS = 366L * 24L * 60L * 60L
        const val MAX_GRACE_SECONDS = 90L * 24L * 60L * 60L
        const val MAX_DAMAGE_LIMIT = 100_000
    }
}

class LandProtectionService(
    private val repository: CivilizationsRepository,
    private val idGenerator: CivilizationsIdGenerator,
    private val clock: Clock,
    private val rules: LandProtectionRules,
) {
    private val ledger = EconomyLedger(idGenerator, clock)

    /** Keeps one current state per active civilization and refreshes its live reserve. */
    fun synchronize(seasonId: SeasonId): ApplicationResult<List<LandProtectionState>> =
        repository.transaction {
            val now = clock.instant()
            val states = mutableListOf<LandProtectionState>()
            for (civilization in listCivilizations(seasonId)) {
                if (civilization.status != CivilizationStatus.ACTIVE ||
                    findCivilizationAccount(civilization.id) == null
                ) continue
                val area = claimedArea(civilization.id)
                val reserve = if (rules.enabled && area > 0) reserveFor(area) else MoneyAmount.ZERO
                val existing = findLandProtectionState(civilization.id)
                val synchronized = when {
                    existing == null -> LandProtectionState(
                        seasonId = seasonId,
                        civilizationId = civilization.id,
                        status = LandProtectionStatus.PROTECTED,
                        nextAssessmentAt = if (rules.enabled && area > 0) {
                            now.plusSeconds(rules.intervalSeconds)
                        } else null,
                        requiredReserve = reserve,
                        delinquentAmount = MoneyAmount.ZERO,
                        graceEndsAt = null,
                        exposureId = null,
                        exposureStartedAt = null,
                        exposureDamageLimit = null,
                        exposureDamageCount = 0,
                        updatedAt = now,
                    ).also(::insertLandProtectionState)
                    !rules.enabled -> persistIfChanged(
                        existing.copy(
                            status = LandProtectionStatus.PROTECTED,
                            nextAssessmentAt = null,
                            requiredReserve = MoneyAmount.ZERO,
                            delinquentAmount = MoneyAmount.ZERO,
                            graceEndsAt = null,
                            exposureId = null,
                            exposureStartedAt = null,
                            exposureDamageLimit = null,
                            exposureDamageCount = 0,
                            updatedAt = existing.updatedAt,
                        ),
                        existing,
                        now,
                    )
                    existing.status == LandProtectionStatus.PROTECTED -> persistIfChanged(
                        existing.copy(
                            nextAssessmentAt = when {
                                area == 0L -> null
                                existing.nextAssessmentAt == null -> now.plusSeconds(rules.intervalSeconds)
                                else -> existing.nextAssessmentAt
                            },
                            requiredReserve = reserve,
                            updatedAt = existing.updatedAt,
                        ),
                        existing,
                        now,
                    )
                    else -> existing
                }
                states += synchronized
            }
            ApplicationResult.Applied(states)
        }

    private fun io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
        .persistIfChanged(
            candidate: LandProtectionState,
            existing: LandProtectionState,
            now: java.time.Instant,
        ): LandProtectionState {
        if (candidate == existing) return existing
        return candidate.copy(updatedAt = now).also(::updateLandProtectionState)
    }

    fun assessAll(seasonId: SeasonId): ApplicationResult<List<LandProtectionState>> =
        repository.transaction {
            val before = listLandProtectionStates(seasonId)
            val after = before.map { state -> assess(state) }
            if (after == before) ApplicationResult.Unchanged(after)
            else ApplicationResult.Applied(after)
        }

    fun assess(civilizationId: CivilizationId): ApplicationResult<LandProtectionState> =
        repository.transaction {
            val state = findLandProtectionState(civilizationId)
                ?: return@transaction ApplicationResult.Rejected(
                    LandProtectionStateNotFound(civilizationId),
                )
            val assessed = assess(state)
            if (assessed == state) ApplicationResult.Unchanged(state)
            else ApplicationResult.Applied(assessed)
        }

    fun prepareMutation(
        request: PrepareExposureMutation,
    ): ApplicationResult<PreparedExposureMutation> = repository.transaction {
        val state = findLandProtectionState(request.ownerCivilizationId)
            ?: return@transaction ApplicationResult.Rejected(
                LandProtectionStateNotFound(request.ownerCivilizationId),
            )
        if (state.status != LandProtectionStatus.EXPOSED ||
            state.exposureId != request.exposureId
        ) {
            return@transaction ApplicationResult.Rejected(LandIsNotExposed)
        }
        val season = findSeason(state.seasonId)
            ?: return@transaction ApplicationResult.Rejected(LandIsNotExposed)
        if (season.status == SeasonStatus.FINALE || season.status == SeasonStatus.ARCHIVED) {
            return@transaction ApplicationResult.Rejected(LandIsNotExposed)
        }
        if (hasOpenBattle(request.ownerCivilizationId) ||
            hasOpenBattle(request.actorCivilizationId)
        ) {
            return@transaction ApplicationResult.Rejected(ExposureSuspendedForBattle)
        }
        val membership = findMembership(state.seasonId, request.actorPlayerId)
        if (membership?.civilizationId != request.actorCivilizationId ||
            request.actorCivilizationId == request.ownerCivilizationId
        ) {
            return@transaction ApplicationResult.Rejected(ExposureRequiresOtherCivilization)
        }
        val claim = findClaim(request.claimId)
        if (claim?.civilizationId != request.ownerCivilizationId ||
            !claim.bounds.contains(request.position.x, request.position.z)
        ) {
            return@transaction ApplicationResult.Rejected(ExposureClaimMismatch)
        }

        var site = findExposureDamageSite(request.exposureId, request.position)
        val firstDamage = site == null
        if (firstDamage) {
            val limit = checkNotNull(state.exposureDamageLimit)
            if (state.exposureDamageCount >= limit) {
                return@transaction ApplicationResult.Rejected(ExposureDamageLimitReached(limit))
            }
            site = ExposureDamageSite(
                id = idGenerator.newExposureDamageSiteId(),
                seasonId = state.seasonId,
                civilizationId = state.civilizationId,
                exposureId = request.exposureId,
                claimId = request.claimId,
                position = request.position,
                originalState = request.observedState,
                createdAt = clock.instant(),
                resolvedAt = null,
            )
            insertExposureDamageSite(site)
            updateLandProtectionState(
                state.copy(
                    exposureDamageCount = state.exposureDamageCount + 1,
                    updatedAt = clock.instant(),
                ),
            )
        }
        site = checkNotNull(site)
        if (site.resolvedAt != null) {
            return@transaction ApplicationResult.Rejected(LandIsNotExposed)
        }
        val latest = findLatestExposureDamageEvent(site.id)
        val event = ExposureDamageEvent(
            id = idGenerator.newExposureDamageEventId(),
            siteId = site.id,
            ordinal = (latest?.ordinal ?: 0) + 1,
            actorPlayerId = request.actorPlayerId,
            actorCivilizationId = request.actorCivilizationId,
            cause = request.cause,
            observedState = request.observedState,
            expectedState = request.expectedState,
            recordedAt = clock.instant(),
        )
        insertExposureDamageEvent(event)
        ApplicationResult.Applied(PreparedExposureMutation(site, event, firstDamage))
    }

    private fun io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
        .assess(state: LandProtectionState): LandProtectionState {
        if (!rules.enabled) return state
        val now = clock.instant()
        val area = claimedArea(state.civilizationId)
        if (area == 0L) {
            return persistIfChanged(
                state.copy(
                    status = LandProtectionStatus.PROTECTED,
                    nextAssessmentAt = null,
                    requiredReserve = MoneyAmount.ZERO,
                    delinquentAmount = MoneyAmount.ZERO,
                    graceEndsAt = null,
                    exposureId = null,
                    exposureStartedAt = null,
                    exposureDamageLimit = null,
                    exposureDamageCount = 0,
                    updatedAt = state.updatedAt,
                ),
                state,
                now,
            )
        }
        if (state.status != LandProtectionStatus.PROTECTED) {
            return recoverOrAdvance(state, now)
        }
        val reserve = reserveFor(area)
        val due = state.nextAssessmentAt ?: now.plusSeconds(rules.intervalSeconds)
        if (due > now) {
            return persistIfChanged(
                state.copy(
                    requiredReserve = reserve,
                    nextAssessmentAt = due,
                    updatedAt = state.updatedAt,
                ),
                state,
                now,
            )
        }
        val prior = findLandUpkeepAssessment(state.civilizationId, due)
        val assessedArea = prior?.claimedArea ?: area
        val charge = prior?.totalCharge ?: chargeFor(area)
        val assessedReserve = prior?.requiredReserve ?: reserve
        val assessedInterval = prior?.intervalSeconds ?: rules.intervalSeconds
        val assessedGrace = prior?.graceSeconds ?: rules.graceSeconds
        val assessedDamageLimit = prior?.damageLimit ?: rules.damageLimitPerExposure
        if (hasOpenBattle(state.civilizationId)) {
            if (prior == null) {
                insertLandUpkeepAssessment(
                    assessment(
                        state, due, now, area, charge, reserve,
                        LandUpkeepAssessmentStatus.DEFERRED_FOR_BATTLE,
                        null,
                        null,
                    ),
                )
            }
            return persistIfChanged(
                state.copy(
                    requiredReserve = assessedReserve,
                    updatedAt = state.updatedAt,
                ),
                state,
                now,
            )
        }
        val account = checkNotNull(findCivilizationAccount(state.civilizationId))
        val canPay = account.balance.minorUnits >= charge.plus(assessedReserve).minorUnits
        if (canPay) {
            val ledgerId = postCharge(state, due, charge)
            val paid = prior?.copy(
                assessedAt = now,
                status = LandUpkeepAssessmentStatus.PAID,
                ledgerTransactionId = ledgerId,
            ) ?: assessment(
                state, due, now, assessedArea, charge, assessedReserve,
                LandUpkeepAssessmentStatus.PAID, ledgerId, null,
            )
            if (prior == null) insertLandUpkeepAssessment(paid) else updateLandUpkeepAssessment(paid)
            return state.copy(
                status = LandProtectionStatus.PROTECTED,
                nextAssessmentAt = now.plusSeconds(assessedInterval),
                requiredReserve = assessedReserve,
                delinquentAmount = MoneyAmount.ZERO,
                graceEndsAt = null,
                exposureId = null,
                exposureStartedAt = null,
                exposureDamageLimit = null,
                exposureDamageCount = 0,
                updatedAt = now,
            ).also(::updateLandProtectionState)
        }
        val grace = prior?.copy(
            assessedAt = now,
            status = LandUpkeepAssessmentStatus.GRACE_STARTED,
            ledgerTransactionId = null,
        ) ?: assessment(
            state, due, now, assessedArea, charge, assessedReserve,
            LandUpkeepAssessmentStatus.GRACE_STARTED, null, null,
        )
        if (prior == null) insertLandUpkeepAssessment(grace) else updateLandUpkeepAssessment(grace)
        return state.copy(
            status = LandProtectionStatus.GRACE,
            nextAssessmentAt = now.plusSeconds(assessedInterval),
            requiredReserve = assessedReserve,
            delinquentAmount = charge,
            graceEndsAt = now.plusSeconds(assessedGrace),
            exposureId = null,
            exposureStartedAt = null,
            exposureDamageLimit = assessedDamageLimit,
            exposureDamageCount = 0,
            updatedAt = now,
        ).also(::updateLandProtectionState)
    }

    private fun io.bennyc.civilizations.application.persistence.CivilizationsWriteContext
        .recoverOrAdvance(state: LandProtectionState, now: java.time.Instant): LandProtectionState {
        val account = checkNotNull(findCivilizationAccount(state.civilizationId))
        val required = state.delinquentAmount.plus(state.requiredReserve)
        if (account.balance.minorUnits >= required.minorUnits) {
            val ledgerId = postCharge(state, now, state.delinquentAmount)
            val missed = listLandUpkeepAssessments(state.civilizationId, 1).singleOrNull()
                ?.takeIf { it.status == LandUpkeepAssessmentStatus.GRACE_STARTED }
            if (missed == null) {
                insertLandUpkeepAssessment(
                    assessment(
                        state, now, now, claimedArea(state.civilizationId),
                        state.delinquentAmount, state.requiredReserve,
                        LandUpkeepAssessmentStatus.RECOVERED, ledgerId, null,
                    ),
                )
            } else {
                updateLandUpkeepAssessment(
                    missed.copy(
                        assessedAt = now,
                        status = LandUpkeepAssessmentStatus.RECOVERED,
                        ledgerTransactionId = ledgerId,
                    ),
                )
            }
            return state.copy(
                status = LandProtectionStatus.PROTECTED,
                nextAssessmentAt = now.plusSeconds(missed?.intervalSeconds ?: rules.intervalSeconds),
                delinquentAmount = MoneyAmount.ZERO,
                graceEndsAt = null,
                exposureId = null,
                exposureStartedAt = null,
                exposureDamageLimit = null,
                exposureDamageCount = 0,
                updatedAt = now,
            ).also(::updateLandProtectionState)
        }
        if (state.status == LandProtectionStatus.GRACE &&
            now >= checkNotNull(state.graceEndsAt) &&
            !hasOpenBattle(state.civilizationId)
        ) {
            return state.copy(
                status = LandProtectionStatus.EXPOSED,
                exposureId = idGenerator.newLandExposureId(),
                exposureStartedAt = now,
                exposureDamageLimit = state.exposureDamageLimit,
                exposureDamageCount = 0,
                updatedAt = now,
            ).also(::updateLandProtectionState)
        }
        return state
    }

    private fun io.bennyc.civilizations.application.persistence.CivilizationsReadContext
        .claimedArea(civilizationId: CivilizationId): Long =
        listClaims(civilizationId).fold(0L) { total, claim ->
            Math.addExact(total, claim.bounds.area)
        }

    private fun reserveFor(area: Long): MoneyAmount =
        rules.baseReserve.plus(rules.perBlockReserve.times(area))

    private fun chargeFor(area: Long): MoneyAmount =
        rules.baseCharge.plus(rules.perBlockCharge.times(area))

    private fun io.bennyc.civilizations.application.persistence.CivilizationsReadContext
        .hasOpenBattle(civilizationId: CivilizationId): Boolean =
        listOpenBattlesForCivilization(civilizationId).any {
            it.status == BattleStatus.ACTIVE || it.status == BattleStatus.RESOLVING
        }

    private fun io.bennyc.civilizations.application.persistence.CivilizationsWriteContext.postCharge(
        state: LandProtectionState,
        referenceAt: java.time.Instant,
        charge: MoneyAmount,
    ): LedgerTransactionId? {
        if (charge == MoneyAmount.ZERO) return null
        return when (val posted = ledger.post(
            this,
            LedgerTransactionRequest(
                seasonId = state.seasonId,
                idempotencyKey = "land-upkeep:${state.civilizationId}:${referenceAt.toEpochMilli()}",
                kind = LedgerTransactionKind.LAND_UPKEEP,
                postings = listOf(LedgerPosting(state.civilizationId, charge.negate())),
                referenceType = "LAND_PROTECTION",
                referenceId = state.civilizationId.toString(),
                actorPlayerId = null,
                description = "Recurring civilization land protection upkeep",
            ),
        )) {
            is ApplicationResult.Applied -> posted.value.id
            is ApplicationResult.Unchanged -> posted.value.id
            is ApplicationResult.Rejected -> error(posted.failure.description)
        }
    }

    private fun assessment(
        state: LandProtectionState,
        scheduledAt: java.time.Instant,
        assessedAt: java.time.Instant,
        area: Long,
        charge: MoneyAmount,
        reserve: MoneyAmount,
        status: LandUpkeepAssessmentStatus,
        ledgerId: LedgerTransactionId?,
        existingId: io.bennyc.civilizations.domain.protection.LandUpkeepAssessmentId?,
    ) = LandUpkeepAssessment(
        id = existingId ?: idGenerator.newLandUpkeepAssessmentId(),
        seasonId = state.seasonId,
        civilizationId = state.civilizationId,
        scheduledAt = scheduledAt,
        assessedAt = assessedAt,
        claimedArea = area,
        baseCharge = rules.baseCharge,
        perBlockCharge = rules.perBlockCharge,
        totalCharge = charge,
        requiredReserve = reserve,
        intervalSeconds = rules.intervalSeconds,
        graceSeconds = rules.graceSeconds,
        damageLimit = rules.damageLimitPerExposure,
        status = status,
        ledgerTransactionId = ledgerId,
    )
}

data class PrepareExposureMutation(
    val exposureId: io.bennyc.civilizations.domain.protection.LandExposureId,
    val ownerCivilizationId: CivilizationId,
    val claimId: io.bennyc.civilizations.domain.claim.ClaimId,
    val position: BlockPosition3D,
    val observedState: SimpleBlockSnapshot,
    val expectedState: SimpleBlockSnapshot,
    val actorPlayerId: PlayerId,
    val actorCivilizationId: CivilizationId,
    val cause: BlockMutationCause,
)

data class PreparedExposureMutation(
    val site: ExposureDamageSite,
    val event: ExposureDamageEvent,
    val firstDamageAtSite: Boolean,
)

data class LandProtectionStateNotFound(val civilizationId: CivilizationId) : ApplicationFailure {
    override val description = "Civilization $civilizationId has no land protection state"
}

data object LandIsNotExposed : ApplicationFailure {
    override val description = "This land is not currently exposed"
}

data object ExposureSuspendedForBattle : ApplicationFailure {
    override val description = "Land exposure is suspended while either civilization has a battle"
}

data object ExposureRequiresOtherCivilization : ApplicationFailure {
    override val description = "Only a member of another civilization may damage exposed land"
}

data object ExposureClaimMismatch : ApplicationFailure {
    override val description = "The exposed claim no longer matches this block"
}

data class ExposureDamageLimitReached(val limit: Int) : ApplicationFailure {
    override val description = "This exposure has reached its $limit-block damage limit"
}
