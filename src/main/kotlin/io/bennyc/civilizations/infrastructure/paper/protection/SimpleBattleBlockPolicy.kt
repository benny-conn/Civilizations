package io.bennyc.civilizations.infrastructure.paper.protection

import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.TileState
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Piston
import org.bukkit.block.data.type.TechnicalPiston

/** Conservative Season One boundary for independently mutable building blocks. */
internal object SimpleBattleBlockPolicy {
    fun allowsBreak(block: Block): Boolean =
        isSimpleBuildingBlock(block) && !hasUnsafeDependentNeighbor(block)

    fun allowsPlace(block: Block): Boolean = isSimpleBuildingBlock(block)

    private fun isSimpleBuildingBlock(block: Block): Boolean {
        val material = block.type
        val data = block.blockData
        return !block.isEmpty &&
            block.isSolid &&
            !material.hasUnsafeGravity() &&
            !block.isLiquid &&
            block.state !is TileState &&
            data !is Bisected &&
            data !is Bed &&
            data !is Piston &&
            data !is TechnicalPiston &&
            (data !is Waterlogged || !data.isWaterlogged)
    }

    /**
     * B1 does not journal secondary physics. Deny a break when a nearby fragile
     * block or gravity block could be destroyed by removing this support.
     */
    private fun hasUnsafeDependentNeighbor(block: Block): Boolean =
        CARDINAL_FACES.any { face ->
            val neighbor = block.getRelative(face)
            when {
                neighbor.isEmpty -> false
                neighbor.isLiquid -> true
                face == BlockFace.UP && neighbor.type.hasUnsafeGravity() -> true
                !neighbor.isSolid -> true
                else -> false
            }
        }

    private fun org.bukkit.Material.hasUnsafeGravity(): Boolean =
        name == "SAND" ||
            name == "RED_SAND" ||
            name == "GRAVEL" ||
            name == "SUSPICIOUS_SAND" ||
            name == "SUSPICIOUS_GRAVEL" ||
            name == "DRAGON_EGG" ||
            name == "ANVIL" ||
            name.endsWith("_ANVIL") ||
            name.endsWith("_CONCRETE_POWDER")

    private val CARDINAL_FACES = listOf(
        BlockFace.NORTH,
        BlockFace.EAST,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.UP,
        BlockFace.DOWN,
    )
}
