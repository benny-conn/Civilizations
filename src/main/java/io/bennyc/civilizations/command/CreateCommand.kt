/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.event.CreateCivEvent
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class CreateCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "new|create") {
    override fun onCommand() {
        checkConsole()
        val name = args[0]
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {
            checkBoolean(!io.bennyc.civilizations.manager.CivManager.civNames.containsIgnoreCase(name), io.bennyc.civilizations.settings.Localization.Warnings.CIV_NAME_EXISTS)
            checkBoolean(
                it.civilization == null,
                io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_CREATE_CIV
            )
            io.bennyc.civilizations.manager.CivManager.createCiv(name, it).also { civ ->
                tellSuccess(
                    io.bennyc.civilizations.settings.Localization.Notifications.CIV_CREATION.replace("{name}", civ.name!!)
                        .replace("{tool}", io.bennyc.civilizations.settings.Settings.CLAIM_TOOL.name.toLowerCase().capitalize().replace("_", " "))
                )
                Common.callEvent(io.bennyc.civilizations.event.CreateCivEvent(civ, player))
            }
        }
    }

    private fun MutableSet<String>.containsIgnoreCase(other: String): Boolean {
        return any { it.equals(other, true) }
    }


    init {
        minArguments = 1
        usage = "<name>"
        setDescription("Create a new Civilization!")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}