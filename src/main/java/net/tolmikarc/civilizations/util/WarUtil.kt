/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.FallingBlock
import org.bukkit.util.Vector
import org.mineacademy.fo.BlockUtil
import org.mineacademy.fo.EntityUtil
import org.mineacademy.fo.MathUtil
import org.mineacademy.fo.remain.CompMaterial
import org.mineacademy.fo.remain.Remain

object WarUtil {


    fun shootBlock(block: Block, velocity: Vector): FallingBlock? {
        return if (cannotShootBlock(block)) {
            null
        } else {
            val falling = Remain.spawnFallingBlock(block.location, block.type)
            val x = MathUtil.range(velocity.x, -2.0, 2.0) * 0.5
            val y = Math.random()
            val z = MathUtil.range(velocity.z, -2.0, 2.0) * 0.5
            falling.velocity = Vector(x, y, z)
            falling.dropItem = false
            block.type = Material.AIR
            EntityUtil.trackFalling(falling) {
                falling.location.block.type = Material.AIR
            }
            falling
        }
    }

    private fun cannotShootBlock(block: Block): Boolean {
        val material = block.type
        return (CompMaterial.isAir(material) || material.toString().contains("STEP") || material.toString()
            .contains("SLAB")) && !BlockUtil.isForBlockSelection(material)
    }
}