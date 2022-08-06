/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AOutlawCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "outlaw") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            val outlaw = findPlayer(
                args[0],
                io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER)
            )
            io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(outlaw).let { civOutlaw ->
                checkBoolean(!this.citizens.contains(civOutlaw), io.bennyc.civilizations.settings.Localization.Warnings.OUTLAW_CITIZEN)
                if (this.relationships.outlaws.contains(civOutlaw)) {
                    this.relationships.removeOutlaw(civOutlaw)
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.OUTLAW_REMOVE.replace("{player}", args[0]))
                } else {
                    this.relationships.addOutlaw(civOutlaw)
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.OUTLAW_ADD.replace("{player}", args[0]))
                }
            }
        }
    }

    init {
        minArguments = 1
        usage = "<civ> <player>"
        setDescription("Outlaw a player from a Civilization or remove them as an outlaw")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}