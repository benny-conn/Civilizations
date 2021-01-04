/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class InfoCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "info") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            val civilization: Civilization?
            if (args.isNotEmpty()) {
                civilization = Civilization.fromName(args[0])
                checkNotNull(civilization, "Please enter a valid Civilization")
            } else {
                checkNotNull(
                    civPlayer.civilization,
                    "You must have a civ to use this command without defining another Civilization"
                )
                civilization = civPlayer.civilization
            }
            civilization?.run { sendInfo(this) }
        }
    }

    private fun sendInfo(civilization: Civilization) {
        val permissions = civilization.claimPermissions
        val toggleables = civilization.claimToggleables
        val citizenNames: MutableList<String?> = ArrayList()
        val officialNames: MutableList<String?> = ArrayList()
        val canBuild: MutableList<String> = ArrayList()
        val canBreak: MutableList<String> = ArrayList()
        val canSwitch: MutableList<String> = ArrayList()
        val canInteract: MutableList<String> = ArrayList()
        val enemies: MutableList<String?> = ArrayList()
        val allies: MutableList<String?> = ArrayList()
        val outlaws: MutableList<String?> = ArrayList()
        for (player in civilization.citizens) {
            citizenNames.add(player.playerName)
        }
        for (player in civilization.officials) {
            officialNames.add(player.playerName)
        }
        for (enemy in civilization.enemies) {
            enemies.add(enemy.name)
        }
        for (ally in civilization.allies) {
            allies.add(ally.name)
        }
        for (outlaw in civilization.outlaws) {
            outlaws.add(outlaw.playerName)
        }
        if (permissions.permissions[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.BUILD.id]) canBuild.add(
            "Member"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.BUILD.id]) canBuild.add(
            "Ally"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.BUILD.id]) canBuild.add(
            "Official"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.BUILD.id]) canBuild.add(
            "Outsider"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.BREAK.id]) canBreak.add(
            "Member"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.BREAK.id]) canBreak.add(
            "Ally"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.BREAK.id]) canBreak.add(
            "Official"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.BREAK.id]) canBreak.add(
            "Outsider"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.SWITCH.id]) canSwitch.add(
            "Member"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.SWITCH.id]) canSwitch.add(
            "Ally"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.SWITCH.id]) canSwitch.add(
            "Official"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.SWITCH.id]) canSwitch.add(
            "Outsider"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.INTERACT.id]) canInteract.add(
            "Member"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.INTERACT.id]) canInteract.add(
            "Ally"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.INTERACT.id]) canInteract.add(
            "Official"
        )
        if (permissions.permissions[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.INTERACT.id]) canInteract.add(
            "Outsider"
        )
        if (officialNames.isEmpty()) officialNames.add("None")
        tellNoPrefix(
            "${Settings.PRIMARY_COLOR}============ ${Settings.SECONDARY_COLOR}" + civilization.name + "${Settings.PRIMARY_COLOR} ============",
            "" + if (Settings.SHOW_COORDS_IN_INFO && civilization.home != null) "${Settings.PRIMARY_COLOR}Home: ${Settings.SECONDARY_COLOR}" + civilization.home!!.blockX + ", " + civilization.home!!.blockZ else "",
            "${Settings.PRIMARY_COLOR}Leader: ${Settings.SECONDARY_COLOR}" + (civilization.leader?.playerName
                ?: "None"),
            "${Settings.PRIMARY_COLOR}Officials: ${Settings.SECONDARY_COLOR}" + Common.join(
                officialNames,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Citizens: ${Settings.SECONDARY_COLOR}" + Common.join(
                citizenNames,
                ", "
            ), "${Settings.PRIMARY_COLOR}Power: ${Settings.SECONDARY_COLOR} ${civilization.power}",
            "${Settings.PRIMARY_COLOR}Balance: ${Settings.SECONDARY_COLOR} ${Settings.CURRENCY_SYMBOL}${civilization.bank.balance}",
            "${Settings.PRIMARY_COLOR}Total Blocks: ${Settings.SECONDARY_COLOR}" + civilization.totalBlocksCount,
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}PVP: ${Settings.SECONDARY_COLOR}" + toggleables.pvp,
            "${Settings.PRIMARY_COLOR}Mob Spawning: ${Settings.SECONDARY_COLOR}" + toggleables.mobs,
            "${Settings.PRIMARY_COLOR}Explosions: ${Settings.SECONDARY_COLOR}" + toggleables.explosion,
            "${Settings.PRIMARY_COLOR}Fire Spread: ${Settings.SECONDARY_COLOR}" + toggleables.fire,
            "${Settings.PRIMARY_COLOR}Public: ${Settings.SECONDARY_COLOR}" + toggleables.public,
            "${Settings.PRIMARY_COLOR}Invite Only: ${Settings.SECONDARY_COLOR}" + toggleables.inviteOnly,
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}Build: ${Settings.SECONDARY_COLOR}" + Common.join(
                canBuild,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Break: ${Settings.SECONDARY_COLOR}" + Common.join(
                canBreak,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Switch: ${Settings.SECONDARY_COLOR}" + Common.join(
                canSwitch,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Interact: ${Settings.SECONDARY_COLOR}" + Common.join(
                canInteract,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}Enemies: ${Settings.SECONDARY_COLOR}" + Common.join(
                enemies,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Allies: ${Settings.SECONDARY_COLOR}" + Common.join(
                allies,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Outlaws: ${Settings.SECONDARY_COLOR}" + Common.join(
                outlaws,
                ", "
            )
        )
    }

    init {
        usage = "[civilization]"
        setDescription("Get information on your or another Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}