package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.CaneGrowthPolicy
import io.bennyc.civilizations.domain.claim.WorldId
import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import java.util.UUID

/** Cancellation only: never grants claim access, loads chunks, or reads SQL. */
class PaperCaneListener(private val policy: () -> CaneGrowthPolicy?) : Listener {
    private data class Position(val world: UUID, val x: Int, val y: Int, val z: Int)
    private fun key(b: Block) = Position(b.world.uid, b.x, b.y, b.z)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onGrow(event: BlockGrowEvent) {
        if (event.newState.type == Material.SUGAR_CANE && !allowed(event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSpread(event: BlockSpreadEvent) {
        if (event.newState.type == Material.SUGAR_CANE && !allowed(event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onForm(event: BlockFormEvent) {
        if (event.newState.type == Material.SUGAR_CANE && !allowed(event.block)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val blocks = if (event is BlockMultiPlaceEvent) event.replacedBlockStates.map { it.block } else listOf(event.blockPlaced)
        if (blocks.any { it.type == Material.SUGAR_CANE && !allowed(it) }) {
            event.isCancelled = true
            event.player.sendMessage(Component.text("Sugar cane must fit inside a registered cane zone and its height limit."))
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFertilize(event: BlockFertilizeEvent) {
        if (event.blocks.none { it.type == Material.SUGAR_CANE }) return
        if (!allowedBatch(event.blocks)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onEntityChange(event: EntityChangeBlockEvent) {
        if (event.to == Material.SUGAR_CANE && !allowed(event.block)) event.isCancelled = true
    }

    private fun allowedBatch(states: List<BlockState>): Boolean {
        val current = policy() ?: return false
        if (current.activation == null) return true
        if (states.size > 4096) return false
        val proposed = states.associate { key(it.block) to it.type }
        return states.filter { it.type == Material.SUGAR_CANE }.all { allowed(it.block, current, proposed) }
    }

    private fun allowed(block: Block): Boolean {
        val current = policy() ?: return false // Runtime loading/failure cannot open a growth window.
        return allowed(block, current, emptyMap())
    }

    private fun allowed(block: Block, current: CaneGrowthPolicy, proposed: Map<Position, Material>): Boolean {
        if (current.activation == null) return true
        val world = block.world
        // Only the target's loaded chunk and at most 3 neighbours in either vertical direction.
        if (!world.isChunkLoaded(block.x shr 4, block.z shr 4)) return false
        fun cane(y: Int): Boolean {
            if (y < world.minHeight || y >= world.maxHeight) return false
            val k = Position(world.uid, block.x, y, block.z)
            return (proposed[k] ?: world.getBlockAt(block.x, y, block.z).type) == Material.SUGAR_CANE
        }
        var bottom = block.y
        var top = block.y
        repeat(3) { if (cane(bottom - 1)) bottom-- }
        repeat(3) { if (cane(top + 1)) top++ }
        return current.permits(WorldId(world.key.asString()), world.uid, block.x, bottom, block.z, top)
    }
}
