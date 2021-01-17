/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ADeleteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "delete") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            fun run() {
                CivManager.removeCiv(this)
                tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
            }
            ConfirmMenu("&4Delete Civ?", "Permanently remove this Civilization", ::run).displayTo(player)
        }
    }

    init {
        setDescription("Delete a Civilization.")
        usage = "<civ>"
    }
}