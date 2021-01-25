/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations

import lombok.experimental.UtilityClass
import net.tolmikarc.civilizations.constants.Permissions
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Plot
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.entity.Player

@UtilityClass
object PermissionChecker {

    fun can(permType: PermissionType, player: Player, civilization: Civ): Boolean {
        val claimPermissions = civilization.permissions
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        if (canBypass(permType, civPlayer)) {
            return true
        }
        val permissionGroup = claimPermissions.getPlayerGroup(civPlayer)
        if (claimPermissions.adminGroups.contains(permissionGroup)) {
            return true
        }
        if (Settings.OUTLAW_PERMISSIONS_DISABLED) if (CivUtil.isPlayerOutlaw(civPlayer, civilization)) return false
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

    private fun canBypass(permType: PermissionType, player: CPlayer): Boolean {
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

    fun isAdmin(player: CPlayer): Boolean {
        val bukkitPlayer = Bukkit.getPlayer(player.uuid) ?: return false
        return bukkitPlayer.hasPermission(Permissions.ADMIN)
    }

    fun canManageCiv(player: CPlayer, civilization: Civ): Boolean {
        return when {
            isAdmin(player) -> return true
            player == civilization.leader -> return true
            else -> civilization.permissions.adminGroups.contains(
                civilization.permissions.getPlayerGroup(
                    player
                )
            )
        }
    }

    fun canManagePlot(civ: Civ, plot: Plot, player: CPlayer): Boolean {
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
                Settings.SWITCHABLES.contains(type)
    }
}