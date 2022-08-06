/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations

import io.bennyc.civilizations.constants.Permissions
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.model.Plot
import io.bennyc.civilizations.permissions.PermissionType
import io.bennyc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.entity.Player


object PermissionChecker {

    fun can(permType: PermissionType, player: Player, civilization: Civilization): Boolean {
        val claimPermissions = civilization.permissions
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        if (canBypass(permType, civPlayer)) {
            return true
        }
        val permissionGroup = claimPermissions.getPlayerGroup(civPlayer)
        
        if (io.bennyc.civilizations.settings.Settings.OUTLAW_PERMISSIONS_DISABLED) if (civilization.isPlayerOutlaw(
                civPlayer
            )
        ) return false
        if (civilization.leader == civPlayer) {
            return true
        }
        return if (ClaimUtil.getPlotFromLocation(player.location, civilization) != null) {
            val plot = ClaimUtil.getPlotFromLocation(player.location, civilization)!!
            plot.members.contains(civPlayer)
        } else {
            permissionGroup.permissions.contains(permType)
        }
    }

    private fun canBypass(permType: PermissionType, player: io.bennyc.civilizations.model.CivPlayer): Boolean {
        if (isAdmin(player)) {
            return true
        }
        val bukkitPlayer = Bukkit.getPlayer(player.uuid) ?: return false
        return when (permType) {
            PermissionType.BUILD -> bukkitPlayer.hasPermission(Permissions.Bypass.BUILD)
            PermissionType.BREAK -> bukkitPlayer.hasPermission(Permissions.Bypass.BREAK)
            PermissionType.SWITCH -> bukkitPlayer.hasPermission(Permissions.Bypass.SWITCH)
            PermissionType.INTERACT -> bukkitPlayer.hasPermission(Permissions.Bypass.INTERACT)
        }
    }

    fun isAdmin(player: io.bennyc.civilizations.model.CivPlayer): Boolean {
        val bukkitPlayer = Bukkit.getPlayer(player.uuid) ?: return false
        return bukkitPlayer.hasPermission(Permissions.ADMIN)
    }

    fun canManageCiv(
        player: io.bennyc.civilizations.model.CivPlayer,
        civilization: Civilization
    ): Boolean {
        return when {
            isAdmin(player) -> true
            player == civilization.leader -> true
            else -> false
        }
    }

    fun canManagePlot(
        civ: Civilization,
        plot: Plot,
        player: io.bennyc.civilizations.model.CivPlayer
    ): Boolean {
        return if (canManageCiv(player, civ)) true else plot.owner == player
    }


    fun isSwitchable(type: Material): Boolean {
        return Tag.DOORS.isTagged(type) ||
                Tag.SHULKER_BOXES.isTagged(type) ||
                Tag.BUTTONS.isTagged(type) ||
                Tag.FENCE_GATES.isTagged(type) ||
                Tag.TRAPDOORS.isTagged(type) ||
                type == Material.LEVER ||
                type == Material.CHEST ||
                type == Material.TRAPPED_CHEST ||
                type == Material.CHEST_MINECART ||
                io.bennyc.civilizations.settings.Settings.SWITCHABLES.contains(type)
    }
}