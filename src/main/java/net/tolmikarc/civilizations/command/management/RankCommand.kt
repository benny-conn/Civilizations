/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.permissions.PermissionGroup
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class RankCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "rank") {
    override fun onCommand() {
        checkConsole()
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization
        checkNotNull(civ, Localization.Warnings.NO_CIV)
        civ?.apply {
            checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)

            when (args[0].toLowerCase()) {
                "set" -> {
                    if (args.size < 3) returnInvalidArgs()
                    val player = PlayerManager.getByName(args[1])
                    val rank = permissionGroups.getGroupByName(args[2])
                    checkNotNull(
                        player,
                        Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
                    )
                    checkBoolean(citizens.contains(player), Localization.Warnings.NOT_IN_CIV)
                    checkNotNull(
                        rank,
                        Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        ) + " ${Localization.OPTIONS}: ${
                            Common.join(
                                permissionGroups.groups.map { it.name },
                                ", "
                            )
                        }"
                    )
                    player?.let { p ->
                        rank?.let { g ->
                            permissionGroups.setPlayerGroup(p, g)
                        }
                    }
                }
                "new" -> {
                    if (args.size < 2) returnInvalidArgs()
                    permissionGroups.groups.add(PermissionGroup(args[1], HashSet()))
                }
                "delete" -> {
                    if (args.size < 2) returnInvalidArgs()
                    val group = permissionGroups.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    if (permissionGroups.defaultGroup == group || permissionGroups.outsiderGroup == group || permissionGroups.allyGroup == group || permissionGroups.enemyGroup == group) {
                        returnTell(Localization.Warnings.DELETE_DEFAULT)
                    }
                    permissionGroups.groups.remove(group)
                    permissionGroups.adminGroups.remove(group)
                    for (player in permissionGroups.playerGroupMap.keys) {
                        if (permissionGroups.playerGroupMap[player] == group)
                            permissionGroups.playerGroupMap[player] = permissionGroups.defaultGroup
                    }
                }
                "rename" -> {
                    if (args.size < 3) returnInvalidArgs()
                    val group = permissionGroups.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    if (permissionGroups.defaultGroup == group || permissionGroups.outsiderGroup == group || permissionGroups.allyGroup == group || permissionGroups.enemyGroup == group) {
                        returnTell(Localization.Warnings.RENAME_DEFAULT)
                    }
                    group?.name = args[2]
                }
            }
        }
    }

    override fun tabComplete(): List<String>? {
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
        usage = "<set | new | delete | rename> [rank | player] [rank | name]"
        if (Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}