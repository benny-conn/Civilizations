/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
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

    fun canAttackCivilization(player: CPlayer, civBeingRaided: Civ): Boolean {
        return isPlayerRaiding(player, civBeingRaided) && isPlayerLivesValid(
            player,
            civBeingRaided
        )
    }

    fun isPlayerToPlayerRatioValid(attacked: Civ, attacker: Civ?): Boolean {
        return ClaimUtil.playersInCivClaims(attacked, attacked) / ClaimUtil.playersInCivClaims(
            attacked,
            attacker
        ) >= Settings.RAID_RATIO_MAX_IN_RAID!!
    }

    private fun isPlayerRaiding(player: CPlayer, attackedCiv: Civ): Boolean {
        val raid = attackedCiv.raid ?: return false
        return raid.playersInvolved.containsKey(player)
    }


    // checks if the attacking civ is attacking the attacked civ
    fun isBeingRaided(attackedCiv: Civ?, attackingCiv: Civ?): Boolean {
        if (attackedCiv == null || attackingCiv == null) return false
        val raid = attackedCiv.raid ?: return false
        return raid.civBeingRaided == attackedCiv && raid.civRaiding == attackingCiv
    }

    fun isBeingRaidedByAlly(attackedCiv: Civ?, allyCiv: Civ?): Boolean {
        if (attackedCiv == null || allyCiv == null) return false
        val raid = attackedCiv.raid ?: return false
        return raid.civBeingRaided == attackedCiv && raid.civRaiding.relationships.allies.contains(allyCiv)
    }

    fun isInRaid(civilization: Civ): Boolean {
        return civilization.raid != null
    }

    private fun getRaidLives(player: CPlayer, civilization: Civ): Int? {
        if (civilization.raid != null) if (civilization.raid!!.playersInvolved.containsKey(player)) return civilization.raid!!.playersInvolved[player]
        return 0
    }

    private fun isPlayerLivesValid(player: CPlayer, civInRaid: Civ): Boolean {
        return if (Settings.RAID_LIVES == -1) true else getRaidLives(player, civInRaid)!! >= 0
    }

    fun isPlayerAtWar(player: Player, civ: Civ): Boolean {
        val cache = PlayerManager.fromBukkitPlayer(player)
        val playerCiv = cache.civilization
        if (playerCiv != null) {
            return civ.relationships.warring.contains(playerCiv) || playerCiv.relationships.warring.contains(civ)
        }
        return false
    }

    fun addDamages(attackedCiv: Civ, attackingCiv: Civ, block: Block) {
        if (attackedCiv.regionDamages == null) attackedCiv.regionDamages = RegionDamages()
        attackedCiv.regionDamages?.brokenBlocksMap?.set(block.location, block.blockData.asString)
        attackedCiv.removePower(Settings.POWER_RAID_BLOCK)
        attackingCiv.addPower(Settings.POWER_BLOCKS_WEIGHT)
        CivManager.queueForSaving(attackedCiv, attackingCiv)
    }

    fun shootBlockAndAddDamages(attackedCiv: Civ, attackingCiv: Civ, block: Block) {
        if (attackedCiv.regionDamages == null) attackedCiv.regionDamages = RegionDamages()
        attackedCiv.regionDamages?.brokenBlocksMap?.set(block.location, block.blockData.asString)
        shootBlock(
            block,
            Vector.getRandom()
        )
        attackedCiv.removePower(Settings.POWER_RAID_BLOCK)
        attackingCiv.addPower(Settings.POWER_BLOCKS_WEIGHT)
        CivManager.queueForSaving(attackedCiv, attackingCiv)
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


    fun increaseBlocksBroken(cache: CPlayer) {
        cache.addRaidBlocksDestroyed(1)
    }
}