/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.listener

import io.bennyc.civilizations.PermissionChecker.can
import io.bennyc.civilizations.model.Selection
import io.bennyc.civilizations.permissions.PermissionType
import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import io.bennyc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import io.bennyc.civilizations.task.CooldownTask.Companion.hasCooldown
import io.bennyc.civilizations.util.ClaimUtil.getCivFromLocation
import io.bennyc.civilizations.util.ClaimUtil.getPlotFromLocation
import org.bukkit.*
import org.bukkit.entity.EntityType
import org.bukkit.entity.Monster
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
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.debug.LagCatcher
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.remain.CompMetadata
import java.text.DecimalFormat

class PlayerListener : Listener {
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        civPlayer.playerName = player.name
        io.bennyc.civilizations.manager.PlayerManager.saveAsync(civPlayer)
        if (civPlayer.civilization != null) {
            val civ = civPlayer.civilization!!
            io.bennyc.civilizations.manager.CivManager.saveAsync(civ)
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val player = event.player
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            io.bennyc.civilizations.manager.PlayerManager.saveAsync(civPlayer)
            if (civPlayer.civilization != null) {
                val civ = civPlayer.civilization!!
                io.bennyc.civilizations.manager.CivManager.saveAsync(civ)
            }
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val killer = player.killer
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (civPlayer.civilization == null) return
            val civilization = civPlayer.civilization!!
            civilization.raid?.let { raid ->

                // make sure player is involved in the raid
                if (!raid.playersInvolved.containsKey(civPlayer)) return

                val playerLives = raid.playersInvolved[civPlayer]!! - 1
                raid.playersInvolved[civPlayer] = playerLives
                // if they have no lives left let them know
                if (raid.playersInvolved[civPlayer]!! <= 0) {
                    Messenger.warn(
                        player,
                        io.bennyc.civilizations.settings.Localization.Warnings.Raid.NO_LIVES
                    )
                    return
                }


                // give player spawn protection if they died during a raid
                addCooldownTimer(civPlayer, CooldownTask.CooldownType.RESPAWN_PROTECTION)
                Messenger.warn(
                    player,
                    io.bennyc.civilizations.settings.Localization.Warnings.Raid.DEATH_COST.replace(
                        "{cost}", io.bennyc.civilizations.settings.Settings.MONEY_PVP_TRANSACTION.toString().format(
                            DecimalFormat.getCurrencyInstance()
                        )
                    )
                        .replace("{power}", io.bennyc.civilizations.settings.Settings.POWER_PVP_TRANSACTION.toString())
                        .replace("{lives}", playerLives.toString())
                )
                Messenger.success(
                    killer,
                    "You earned ${
                        io.bennyc.civilizations.settings.Settings.MONEY_PVP_TRANSACTION.toString().format(DecimalFormat.getCurrencyInstance())
                    } and ${io.bennyc.civilizations.settings.Settings.POWER_PVP_TRANSACTION} power for killing ${player.name}."
                )
                killer?.let { killer ->
                    io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(killer).addPower(io.bennyc.civilizations.settings.Settings.POWER_PVP_TRANSACTION)
                    HookManager.deposit(killer, io.bennyc.civilizations.settings.Settings.MONEY_PVP_TRANSACTION)
                }
                civPlayer.removePower(io.bennyc.civilizations.settings.Settings.POWER_PVP_TRANSACTION)
                HookManager.withdraw(player, io.bennyc.civilizations.settings.Settings.MONEY_PVP_TRANSACTION)
            }

        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        if (io.bennyc.civilizations.settings.Settings.RESPAWN_CIV) {
            val player = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(event.player)
            player.civilization?.let {
                it.home?.let { home -> event.respawnLocation = home }
                if (it.isInRaid() && player.lastLocationBeforeRaid != null)
                    event.respawnLocation = player.lastLocationBeforeRaid!!
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onToolUse(event: PlayerInteractEvent) {
        val player = event.player
        val itemInHand = player.inventory.itemInMainHand
        if (itemInHand.type != io.bennyc.civilizations.settings.Settings.CLAIM_TOOL) return
        if (!event.hasBlock()) return
        val block = event.clickedBlock!!
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (io.bennyc.civilizations.settings.Settings.DUAL_CLICK_CLAIM) {
                if (event.action == Action.LEFT_CLICK_BLOCK) {
                    civPlayer.selection.select(block, player, Selection.ClickType.LEFT)
                    Messenger.success(
                        player,
                        io.bennyc.civilizations.settings.Localization.Notifications.SELECT_PRIMARY.replace(
                            "{x}",
                            civPlayer.selection.primary!!.x.toString()
                        )
                            .replace("{z}", civPlayer.selection.primary!!.z.toString())
                    )
                }
                if (event.action == Action.RIGHT_CLICK_BLOCK) {
                    if (event.hand == EquipmentSlot.HAND) {
                        civPlayer.selection.select(block, player, Selection.ClickType.RIGHT)
                        Messenger.success(
                            player,
                            io.bennyc.civilizations.settings.Localization.Notifications.SELECT_SECONDARY.replace(
                                "{x}",
                                civPlayer.selection.secondary!!.x.toString()
                            ).replace("{z}", civPlayer.selection.secondary!!.z.toString())
                        )
                    }
                }
            } else {
                val selectionType = civPlayer.selection.selectNoClickType(block, player)
                if (selectionType == Selection.SelectionType.PRIMARY) {
                    Messenger.success(
                        player,
                        io.bennyc.civilizations.settings.Localization.Notifications.SELECT_PRIMARY.replace(
                            "{x}",
                            civPlayer.selection.primary!!.x.toString()
                        ).replace("{z}", civPlayer.selection.primary!!.z.toString())
                    )
                } else if (selectionType == Selection.SelectionType.SECONDARY) {
                    Messenger.success(
                        player,
                        io.bennyc.civilizations.settings.Localization.Notifications.SELECT_SECONDARY.replace(
                            "{x}",
                            civPlayer.selection.secondary!!.x.toString()
                        ).replace("{z}", civPlayer.selection.secondary!!.z.toString())
                    )
                }
            }
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onInteract(event: PlayerInteractEvent) {
        LagCatcher.start("use")
        try {
            if (event.action == Action.LEFT_CLICK_AIR) return
            if (event.action == Action.LEFT_CLICK_BLOCK) return
            val player = event.player
            val civilization = getCivFromLocation(player.location) ?: return
            val block = event.clickedBlock
            if (block != null) {
                val type = block.type
                if (!type.isInteractable) return
                if (Tag.SIGNS.isTagged(type)) return
                if (type == Material.ENCHANTING_TABLE) return
                if (io.bennyc.civilizations.PermissionChecker.isSwitchable(type)) {
                    event.isCancelled = !can(PermissionType.SWITCH, player, civilization)
                } else {
                    event.isCancelled = !can(PermissionType.INTERACT, player, civilization)
                }
            } else if (event.item != null) {
                if (player.inventory.itemInMainHand.type == Material.FIREWORK_ROCKET) return
                if (player.inventory.itemInMainHand.type.isEdible) return
                event.isCancelled = !can(PermissionType.INTERACT, player, civilization)
            }
            if (event.isCancelled) {
                Common.tell(player, io.bennyc.civilizations.settings.Localization.Warnings.INTERACT)
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
            io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
                val block = event.block
                val civilization = getCivFromLocation(block.location) ?: return
                // if a player is raiding he can break
                if (civilization.canAttackCivilization(civPlayer)) {
                    // except player cannot break things that are defined in settings so long as the settings says player cant
                    if (!io.bennyc.civilizations.settings.Settings.RAID_BREAK_SWITCHABLES) if (io.bennyc.civilizations.PermissionChecker.isSwitchable(block.type)) {
                        event.isCancelled = true
                        return
                    }
                    civPlayer.civilization?.let { playerCivilization ->
                        civilization.addDamages(playerCivilization, block)
                        civPlayer.addRaidBlocksDestroyed(1)
                    }
                } else {
                    event.isCancelled = !can(PermissionType.BREAK, player, civilization)
                    if (event.isCancelled)
                        Common.tell(player, io.bennyc.civilizations.settings.Localization.Warnings.BREAK)
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
            io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->


                // inform players that their chests are vulnerable!
                if (block.type == Material.CHEST)
                    if (io.bennyc.civilizations.settings.Settings.TUTORIAL)
                        if (player.getStatistic(Statistic.CHEST_OPENED) <= 1)
                            if (civPlayer.civilization == null)
                                Common.tell(player, io.bennyc.civilizations.settings.Localization.Notifications.TUTORIAL)


                val civilization = getCivFromLocation(block.location) ?: return
                // if a player can attack (is in a raid currently and valid player proportions) then let him place tnt
                if (civilization.canAttackCivilization(civPlayer)) {
                    if (io.bennyc.civilizations.settings.Settings.RAID_TNT_COOLDOWN != -1) {
                        if (block.type == Material.TNT) {
                            // make sure player doesnt have a tnt cooldown
                            if (hasCooldown(civPlayer, CooldownTask.CooldownType.TNT)) {
                                Common.tell(
                                    player,
                                    io.bennyc.civilizations.settings.Localization.Warnings.COOLDOWN_WAIT.replace(
                                        "{duration}",
                                        getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TNT).toString()
                                    )
                                )
                                event.isCancelled = true
                                return
                            }
                            addCooldownTimer(civPlayer, CooldownTask.CooldownType.TNT)
                            block.type = Material.AIR
                            CompMetadata.setMetadata(
                                block.world.spawnEntity(block.location, EntityType.PRIMED_TNT),
                                io.bennyc.civilizations.constants.Constants.WAR_TNT_TAG,
                                player.uniqueId.toString()
                            )
                        }
                    }
                } else {
                    event.isCancelled = !can(PermissionType.BUILD, player, civilization)
                    if (event.isCancelled) Common.tell(player, io.bennyc.civilizations.settings.Localization.Warnings.BUILD)
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

            if (from.blockX == to.blockX && from.blockZ == to.blockZ) {
                // we did not move an entire block, don't do anything
                return
            }
            
            val civTo = getCivFromLocation(to.block.location)
            val civFrom = getCivFromLocation(from.block.location)
            // make sure some civilization is involved, else what would this code be for?
            if (civTo == null && civFrom == null) return
            // are we entering a new civ?
            if (civTo != null && civTo != civFrom) {
                if (!Common.callEvent(
                        io.bennyc.civilizations.event.CivEnterEvent(
                            civTo,
                            event.player,
                            from, to
                        )
                    )
                ) event.isCancelled = true
            } else if (civTo == null && civFrom != null) { // are we leaving the civ?
                if (!Common.callEvent(
                        io.bennyc.civilizations.event.CivLeaveEvent(
                            civFrom,
                            event.player,
                            from, to
                        )
                    )
                ) event.isCancelled = true
            }
            val plotTo = getPlotFromLocation(event.to!!)
            val plotFrom = getPlotFromLocation(event.from)
            // are we entering a new plot
            if (plotTo != null && plotTo != plotFrom) {
                if (!Common.callEvent(
                        io.bennyc.civilizations.event.PlotEnterEvent(
                            plotTo,
                            event.player,
                            from, to
                        )
                    )
                ) event.isCancelled = true
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
                        io.bennyc.civilizations.event.CivEnterEvent(
                            civTo,
                            event.player,
                            from, to
                        )
                    )
                ) event.isCancelled = true
            } else if (civTo == null && civFrom != null) { // are we leaving the civ?
                if (!Common.callEvent(
                        io.bennyc.civilizations.event.CivLeaveEvent(
                            civFrom,
                            event.player,
                            from, to
                        )
                    )
                ) event.isCancelled = true
            }
            val plotTo = getPlotFromLocation(event.to!!)
            val plotFrom = getPlotFromLocation(event.from)
            // are we entering a new plot
            if (plotTo != null && plotTo != plotFrom) {
                if (!Common.callEvent(
                        io.bennyc.civilizations.event.PlotEnterEvent(
                            plotTo,
                            event.player,
                            from, to
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
        val damaged = event.entity
        val damager = event.damager
        try {
            if (damaged !is Player || damager !is Player) return
            val civDamaged = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(damaged)
            // check if player has spawn protection
            if (hasCooldown(civDamaged, CooldownTask.CooldownType.RESPAWN_PROTECTION)) {
                event.isCancelled = true
                return
            }
            val civDamager = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(damager)
            val location = damaged.getLocation()
            val civilization = getCivFromLocation(location) ?: return
            if (civilization.canAttackCivilization(civDamager) && civilization.canAttackCivilization(civDamaged)) {
                event.isCancelled = hasCooldown(civDamaged, CooldownTask.CooldownType.RESPAWN_PROTECTION)
                if (!event.isCancelled) {
                    // prevent pvplogging
                    if (io.bennyc.civilizations.settings.Settings.RAID_PVP_TP_COOLDOWN) addCooldownTimer(
                        civDamaged,
                        CooldownTask.CooldownType.TELEPORT
                    )
                }
                return
            }
            val plot = getPlotFromLocation(location, civilization)
            if (plot != null) {
                if (!plot.toggleables.pvp) event.isCancelled = true
                return
            }
            event.isCancelled = !civilization.toggleables.pvp
        } finally {
            LagCatcher.end("pvp")
            if (event.isCancelled) {
                Common.tell(damager, io.bennyc.civilizations.settings.Localization.Warnings.PVP)
            }
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
            if (damaged is Monster) {
                val plot = getPlotFromLocation(damaged.location, civ)
                if (plot != null) {
                    if (!plot.toggleables.mobs)
                        damaged.remove()
                } else {
                    if (!civ.toggleables.mobs)
                        damaged.remove()
                }
            }
            event.isCancelled = !can(PermissionType.INTERACT, damager, civ)
        } finally {
            LagCatcher.end("entity-damage-by-player")
        }
    }

    @EventHandler
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(event.player)
        val civ = civPlayer.civilization
        civ?.let { theCivilization ->
            if (theCivilization.channel.players.contains(event.player)) {
                event.format =
                    "{1}[{2}${theCivilization.name}{1}] {2}${event.player.displayName}{1}:"
                for (player in Bukkit.getOnlinePlayers()) {
                    event.recipients.removeIf { !theCivilization.citizens.contains(io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)) }
                }
                event.recipients.add(event.player)
            }
        }
    }

}