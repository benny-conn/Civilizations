package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.application.ApplicationResult
import io.bennyc.civilizations.application.war.BattleRoster
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleStatus
import io.bennyc.civilizations.infrastructure.runtime.ActiveSeasonRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.HostileClaimEntryRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.RuntimeMutationOutcome
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import java.util.logging.Logger

/** Paper-thread hostile-boundary detection backed only by the published runtime snapshot. */
internal class PaperBattleEntryListener(
    private val runtime: CivilizationsRuntime,
    private val server: Server,
    private val logger: Logger,
    private val gate: BattleEntryAttemptGate = BattleEntryAttemptGate(),
    private val nanoTime: () -> Long = System::nanoTime,
) : Listener {
    private var horizontalBlockTransitions = 0L
    private var territoryTransitions = 0L
    private var battlesStarted = 0L

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        if (event is PlayerTeleportEvent) {
            return
        }
        inspectTransition(event.player, event.from, event.to)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        inspectTransition(event.player, event.from, event.to)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        gate.forget(PlayerId(event.player.uniqueId))
    }

    fun metricsSummary(): String {
        val metrics = gate.metrics()
        return "horizontalTransitions=$horizontalBlockTransitions, " +
            "territoryTransitions=$territoryTransitions, pending=${metrics.pending}, " +
            "peak=${metrics.peakPending}, attempts=${metrics.accepted}, " +
            "started=$battlesStarted, pendingSuppressed=${metrics.pendingRejected}, " +
            "cooldownSuppressed=${metrics.cooldownRejected}, " +
            "saturated=${metrics.saturatedRejected}"
    }

    private fun inspectTransition(
        player: Player,
        from: Location,
        to: Location,
    ) {
        if (sameHorizontalBlock(from, to)) {
            return
        }
        horizontalBlockTransitions++
        val active = (runtime.state as? CivilizationsRuntimeState.Ready)?.activeSeason ?: return
        val fromPosition = from.position() ?: return
        val toPosition = to.position() ?: return
        val fromClaimId = active.claimIndex.claimAt(fromPosition)?.id
        val toClaimId = active.claimIndex.claimAt(toPosition)?.id
        if (fromClaimId == toClaimId) {
            return
        }
        territoryTransitions++

        val actorId = PlayerId(player.uniqueId)
        val entry = active.hostileClaimEntry(actorId, toPosition) ?: return
        val now = nanoTime()
        when {
            entry.existingOpenBattle != null -> {
                if (gate.shouldSendFeedback(actorId, now)) {
                    notifyExistingBattle(player, active, entry)
                }
            }
            !entry.battlePhaseOpen -> {
                if (gate.shouldSendFeedback(actorId, now)) {
                    player.sendActionBar(
                        Component.text(
                            "Hostile territory: war is declared, but battles require WAR phase.",
                            NamedTextColor.YELLOW,
                        ),
                    )
                }
            }
            !player.hasPermission(PARTICIPATE_PERMISSION) -> {
                if (gate.shouldSendFeedback(actorId, now)) {
                    player.sendActionBar(
                        Component.text(
                            "You do not have permission to participate in battles.",
                            NamedTextColor.RED,
                        ),
                    )
                }
            }
            else -> beginBattle(player, active, entry, now)
        }
    }

    private fun beginBattle(
        player: Player,
        active: ActiveSeasonRuntimeState,
        entry: HostileClaimEntryRuntimeState,
        nowNanos: Long,
    ) {
        val actorId = PlayerId(player.uniqueId)
        when (gate.begin(actorId, nowNanos)) {
            BattleEntryGateDecision.Pending,
            BattleEntryGateDecision.CoolingDown,
            -> return
            BattleEntryGateDecision.Saturated -> {
                player.sendActionBar(
                    Component.text(
                        "Battle activation is busy; cross the boundary again shortly.",
                        NamedTextColor.RED,
                    ),
                )
                return
            }
            BattleEntryGateDecision.Accepted -> Unit
        }

        val defenderName = active.civilizationName(entry.defendingCivilizationId)
        player.sendActionBar(
            Component.text(
                "Entering $defenderName territory — starting battle…",
                NamedTextColor.RED,
            ),
        )
        runtime.submitMutation(
            operation = {
                wars.startBattleFromEntry(entry.war.id, actorId, entry.enteredClaim.id)
            },
            completion = { outcome ->
                gate.complete(actorId)
                when (outcome) {
                    is RuntimeMutationOutcome.Completed -> when (val result = outcome.result) {
                        is ApplicationResult.Applied -> announceBattle(result.value, outcome.state)
                        is ApplicationResult.Unchanged -> {
                            server.getPlayer(actorId.value)?.sendActionBar(
                                Component.text("Battle is already active.", NamedTextColor.YELLOW),
                            )
                        }
                        is ApplicationResult.Rejected -> {
                            logger.fine(result.failure.description)
                            server.getPlayer(actorId.value)?.sendActionBar(
                                Component.text(result.failure.description, NamedTextColor.RED),
                            )
                        }
                    }
                    is RuntimeMutationOutcome.NotReady ->
                        server.getPlayer(actorId.value)?.sendActionBar(
                            Component.text(
                                "Battle activation is unavailable.",
                                NamedTextColor.RED,
                            ),
                        )
                    is RuntimeMutationOutcome.Failed -> Unit
                }
            },
        )
    }

    private fun announceBattle(
        roster: BattleRoster,
        ready: CivilizationsRuntimeState.Ready,
    ) {
        battlesStarted++
        val active = ready.activeSeason ?: return
        val attacker = active.civilizationName(roster.battle.attackingCivilizationId)
        val defender = active.civilizationName(roster.battle.defendingCivilizationId)
        val message = prefixed(
            "$attacker attacked $defender. Battle ${roster.battle.id} ends at " +
                "${roster.battle.endsAt}.",
            NamedTextColor.RED,
        )
        for (participant in roster.participants) {
            server.getPlayer(participant.playerId.value)?.sendMessage(message)
        }
        logger.info(
            "Battle ${roster.battle.id} started for war ${roster.battle.warId}: " +
                "$attacker attacked $defender; endsAt=${roster.battle.endsAt}",
        )
    }

    private fun notifyExistingBattle(
        player: Player,
        active: ActiveSeasonRuntimeState,
        entry: HostileClaimEntryRuntimeState,
    ) {
        val battle = checkNotNull(entry.existingOpenBattle)
        val message = if (battle.status == BattleStatus.ACTIVE && battle.warId == entry.war.id) {
            val defender = active.civilizationName(entry.defendingCivilizationId)
            "Battle territory: $defender • ends ${battle.endsAt}"
        } else {
            "Your civilization already has an open battle (${battle.id})."
        }
        player.sendActionBar(Component.text(message, NamedTextColor.YELLOW))
    }

    private fun ActiveSeasonRuntimeState.civilizationName(id: CivilizationId): String =
        civilizations.singleOrNull { it.id == id }?.name?.value ?: id.toString()

    private fun sameHorizontalBlock(first: Location, second: Location): Boolean =
        first.world?.key == second.world?.key &&
            first.blockX == second.blockX &&
            first.blockZ == second.blockZ

    private fun Location.position(): BlockPosition2D? {
        val currentWorld = world ?: return null
        return BlockPosition2D(
            worldId = WorldId(currentWorld.key.asString()),
            x = blockX,
            z = blockZ,
        )
    }

    private fun prefixed(message: String, color: NamedTextColor): Component =
        Component.text("[Civilizations] ", NamedTextColor.DARK_PURPLE)
            .append(Component.text(message, color))

    companion object {
        const val PARTICIPATE_PERMISSION = "civilizations.war.participate"
    }
}
