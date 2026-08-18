package io.bennyc.civilizations.application.protection

import io.bennyc.civilizations.application.claim.ClaimSpatialIndex
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.season.SeasonStatus

/** Player-originated actions whose target can be authorized by land policy. */
enum class PlayerProtectionAction {
    BLOCK_BREAK,
    BLOCK_PLACE,
    BLOCK_INTERACT,
    CONTAINER_ACCESS,
    BUCKET_FILL,
    BUCKET_EMPTY,
    FIRE_IGNITE,
    ENTITY_INTERACT,
    ENTITY_DAMAGE,
    PVP,
}

/** Non-player world changes which need either target or boundary protection. */
enum class EnvironmentProtectionAction {
    EXPLOSION,
    FIRE,
    ENTITY_BLOCK_CHANGE,
    FLUID_FLOW,
    PISTON_MOVE,
    CONTAINER_TRANSFER,
}

enum class ConflictKind {
    WAR,
    ASSASSINATION,
}

/**
 * A narrowly scoped capability produced by a future persisted conflict service.
 * The season WAR phase is deliberately not an authorization by itself.
 */
sealed interface ConflictAuthorization {
    data object None : ConflictAuthorization

    data class Active(
        val kind: ConflictKind,
        val actorId: PlayerId,
        val battlefieldClaimIds: Set<ClaimId>,
        val allowedActions: Set<PlayerProtectionAction>,
        val targetPlayerIds: Set<PlayerId> = emptySet(),
    ) : ConflictAuthorization {
        init {
            require(battlefieldClaimIds.isNotEmpty()) {
                "Conflict authorization must identify at least one battlefield claim"
            }
            require(allowedActions.isNotEmpty()) {
                "Conflict authorization must identify at least one allowed action"
            }
            require(PlayerProtectionAction.CONTAINER_ACCESS !in allowedActions) {
                "MVP conflict authorization may not expose inventory-bearing blocks"
            }
            require(
                PlayerProtectionAction.PVP !in allowedActions || targetPlayerIds.isNotEmpty(),
            ) {
                "PVP authorization must identify the valid target participants"
            }
            require(
                kind != ConflictKind.ASSASSINATION ||
                    allowedActions == setOf(PlayerProtectionAction.PVP),
            ) {
                "Assassination authorization is limited to targeted PVP"
            }
        }
    }
}

data class PlayerProtectionRequest(
    val actorId: PlayerId,
    val action: PlayerProtectionAction,
    val target: BlockPosition2D,
    val targetPlayerId: PlayerId? = null,
    val adminBypass: Boolean = false,
    val conflictAuthorization: ConflictAuthorization = ConflictAuthorization.None,
)

sealed interface ProtectionDecision {
    val claim: Claim?
    val reason: ProtectionReason

    data class Allowed(
        override val reason: ProtectionReason,
        override val claim: Claim? = null,
    ) : ProtectionDecision

    data class Denied(
        override val reason: ProtectionReason,
        override val claim: Claim? = null,
    ) : ProtectionDecision
}

enum class ProtectionReason {
    UNCLAIMED,
    ADMIN_BYPASS,
    OWNER_MEMBER,
    CONFLICT_OVERRIDE,
    SAME_TERRITORY,
    OUTSIDER,
    PVP_REQUIRES_CONFLICT,
    SEASON_FROZEN,
    CLAIMED_ENVIRONMENT,
    TERRITORY_BOUNDARY,
}

/**
 * Pure, allocation-light land protection over one published runtime snapshot.
 * It performs no persistence or Paper access and is safe for event hot paths.
 */
