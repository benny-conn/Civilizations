/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.permissions.Rank
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ARankCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "rank") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(
            civ,
            io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                "{item}",
                io.bennyc.civilizations.settings.Localization.CIVILIZATION
            )
        )
        civ?.apply {
            when (args[1].toLowerCase()) {
                "list" -> {
                    tellInfo(Common.join(civ.permissions.allRankNames, ", "))
                }

                "set" -> {
                    checkArgs(
                        4,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            io.bennyc.civilizations.settings.Localization.PLAYER
                        )
                    )
                    val player = io.bennyc.civilizations.manager.PlayerManager.getByName(args[2])
                    val rank = permissions.getGroupByName(args[3])
                    checkNotNull(
                        player,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            io.bennyc.civilizations.settings.Localization.PLAYER
                        )
                    )
                    checkBoolean(
                        citizens.contains(player),
                        io.bennyc.civilizations.settings.Localization.Warnings.NOT_IN_CIV
                    )
                    checkNotNull(
                        rank,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        ) + " ${io.bennyc.civilizations.settings.Localization.OPTIONS}: ${
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
                }

                "new" -> {
                    checkArgs(
                        2,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "name"
                        )
                    )
                    permissions.ranks.add(Rank(args[1], HashSet()))
                }

                "delete" -> {
                    checkArgs(
                        2,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        )
                    )
                    val group = permissions.getGroupByName(args[1])
                    checkNotNull(
                        group,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        )
                    )
                    if (permissions.defaultRank == group || permissions.outsiderRank == group || permissions.allyRank == group || permissions.enemyRank == group) {
                        returnTell(io.bennyc.civilizations.settings.Localization.Warnings.DELETE_DEFAULT)
                    }
                    permissions.ranks.remove(group)
                    for (player in permissions.playerGroupMap.keys) {
                        if (permissions.playerGroupMap[player] == group)
                            permissions.playerGroupMap[player] = permissions.defaultRank
                    }
                }

                "edit" -> {
                    if (args.size < 3) returnInvalidArgs()
                    val group = permissions.getGroupByName(args[1])
                    checkNotNull(
                        group,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        )
                    )
                    io.bennyc.civilizations.menu.RankMenu(group!!, civ, null).displayTo(player)
                }

                "rename" -> {
                    checkArgs(
                        3,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        )
                    )
                    val group = permissions.getGroupByName(args[1])
                    checkNotNull(
                        group,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            "rank"
                        )
                    )
                    if (permissions.defaultRank == group || permissions.outsiderRank == group || permissions.allyRank == group || permissions.enemyRank == group) {
                        returnTell(io.bennyc.civilizations.settings.Localization.Warnings.RENAME_DEFAULT)
                    }
                    group?.name = args[2]
                }
            }
            tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
        }
    }

    override fun tabComplete(): List<String>? {
        return when (args.size) {
            2 -> listOf("set", "new", "delete", "rename", "list")
            else -> super.tabComplete()
        }

    }

    init {
        minArguments = 2
        setDescription("Assign ranks to players and create new custom ranks")
        usage = "<set | new | delete | rename> [...]"
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}