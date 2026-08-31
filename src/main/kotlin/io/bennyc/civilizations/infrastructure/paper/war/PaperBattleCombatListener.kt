package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.application.war.BattleCombatUpdate
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import io.bennyc.civilizations.infrastructure.runtime.ActiveBattleCombatantRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.BattleLifeLossCapture
import io.bennyc.civilizations.infrastructure.runtime.BattleLifeLossCompletion
import io.bennyc.civilizations.infrastructure.runtime.BattleLifeLossQueue
import io.bennyc.civilizations.infrastructure.runtime.BattleLifeLossSubmission
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Server
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Translates authoritative Paper player/stand-in deaths into A4's durable life state.
 * It deliberately owns no disconnect timer and does not alter vanilla death or respawn.
 */
class PaperBattleCombatListener(
    private val plugin: Plugin,
    private val runtime: CivilizationsRuntime,
    private val server: Server,
    private val logger: Logger,
    private val resolutionCoordinator: PaperBattleResolutionCoordinator,
) : Listener, AutoCloseable {
    private val respawnNotices = mutableMapOf<PlayerId, BattleRespawnNotice>()
    private val authorizedLethalStandIns = mutableMapOf<UUID, PlayerId>()
    private val losses = BattleLifeLossQueue(
        record = { request, completion ->
            runtime.submitMutation(
                operation = { combat.recordLifeLosses(request) },
                completion = completion,
            )
        },
        scheduleNextTick = { action ->
            server.scheduler.runTask(plugin, Runnable(action))
        },
        onCompletion = ::completeLoss,
    )

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val playerId = event.player.playerId()
        capture(playerId) { active ->
            PaperBattleDeathIdentity.playerDeath(
                battleId = active.battle.id,
                playerId = playerId,
                observedLivesRemaining = active.combatant.livesRemaining,
                serverTick = server.currentTick,
            )
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onEntityDeath(event: EntityDeathEvent) {
        if (event.entity is Player) return
        val ownerId = BattleLockStandIn.ownerId(event.entity) ?: return
        captureBattleLockDeath(ownerId, event.entity)
    }

    /**
     * BattleLock 1.8 removes a lethally hit proxy from its MONITOR damage callback, so a
     * later EntityDeathEvent is not guaranteed. Protection marks the exact authorized hit;
     * the MONITOR handler records it only if no later listener cancelled the damage.
     */
    fun markAuthorizedBattleLockLethalDamage(ownerId: PlayerId, entity: Entity) {
        if (BattleLockStandIn.ownerId(entity) != ownerId) return
        authorizedLethalStandIns[entity.uniqueId] = ownerId
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAuthorizedBattleLockLethalDamage(event: EntityDamageByEntityEvent) {
        val ownerId = authorizedLethalStandIns.remove(event.entity.uniqueId) ?: return
        if (!event.isCancelled) {
            captureBattleLockDeath(ownerId, event.entity)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) {
        val playerId = event.player.playerId()
        respawnNotices.remove(playerId)?.let { notice ->
            sendNotice(event.player, notice)
            return
        }
        val combatant = activeCombatant(playerId) ?: return
        if (losses.isFinalLifePending(playerId, combatant.combatant.livesRemaining)) {
            event.player.sendMessage(
                Component.text(
                    "Your battle elimination is being recorded; battle actions remain locked.",
                    NamedTextColor.RED,
                ),
            )
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        respawnNotices.remove(event.player.playerId())?.let { notice ->
            sendNotice(event.player, notice)
        }
    }

    /** Closes the event-to-snapshot gap for final-life deaths. */
    fun isCapabilitySuppressed(playerId: PlayerId): Boolean {
        val combatant = activeCombatant(playerId) ?: return false
        return losses.isFinalLifePending(playerId, combatant.combatant.livesRemaining)
    }

    fun metricsSummary(): String {
        val metrics = losses.metrics()
        return "pending=${metrics.pending}, peak=${metrics.peakPending}, " +
            "accepted=${metrics.accepted}, applied=${metrics.applied}, " +
            "unchanged=${metrics.unchanged}, duplicates=${metrics.duplicates}, " +
            "redundant=${metrics.redundant}, rejected=${metrics.rejected}, " +
            "unavailable=${metrics.unavailable}, " +
            "failed=${metrics.failed}"
    }

    override fun close() {
        losses.close()
        respawnNotices.clear()
        authorizedLethalStandIns.clear()
    }

    private fun capture(
        playerId: PlayerId,
        eventId: (ActiveBattleCombatantRuntimeState) -> BattleLifeEventId,
    ) {
        val active = activeCombatant(playerId) ?: return
        when (
            losses.submit(
                BattleLifeLossCapture(
                    battleId = active.battle.id,
                    eventId = eventId(active),
                    playerId = playerId,
                    observedLivesRemaining = active.combatant.livesRemaining,
                ),
            )
        ) {
            BattleLifeLossSubmission.Accepted,
            BattleLifeLossSubmission.Duplicate,
            BattleLifeLossSubmission.Redundant,
            -> Unit
            BattleLifeLossSubmission.Closed -> Unit
        }
    }

    private fun captureBattleLockDeath(ownerId: PlayerId, entity: Entity) {
        capture(ownerId) { BattleLockStandIn.lifeEventId(entity) }
    }

    private fun completeLoss(completion: BattleLifeLossCompletion) {
        when (completion) {
            is BattleLifeLossCompletion.Applied -> {
                publishNotices(completion.update)
                resolutionCoordinator.recover(completion.state)
            }
            is BattleLifeLossCompletion.Unchanged ->
                resolutionCoordinator.recover(completion.state)
            is BattleLifeLossCompletion.Rejected -> logger.fine(
                "Battle life loss was not applied: ${completion.failure.description}",
            )
            BattleLifeLossCompletion.Unavailable -> logger.warning(
                "Civilizations became unavailable before a battle death was recorded",
            )
            is BattleLifeLossCompletion.Failed -> logger.log(
                Level.SEVERE,
                "Could not record a battle death",
                completion.failure,
            )
        }
    }

    private fun publishNotices(update: BattleCombatUpdate) {
        for (event in update.lifeEvents) {
            val combatant = update.combatants.single { it.playerId == event.playerId }
            val notice = if (combatant.isEliminated) {
                BattleRespawnNotice.Eliminated(update.battle.id.toString())
            } else {
                BattleRespawnNotice.LivesRemaining(
                    update.battle.id.toString(),
                    combatant.livesRemaining,
                )
            }
            val player = server.getPlayer(event.playerId.value)
            if (player == null || player.isDead) {
                respawnNotices[event.playerId] = notice
            } else {
                sendNotice(player, notice)
            }
        }
    }

    private fun sendNotice(player: Player, notice: BattleRespawnNotice) {
        val message = when (notice) {
            is BattleRespawnNotice.Eliminated ->
                "You have been eliminated from battle ${notice.battleId}. " +
                    "You can no longer fight or damage either side's battle land."
            is BattleRespawnNotice.LivesRemaining ->
                "You lost a battle life in ${notice.battleId}; " +
                    "${notice.remaining} remaining."
        }
        player.sendMessage(Component.text(message, NamedTextColor.RED))
    }

    private fun activeCombatant(playerId: PlayerId) =
        ((runtime.state as? CivilizationsRuntimeState.Ready)
            ?.activeSeason
            ?.activeBattleCombatant(playerId))

    private fun Player.playerId(): PlayerId = PlayerId(uniqueId)
}

private sealed interface BattleRespawnNotice {
    val battleId: String

    data class Eliminated(override val battleId: String) : BattleRespawnNotice

    data class LivesRemaining(
        override val battleId: String,
        val remaining: Int,
    ) : BattleRespawnNotice
}
