/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class TopCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "top") {
    override fun onCommand() {
        checkConsole()
        if (!args[0].equals("power", ignoreCase = true) &&
            !args[0].equals("balance", ignoreCase = true) &&
            !args[0].equals("citizens", ignoreCase = true) &&
            !args[0].equals("land", ignoreCase = true) &&
            !args[0].equals("players", ignoreCase = true)
        ) {
            returnInvalidArgs()
        }
        val page = if (args.size > 1) args[1].toInt() else 1
        if (!args[0].equals("players", ignoreCase = true)) {
            AsyncEnvironment.run {
                val civilizationsSorted = ArrayList(CivManager.all).sortedBy {
                    return@sortedBy when (args[0]) {
                        "power" -> it.power
                        "balance" -> it.bank.balance.toInt()
                        "land" -> it.totalBlocksCount
                        "citizens" -> it.citizenCount
                        else -> it.power
                    }
                }
                val lowerLimit = (page * 10) - 9
                val upperLimit = page * 10
                tellNoPrefix("${Settings.PRIMARY_COLOR}======= ${Settings.SECONDARY_COLOR}Top Civs: ${args[0].capitalize()} ($page) ${Settings.PRIMARY_COLOR}=======")
                when (args[0]) {
                    "power" -> {
                        for (i in lowerLimit..(upperLimit + 1)) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i].name}: ${civilizationsSorted[i].power} ")
                        }
                    }
                    "balance" -> {
                        for (i in lowerLimit..(upperLimit + 1)) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i].name}: ${civilizationsSorted[i].bank.balance} ")
                        }
                    }
                    "land" -> {
                        for (i in lowerLimit..(upperLimit + 1)) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i].name}: ${civilizationsSorted[i].totalBlocksCount} ")
                        }
                    }
                    "citizens" -> {
                        for (i in lowerLimit..(upperLimit + 1)) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i].name}: ${civilizationsSorted[i].citizenCount} ")
                        }
                    }
                    else -> {
                        for (i in lowerLimit..(upperLimit + 1)) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i].name}: ${civilizationsSorted[i].power} ")
                        }
                    }
                }
            }
        } else {
            AsyncEnvironment.run {
                val topPlayers = ArrayList(PlayerManager.cacheMap.values).sortedBy { it.power }
                val lowerLimit = (page * 10) - 9
                val upperLimit = page * 10
                tellNoPrefix("${Settings.PRIMARY_COLOR}======= ${Settings.SECONDARY_COLOR}Top Players ($page) ${Settings.PRIMARY_COLOR}=======")
                for (i in lowerLimit..(upperLimit + 1)) {
                    if (i >= topPlayers.size)
                        break
                    tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${topPlayers[i].playerName}: ${topPlayers[i].power} ")
                }
            }
        }
    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 1) listOf("power", "citizens", "land", "balance", "players") else null
    }

    init {
        usage = "<power | citizens | money | land | players> [Page #]"
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}