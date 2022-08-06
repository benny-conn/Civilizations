/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.menu.CivilizationMenu
import io.bennyc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AMenuCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "menu|gui") {
    override fun onCommand() {
        checkConsole()
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.run { io.bennyc.civilizations.menu.CivilizationMenu(this).displayTo(player) }
    }

    init {
        minArguments = 1
    }
}