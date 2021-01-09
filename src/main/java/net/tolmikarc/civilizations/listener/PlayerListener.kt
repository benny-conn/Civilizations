/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.listener

import net.tolmikarc.civilizations.MapDrawer
import net.tolmikarc.civilizations.PermissionChecker.can
import net.tolmikarc.civilizations.constants.Constants
import net.tolmikarc.civilizations.event.CivEnterEvent
import net.tolmikarc.civilizations.event.CivLeaveEvent
import net.tolmikarc.civilizations.event.PlotEnterEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.impl.Claim
import net.tolmikarc.civilizations.model.impl.Selection
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import net.tolmikarc.civilizations.util.ClaimUtil.getCivFromLocation
import net.tolmikarc.civilizations.util.ClaimUtil.getPlotFromLocation
import net.tolmikarc.civilizations.util.WarUtil.addDamages
import net.tolmikarc.civilizations.util.WarUtil.canAttackCivilization
import net.tolmikarc.civilizations.util.WarUtil.increaseBlocksBroken
import org.bukkit.Bukkit
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
import org.bukkit.event.server.MapInitializeEvent
import org.bukkit.inventory.EquipmentSlot
import org.mineacademy.fo.Common
import org.mineacademy.fo.debug.LagCatcher
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.remain.CompMetadata

