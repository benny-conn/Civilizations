/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AOutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
            val outlaw = findPlayer(args[0], "Specify a valid and online player")
            PlayerManager.fromBukkitPlayer(outlaw).let { civOutlaw ->
                checkBoolean(!this.citizens.contains(civOutlaw), "You cannot outlaw a player in the town.")
                if (this.relationships.outlaws.contains(civOutlaw)) {
                    this.relationships.removeOutlaw(civOutlaw)
                    tell("${Settings.SECONDARY_COLOR}Successfully removed ${Settings.PRIMARY_COLOR}${args[0]}${Settings.PRIMARY_COLOR} as an outlaw")
                } else {
                    this.relationships.addOutlaw(civOutlaw)
                    tell("${Settings.SECONDARY_COLOR}Successfully outlawed player ${Settings.PRIMARY_COLOR}${args[0]}")
                }
            }
        }
    }

    init {
        minArguments = 1
        usage = "<civ> <player>"
        setDescription("Outlaw a player from a Civilization or remove them as an outlaw")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}