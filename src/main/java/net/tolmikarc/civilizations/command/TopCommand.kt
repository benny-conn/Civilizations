/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
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
            Common.runLaterAsync {
                TODO("Gotta redo this one")
            }
        } else {
            Common.runLaterAsync {
                TODO("Gotta redo this one")
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