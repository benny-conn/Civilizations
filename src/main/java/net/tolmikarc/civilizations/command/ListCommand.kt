/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ListCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "list") {
    override fun onCommand() {
        checkConsole()
        val page = if (args.size > 1) args[0].toInt() else 1
        AsyncEnvironment.run {
            val lowerLimit = (page * 10) - 9
            val upperLimit = page * 10
            tellNoPrefix("${Settings.PRIMARY_COLOR}======= ${Settings.SECONDARY_COLOR}Civs: ($page) ${Settings.PRIMARY_COLOR}=======")
            val civs = ArrayList(CivManager.all)
            for (i in lowerLimit..upperLimit) {
                tellNoPrefix("${Settings.PRIMARY_COLOR} ${i}. ${civs[i - 1].name}")
            }

        }

    }

    init {
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}