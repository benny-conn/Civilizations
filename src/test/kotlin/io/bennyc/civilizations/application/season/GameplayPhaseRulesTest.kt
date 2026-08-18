package io.bennyc.civilizations.application.season

import io.bennyc.civilizations.domain.season.SeasonStatus
import kotlin.test.Test
import kotlin.test.assertFailsWith

class GameplayPhaseRulesTest {
    @Test
    fun `configuration cannot enable unsafe lifecycle combinations`() {
        assertFailsWith<IllegalArgumentException> {
            GameplayPhaseRules(rosterChangesAllowedIn = setOf(SeasonStatus.WAR))
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayPhaseRules(claimCreationAllowedIn = setOf(SeasonStatus.FINALE))
        }
        assertFailsWith<IllegalArgumentException> {
            GameplayPhaseRules(memberLandActionsAllowedIn = setOf(SeasonStatus.ARCHIVED))
        }
    }
}
