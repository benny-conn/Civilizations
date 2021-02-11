/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.conversation.RankCreationConversation
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.RankMenu
import net.tolmikarc.civilizations.permissions.Rank
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
                    val rank = permissions.getGroupByName(args[2])
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
                                permissions.ranks.map { it.name },
                                ", "
                            )
                        }"
                    )
                    player?.let { p ->
                        rank?.let { g ->
                            permissions.setPlayerGroup(p, g)
                        }
                    }
                    tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                }
                "new" -> {
                    if (args.size < 2) {
                        RankCreationConversation(civ, player).start(player)
                        return
                    }
                    permissions.ranks.add(Rank(args[1], HashSet()))
                    tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                }
                "delete" -> {
                    if (args.size < 2) returnInvalidArgs()
                    val group = permissions.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    if (permissions.defaultRank == group || permissions.outsiderRank == group || permissions.allyRank == group || permissions.enemyRank == group) {
                        returnTell(Localization.Warnings.DELETE_DEFAULT)
                    }
                    permissions.ranks.remove(group)
                    permissions.adminGroups.remove(group)
                    for (player in permissions.playerGroupMap.keys) {
                        if (permissions.playerGroupMap[player] == group)
                            permissions.playerGroupMap[player] = permissions.defaultRank
                    }
                    tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                }
                "rename" -> {
                    if (args.size < 3) returnInvalidArgs()
                    val group = permissions.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    if (permissions.defaultRank == group || permissions.outsiderRank == group || permissions.allyRank == group || permissions.enemyRank == group) {
                        returnTell(Localization.Warnings.RENAME_DEFAULT)
                    }
                    group?.name = args[2]
                    tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
                }
                "edit" -> {
                    if (args.size < 3) returnInvalidArgs()
                    val group = permissions.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    RankMenu(group!!, civ, null).displayTo(player)
                }
                "list" -> {
                    tellInfo(Common.join(civ.permissions.allRankNames, ", "))
                }
            }
        }
    }

    override fun tabComplete(): List<String>? {
        val civ = PlayerManager.fromBukkitPlayer(player).civilization
        return when (args.size) {
            1 -> listOf("set", "new", "delete", "rename", "list")
            2 -> {
                if (args[0].equals("delete", true) || args[0].equals("rename", true))
                    civ?.permissions?.ranks?.map { it.name }?.toList() ?: super.tabComplete()
                else super.tabComplete()
            }
            else -> super.tabComplete()
        }

    }

    init {
        minArguments = 1
        setDescription("Assign ranks to players and create new custom ranks")
        usage = "<set | new | delete | rename | list> [rank | player] [rank | name]"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}