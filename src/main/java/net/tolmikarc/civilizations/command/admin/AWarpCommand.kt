/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AWarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warp") {
    override fun onCommand() {
        checkConsole()
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        val warp = civ!!.warps[args[0]]
        checkNotNull(warp, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp"))
        PaperLib.teleportAsync(player, warp!!).thenAccept {
            if (it)
                tellSuccess("Teleported to Warp!")
            else
                tellError(Localization.Warnings.FAILED_TELEPORT)
        }
    }

    init {
        setDescription("Teleport to a Civilization Warp")
        usage = "<civ> <warp>"
        minArguments = 2
    }
}