class PlayerListener : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        civPlayer.playerName = player.name
        PlayerManager.queueForSaving(civPlayer)
        if (civPlayer.civilization != null) {
            val civ = civPlayer.civilization!!
            CivManager.queueForSaving(civ)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            PlayerManager.saveAsync(civPlayer)
            if (civPlayer.civilization != null) {
                val civ = civPlayer.civilization!!
                CivManager.saveAsync(civ)
            }
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val killer = player.killer
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (civPlayer.civilization == null) return
            val civilization = civPlayer.civilization!!
            civilization.raid?.let { raid ->
                killer?.let { killer ->
                    PlayerManager.fromBukkitPlayer(killer).addPower(Settings.POWER_PVP_TRANSACTION)
                    HookManager.deposit(killer, 10.0)
                }
                civPlayer.removePower(Settings.POWER_PVP_TRANSACTION)
                // TODO make this number n0t mag1c numb3r
                HookManager.withdraw(player, 10.0)
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
            val player = PlayerManager.fromBukkitPlayer(event.player)
            player.civilization?.home?.let { event.respawnLocation = it }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onToolUse(event: PlayerInteractEvent) {
        val player = event.player
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type != Settings.CLAIM_TOOL) return
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (event.action == Action.LEFT_CLICK_BLOCK) {
                val block = event.clickedBlock!!
                civPlayer.selection.select(block, player, Selection.ClickType.LEFT)
                Common.tell(
                    player,
                    "&6Set first point to " + civPlayer.selection.primary!!.x.toString() + " " + civPlayer.selection.primary!!.z.toString()
                )
            }
            if (event.action == Action.RIGHT_CLICK_BLOCK) {
                val block = event.clickedBlock!!
                if (event.hand == EquipmentSlot.HAND) {
                    civPlayer.selection.select(block, player, Selection.ClickType.RIGHT)
                    Common.tell(
                        player,
                        "&6Set second point to " + civPlayer.selection.secondary!!.x.toString() + " " + civPlayer.selection.secondary!!.z.toString()
                    )
                }
            }
            event.isCancelled = true
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
                        !can(PermissionType.SWITCH, player, civilization) else {
                        event.isCancelled = !can(PermissionType.INTERACT, player, civilization)
                    }
                }
            } else {
                event.isCancelled = !can(PermissionType.INTERACT, player, civilization)
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
            PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
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
                    event.isCancelled = !can(PermissionType.BREAK, player, civilization)
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
            PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
                val civilization = getCivFromLocation(block.location) ?: return
                // if a player can attack (is in a raid currently and valid player proportions) then let him place tnt
                if (canAttackCivilization(civPlayer, civilization)) {
                    if (Settings.RAID_TNT_COOLDOWN != -1) {
                        if (block.type == Material.TNT) {
                            // make sure player doesnt have a tnt cooldown
                            if (hasCooldown(civPlayer.uuid, CooldownTask.CooldownType.TNT)) {
                                event.isCancelled = true
                                Common.tell(
                                    player,
                                    "Please wait " + getCooldownRemaining(
                                        civPlayer.uuid,
                                        CooldownTask.CooldownType.TNT
                                    ) + " settings before using TNT again"
                                )
                                return
                            }
                            addCooldownTimer(civPlayer.uuid, CooldownTask.CooldownType.TNT)
                            block.type = Material.AIR
                            CompMetadata.setMetadata(
                                block.world.spawnEntity(block.location, EntityType.PRIMED_TNT),
                                Constants.WAR_TNT_TAG,
                                player.uniqueId.toString()
                            )
                        }
                    }
                } else {
                    event.isCancelled = !can(PermissionType.BUILD, player, civilization)
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
                    if (!Common.callEvent(
                            CivEnterEvent(
                                civTo,
                                event.player
                            )
                        )
                    ) event.isCancelled = true
                } else if (civTo == null && civFrom != null) { // are we leaving the civ?
                    if (!Common.callEvent(
                            CivLeaveEvent(
                                civFrom,
                                event.player
                            )
                        )
                    ) event.isCancelled = true
                }
                val plotTo = getPlotFromLocation(event.to!!)
                val plotFrom = getPlotFromLocation(event.from)
                // are we entering a new plot
                if (plotTo != null && plotTo != plotFrom) {
                    if (!Common.callEvent(
                            PlotEnterEvent(
                                plotTo,
                                event.player
                            )
                        )
                    ) event.isCancelled = true
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
                if (!Common.callEvent(
                        CivEnterEvent(
                            civTo,
                            event.player
                        )
                    )
                ) event.isCancelled = true
            } else if (civTo == null && civFrom != null) { // are we leaving the civ?
                if (!Common.callEvent(
                        CivLeaveEvent(
                            civFrom,
                            event.player
                        )
                    )
                ) event.isCancelled = true
            }
            val plotTo = getPlotFromLocation(event.to!!)
            val plotFrom = getPlotFromLocation(event.from)
            // are we entering a new plot
            if (plotTo != null && plotTo != plotFrom) {
                if (!Common.callEvent(
                        PlotEnterEvent(
                            plotTo,
                            event.player
                        )
                    )
                ) event.isCancelled = true
            }
        } finally {
            LagCatcher.end("teleport")
        }
    }


    @EventHandler(priority = EventPriority.LOW)
    fun onPVP(event: EntityDamageByEntityEvent) {
        LagCatcher.start("pvp")
        try {
            val damaged = event.entity
            val damager = event.damager
            if (damaged !is Player || damager !is Player) return
            val civDamaged = PlayerManager.fromBukkitPlayer(damaged)
            val civDamager = PlayerManager.fromBukkitPlayer(damager)
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

    @EventHandler
    fun onPlayerDamageEntity(event: EntityDamageByEntityEvent) {
        LagCatcher.start("entity-damage-by-player")
        try {
            val damaged = event.entity
            val damager = event.damager
            if (damager !is Player || damaged is Player) return
            val civ = getCivFromLocation(damaged.location) ?: return
            event.isCancelled = !can(PermissionType.INTERACT, damager, civ)
        } finally {
            LagCatcher.end("entity-damage-by-player")
        }
    }

    @EventHandler
    fun onMap(event: MapInitializeEvent) {
        val map = event.map
        Common.log("${map.centerX} ${map.centerZ}")
        val region = Claim(
            map.id,
            Location(map.world, (map.centerX - 64).toDouble(), 1.0, (map.centerZ - 64).toDouble()), Location(
                map.world,
                (map.centerX + 64).toDouble(), 1.0, (map.centerZ + 64).toDouble()
            )
        )
        map.addRenderer(MapDrawer(region))
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val civPlayer = PlayerManager.fromBukkitPlayer(event.player)
        val civ = civPlayer.civilization
        civ?.let { theCiv ->
            if (theCiv.channel.players.contains(event.player)) {
                // TODO externalize format
                event.format =
                    "${Settings.PRIMARY_COLOR}[${Settings.SECONDARY_COLOR}${theCiv.name}${Settings.PRIMARY_COLOR}] ${Settings.SECONDARY_COLOR}${event.player.displayName}${Settings.PRIMARY_COLOR}:"
                for (player in Bukkit.getOnlinePlayers()) {
                    event.recipients.removeIf { !theCiv.citizens.contains(PlayerManager.fromBukkitPlayer(player)) }

                }
            }
        }
    }

}