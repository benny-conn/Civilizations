package io.bennyc.civilizations.application.protection

import io.bennyc.civilizations.application.claim.ClaimSpatialIndex
import io.bennyc.civilizations.application.season.GameplayPhaseRules
import io.bennyc.civilizations.domain.civilization.Membership
import io.bennyc.civilizations.domain.civilization.MembershipRole
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ProtectionServiceTest {
    @Test
    fun `members own their claimed mutations while outsiders do not`() {
        val service = service()
        val memberActions = PlayerProtectionAction.entries - PlayerProtectionAction.PVP

        for (action in memberActions) {
            service.decidePlayerAction(request(memberA, action, insideA)).assertAllowed(
                ProtectionReason.OWNER_MEMBER,
            )
            service.decidePlayerAction(request(outsider, action, insideA)).assertDenied(
                ProtectionReason.OUTSIDER,
            )
        }
        service.decidePlayerAction(request(outsider, PlayerProtectionAction.BLOCK_BREAK, wilderness))
            .assertAllowed(ProtectionReason.UNCLAIMED)
    }

    @Test
    fun `admin bypass is explicit and finale freezes ordinary members`() {
        val finale = service(SeasonStatus.FINALE)

        finale.decidePlayerAction(request(memberA, PlayerProtectionAction.BLOCK_PLACE, insideA))
            .assertDenied(ProtectionReason.SEASON_FROZEN)
        finale.decidePlayerAction(
            request(memberA, PlayerProtectionAction.BLOCK_PLACE, insideA, adminBypass = true),
        ).assertAllowed(ProtectionReason.ADMIN_BYPASS)
    }

    @Test
    fun `global war phase alone never authorizes damage or pvp`() {
        val war = service(SeasonStatus.WAR)

        war.decidePlayerAction(request(memberB, PlayerProtectionAction.BLOCK_BREAK, insideA))
            .assertDenied(ProtectionReason.OUTSIDER)
        war.decidePlayerAction(
            request(memberA, PlayerProtectionAction.PVP, insideA, targetPlayerId = memberB),
        ).assertDenied(ProtectionReason.PVP_REQUIRES_CONFLICT)
    }

    @Test
    fun `configured member land gate can freeze owner actions during war`() {
        val war = service(
            SeasonStatus.WAR,
            GameplayPhaseRules(
                memberLandActionsAllowedIn = setOf(SeasonStatus.SETUP, SeasonStatus.PEACE),
            ),
        )

        war.decidePlayerAction(request(memberA, PlayerProtectionAction.BLOCK_BREAK, insideA))
            .assertDenied(ProtectionReason.SEASON_FROZEN)
    }

    @Test
    fun `war capability is actor action eligible-land phase and target scoped`() {
        val authorization = ConflictAuthorization.Active(
            kind = ConflictKind.WAR,
            actorId = memberB,
            eligibleClaimIds = setOf(claimA.id),
            allowedActions = setOf(
                PlayerProtectionAction.BLOCK_BREAK,
                PlayerProtectionAction.PVP,
            ),
            targetPlayerIds = setOf(memberA),
        )
        val war = service(SeasonStatus.WAR)

        war.decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.BLOCK_BREAK,
                insideA,
                conflict = authorization,
            ),
        ).assertAllowed(ProtectionReason.CONFLICT_OVERRIDE)
        war.decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.PVP,
                insideA,
                targetPlayerId = memberA,
                conflict = authorization,
            ),
        ).assertAllowed(ProtectionReason.CONFLICT_OVERRIDE)

        war.decidePlayerAction(
            request(
                outsider,
                PlayerProtectionAction.BLOCK_BREAK,
                insideA,
                conflict = authorization,
            ),
        ).assertDenied(ProtectionReason.OUTSIDER)
        war.decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.BLOCK_BREAK,
                insideB,
                conflict = authorization,
            ),
        ).assertAllowed(ProtectionReason.OWNER_MEMBER)
        war.decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.PVP,
                insideA,
                targetPlayerId = outsider,
                conflict = authorization,
            ),
        ).assertDenied(ProtectionReason.PVP_REQUIRES_CONFLICT)
        service(SeasonStatus.PEACE).decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.BLOCK_BREAK,
                insideA,
                conflict = authorization,
            ),
        ).assertDenied(ProtectionReason.OUTSIDER)
    }

    @Test
    fun `assassination capability permits only its named pvp target`() {
        val authorization = ConflictAuthorization.Active(
            kind = ConflictKind.ASSASSINATION,
            actorId = memberB,
            eligibleClaimIds = setOf(claimA.id),
            allowedActions = setOf(PlayerProtectionAction.PVP),
            targetPlayerIds = setOf(memberA),
        )
        val peace = service(SeasonStatus.PEACE)

        peace.decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.PVP,
                insideA,
                targetPlayerId = memberA,
                conflict = authorization,
            ),
        ).assertAllowed(ProtectionReason.CONFLICT_OVERRIDE)
        peace.decidePlayerAction(
            request(
                memberB,
                PlayerProtectionAction.BLOCK_BREAK,
                insideA,
                conflict = authorization,
            ),
        ).assertDenied(ProtectionReason.OUTSIDER)
    }

    @Test
    fun `conflict capabilities cannot expose containers or untargeted pvp`() {
        assertFailsWith<IllegalArgumentException> {
            ConflictAuthorization.Active(
                kind = ConflictKind.WAR,
                actorId = memberB,
                eligibleClaimIds = setOf(claimA.id),
                allowedActions = setOf(PlayerProtectionAction.CONTAINER_ACCESS),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ConflictAuthorization.Active(
                kind = ConflictKind.ASSASSINATION,
                actorId = memberB,
                eligibleClaimIds = setOf(claimA.id),
                allowedActions = setOf(PlayerProtectionAction.PVP),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ConflictAuthorization.Active(
                kind = ConflictKind.ASSASSINATION,
                actorId = memberB,
                eligibleClaimIds = setOf(claimA.id),
                allowedActions = setOf(
                    PlayerProtectionAction.PVP,
                    PlayerProtectionAction.BLOCK_BREAK,
                ),
                targetPlayerIds = setOf(memberA),
            )
        }
    }

    @Test
    fun `environmental damage is removed only from claimed targets`() {
        val service = service()
        for (action in listOf(
            EnvironmentProtectionAction.EXPLOSION,
            EnvironmentProtectionAction.FIRE,
            EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
        )) {
            service.decideEnvironmentTarget(action, insideA)
                .assertDenied(ProtectionReason.CLAIMED_ENVIRONMENT)
            service.decideEnvironmentTarget(action, wilderness)
                .assertAllowed(ProtectionReason.UNCLAIMED)
        }
    }

    @Test
    fun `fluids pistons and inventory automation cannot cross ownership boundaries`() {
        val service = service()
        for (action in listOf(
            EnvironmentProtectionAction.FLUID_FLOW,
            EnvironmentProtectionAction.PISTON_MOVE,
            EnvironmentProtectionAction.CONTAINER_TRANSFER,
        )) {
            service.decideEnvironmentTransition(action, insideA, anotherInsideA)
                .assertAllowed(ProtectionReason.SAME_TERRITORY)
            service.decideEnvironmentTransition(action, insideA, insideB)
                .assertDenied(ProtectionReason.TERRITORY_BOUNDARY)
            service.decideEnvironmentTransition(action, insideA, wilderness)
                .assertDenied(ProtectionReason.TERRITORY_BOUNDARY)
            service.decideEnvironmentTransition(action, wilderness, otherWilderness)
                .assertAllowed(ProtectionReason.UNCLAIMED)
        }
    }

    @Test
    fun `finale freezes environmental transitions inside claims`() {
        service(SeasonStatus.FINALE).decideEnvironmentTransition(
            EnvironmentProtectionAction.FLUID_FLOW,
            insideA,
            anotherInsideA,
        ).assertDenied(ProtectionReason.SEASON_FROZEN)
    }

    @Test
    fun `membership index rejects corrupt duplicate players`() {
        assertFailsWith<IllegalArgumentException> {
            ProtectionService(
                SeasonStatus.PEACE,
                ClaimSpatialIndex(seasonId, listOf(claimA, claimB)),
                listOf(membership(memberA, civA), membership(memberA, civB)),
            )
        }
    }

    private fun service(
        status: SeasonStatus = SeasonStatus.PEACE,
        phaseRules: GameplayPhaseRules = GameplayPhaseRules(),
    ) = ProtectionService(
        seasonStatus = status,
        claimIndex = ClaimSpatialIndex(seasonId, listOf(claimA, claimB)),
        memberships = listOf(membership(memberA, civA), membership(memberB, civB)),
        phaseRules = phaseRules,
    )

    private fun request(
        actor: PlayerId,
        action: PlayerProtectionAction,
        target: BlockPosition2D,
        targetPlayerId: PlayerId? = null,
        adminBypass: Boolean = false,
        conflict: ConflictAuthorization = ConflictAuthorization.None,
    ) = PlayerProtectionRequest(
        actorId = actor,
        action = action,
        target = target,
        targetPlayerId = targetPlayerId,
        adminBypass = adminBypass,
        conflictAuthorization = conflict,
    )

    private fun membership(playerId: PlayerId, civilizationId: CivilizationId) = Membership(
        seasonId = seasonId,
        civilizationId = civilizationId,
        playerId = playerId,
        role = MembershipRole.MEMBER,
        joinedAt = Instant.EPOCH,
    )

    private fun ProtectionDecision.assertAllowed(reason: ProtectionReason) {
        assertEquals(reason, assertIs<ProtectionDecision.Allowed>(this).reason)
    }

    private fun ProtectionDecision.assertDenied(reason: ProtectionReason) {
        assertEquals(reason, assertIs<ProtectionDecision.Denied>(this).reason)
    }

    private companion object {
        val seasonId = SeasonId(UUID(0, 1))
        val civA = CivilizationId(UUID(0, 2))
        val civB = CivilizationId(UUID(0, 3))
        val memberA = PlayerId(UUID(0, 4))
        val memberB = PlayerId(UUID(0, 5))
        val outsider = PlayerId(UUID(0, 6))
        val world = WorldId("minecraft:overworld")
        val claimA = Claim(
            ClaimId(UUID(0, 7)),
            seasonId,
            civA,
            ClaimBounds.between(world, 0, 0, 9, 9),
        )
        val claimB = Claim(
            ClaimId(UUID(0, 8)),
            seasonId,
            civB,
            ClaimBounds.between(world, 10, 0, 19, 9),
        )
        val insideA = BlockPosition2D(world, 0, 0)
        val anotherInsideA = BlockPosition2D(world, 9, 9)
        val insideB = BlockPosition2D(world, 10, 0)
        val wilderness = BlockPosition2D(world, -1, 0)
        val otherWilderness = BlockPosition2D(world, -2, 0)
    }
}