class ProtectionService(
    private val seasonStatus: SeasonStatus,
    private val claimIndex: ClaimSpatialIndex,
    memberships: Iterable<Membership>,
) {
    private val civilizationByPlayer = buildMap {
        for (membership in memberships) {
            require(put(membership.playerId, membership.civilizationId) == null) {
                "Player ${membership.playerId} has multiple memberships in the active season"
            }
        }
    }

    fun decidePlayerAction(request: PlayerProtectionRequest): ProtectionDecision {
        val claim = claimIndex.claimAt(request.target)
            ?: return ProtectionDecision.Allowed(ProtectionReason.UNCLAIMED)

        if (request.adminBypass) {
            return ProtectionDecision.Allowed(ProtectionReason.ADMIN_BYPASS, claim)
        }
        if (seasonStatus.isFrozen) {
            return ProtectionDecision.Denied(ProtectionReason.SEASON_FROZEN, claim)
        }
        if (request.conflictAuthorization.allows(request, claim, seasonStatus)) {
            return ProtectionDecision.Allowed(ProtectionReason.CONFLICT_OVERRIDE, claim)
        }
        if (request.action == PlayerProtectionAction.PVP) {
            return ProtectionDecision.Denied(ProtectionReason.PVP_REQUIRES_CONFLICT, claim)
        }
        if (civilizationByPlayer[request.actorId] == claim.civilizationId) {
            return ProtectionDecision.Allowed(ProtectionReason.OWNER_MEMBER, claim)
        }
        return ProtectionDecision.Denied(ProtectionReason.OUTSIDER, claim)
    }

    /** Explosions, fire, and autonomous entity changes never mutate claimed land. */
    fun decideEnvironmentTarget(
        action: EnvironmentProtectionAction,
        target: BlockPosition2D,
    ): ProtectionDecision {
        require(action.isTargetMutation) { "$action requires a source and destination" }
        val claim = claimIndex.claimAt(target)
            ?: return ProtectionDecision.Allowed(ProtectionReason.UNCLAIMED)
        return ProtectionDecision.Denied(
            if (seasonStatus.isFrozen) ProtectionReason.SEASON_FROZEN
            else ProtectionReason.CLAIMED_ENVIRONMENT,
            claim,
        )
    }

    /** Fluids and pistons may operate inside one ownership area, never across its boundary. */
    fun decideEnvironmentTransition(
        action: EnvironmentProtectionAction,
        source: BlockPosition2D,
        target: BlockPosition2D,
    ): ProtectionDecision {
        require(action.isTransition) { "$action is not a source/destination transition" }
        val sourceClaim = claimIndex.claimAt(source)
        val targetClaim = claimIndex.claimAt(target)

        if (seasonStatus.isFrozen && (sourceClaim != null || targetClaim != null)) {
            return ProtectionDecision.Denied(
                ProtectionReason.SEASON_FROZEN,
                targetClaim ?: sourceClaim,
            )
        }

        val sourceOwner = sourceClaim?.civilizationId
        val targetOwner = targetClaim?.civilizationId
        return if (sourceOwner == targetOwner) {
            ProtectionDecision.Allowed(
                if (sourceOwner == null) ProtectionReason.UNCLAIMED
                else ProtectionReason.SAME_TERRITORY,
                targetClaim ?: sourceClaim,
            )
        } else {
            ProtectionDecision.Denied(
                ProtectionReason.TERRITORY_BOUNDARY,
                targetClaim ?: sourceClaim,
            )
        }
    }

    private val SeasonStatus.isFrozen: Boolean
        get() = this == SeasonStatus.FINALE || this == SeasonStatus.ARCHIVED

    private val EnvironmentProtectionAction.isTargetMutation: Boolean
        get() = when (this) {
            EnvironmentProtectionAction.EXPLOSION,
            EnvironmentProtectionAction.FIRE,
            EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
            -> true
            EnvironmentProtectionAction.FLUID_FLOW,
            EnvironmentProtectionAction.PISTON_MOVE,
            EnvironmentProtectionAction.CONTAINER_TRANSFER,
            -> false
        }

    private val EnvironmentProtectionAction.isTransition: Boolean
        get() = !isTargetMutation

    private fun ConflictAuthorization.allows(
        request: PlayerProtectionRequest,
        claim: Claim,
        seasonStatus: SeasonStatus,
    ): Boolean {
        val active = this as? ConflictAuthorization.Active ?: return false
        if (active.actorId != request.actorId ||
            claim.id !in active.battlefieldClaimIds ||
            request.action !in active.allowedActions
        ) {
            return false
        }
        val phaseAllowsConflict = when (active.kind) {
            ConflictKind.WAR -> seasonStatus == SeasonStatus.WAR
            ConflictKind.ASSASSINATION ->
                seasonStatus == SeasonStatus.PEACE || seasonStatus == SeasonStatus.WAR
        }
        if (!phaseAllowsConflict) {
            return false
        }
        return request.action != PlayerProtectionAction.PVP ||
            request.targetPlayerId in active.targetPlayerIds
    }
}
