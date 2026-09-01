package io.bennyc.civilizations.infrastructure.paper.protection

import io.bennyc.civilizations.application.protection.PrepareExposureMutation
import io.bennyc.civilizations.application.protection.PreparedExposureMutation
import io.bennyc.civilizations.application.protection.PlayerProtectionAction
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.infrastructure.runtime.ActiveExposureBlockMutationAuthorization
import io.bennyc.civilizations.infrastructure.runtime.ExposureBlockMutationQueue
import io.bennyc.civilizations.infrastructure.runtime.ExposureJournalCompletion
import io.bennyc.civilizations.infrastructure.runtime.ExposureQueueSubmission
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.util.logging.Level
import java.util.logging.Logger

/** Journal-first simple block mutation for upkeep-exposed land. */
internal class PaperExposureBlockMutationAdapter(
    private val server: Server,
    private val logger: Logger,
    private val queue: ExposureBlockMutationQueue,
    private val authorize: (
        PlayerId,
        PlayerProtectionAction,
        BlockPosition2D,
    ) -> ActiveExposureBlockMutationAuthorization?,
) {
    private var replayToken: ReplayToken? = null
    private var applied = 0L
    private var stale = 0L

    fun intercept(event: BlockBreakEvent): Boolean {
        val position = event.block.position3D()
        if (isReplaying(event.player, PlayerProtectionAction.BLOCK_BREAK, position)) return true
        if (event.player.hasPermission(ADMIN_BYPASS_PERMISSION) ||
            !event.player.hasPermission(DAMAGE_PERMISSION)
        ) return false
        val authorization = authorize(
            PlayerId(event.player.uniqueId),
            PlayerProtectionAction.BLOCK_BREAK,
            position.horizontal(),
        ) ?: return false
        event.isCancelled = true
        if (!SimpleBattleBlockPolicy.allowsBreak(event.block)) {
            notify(event.player, "Only simple building blocks can be damaged in exposed land.")
            return true
        }
        val capture = BreakCapture(
            common = Capture(
                authorization,
                position,
                event.block.snapshot(),
                SimpleBlockSnapshot("minecraft:air"),
            ),
            heldSlot = event.player.inventory.heldItemSlot,
            tool = event.player.inventory.itemInMainHand.clone(),
            gameMode = event.player.gameMode,
        )
        submit(capture.common, BlockMutationCause.PLAYER_BREAK) { prepared ->
            applyBreak(capture, prepared)
        }
        return true
    }

    fun intercept(event: BlockPlaceEvent): Boolean {
        val position = event.blockPlaced.position3D()
        if (isReplaying(event.player, PlayerProtectionAction.BLOCK_PLACE, position)) return true
        if (event.player.hasPermission(ADMIN_BYPASS_PERMISSION) ||
            !event.player.hasPermission(DAMAGE_PERMISSION)
        ) return false
        if (event is BlockMultiPlaceEvent) {
            val exposed = event.replacedBlockStates.any {
                authorize(
                    PlayerId(event.player.uniqueId),
                    PlayerProtectionAction.BLOCK_PLACE,
                    it.block.position(),
                ) != null
            }
            if (!exposed) return false
            event.isCancelled = true
            notify(event.player, "Multi-block placement is not supported in exposed land.")
            return true
        }
        val authorization = authorize(
            PlayerId(event.player.uniqueId),
            PlayerProtectionAction.BLOCK_PLACE,
            position.horizontal(),
        ) ?: return false
        event.isCancelled = true
        if (!event.canBuild() || !SimpleBattleBlockPolicy.allowsPlace(event.blockPlaced)) {
            notify(event.player, "Only simple building blocks can be changed in exposed land.")
            return true
        }
        val capture = PlaceCapture(
            common = Capture(
                authorization,
                position,
                SimpleBlockSnapshot(event.blockReplacedState.blockData.getAsString(false)),
                event.blockPlaced.snapshot(),
            ),
            blockAgainst = event.blockAgainst.position3D(),
            hand = event.hand,
            item = event.itemInHand.clone(),
            gameMode = event.player.gameMode,
        )
        submit(capture.common, BlockMutationCause.PLAYER_PLACE) { prepared ->
            applyPlace(capture, prepared)
        }
        return true
    }

    fun metricsSummary(): String {
        val metrics = queue.metrics()
        return "pending=${metrics.pending}, accepted=${metrics.accepted}, applied=$applied, " +
            "stale=$stale, duplicates=${metrics.duplicates}, saturated=${metrics.saturated}, " +
            "prepared=${metrics.prepared}, rejected=${metrics.rejected}, failed=${metrics.failed}"
    }

    private fun submit(
        capture: Capture,
        cause: BlockMutationCause,
        apply: (PreparedExposureMutation) -> Boolean,
    ) {
        val authorization = capture.authorization
        val request = PrepareExposureMutation(
            exposureId = authorization.exposureId,
            ownerCivilizationId = authorization.ownerCivilizationId,
            claimId = authorization.claimId,
            position = capture.position,
            observedState = capture.observedState,
            expectedState = capture.desiredState,
            actorPlayerId = authorization.actorId,
            actorCivilizationId = authorization.actorCivilizationId,
            cause = cause,
        )
        val submission = queue.submit(request) { completion ->
            when (completion) {
                is ExposureJournalCompletion.Prepared -> {
                    if (runCatching { apply(completion.mutation) }
                            .onFailure { logger.log(Level.SEVERE, "Exposed-land replay failed", it) }
                            .getOrDefault(false)
                    ) applied++ else {
                        stale++
                        server.getPlayer(authorization.actorId.value)?.let {
                            notify(it, "The block or protection state changed before the action finished.")
                        }
                    }
                }
                is ExposureJournalCompletion.Rejected ->
                    server.getPlayer(authorization.actorId.value)?.let {
                        notify(it, completion.failure.description)
                    }
                ExposureJournalCompletion.Unavailable -> Unit
                is ExposureJournalCompletion.Failed ->
                    logger.log(Level.SEVERE, "Exposed-land damage journal failed", completion.failure)
            }
        }
        if (submission == ExposureQueueSubmission.Saturated) {
            server.getPlayer(authorization.actorId.value)?.let {
                notify(it, "Exposed-land damage is busy; try again shortly.")
            }
        }
    }

    private fun applyBreak(capture: BreakCapture, prepared: PreparedExposureMutation): Boolean {
        val block = revalidate(capture.common, prepared) ?: return false
        val player = activeNearbyPlayer(capture.common, capture.gameMode) ?: return false
        if (player.inventory.heldItemSlot != capture.heldSlot ||
            player.inventory.itemInMainHand.type != capture.tool.type ||
            player.inventory.itemInMainHand.enchantments != capture.tool.enchantments ||
            !SimpleBattleBlockPolicy.allowsBreak(block)
        ) return false
        return replaying(
            ReplayToken(capture.common.authorization.actorId, PlayerProtectionAction.BLOCK_BREAK, capture.common.position),
        ) { player.breakBlock(block) }
    }

    private fun applyPlace(capture: PlaceCapture, prepared: PreparedExposureMutation): Boolean {
        val block = revalidate(capture.common, prepared) ?: return false
        val player = activeNearbyPlayer(capture.common, capture.gameMode) ?: return false
        val currentItem = player.inventory.getItem(capture.hand)
        if (!currentItem.isSimilar(capture.item) || currentItem.amount < 1) return false
        val against = loadedBlock(capture.blockAgainst) ?: return false
        val originalState = block.state
        block.setBlockData(server.createBlockData(capture.common.desiredState.blockData), false)
        if (!SimpleBattleBlockPolicy.allowsPlace(block)) {
            originalState.update(true, false)
            return false
        }
        val replay = BlockPlaceEvent(
            block,
            originalState,
            against,
            currentItem.clone(),
            player,
            true,
            capture.hand,
        )
        replaying(
            ReplayToken(capture.common.authorization.actorId, PlayerProtectionAction.BLOCK_PLACE, capture.common.position),
        ) { server.pluginManager.callEvent(replay) }
        if (replay.isCancelled || !replay.canBuild() || block.snapshot() != capture.common.desiredState) {
            originalState.update(true, false)
            return false
        }
        if (player.gameMode != GameMode.CREATIVE) {
            val after = player.inventory.getItem(capture.hand)
            if (!after.isSimilar(currentItem) || after.amount != currentItem.amount) {
                originalState.update(true, false)
                return false
            }
            player.inventory.setItem(capture.hand, after.asQuantity(after.amount - 1))
        }
        return true
    }

    private fun revalidate(capture: Capture, prepared: PreparedExposureMutation): Block? {
        val authorization = capture.authorization
        if (prepared.site.exposureId != authorization.exposureId ||
            prepared.site.claimId != authorization.claimId ||
            prepared.site.position != capture.position ||
            prepared.event.actorPlayerId != authorization.actorId ||
            prepared.event.observedState != capture.observedState ||
            prepared.event.expectedState != capture.desiredState
        ) return null
        val current = authorize(
            authorization.actorId,
            authorization.action,
            capture.position.horizontal(),
        ) ?: return null
        if (current.exposureId != authorization.exposureId || current.claimId != authorization.claimId) {
            return null
        }
        return loadedBlock(capture.position)?.takeIf { it.snapshot() == capture.observedState }
    }

    private fun activeNearbyPlayer(capture: Capture, gameMode: GameMode): Player? {
        val player = server.getPlayer(capture.authorization.actorId.value) ?: return null
        if (player.gameMode != gameMode || player.world.key.asString() != capture.position.worldId.value) {
            return null
        }
        val center = Location(
            player.world,
            capture.position.x + 0.5,
            capture.position.y + 0.5,
            capture.position.z + 0.5,
        )
        return player.takeIf { it.location.distanceSquared(center) <= MAX_DISTANCE_SQUARED }
    }

    private fun loadedBlock(position: BlockPosition3D): Block? {
        val key = NamespacedKey.fromString(position.worldId.value) ?: return null
        val world = server.getWorld(key) ?: return null
        if (position.y !in world.minHeight until world.maxHeight ||
            !world.isChunkLoaded(Math.floorDiv(position.x, 16), Math.floorDiv(position.z, 16))
        ) return null
        return world.getBlockAt(position.x, position.y, position.z)
    }

    private fun isReplaying(player: Player, action: PlayerProtectionAction, position: BlockPosition3D) =
        replayToken == ReplayToken(PlayerId(player.uniqueId), action, position)

    private fun <T> replaying(token: ReplayToken, action: () -> T): T {
        check(replayToken == null)
        replayToken = token
        return try { action() } finally { replayToken = null }
    }

    private fun notify(player: Player, message: String) {
        player.sendActionBar(Component.text(message, NamedTextColor.RED))
    }

    private fun Block.position() = BlockPosition2D(WorldId(world.key.asString()), x, z)
    private fun Block.position3D() = BlockPosition3D(WorldId(world.key.asString()), x, y, z)
    private fun Block.snapshot() = SimpleBlockSnapshot(blockData.getAsString(false))

    private companion object {
        const val ADMIN_BYPASS_PERMISSION = "civilizations.admin.bypass"
        const val DAMAGE_PERMISSION = "civilizations.land.exposure.damage"
        const val MAX_DISTANCE_SQUARED = 64.0
    }
}

private data class Capture(
    val authorization: ActiveExposureBlockMutationAuthorization,
    val position: BlockPosition3D,
    val observedState: SimpleBlockSnapshot,
    val desiredState: SimpleBlockSnapshot,
)

private data class BreakCapture(
    val common: Capture,
    val heldSlot: Int,
    val tool: ItemStack,
    val gameMode: GameMode,
)

private data class PlaceCapture(
    val common: Capture,
    val blockAgainst: BlockPosition3D,
    val hand: EquipmentSlot,
    val item: ItemStack,
    val gameMode: GameMode,
)

private data class ReplayToken(
    val actorId: PlayerId,
    val action: PlayerProtectionAction,
    val position: BlockPosition3D,
)
