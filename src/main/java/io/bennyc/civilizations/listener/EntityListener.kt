/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.listener

import io.bennyc.civilizations.permissions.PermissionType
import io.bennyc.civilizations.util.ClaimUtil
import io.bennyc.civilizations.util.ClaimUtil.getCivFromLocation
import io.bennyc.civilizations.util.ClaimUtil.getPlotFromLocation
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TNTPrimed
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.entity.ExplosionPrimeEvent
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.hanging.HangingBreakEvent
import org.bukkit.event.hanging.HangingBreakEvent.RemoveCause
import org.mineacademy.fo.RandomUtil
import org.mineacademy.fo.remain.CompMetadata
import java.util.*


class EntityListener : Listener {
    @EventHandler
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        val location = event.location
        if (!location.chunk.isLoaded) return
        val civilization = getCivFromLocation(location) ?: return
        val plot = getPlotFromLocation(location, civilization)
        if (plot != null) {
            if (!plot.toggleables.mobs) event.isCancelled = true
            return
        }
        if (!civilization.toggleables.mobs) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onPrime(event: ExplosionPrimeEvent) {
        val entity = event.entity
        if (entity.type != EntityType.PRIMED_TNT) return
        val tnt = entity as TNTPrimed
        if (CompMetadata.hasMetadata(
                tnt,
                io.bennyc.civilizations.constants.Constants.WAR_TNT_TAG
            )
        ) {
            event.radius = 10F
        }
    }

    @EventHandler
    fun onExplode(event: EntityExplodeEvent) {
        val entity = event.entity
        val blockIterator = event.blockList().iterator()
        while (blockIterator.hasNext()) {
            val block = blockIterator.next()
            val civilization = getCivFromLocation(block.location) ?: continue
            if (CompMetadata.hasMetadata(
                    entity,
                    io.bennyc.civilizations.constants.Constants.WAR_TNT_TAG
                )
            ) {
                event.yield = 0F
                val attackingPlayer = io.bennyc.civilizations.manager.PlayerManager.getByUUID(
                    UUID.fromString(
                        CompMetadata.getMetadata(
                            entity,
                            io.bennyc.civilizations.constants.Constants.WAR_TNT_TAG
                        )
                    )!!
                )

                if (civilization.canAttackCivilization(attackingPlayer)) {
                    if (!io.bennyc.civilizations.settings.Settings.RAID_BREAK_SWITCHABLES)
                        if (io.bennyc.civilizations.PermissionChecker.isSwitchable(block.type) || block.type == Material.TNT || Tag.STAIRS.isTagged(
                                block.type
                            ) || Tag.CLIMBABLE.isTagged(block.type)
                        ) {
                            blockIterator.remove()
                            continue
                        }
                    val attackingCivilization = attackingPlayer.civilization!!
                    if (RandomUtil.chance(75))
                        civilization.shootBlockAndAddDamages( attackingCivilization, block)
                    else
                        civilization.addDamages( attackingCivilization, block)
                    attackingPlayer.addRaidBlocksDestroyed(1)
                    continue
                }
            }
            val plot = getPlotFromLocation(block.location, civilization)
            if (plot != null) {
                if (!plot.toggleables.explosion) blockIterator.remove()
                continue
            }
            if (!civilization.toggleables.explosion) blockIterator.remove()
        }
    }

    @EventHandler
    private fun onPlayerFall(event: EntityDamageEvent) {
        if (event.cause == EntityDamageEvent.DamageCause.FALL && event.entity is Player) {
            val player = event.entity as Player
            val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
            if (civPlayer.flying) {
                event.isCancelled = true
                civPlayer.flying = false
            }
        }
    }

    @EventHandler
    private fun onItemFrameChange(event: HangingBreakByEntityEvent) {
        val loc = event.entity.location
        if (!ClaimUtil.isLocationInACiv(loc)) return
        val player = event.remover as Player
        val civ = getCivFromLocation(loc)
        event.isCancelled = !io.bennyc.civilizations.PermissionChecker.can(PermissionType.INTERACT, player, civ!!)
    }

    @EventHandler
    fun onHangingBreak(event: HangingBreakEvent) {
        val loc = event.entity.location
        if (!ClaimUtil.isLocationInACiv(loc)) return
        if (event.cause == RemoveCause.EXPLOSION) {
            event.isCancelled = true
        }
    }

}