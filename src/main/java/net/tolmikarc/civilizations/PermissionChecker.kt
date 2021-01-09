/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations

import lombok.experimental.UtilityClass
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Plot
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.ClaimUtil
import org.bukkit.Bukkit
import org.bukkit.entity.Player

@UtilityClass
object PermissionChecker {

    fun can(permType: PermissionType, player: Player, civilization: Civ): Boolean {
        val claimPermissions = civilization.permissionGroups
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        if (isAdmin(civPlayer)) return true
        val permissionGroup = claimPermissions.getPlayerGroup(civPlayer)
        if (claimPermissions.adminGroups.contains(permissionGroup)) return true
        if (Settings.OUTLAW_PERMISSIONS_DISABLED) if (CivUtil.isPlayerOutlaw(civPlayer, civilization)) return false
        if (civilization.leader == civPlayer) return true
        return if (ClaimUtil.getPlotFromLocation(player.location, civilization) != null) {
            val plot = ClaimUtil.getPlotFromLocation(player.location, civilization)!!
            plot.members.contains(civPlayer)
        } else {
            permissionGroup.permissions.contains(permType)
        }
    }

    fun isAdmin(player: CPlayer): Boolean {
        return if (Bukkit.getPlayer(player.uuid) != null) Bukkit.getPlayer(player.uuid)!!
            .hasPermission("civilizations.admin") else false
    }

    fun canManageCiv(player: CPlayer, civilization: Civ): Boolean {
        return if (isAdmin(player)) true else civilization.permissionGroups.adminGroups.contains(
            civilization.permissionGroups.getPlayerGroup(
                player
            )
        ) || civilization.leader == player
    }

    fun canManagePlot(civ: Civ, plot: Plot, player: CPlayer): Boolean {
        return if (canManageCiv(player, civ)) true else plot.owner == player
    }
}