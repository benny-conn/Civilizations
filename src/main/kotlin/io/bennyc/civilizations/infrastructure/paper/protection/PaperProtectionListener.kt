package io.bennyc.civilizations.infrastructure.paper.protection

import io.bennyc.civilizations.application.protection.EnvironmentProtectionAction
import io.bennyc.civilizations.application.protection.PlayerProtectionAction
import io.bennyc.civilizations.application.protection.PlayerProtectionRequest
import io.bennyc.civilizations.application.protection.ProtectionDecision
import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntimeState
import io.bennyc.civilizations.infrastructure.runtime.BattleBlockMutationQueue
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.block.Block
import org.bukkit.entity.AreaEffectCloud
import org.bukkit.entity.Entity
import org.bukkit.entity.EvokerFangs
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.TNTPrimed
import org.bukkit.entity.Tameable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockFromToEvent
import org.bukkit.event.block.BlockIgniteEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.EntityInteractEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingPlaceEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.player.PlayerArmorStandManipulateEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.event.player.PlayerBucketFillEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.vehicle.VehicleDestroyEvent
import org.bukkit.inventory.InventoryHolder
import java.util.logging.Logger

/** Thin Paper translations around the pure protection policy in the runtime snapshot. */
class PaperProtectionListener(
    private val runtime: CivilizationsRuntime,
    server: Server,
    logger: Logger,
) : Listener {
    private val battleMutations = PaperBattleBlockMutationAdapter(
        server = server,
        logger = logger,
        mutationQueue = BattleBlockMutationQueue(runtime::prepareBlockMutation),
        authorize = { actorId, action, target ->
            val ready = runtime.state as? CivilizationsRuntimeState.Ready
            ready?.activeSeason?.authorizeBattleBlockMutation(actorId, action, target)
        },
    )

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockBreak(event: BlockBreakEvent) {
        if (battleMutations.intercept(event)) {
            return
        }

        val action = if (event.block.state is InventoryHolder) {
            PlayerProtectionAction.CONTAINER_ACCESS
        } else {
            PlayerProtectionAction.BLOCK_BREAK
        }
        if (!allows(event.player, action, event.block.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (battleMutations.intercept(event)) {
            return
        }

        val affected = if (event is BlockMultiPlaceEvent) {
            event.replacedBlockStates.map { state -> state.block.position() }
        } else {
            listOf(event.blockPlaced.position())
        }
        val denied = affected.any { target ->
            !allows(event.player, PlayerProtectionAction.BLOCK_PLACE, target, notify = false)
        }
        if (denied) {
            event.isCancelled = true
            notifyDenied(event.player)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.PHYSICAL) {
            return
        }
        val block = event.clickedBlock ?: return
        val action = if (block.state is InventoryHolder) {
            PlayerProtectionAction.CONTAINER_ACCESS
        } else {
            PlayerProtectionAction.BLOCK_INTERACT
        }
        if (!allows(event.player, action, block.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBucketFill(event: PlayerBucketFillEvent) {
        if (!allows(event.player, PlayerProtectionAction.BUCKET_FILL, event.block.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBucketEmpty(event: PlayerBucketEmptyEvent) {
        if (!allows(event.player, PlayerProtectionAction.BUCKET_EMPTY, event.block.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onIgnite(event: BlockIgniteEvent) {
        val player = event.player ?: event.ignitingEntity?.responsiblePlayer()
        event.isCancelled = if (player != null) {
            !allows(player, PlayerProtectionAction.FIRE_IGNITE, event.block.position())
        } else {
            !allowsEnvironmentTarget(EnvironmentProtectionAction.FIRE, event.block.position())
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) {
        if (!allowsEnvironmentTarget(EnvironmentProtectionAction.FIRE, event.block.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFireSpread(event: BlockSpreadEvent) {
        if (event.source.type != Material.FIRE && event.source.type != Material.SOUL_FIRE) {
            return
        }
        if (!allowsEnvironmentTarget(EnvironmentProtectionAction.FIRE, event.block.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFluidFlow(event: BlockFromToEvent) {
        if (!allowsTransition(
                EnvironmentProtectionAction.FLUID_FLOW,
                event.block.position(),
                event.toBlock.position(),
            )
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) {
        val headAllowed = allowsTransition(
            EnvironmentProtectionAction.PISTON_MOVE,
            event.block.position(),
            event.block.getRelative(event.direction).position(),
        )
        val blocksAllowed = event.blocks.all { block ->
            allowsTransition(
                EnvironmentProtectionAction.PISTON_MOVE,
                block.position(),
                block.getRelative(event.direction).position(),
            )
        }
        if (!headAllowed || !blocksAllowed) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) {
        val headAllowed = allowsTransition(
            EnvironmentProtectionAction.PISTON_MOVE,
            event.block.getRelative(event.direction).position(),
            event.block.position(),
        )
        val blocksAllowed = event.blocks.all { block ->
            allowsTransition(
                EnvironmentProtectionAction.PISTON_MOVE,
                block.position(),
                block.getRelative(event.direction.oppositeFace).position(),
            )
        }
        if (!headAllowed || !blocksAllowed) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityExplosion(event: EntityExplodeEvent) {
        event.blockList().removeIf { block ->
            !allowsEnvironmentTarget(EnvironmentProtectionAction.EXPLOSION, block.position())
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBlockExplosion(event: BlockExplodeEvent) {
        event.blockList().removeIf { block ->
            !allowsEnvironmentTarget(EnvironmentProtectionAction.EXPLOSION, block.position())
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        if (!allowsEnvironmentTarget(
                EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
                event.block.position(),
            )
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingPlace(event: HangingPlaceEvent) {
        val player = event.player ?: return
        if (!allows(player, PlayerProtectionAction.ENTITY_INTERACT, event.entity.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHangingBreak(event: HangingBreakEvent) {
        val target = event.entity.position()
        val byEntity = event as? HangingBreakByEntityEvent
        val player = byEntity?.damageSource?.causingEntity?.responsiblePlayer()
            ?: byEntity?.remover?.responsiblePlayer()
        event.isCancelled = if (player != null) {
            !allows(player, PlayerProtectionAction.ENTITY_DAMAGE, target)
        } else {
            !allowsEnvironmentTarget(EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE, target)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        if (!allows(
                event.player,
                PlayerProtectionAction.ENTITY_INTERACT,
                event.rightClicked.position(),
            )
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityInteract(event: PlayerInteractEntityEvent) {
        if (!allows(
                event.player,
                PlayerProtectionAction.ENTITY_INTERACT,
                event.rightClicked.position(),
            )
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        val player = event.damageSource.causingEntity?.responsiblePlayer()
            ?: event.damager.responsiblePlayer()
            ?: return
        val targetPlayer = event.entity as? Player
        val action = if (targetPlayer == null) {
            PlayerProtectionAction.ENTITY_DAMAGE
        } else {
            PlayerProtectionAction.PVP
        }
        if (!allows(
                player = player,
                action = action,
                target = event.entity.position(),
                targetPlayerId = targetPlayer?.playerId(),
            )
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val player = event.damageSource.causingEntity?.responsiblePlayer()
            ?: event.attacker?.responsiblePlayer()
        event.isCancelled = if (player != null) {
            !allows(player, PlayerProtectionAction.ENTITY_DAMAGE, event.vehicle.position())
        } else {
            !allowsEnvironmentTarget(
                EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
                event.vehicle.position(),
            )
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onAutonomousEntityInteract(event: EntityInteractEvent) {
        if (!allowsEnvironmentTarget(
                EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
                event.block.position(),
            )
        ) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val player = event.player as? Player ?: return
        val location = event.inventory.location ?: return
        if (!allows(player, PlayerProtectionAction.CONTAINER_ACCESS, location.position())) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryMove(event: InventoryMoveItemEvent) {
        val source = event.source.location?.position()
        val target = event.destination.location?.position()
        event.isCancelled = when {
            source != null && target != null -> !allowsTransition(
                EnvironmentProtectionAction.CONTAINER_TRANSFER,
                source,
                target,
            )
            source != null -> !allowsEnvironmentTarget(
                EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
                source,
            )
            target != null -> !allowsEnvironmentTarget(
                EnvironmentProtectionAction.ENTITY_BLOCK_CHANGE,
                target,
            )
            else -> false
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryPickup(event: InventoryPickupItemEvent) {
        val target = event.inventory.location?.position() ?: return
        if (!allowsTransition(
                EnvironmentProtectionAction.CONTAINER_TRANSFER,
                event.item.position(),
                target,
            )
        ) {
            event.isCancelled = true
        }
    }

    fun battleMutationMetricsSummary(): String = battleMutations.metricsSummary()

    private fun allows(
        player: Player,
        action: PlayerProtectionAction,
        target: BlockPosition2D,
        targetPlayerId: PlayerId? = null,
        notify: Boolean = true,
    ): Boolean {
        val state = runtime.state as? CivilizationsRuntimeState.Ready
            ?: return denyWhileLoading(player, notify)
        val activeSeason = state.activeSeason ?: return true
        val decision = activeSeason.protection.decidePlayerAction(
            PlayerProtectionRequest(
                actorId = player.playerId(),
                action = action,
                target = target,
                targetPlayerId = targetPlayerId,
                adminBypass = player.hasPermission(ADMIN_BYPASS_PERMISSION),
            ),
        )
        return when (decision) {
            is ProtectionDecision.Allowed -> true
            is ProtectionDecision.Denied -> {
                if (notify) notifyDenied(player)
                false
            }
        }
    }

    private fun allowsEnvironmentTarget(
        action: EnvironmentProtectionAction,
        target: BlockPosition2D,
    ): Boolean {
        val state = runtime.state as? CivilizationsRuntimeState.Ready ?: return false
        val activeSeason = state.activeSeason ?: return true
        return activeSeason.protection.decideEnvironmentTarget(action, target) is
            ProtectionDecision.Allowed
    }

    private fun allowsTransition(
        action: EnvironmentProtectionAction,
        source: BlockPosition2D,
        target: BlockPosition2D,
    ): Boolean {
        val state = runtime.state as? CivilizationsRuntimeState.Ready ?: return false
        val activeSeason = state.activeSeason ?: return true
        return activeSeason.protection.decideEnvironmentTransition(action, source, target) is
            ProtectionDecision.Allowed
    }

    private fun denyWhileLoading(player: Player, notify: Boolean): Boolean {
        if (notify) {
            player.sendActionBar(
                Component.text("Civilizations is still loading; try again shortly.", NamedTextColor.RED),
            )
        }
        return false
    }

    private fun notifyDenied(player: Player) {
        player.sendActionBar(Component.text("That action is protected.", NamedTextColor.RED))
    }

    private fun Player.playerId(): PlayerId = PlayerId(uniqueId)

    private fun Entity.responsiblePlayer(): Player? = when (this) {
        is Player -> this
        is Projectile -> shooter as? Player
        is TNTPrimed -> source?.responsiblePlayer()
        is AreaEffectCloud -> source as? Player
        is EvokerFangs -> owner?.responsiblePlayer()
        is Tameable -> owner as? Player
        else -> null
    }

    private fun Block.position(): BlockPosition2D = BlockPosition2D(
        worldId = WorldId(world.key.asString()),
        x = x,
        z = z,
    )

    private fun Entity.position(): BlockPosition2D = location.position()

    private fun Location.position(): BlockPosition2D = BlockPosition2D(
        worldId = WorldId(requireNotNull(world).key.asString()),
        x = blockX,
        z = blockZ,
    )

    private companion object {
        const val ADMIN_BYPASS_PERMISSION = "civilizations.admin.bypass"
    }
}
