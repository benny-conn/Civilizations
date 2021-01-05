/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.war.RegionDamages
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.mineacademy.fo.BlockUtil
import org.mineacademy.fo.EntityUtil
import org.mineacademy.fo.MathUtil
import org.mineacademy.fo.remain.CompMaterial
import org.mineacademy.fo.remain.Remain

object WarUtil {

    fun canAttackCivilization(player: CivPlayer, civBeingRaided: Civilization): Boolean {
        return isPlayerRaiding(player, civBeingRaided) && isPlayerLivesValid(
            player,
            civBeingRaided
        )
    }

    fun isPlayerToPlayerRatioValid(attacked: Civilization, attacker: Civilization?): Boolean {
        return ClaimUtil.playersInCivClaims(attacked, attacked) / ClaimUtil.playersInCivClaims(
            attacked,
            attacker
        ) >= Settings.RAID_RATIO_MAX_IN_RAID!!
    }

    private fun isPlayerRaiding(player: CivPlayer, attackedCiv: Civilization): Boolean {
        val attackingCiv = player.civilization ?: return false
        val raid = attackedCiv.raid ?: return false
        return raid.playersInvolved.containsKey(player) && isBeingRaided(attackedCiv, attackingCiv)
    }


    // checks if the attacking civ is attacking the attacked civ
    fun isBeingRaided(attackedCiv: Civilization?, attackingCiv: Civilization?): Boolean {
        if (attackedCiv == null || attackingCiv == null) return false
        val raid = attackedCiv.raid ?: return false
        return raid.civBeingRaided == attackedCiv && raid.civRaiding == attackingCiv
    }

    fun isInRaid(civilization: Civilization): Boolean {
        return civilization.raid != null
    }

    private fun getRaidLives(player: CivPlayer, civilization: Civilization): Int? {
        if (civilization.raid != null) if (civilization.raid!!.playersInvolved.containsKey(player)) return civilization.raid!!.playersInvolved[player]
        return 0
    }

    private fun isPlayerLivesValid(player: CivPlayer, civInRaid: Civilization): Boolean {
        return if (Settings.RAID_LIVES == -1) true else getRaidLives(player, civInRaid)!! >= 0
    }

    fun isPlayerAtWar(player: Player, civ: Civilization): Boolean {
        val cache = CivPlayer.fromBukkitPlayer(player)
        val playerCiv = cache.civilization
        if (playerCiv != null) {
            return civ.warring.contains(playerCiv) || playerCiv.warring.contains(civ)
        }
        return false
    }

    fun addDamages(attackedCiv: Civilization, attackingCiv: Civilization, block: Block) {
        if (attackedCiv.regionDamages == null) attackedCiv.regionDamages = RegionDamages(attackedCiv.uuid)
        attackedCiv.regionDamages?.brokenBlocksMap?.set(block.location, block.blockData.asString)
        block.type = Material.AIR
        attackedCiv.removePower(Settings.POWER_RAID_BLOCK)
        attackingCiv.addPower(Settings.POWER_BLOCKS_WEIGHT)
        attackedCiv.queueForSaving()
        attackingCiv.queueForSaving()
    }

    fun shootBlockAndAddDamages(attackedCiv: Civilization, attackingCiv: Civilization, block: Block) {
        if (attackedCiv.regionDamages == null) attackedCiv.regionDamages = RegionDamages(attackedCiv.uuid)
        attackedCiv.regionDamages?.brokenBlocksMap?.set(block.location, block.blockData.asString)
        shootBlock(
            block,
            Vector.getRandom()
        )
        attackedCiv.removePower(Settings.POWER_RAID_BLOCK)
        attackingCiv.addPower(Settings.POWER_BLOCKS_WEIGHT)
        attackedCiv.queueForSaving()
        attackingCiv.queueForSaving()
    }


    private fun shootBlock(block: Block, velocity: Vector): FallingBlock? {
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


    fun increaseBlocksBroken(cache: CivPlayer) {
        cache.addRaidBlocksDestroyed(1)
    }
}