/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import lombok.experimental.UtilityClass
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.CivPlot
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimPermissions.PermType
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.entity.Player

@UtilityClass
object PermissionUtil {
    fun can(permType: PermType, player: Player, civilization: Civ): Boolean {
        val claimPermissions = civilization.claimPermissions
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        if (isAdmin(civPlayer)) return true
        if (Settings.OUTLAW_PERMISSIONS_DISABLED) if (CivUtil.isPlayerOutlaw(civPlayer, civilization)) return false
        if (civilization.leader == civPlayer) return true
        return if (ClaimUtil.getPlotFromLocation(player.location, civilization) != null) {
            val plot = ClaimUtil.getPlotFromLocation(player.location, civilization)!!
            val plotPermissions = plot.claimPermissions
            if (civilization.officials.contains(civPlayer)) return plotPermissions.permissions[ClaimPermissions.PermGroup.OFFICIAL.id][permType.id]
            if (plot.members.contains(civPlayer)) return plotPermissions.permissions[ClaimPermissions.PermGroup.MEMBER.id][permType.id]
            if (civilization.citizens.contains(civPlayer)) plotPermissions.permissions[ClaimPermissions.PermGroup.ALLY.id][permType.id] else plotPermissions.permissions[ClaimPermissions.PermGroup.OUTSIDER.id][permType.id]
        } else {
            if (civilization.officials.contains(civPlayer)) return claimPermissions.permissions[ClaimPermissions.PermGroup.OFFICIAL.id][permType.id]
            if (civilization.citizens.contains(civPlayer)) return claimPermissions.permissions[ClaimPermissions.PermGroup.MEMBER.id][permType.id]
            if (civPlayer.civilization != null) if (civilization.allies.contains(civPlayer.civilization)) return claimPermissions.permissions[ClaimPermissions.PermGroup.ALLY.id][permType.id]
            claimPermissions.permissions[ClaimPermissions.PermGroup.OUTSIDER.id][permType.id]
        }
    }

    fun isAdmin(player: CPlayer): Boolean {
        return if (Bukkit.getPlayer(player.uuid) != null) Bukkit.getPlayer(player.uuid)!!
            .hasPermission("civilizations.admin") else false
    }

    fun canManageCiv(player: CPlayer, civilization: Civ): Boolean {
        return if (isAdmin(player)) true else civilization.officials.contains(player) || civilization.leader == player
    }

    fun canManagePlot(civ: Civ, plot: CivPlot, player: CPlayer): Boolean {
        return if (canManageCiv(player, civ)) true else plot.owner == player
    }
}