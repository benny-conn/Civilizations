package net.tolmikarc.civilizations.util

import lombok.experimental.UtilityClass
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.model.Plot
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimPermissions.PermType
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.entity.Player

@UtilityClass
object PermissionUtil {
    fun can(permType: PermType, player: Player, civilization: Civilization): Boolean {
        val claimPermissions = civilization.claimPermissions
        val civPlayer = CivPlayer.fromBukkitPlayer(player)
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

    fun isAdmin(player: CivPlayer): Boolean {
        return if (Bukkit.getPlayer(player.playerUUID) != null) Bukkit.getPlayer(player.playerUUID)!!
            .hasPermission("civilizations.admin") else false
    }

    fun canManageCiv(player: CivPlayer, civilization: Civilization): Boolean {
        return if (isAdmin(player)) true else civilization.officials.contains(player) || civilization.leader == player
    }

    fun canManagePlot(civ: Civilization, plot: Plot, player: CivPlayer): Boolean {
        return if (canManageCiv(player, civ)) true else plot.owner != null && plot.owner == player
    }
}