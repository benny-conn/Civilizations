package io.bennyc.civilizations.application.season

import io.bennyc.civilizations.domain.season.SeasonStatus

/**
 * Configurable phase gates for live gameplay operations.
 *
 * Configuration may make a season more restrictive, but it cannot opt into
 * lifecycle combinations that would invalidate battle snapshots or permit
 * mutation of archived state.
 */
data class GameplayPhaseRules(
    val rosterChangesAllowedIn: Set<SeasonStatus> = ROSTER_AND_CLAIM_PHASES,
    val claimCreationAllowedIn: Set<SeasonStatus> = ROSTER_AND_CLAIM_PHASES,
    val memberLandActionsAllowedIn: Set<SeasonStatus> = MEMBER_LAND_ACTION_PHASES,
) {
    init {
        require(rosterChangesAllowedIn.all(ROSTER_AND_CLAIM_PHASES::contains)) {
            "Roster changes may only be enabled in SETUP or PEACE"
        }
        require(claimCreationAllowedIn.all(ROSTER_AND_CLAIM_PHASES::contains)) {
            "Claim creation may only be enabled in SETUP or PEACE"
        }
        require(memberLandActionsAllowedIn.all(MEMBER_LAND_ACTION_PHASES::contains)) {
            "Member land actions may only be enabled in SETUP, PEACE, or WAR"
        }
    }

    private companion object {
        val ROSTER_AND_CLAIM_PHASES = setOf(SeasonStatus.SETUP, SeasonStatus.PEACE)
        val MEMBER_LAND_ACTION_PHASES = ROSTER_AND_CLAIM_PHASES + SeasonStatus.WAR
    }
}
