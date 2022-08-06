/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.command.parent.ClaimSubCommand
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import java.util.*

class ClaimCommand(parent: SimpleCommandGroup?) : io.bennyc.civilizations.command.parent.ClaimSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                if (args.isNotEmpty()) {
                    when (args[0].lowercase(Locale.getDefault())) {
                        "visualize" -> {
                            if (args.size > 1 && !args[1].equals("here", true))
                                if (io.bennyc.civilizations.manager.CivManager.getByName(args[1]) != null)
                                    visualize(it, io.bennyc.civilizations.manager.CivManager.getByName(args[1])!!)
                                else
                                    returnInvalidArgs()
                            else
                                visualize(it, this)
                        }
                        "colony"    -> {
                            claim(this, it, true)
                        }
                        "?"         -> {
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
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}