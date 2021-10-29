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
import java.text.DecimalFormat

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
                val civilizationsSorted = ArrayList(CivManager.all).sortedByDescending {
                    when (args[0]) {
                        "power" -> it.power
                        "balance" -> it.bank.balance.toInt()
                        "land" -> it.claims.totalBlocksCount
                        "citizens" -> it.citizens.size
                        else -> it.power
                    }
                }
                val lowerLimit = (page * 10) - 9
                val upperLimit = page * 10
                tellNoPrefix("${Settings.PRIMARY_COLOR}======= ${Settings.SECONDARY_COLOR}Top Civs: ${args[0].capitalize()} ($page) ${Settings.PRIMARY_COLOR}=======")
                when (args[0]) {
                    "power" -> {
                        for (i in lowerLimit..upperLimit) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i - 1].name}: ${civilizationsSorted[i - 1].power} ")
                        }
                    }
                    "balance" -> {
                        for (i in lowerLimit..upperLimit) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix(
                                "${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i - 1].name}: ${
                                    civilizationsSorted[i - 1].bank.balance.toString()
                                        .format(DecimalFormat.getCurrencyInstance())
                                } "
                            )
                        }
                    }
                    "land" -> {
                        for (i in lowerLimit..upperLimit) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i - 1].name}: ${civilizationsSorted[i - 1].claims.totalBlocksCount} ")
                        }
                    }
                    "citizens" -> {
                        for (i in lowerLimit..upperLimit) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i - 1].name}: ${civilizationsSorted[i - 1].citizens.size} ")
                        }
                    }
                    else -> {
                        for (i in lowerLimit..upperLimit) {
                            if (i >= civilizationsSorted.size)
                                break
                            tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civilizationsSorted[i - 1].name}: ${civilizationsSorted[i - 1].power} ")
                        }
                    }
                }
            }
        } else {
            AsyncEnvironment.run {
                val topPlayers = ArrayList(PlayerManager.cacheMap.values).sortedByDescending { it.power }
                val lowerLimit = (page * 10) - 9
                val upperLimit = page * 10
                tellNoPrefix("${Settings.PRIMARY_COLOR}======= ${Settings.SECONDARY_COLOR}Top Players ($page) ${Settings.PRIMARY_COLOR}=======")
                for (i in lowerLimit..upperLimit) {
                    if (i >= topPlayers.size)
                        break
                    tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${topPlayers[i - 1].playerName}: ${topPlayers[i - 1].power} ")
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