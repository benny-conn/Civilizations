/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.constants.Constants
import net.tolmikarc.civilizations.event.civ.CivEnterEvent
import net.tolmikarc.civilizations.event.civ.CivLeaveEvent
import net.tolmikarc.civilizations.event.civ.PlotEnterEvent
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import net.tolmikarc.civilizations.util.ClaimUtil.getCivFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.getPlotFromLocation
import net.tolmikarc.civilizations.util.PermissionUtil.can
import net.tolmikarc.civilizations.util.WarUtil.addDamages
import net.tolmikarc.civilizations.util.WarUtil.canAttackCivilization
import net.tolmikarc.civilizations.util.WarUtil.increaseBlocksBroken
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.EquipmentSlot
import org.mineacademy.fo.Common
import org.mineacademy.fo.debug.LagCatcher
import org.mineacademy.fo.remain.CompMetadata

class PlayerListener : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val civPlayer = CivPlayer.fromBukkitPlayer(player)
        civPlayer.playerName = player.name
        civPlayer.queueForSaving()
        if (civPlayer.civilization != null) {
            val civ: Civilization = civPlayer.civilization!!
            civ.queueForSaving()
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            CivPlayer.saveAsync(civPlayer)
            if (civPlayer.civilization != null) {
                val civ: Civilization = civPlayer.civilization!!
                Civilization.saveAsync(civ)
            }
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val killer = player.killer
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            if (civPlayer.civilization == null) return
            val civilization: Civilization = civPlayer.civilization!!
            civilization.raid?.let { raid ->
                killer?.let { killer ->
                    CivPlayer.fromBukkitPlayer(killer).addPower(Settings.POWER_PVP_TRANSACTION)
                }
                civPlayer.removePower(Settings.POWER_PVP_TRANSACTION)
                if (!raid.playersInvolved.containsKey(civPlayer)) return
                if (raid.playersInvolved[civPlayer]!! <= 0) return
                val playerLives: Int = raid.playersInvolved[civPlayer]!! - 1
                raid.playersInvolved[civPlayer] = playerLives
            }

        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (Settings.RESPAWN_CIV) {
            val player = CivPlayer.fromBukkitPlayer(event.player)
            player.civilization?.home?.let { event.respawnLocation = it }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onToolUse(event: PlayerInteractEvent) {
        val player = event.player
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type != Settings.CLAIM_TOOL) return
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            if (event.action == Action.LEFT_CLICK_BLOCK) {
                civPlayer.vertex1 =
                    Location(player.world, event.clickedBlock!!.x.toDouble(), 0.0, event.clickedBlock!!.z.toDouble())
                Common.tell(
                    player,
                    "&6Set first point to " + civPlayer.vertex1!!.x.toString() + " " + civPlayer.vertex1!!.z
                )
                event.isCancelled = true
            }
            if (event.action == Action.RIGHT_CLICK_BLOCK) {
                if (event.hand == EquipmentSlot.HAND) {
                    civPlayer.vertex2 = Location(
                        player.world,
                        event.clickedBlock!!.x.toDouble(),
                        256.0,
                        event.clickedBlock!!.z.toDouble()
                    )
                    Common.tell(
                        player,
                        "&6Set second point to " + civPlayer.vertex2!!.x.toString() + " " + civPlayer.vertex2!!.z
                    )
                    event.isCancelled = true
                }
            }
        }
    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        LagCatcher.start("use")
        try {
            if (event.action == Action.LEFT_CLICK_BLOCK) return
            val player = event.player
            val civilization = getCivFromLocation(player.location) ?: return
            if (event.hasBlock()) {
                val block = event.clickedBlock
                if (block != null) {
                    if (Settings.SWITCHABLES.contains(block.type)) event.isCancelled =
                        !can(ClaimPermissions.PermType.SWITCH, player, civilization) else {
                        event.isCancelled = !can(ClaimPermissions.PermType.INTERACT, player, civilization)
                    }
                }
            } else {
                event.isCancelled = !can(ClaimPermissions.PermType.INTERACT, player, civilization)
            }
            if (event.isCancelled) {
                Common.tell(player, "&cYou cannot Interact here.")
            }
        } finally {
            LagCatcher.end("use")
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        LagCatcher.start("block-break")
        try {
            val player = event.player
            CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
                val block = event.block
                val civilization = getCivFromLocation(block.location) ?: return
                // if a player is raiding he can break
                if (canAttackCivilization(civPlayer, civilization)) {
                    // except player cannot break things that are defined in settings so long as the settings says player cant
                    if (!Settings.RAID_BREAK_SWITCHABLES) if (Settings.SWITCHABLES.contains(block.type)) {
                        event.isCancelled = true
                        return
                    }
                    civPlayer.civilization?.let { playerCiv ->
                        addDamages(civilization, playerCiv, block)
                        increaseBlocksBroken(civPlayer)
                    }
                } else {
                    event.isCancelled = !can(ClaimPermissions.PermType.BREAK, player, civilization)
                    if (event.isCancelled)
                        Common.tell(player, "&cYou cannot break here.")
                    else
                        return
                }
            }
        } finally {
            LagCatcher.end("block-break")
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        LagCatcher.start("block-place")
        try {
            val player = event.player
            val block = event.block
            CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
                val civilization = getCivFromLocation(block.location) ?: return
                // if a player can attack (is in a raid currently and valid player proportions) then let him place tnt
                if (canAttackCivilization(civPlayer, civilization)) {
                    if (Settings.RAID_TNT_COOLDOWN != -1) {
                        if (block.type == Material.TNT) {
                            // make sure player doesnt have a tnt cooldown
                            if (hasCooldown(civPlayer.playerUUID, CooldownTask.CooldownType.TNT)) {
                                event.isCancelled = true
                                Common.tell(
                                    player,
                                    "Please wait " + getCooldownRemaining(
                                        civPlayer.playerUUID,
                                        CooldownTask.CooldownType.TNT
                                    ) + " settings before using TNT again"
                                )
                                return
                            }
                            addCooldownTimer(civilization.uuid, CooldownTask.CooldownType.TNT)
                            block.type = Material.AIR
                            CompMetadata.setMetadata(
                                block.world.spawnEntity(block.location, EntityType.PRIMED_TNT),
                                Constants.WAR_TNT_TAG,
                                player.uniqueId.toString()
                            )
                        }
                    }
                } else {
                    event.isCancelled = !can(ClaimPermissions.PermType.BUILD, player, civilization)
                    if (event.isCancelled) Common.tell(player, "&cYou cannot place here")
                }
            }
        } finally {
            LagCatcher.end("block-place")
        }
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val to: Location = event.to!!
        val from: Location = event.from
        LagCatcher.start("move")
        try {
            // Are we going into a new block?
            if (from.blockX != to.blockX || from.blockZ != to.blockZ) {
                val civTo = getCivFromLocation(to)
                val civFrom = getCivFromLocation(from)
                // make sure some civilization is involved, else what would this code be for?
                if (civTo == null && civFrom == null) return
                // are we entering a new civ?
                if (civTo != null && civTo != civFrom) {
                    Common.callEvent(
                        CivEnterEvent(
                            civTo,
                            event
                        )
                    )
                } else if (civTo == null && civFrom != null) { // are we leaving the civ?
                    Common.callEvent(
                        CivLeaveEvent(
                            civFrom,
                            event
                        )
                    )
                }
                val plotTo = getPlotFromLocation(event.to!!)
                val plotFrom = getPlotFromLocation(event.from)
                // are we entering a new plot
                if (plotTo != null && plotTo != plotFrom) {
                    Common.callEvent(
                        PlotEnterEvent(
                            plotTo,
                            event
                        )
                    )
                }
            }
        } finally {
            LagCatcher.end("move")
        }
    }

    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val to: Location = event.to!!
        val from: Location = event.from
        LagCatcher.start("teleport")
        try {
            val civTo = getCivFromLocation(to)
            val civFrom = getCivFromLocation(from)
            // make sure some civilization is involved, else what would this code be for?
            if (civTo == null && civFrom == null) return
            // are we entering a new civ?
            if (civTo != null && civTo != civFrom) {
                Common.callEvent(CivEnterEvent(civTo, event))
            } else if (civTo == null && civFrom != null) { // are we leaving the civ?
                Common.callEvent(CivLeaveEvent(civFrom, event))
            }
            val plotTo = getPlotFromLocation(event.to!!)
            val plotFrom = getPlotFromLocation(event.from)
            // are we entering a new plot
            if (plotTo != null && plotTo != plotFrom) {
                Common.callEvent(PlotEnterEvent(plotTo, event))
            }
        } finally {
            LagCatcher.end("teleport")
        }
    }


    @EventHandler
    fun onPVP(event: EntityDamageByEntityEvent) {
        LagCatcher.start("pvp")
        try {
            val damaged = event.entity
            val damager = event.damager
            if (damaged !is Player || damager !is Player) return
            val civDamaged = CivPlayer.fromBukkitPlayer(damaged)
            val civDamager = CivPlayer.fromBukkitPlayer(damager)
            val location = damaged.getLocation()
            val civilization = getCivFromLocation(location) ?: return
            if (civilization.citizens.contains(civDamaged)) {
                event.isCancelled = !canAttackCivilization(civDamager, civilization)
                if (!event.isCancelled) {
                    if (Settings.RAID_PVP_TP_COOLDOWN) addCooldownTimer(
                        damaged.getUniqueId(),
                        CooldownTask.CooldownType.TELEPORT
                    )
                } else {
                    Common.tell(damager, "&cYou cannot PVP here.")
                }
                return
            }
            val plot = getPlotFromLocation(location, civilization)
            if (plot != null) {
                if (!plot.claimToggleables.pvp) event.isCancelled = true
                if (event.isCancelled) {
                    Common.tell(damager, "&cYou cannot PVP here.")
                }
                return
            }
            event.isCancelled = !civilization.claimToggleables.pvp
            if (event.isCancelled) {
                Common.tell(damager, "&cYou cannot PVP here.")
            }
        } finally {
            LagCatcher.end("pvp")
        }
    }

}