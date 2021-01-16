/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
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
                    val rank = ranks.getGroupByName(args[2])
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
                                ranks.ranks.map { it.name },
                                ", "
                            )
                        }"
                    )
                    player?.let { p ->
                        rank?.let { g ->
                            ranks.setPlayerGroup(p, g)
                        }
                    }
                }
                "new" -> {
                    if (args.size < 2) returnInvalidArgs()
                    ranks.ranks.add(Rank(args[1], HashSet()))
                }
                "delete" -> {
                    if (args.size < 2) returnInvalidArgs()
                    val group = ranks.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    if (ranks.defaultRank == group || ranks.outsiderRank == group || ranks.allyRank == group || ranks.enemyRank == group) {
                        returnTell(Localization.Warnings.DELETE_DEFAULT)
                    }
                    ranks.ranks.remove(group)
                    ranks.adminGroups.remove(group)
                    for (player in ranks.playerGroupMap.keys) {
                        if (ranks.playerGroupMap[player] == group)
                            ranks.playerGroupMap[player] = ranks.defaultRank
                    }
                }
                "rename" -> {
                    if (args.size < 3) returnInvalidArgs()
                    val group = ranks.getGroupByName(args[1])
                    checkNotNull(group, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "rank"))
                    if (ranks.defaultRank == group || ranks.outsiderRank == group || ranks.allyRank == group || ranks.enemyRank == group) {
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
                    civ?.ranks?.ranks?.map { it.name }?.toList() ?: super.tabComplete()
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