/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.menu.CivilizationMenu
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AMenuCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "menu|gui") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.run { CivilizationMenu(this).displayTo(player) }
    }

    init {
        minArguments = 1
    }
}