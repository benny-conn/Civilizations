/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.command.parents.ClaimSubCommand
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup

class ClaimCommand(parent: SimpleCommandGroup?) : ClaimSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                if (args.isNotEmpty()) {
                    when (args[0].toLowerCase()) {
                        "visualize" -> {
                            if (args.size > 1 && !args[1].equals("here", true))
                                if (CivManager.getByName(args[1]) != null)
                                    visualize(it, CivManager.getByName(args[1])!!)
                                else
                                    returnInvalidArgs()
                            else
                                visualize(it, this)
                        }
                        "colony" -> {
                            claim(this, it, true)
                        }
                        "?" -> {
                            // TODO add info for claiming
                        }
                    }
                } else
                    claim(this, it, false)
            }
        }
    }

    override fun tabComplete(): List<String>? {
        if (args.size == 1) return listOf("colony", "visualize")
        return if (args.size == 2) listOf("here") else super.tabComplete()
    }

    init {
        usage = "[colony | visualize]"
        setDescription("Claim a new piece of land for your Civilization or visualize current ones.")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}