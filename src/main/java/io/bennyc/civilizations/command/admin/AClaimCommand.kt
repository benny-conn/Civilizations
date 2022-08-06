/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.command.parent.ClaimSubCommand
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import java.util.*

class AClaimCommand(parent: SimpleCommandGroup?) : io.bennyc.civilizations.command.parent.ClaimSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            if (args.isNotEmpty()) {
                when (args[0].lowercase(Locale.getDefault())) {
                    "visualize" -> {
                        visualize(civPlayer, this)
                    }
                    "colony"    -> {
                        claim(this, civPlayer, true)
                    }
                }
            } else
                claim(this, civPlayer, false)
        }
    }

    override fun tabComplete(): List<String>? {
        if (args.size == 2) return listOf("colony", "visualize")
        return if (args.size == 3) listOf("here") else super.tabComplete()
    }

    init {
        usage = "<civ> [colony | visualize]"
        setDescription("Claim a new piece of land for your Civilization or visualize current ones.")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}