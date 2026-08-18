package io.bennyc.civilizations.application.support

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.identity.CivilizationsIdGenerator
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.util.UUID
import kotlin.test.fail

internal class SequentialIdGenerator : CivilizationsIdGenerator {
    private var season = 0L
    private var civilization = 100L
    private var claim = 200L

    override fun newSeasonId(): SeasonId = SeasonId(UUID(0, ++season))

    override fun newCivilizationId(): CivilizationId = CivilizationId(UUID(0, ++civilization))

    override fun newClaimId(): ClaimId = ClaimId(UUID(0, ++claim))
}

internal fun playerId(value: Long) = io.bennyc.civilizations.domain.identity.PlayerId(UUID(1, value))

internal fun <T> ApplicationResult<T>.appliedValue(): T = when (this) {
    is ApplicationResult.Applied -> value
    is ApplicationResult.Rejected -> fail("Expected applied result, got ${failure.description}")
    is ApplicationResult.Unchanged -> fail("Expected applied result, got unchanged $value")
}

internal fun <T> ApplicationResult<T>.unchangedValue(): T = when (this) {
    is ApplicationResult.Applied -> fail("Expected unchanged result, got applied $value")
    is ApplicationResult.Rejected -> fail("Expected unchanged result, got ${failure.description}")
    is ApplicationResult.Unchanged -> value
}

internal fun ApplicationResult<*>.rejection() = when (this) {
    is ApplicationResult.Applied -> fail("Expected rejection, got applied $value")
    is ApplicationResult.Rejected -> failure
    is ApplicationResult.Unchanged -> fail("Expected rejection, got unchanged $value")
}
