/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.event.civ.CreateCivEvent
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class CreateCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "new|create") {
    override fun onCommand() {
        checkConsole()
        val name = args[0]
        PlayerManager.fromBukkitPlayer(player).let {
            checkBoolean(!CivManager.civNames.contains(name), "A Civilization under that name already exists")
            checkBoolean(
                it.civilization == null,
                "You cannot create a Civilization if you already have one. Type /civ leave to leave your Civilization"
            )
            CivManager.createCiv(name, it).also { civ ->
                tellSuccess(
                    "${Settings.SECONDARY_COLOR}Successfully created the Civilization ${Settings.PRIMARY_COLOR}" + civ.name + "${Settings.SECONDARY_COLOR} with you as its leader. " +
                            "To claim land for your Civilization, use a " + "${Settings.PRIMARY_COLOR}${
                        Settings.CLAIM_TOOL.name.toLowerCase().capitalize().replace("_", " ")
                    } ${Settings.SECONDARY_COLOR}to mark two corners and then use " + "${Settings.PRIMARY_COLOR}/civ claim${Settings.SECONDARY_COLOR}. " +
                            "Type " + "${Settings.PRIMARY_COLOR}/civ claim ? ${Settings.SECONDARY_COLOR}for info on claiming"
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