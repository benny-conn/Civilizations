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
            checkBoolean(!CivManager.civNames.contains(name), Localization.Warnings.CIV_NAME_EXISTS)
            checkBoolean(
                it.civilization == null,
                Localization.Warnings.CANNOT_CREATE_CIV
            )
            CivManager.createCiv(name, it).also { civ ->
                tellSuccess(
                    "{2}Successfully created the Civilization {1}" + civ.name + "{2} with you as its leader. " +
                            "To claim land for your Civilization, use a " + "{1}${
                        Settings.CLAIM_TOOL.name.toLowerCase().capitalize().replace("_", " ")
                    } {2}to mark two corners and then use " + "{1}/civ claim{2}. " +
                            "Type " + "{1}/civ claim ? {2}for info on claiming"
                )
                Common.callEvent(CreateCivEvent(civ, player))
            }
        }
    }

    init {
        minArguments = 1
        usage = "<name>"
        setDescription("Create a new Civilization!")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}