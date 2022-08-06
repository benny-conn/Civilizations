/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.papermc.lib.PaperLib
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warp") {
    override fun onCommand() {
        checkConsole()
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        val warp = civ!!.warps[args[0]]
        checkNotNull(warp, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp"))
        PaperLib.teleportAsync(player, warp!!).thenAccept {
            if (it)
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TELEPORT)
            else
                tellError(io.bennyc.civilizations.settings.Localization.Warnings.FAILED_TELEPORT)
        }
    }

    init {
        setDescription("Teleport to a Civilization Warp")
        usage = "<civ> <warp>"
        minArguments = 2
    }
}