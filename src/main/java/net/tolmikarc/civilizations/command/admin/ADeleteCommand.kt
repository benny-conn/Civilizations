/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.menu.ConfirmMenu
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class ADeleteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "delete") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
            fun run() {
                CivManager.removeCiv(this)
                tellSuccess("${Settings.PRIMARY_COLOR}Successfully deleted the Civilization, ${Settings.SECONDARY_COLOR}$name")
            }
            ConfirmMenu("&4Delete Civ?", "Permanently remove this Civilization", ::run).displayTo(player)
        }
    }

    init {
        setDescription("Delete a Civilization.")
        usage = "<civ>"
    }
}