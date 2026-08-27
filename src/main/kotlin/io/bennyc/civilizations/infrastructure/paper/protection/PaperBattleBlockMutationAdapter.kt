package io.bennyc.civilizations.infrastructure.paper.protection

import io.bennyc.civilizations.application.damage.PrepareBlockMutation
import io.bennyc.civilizations.application.damage.PreparedBlockMutation
import io.bennyc.civilizations.application.protection.PlayerProtectionAction
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.damage.BlockMutationCause
import io.bennyc.civilizations.domain.damage.BlockPosition3D
import io.bennyc.civilizations.domain.damage.SimpleBlockSnapshot
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.infrastructure.runtime.ActiveBattleBlockMutationAuthorization
import io.bennyc.civilizations.infrastructure.runtime.BattleBlockJournalCompletion
import io.bennyc.civilizations.infrastructure.runtime.BattleBlockMutationQueue
import io.bennyc.civilizations.infrastructure.runtime.BattleBlockQueueSubmission
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
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Paper-thread half of the journal-before-world-mutation contract.
 * It cancels the original event, submits immutable values, then revalidates
 * and replays one action after the journal transaction commits.
 */
internal class PaperBattleBlockMutationAdapter(
    private val server: Server,
    private val logger: Logger,
    private val mutationQueue: BattleBlockMutationQueue,
    private val authorize: (
        PlayerId,
        PlayerProtectionAction,
        BlockPosition2D,
    ) -> ActiveBattleBlockMutationAuthorization?,
) {
    private var replayToken: BattleBlockReplayToken? = null
    private var appliedBattleMutations = 0L
    private var staleBattleMutations = 0L

    /** Returns true when B1 consumed the event or is replaying an already prepared event. */
    fun intercept(event: BlockBreakEvent): Boolean {
        val target = event.block.position3D()
        if (isReplaying(event.player, PlayerProtectionAction.BLOCK_BREAK, target)) {
            return true
        }
        val battle = battleAuthorization(
            event.player,
            PlayerProtectionAction.BLOCK_BREAK,
            target.horizontal(),
        ) ?: return false

        event.isCancelled = true
        if (!SimpleBattleBlockPolicy.allowsBreak(event.block)) {
            notifyUnsupportedBattleBlock(event.player)
            return true
        }
        queueBreak(event, battle, target)
        return true
    }

    /** Returns true when B1 consumed the event or is replaying an already prepared event. */
    fun intercept(event: BlockPlaceEvent): Boolean {
        val target = event.blockPlaced.position3D()
        if (isReplaying(event.player, PlayerProtectionAction.BLOCK_PLACE, target)) {
            return true
        }
        if (event.player.hasPermission(ADMIN_BYPASS_PERMISSION)) {
            return false
        }
        if (event is BlockMultiPlaceEvent) {
            val affectsBattle = event.replacedBlockStates.any { state ->
                battleAuthorization(
                    event.player,
                    PlayerProtectionAction.BLOCK_PLACE,
                    state.block.position(),
                ) != null
            }
            if (!affectsBattle) {
                return false
            }
            event.isCancelled = true
            notifyUnsupportedBattleBlock(event.player)
            return true
        }

        val battle = battleAuthorization(
            event.player,
            PlayerProtectionAction.BLOCK_PLACE,
            target.horizontal(),
        ) ?: return false
        event.isCancelled = true
        if (!event.canBuild() || !SimpleBattleBlockPolicy.allowsPlace(event.blockPlaced)) {
            notifyUnsupportedBattleBlock(event.player)
            return true
        }
        queuePlace(event, battle, target)
        return true
    }

    fun metricsSummary(): String {
        val metrics = mutationQueue.metrics()
        val average = String.format(Locale.ROOT, "%.2f", metrics.averageJournalMillis)
        return "pending=${metrics.pending}, peak=${metrics.peakPending}, " +
            "accepted=${metrics.accepted}, applied=$appliedBattleMutations, " +
            "stale=$staleBattleMutations, duplicates=${metrics.duplicates}, " +
            "saturated=${metrics.saturated}, rejected=${metrics.rejected}, " +
            "unavailable=${metrics.unavailable}, failed=${metrics.failed}, " +
            "averageJournalMs=$average"
    }

    private fun queueBreak(
        event: BlockBreakEvent,
        authorization: ActiveBattleBlockMutationAuthorization,
        position: BlockPosition3D,
    ) {
        val capture = BattleBlockBreakCapture(
            common = BattleBlockMutationCapture(
                authorization = authorization,
                position = position,
                observedState = event.block.snapshot(),
                desiredState = SimpleBlockSnapshot("minecraft:air"),
            ),
            heldSlot = event.player.inventory.heldItemSlot,
            tool = event.player.inventory.itemInMainHand.clone(),
            gameMode = event.player.gameMode,
        )
        submitBattleMutation(
            playerId = event.player.uniqueId,
            capture = capture.common,
            cause = BlockMutationCause.PLAYER_BREAK,
        ) { prepared ->
            applyBreak(capture, prepared)
        }
    }

    private fun queuePlace(
        event: BlockPlaceEvent,
        authorization: ActiveBattleBlockMutationAuthorization,
        position: BlockPosition3D,
    ) {
        val capture = BattleBlockPlaceCapture(
            common = BattleBlockMutationCapture(
                authorization = authorization,
                position = position,
                observedState = SimpleBlockSnapshot(
                    event.blockReplacedState.blockData.getAsString(false),
                ),
                desiredState = event.blockPlaced.snapshot(),
            ),
            blockAgainst = event.blockAgainst.position3D(),
            hand = event.hand,
            item = event.itemInHand.clone(),
            gameMode = event.player.gameMode,
        )
        submitBattleMutation(
            playerId = event.player.uniqueId,
            capture = capture.common,
            cause = BlockMutationCause.PLAYER_PLACE,
        ) { prepared ->
            applyPlace(capture, prepared)
        }
    }

    private fun submitBattleMutation(
        playerId: java.util.UUID,
        capture: BattleBlockMutationCapture,
        cause: BlockMutationCause,
        apply: (PreparedBlockMutation) -> Boolean,
    ) {
        val request = PrepareBlockMutation(
            battleId = capture.authorization.battleId,
            claimId = capture.authorization.claimId,
            position = capture.position,
            observedState = capture.observedState,
            actorId = capture.authorization.actorId,
            cause = cause,
        )
        val submission = mutationQueue.submit(request) { completion ->
            when (completion) {
                is BattleBlockJournalCompletion.Prepared -> {
                    val applied = runCatching { apply(completion.mutation) }
                        .onFailure { failure ->
                            logger.log(
                                Level.SEVERE,
                                "Failed to apply prepared battle mutation at ${capture.position}",
                                failure,
                            )
                        }
                        .getOrDefault(false)
                    if (applied) {
                        appliedBattleMutations++
                    } else {
                        staleBattleMutations++
                        server.getPlayer(playerId)?.let(::notifyStaleBattleMutation)
                    }
                }
                is BattleBlockJournalCompletion.Rejected -> {
                    logger.fine(completion.failure.description)
                    server.getPlayer(playerId)?.let(::notifyBattleClosed)
                }
                BattleBlockJournalCompletion.Unavailable ->
                    server.getPlayer(playerId)?.let(::notifyBattleClosed)
                is BattleBlockJournalCompletion.Failed ->
                    logger.log(Level.SEVERE, "Battle damage journal failed", completion.failure)
            }
        }
        if (submission == BattleBlockQueueSubmission.Saturated) {
            server.getPlayer(playerId)?.let(::notifyBattleQueueBusy)
        }
    }

    private fun applyBreak(
        capture: BattleBlockBreakCapture,
        prepared: PreparedBlockMutation,
    ): Boolean {
        val block = revalidate(capture.common, prepared) ?: return false
        val player = activeNearbyPlayer(capture.common, capture.gameMode) ?: return false
        if (player.inventory.heldItemSlot != capture.heldSlot ||
            !sameBreakTool(player.inventory.itemInMainHand, capture.tool) ||
            !SimpleBattleBlockPolicy.allowsBreak(block)
        ) {
            return false
        }
        return replaying(
            BattleBlockReplayToken(
                actorId = capture.common.authorization.actorId,
                action = PlayerProtectionAction.BLOCK_BREAK,
                position = capture.common.position,
            ),
        ) {
            player.breakBlock(block)
        }
    }

    private fun applyPlace(
        capture: BattleBlockPlaceCapture,
        prepared: PreparedBlockMutation,
    ): Boolean {
        val block = revalidate(capture.common, prepared) ?: return false
        val player = activeNearbyPlayer(capture.common, capture.gameMode) ?: return false
        val currentItem = player.inventory.getItem(capture.hand)
        if (!currentItem.isSimilar(capture.item) || currentItem.amount < 1) {
            return false
        }
        val blockAgainst = loadedBlock(capture.blockAgainst) ?: return false
        val desiredData = server.createBlockData(capture.common.desiredState.blockData)
        val originalState = block.state
        block.setBlockData(desiredData, false)
        if (!SimpleBattleBlockPolicy.allowsPlace(block)) {
            originalState.update(true, false)
            return false
        }

        val replayEvent = BlockPlaceEvent(
            block,
            originalState,
            blockAgainst,
            currentItem.clone(),
            player,
            true,
            capture.hand,
        )
        replaying(
            BattleBlockReplayToken(
                actorId = capture.common.authorization.actorId,
                action = PlayerProtectionAction.BLOCK_PLACE,
                position = capture.common.position,
            ),
        ) {
            server.pluginManager.callEvent(replayEvent)
        }
        if (replayEvent.isCancelled ||
            !replayEvent.canBuild() ||
            block.snapshot() != capture.common.desiredState
        ) {
            originalState.update(true, false)
            return false
        }

        if (player.gameMode != GameMode.CREATIVE) {
            val afterEvent = player.inventory.getItem(capture.hand)
            if (!afterEvent.isSimilar(currentItem) || afterEvent.amount != currentItem.amount) {
                originalState.update(true, false)
                return false
            }
            player.inventory.setItem(
                capture.hand,
                afterEvent.asQuantity(afterEvent.amount - 1),
            )
        }
        return true
    }

    private fun revalidate(
        capture: BattleBlockMutationCapture,
        prepared: PreparedBlockMutation,
    ): Block? {
        if (prepared.actorId != capture.authorization.actorId ||
            prepared.journalEntry.battleId != capture.authorization.battleId ||
            prepared.journalEntry.claimId != capture.authorization.claimId ||
            prepared.journalEntry.position != capture.position ||
            prepared.expectedCurrentState != capture.observedState
        ) {
            return null
        }
        val current = battleAuthorization(
            capture.authorization.actorId,
            capture.authorization.action,
            capture.position.horizontal(),
        ) ?: return null
        if (current.battleId != capture.authorization.battleId ||
            current.claimId != capture.authorization.claimId
        ) {
            return null
        }
        return loadedBlock(capture.position)
            ?.takeIf { block -> block.snapshot() == capture.observedState }
    }

    private fun activeNearbyPlayer(
        capture: BattleBlockMutationCapture,
        expectedGameMode: GameMode,
    ): Player? {
        val player = server.getPlayer(capture.authorization.actorId.value) ?: return null
        if (player.gameMode != expectedGameMode ||
            player.world.key.asString() != capture.position.worldId.value
        ) {
            return null
        }
        val center = Location(
            player.world,
            capture.position.x + 0.5,
            capture.position.y + 0.5,
            capture.position.z + 0.5,
        )
        return player.takeIf { it.location.distanceSquared(center) <= MAX_REPLAY_DISTANCE_SQUARED }
    }

    private fun loadedBlock(position: BlockPosition3D): Block? {
        val worldKey = NamespacedKey.fromString(position.worldId.value) ?: return null
        val world = server.getWorld(worldKey) ?: return null
        val chunkX = Math.floorDiv(position.x, CHUNK_WIDTH)
        val chunkZ = Math.floorDiv(position.z, CHUNK_WIDTH)
        if (position.y !in world.minHeight until world.maxHeight ||
            !world.isChunkLoaded(chunkX, chunkZ)
        ) {
            return null
        }
        return world.getBlockAt(position.x, position.y, position.z)
    }

    private fun battleAuthorization(
        player: Player,
        action: PlayerProtectionAction,
        target: BlockPosition2D,
    ): ActiveBattleBlockMutationAuthorization? {
        if (player.hasPermission(ADMIN_BYPASS_PERMISSION)) {
            return null
        }
        return battleAuthorization(PlayerId(player.uniqueId), action, target)
    }

    private fun battleAuthorization(
        actorId: PlayerId,
        action: PlayerProtectionAction,
        target: BlockPosition2D,
    ): ActiveBattleBlockMutationAuthorization? = authorize(actorId, action, target)

    private fun isReplaying(
        player: Player,
        action: PlayerProtectionAction,
        position: BlockPosition3D,
    ): Boolean = replayToken == BattleBlockReplayToken(PlayerId(player.uniqueId), action, position)

    private fun <T> replaying(token: BattleBlockReplayToken, action: () -> T): T {
        check(replayToken == null) { "Battle block mutation replay cannot nest" }
        replayToken = token
        return try {
            action()
        } finally {
            replayToken = null
        }
    }

    private fun sameBreakTool(current: ItemStack, captured: ItemStack): Boolean =
        current.type == captured.type && current.enchantments == captured.enchantments

    private fun notifyUnsupportedBattleBlock(player: Player) {
        player.sendActionBar(
            Component.text(
                "Only independent building blocks can be changed during battle.",
                NamedTextColor.RED,
            ),
        )
    }

    private fun notifyBattleQueueBusy(player: Player) {
        player.sendActionBar(
            Component.text("Battle damage is busy; try again shortly.", NamedTextColor.RED),
        )
    }

    private fun notifyStaleBattleMutation(player: Player) {
        player.sendActionBar(
            Component.text(
                "The block or battle changed before that action could finish.",
                NamedTextColor.RED,
            ),
        )
    }

    private fun notifyBattleClosed(player: Player) {
        player.sendActionBar(
            Component.text("That battle no longer permits block changes.", NamedTextColor.RED),
        )
    }

    private fun Block.position(): BlockPosition2D = BlockPosition2D(
        worldId = WorldId(world.key.asString()),
        x = x,
        z = z,
    )

    private fun Block.position3D(): BlockPosition3D = BlockPosition3D(
        worldId = WorldId(world.key.asString()),
        x = x,
        y = y,
        z = z,
    )

    private fun Block.snapshot(): SimpleBlockSnapshot =
        SimpleBlockSnapshot(blockData.getAsString(false))

    private companion object {
        const val ADMIN_BYPASS_PERMISSION = "civilizations.admin.bypass"
        const val CHUNK_WIDTH = 16
        const val MAX_REPLAY_DISTANCE_SQUARED = 64.0
    }
}

private data class BattleBlockMutationCapture(
    val authorization: ActiveBattleBlockMutationAuthorization,
    val position: BlockPosition3D,
    val observedState: SimpleBlockSnapshot,
    val desiredState: SimpleBlockSnapshot,
)

private data class BattleBlockBreakCapture(
    val common: BattleBlockMutationCapture,
    val heldSlot: Int,
    val tool: ItemStack,
    val gameMode: GameMode,
)

private data class BattleBlockPlaceCapture(
    val common: BattleBlockMutationCapture,
    val blockAgainst: BlockPosition3D,
    val hand: EquipmentSlot,
    val item: ItemStack,
    val gameMode: GameMode,
)

private data class BattleBlockReplayToken(
    val actorId: PlayerId,
    val action: PlayerProtectionAction,
    val position: BlockPosition3D,
)
