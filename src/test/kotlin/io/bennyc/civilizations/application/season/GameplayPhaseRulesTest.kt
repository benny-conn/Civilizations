package io.bennyc.civilizations.application.season

import io.bennyc.civilizations.domain.season.SeasonStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameplayPhaseRulesTest {
    @Test
    fun `default roster rules include war without widening claim creation`() {
        val rules = GameplayPhaseRules()

        assertEquals(
            setOf(SeasonStatus.SETUP, SeasonStatus.PEACE, SeasonStatus.WAR),
            rules.rosterChangesAllowedIn,
        )
        assertEquals(
            setOf(SeasonStatus.SETUP, SeasonStatus.PEACE),
            rules.claimCreationAllowedIn,
        )
    }

    @Test
    fun `configuration cannot enable unsafe lifecycle combinations`() {
        assertFailsWith<IllegalArgumentException> {
            GameplayPhaseRules(rosterChangesAllowedIn = setOf(SeasonStatus.FINALE))
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayPhaseRules(claimCreationAllowedIn = setOf(SeasonStatus.FINALE))
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayPhaseRules(memberLandActionsAllowedIn = setOf(SeasonStatus.ARCHIVED))
        }
    }
}
