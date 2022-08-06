/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.menu.ConfirmMenu
import io.bennyc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ADeleteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "delete") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            fun run() {
                io.bennyc.civilizations.manager.CivManager.removeCiv(this)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
            }
            io.bennyc.civilizations.menu.ConfirmMenu(
                "&4Delete Civilization?",
                "Permanently remove this Civilization",
                ::run
            ).displayTo(player)
        }
    }

    init {
        setDescription("Delete a Civilization.")
        usage = "<civ>"
    }
}