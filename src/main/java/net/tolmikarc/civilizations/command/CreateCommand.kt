/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.event.CreateCivEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class CreateCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "new|create") {
    override fun onCommand() {
        checkConsole()
        val name = args[0]
        PlayerManager.fromBukkitPlayer(player).let {
            checkBoolean(!CivManager.civNames.containsIgnoreCase(name), Localization.Warnings.CIV_NAME_EXISTS)
            checkBoolean(
                it.civilization == null,
                Localization.Warnings.CANNOT_CREATE_CIV
            )
            CivManager.createCiv(name, it).also { civ ->
                tellSuccess(
                    Localization.Notifications.CIV_CREATION.replace("{name}", civ.name!!)
                        .replace("{tool}", Settings.CLAIM_TOOL.name.toLowerCase().capitalize().replace("_", " "))
                )
                Common.callEvent(CreateCivEvent(civ, player))
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
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }

}