/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.permissions.PermissionGroup
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class RankCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "rank") {
    override fun onCommand() {
        checkConsole()
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, "You must have a civ to use this command.")
        checkBoolean(
            PermissionChecker.canManageCiv(civPlayer, civ!!),
            "You are not permitted to set the ranks of this civ."
        )

        when (args[0].toLowerCase()) {
            "set" -> {
                checkArgs(3, "Please specify a player's name and a rank to apply to them")
                val player = PlayerManager.getByName(args[1])
                val rank = civ.permissionGroups.getGroupByName(args[2])
                checkNotNull(player, "Please specify a valid player")
                checkNotNull(
                    rank,
                    "Please specify a valid rank. Options: ${
                        Common.join(
                            civ.permissionGroups.groups.map { it.name },
                            ", "
                        )
                    }"
                )
                player?.let { p ->
                    rank?.let { g ->
                        civ.permissionGroups.setPlayerGroup(p, g)
                    }
                }
            }
            "new" -> {
                checkArgs(2, "Please specify a name for the new rank.")
                civ.permissionGroups.groups.add(PermissionGroup(args[1], HashSet()))
            }
            "delete" -> {
                checkArgs(2, "Please specify a group to delete")
                val group = civ.permissionGroups.getGroupByName(args[1])
                checkNotNull(group, "Please specify a valid group to delete")
                if (civ.permissionGroups.defaultGroup == group || civ.permissionGroups.outsiderGroup == group || civ.permissionGroups.allyGroup == group || civ.permissionGroups.enemyGroup == group) {
                    returnTell("You cannot delete a default group")
                }
                civ.permissionGroups.groups.remove(group)
                civ.permissionGroups.adminGroups.remove(group)
                for (player in civ.permissionGroups.playerGroupMap.keys) {
                    if (civ.permissionGroups.playerGroupMap[player] == group)
                        civ.permissionGroups.playerGroupMap[player] = civ.permissionGroups.defaultGroup
                }
            }
            "rename" -> {
                checkArgs(3, "Please specify a group to rename and the name to assign")
                val group = civ.permissionGroups.getGroupByName(args[1])
                checkNotNull(group, "Please specify a valid group to rename")
                if (civ.permissionGroups.defaultGroup == group || civ.permissionGroups.outsiderGroup == group || civ.permissionGroups.allyGroup == group || civ.permissionGroups.enemyGroup == group) {
                    returnTell("You cannot rename a default group")
                }
                group?.name = args[2]
            }
        }
    }

    override fun tabComplete(): List<String> {
        val civ = PlayerManager.fromBukkitPlayer(player).civilization
        return when (args.size) {
            1 -> listOf("set", "new", "delete", "rename")
            2 -> {
                if (args[0].equals("delete", true) || args[0].equals("rename", true))
                    civ?.permissionGroups?.groups?.map { it.name }?.toList() ?: super.tabComplete()
                else super.tabComplete()
            }
            else -> super.tabComplete()
        }

    }

    init {
        minArguments = 1
        setDescription("Assign ranks to players and create new custom ranks")
        usage = "<set | new | delete | rename> [...]"
        if (Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}