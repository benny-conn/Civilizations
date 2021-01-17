/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AOutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            val outlaw = findPlayer(
                args[0],
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
            )
            PlayerManager.fromBukkitPlayer(outlaw).let { civOutlaw ->
                checkBoolean(!this.citizens.contains(civOutlaw), Localization.Warnings.OUTLAW_CITIZEN)
                if (this.relationships.outlaws.contains(civOutlaw)) {
                    this.relationships.removeOutlaw(civOutlaw)
                    tellSuccess(Localization.Notifications.OUTLAW_REMOVE.replace("{player}", args[0]))
                } else {
                    this.relationships.addOutlaw(civOutlaw)
                    tellSuccess(Localization.Notifications.OUTLAW_ADD.replace("{player}", args[0]))
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