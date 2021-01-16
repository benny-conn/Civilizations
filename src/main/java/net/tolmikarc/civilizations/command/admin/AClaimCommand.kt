/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.command.parents.ClaimSubCommand
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup

class AClaimCommand(parent: SimpleCommandGroup?) : ClaimSubCommand(parent) {
    override fun onCommand() {
        checkConsole()
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            if (args.isNotEmpty()) {
                when (args[0].toLowerCase()) {
                    "visualize" -> {
                        visualize(civPlayer, this)
                    }
                    "colony" -> {
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
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}