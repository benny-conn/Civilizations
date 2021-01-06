/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.constants.Constants
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil.getCivFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.getPlotFromLocation
import net.tolmikarc.civilizations.util.WarUtil
import net.tolmikarc.civilizations.util.WarUtil.addDamages
import net.tolmikarc.civilizations.util.WarUtil.canAttackCivilization
import net.tolmikarc.civilizations.util.WarUtil.shootBlockAndAddDamages
import org.bukkit.Material
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.mineacademy.fo.RandomUtil
import org.mineacademy.fo.remain.CompMetadata
import java.util.*

class EntityListener : Listener {
    @EventHandler
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val location = event.location
        val civilization = getCivFromLocation(location) ?: return
        val plot = getPlotFromLocation(location, civilization)
        if (plot != null) {
            if (!plot.claimToggleables.mobs) event.isCancelled = true
            return
        }
        if (!civilization.claimToggleables.mobs) {
            event.isCancelled = true

            // Remove monsters in civilization
            for (region in civilization.claims) for (entity in region.entities) {
                (entity as? Monster)?.remove()
            }
        }
    }

    @EventHandler
    fun onExplode(event: EntityExplodeEvent) {
        val entity = event.entity
        if (!CompMetadata.hasMetadata(entity, Constants.WAR_TNT_TAG)) return
        val blockIterator = event.blockList().iterator()
        while (blockIterator.hasNext()) {
            val block = blockIterator.next()
            val civilization = getCivFromLocation(block.location) ?: continue
            if (CompMetadata.hasMetadata(
                    entity,
                    Constants.WAR_TNT_TAG
                )
            ) {
                val attackingPlayer = PlayerManager.getByUUID(
                    UUID.fromString(
                        CompMetadata.getMetadata(
                            entity,
                            Constants.WAR_TNT_TAG
                        )
                    )!!
                )!!
                if (canAttackCivilization(attackingPlayer, civilization)) {
                    if (!Settings.RAID_BREAK_SWITCHABLES) if (Settings.SWITCHABLES.contains(block.type) || block.type == Material.TNT) {
                        blockIterator.remove()
                        continue
                    }
                    if (civilization.regionDamages?.cleanUpSet?.contains(block) == true) {
                        blockIterator.remove()
                        continue
                    }
                    val attackingCiv = attackingPlayer.civilization!!

                    if (RandomUtil.chance(50))
                        shootBlockAndAddDamages(civilization, attackingCiv, block)
                    else
                        addDamages(civilization, attackingCiv, block)
                    WarUtil.increaseBlocksBroken(attackingPlayer)
                    blockIterator.remove()
                    continue
                }
            }
            val plot = getPlotFromLocation(block.location, civilization)
            if (plot != null) {
                if (!plot.claimToggleables.explosion) blockIterator.remove()
                continue
            }
            if (!civilization.claimToggleables.explosion) blockIterator.remove()
        }
    }

    @EventHandler
    private fun onPlayerFall(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL && event.entity is Player) {
            val player = event.entity as Player
            val civPlayer = PlayerManager.fromBukkitPlayer(player)
            if (civPlayer.flying) {
                event.isCancelled = true
                civPlayer.flying = false
            }
        }
    }
}