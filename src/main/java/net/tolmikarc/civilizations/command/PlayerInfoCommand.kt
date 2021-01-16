/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class PlayerInfoCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "player") {
    override fun onCommand() {
        checkConsole()
        val civPlayer: CPlayer?
        if (args.isNotEmpty()) {
            civPlayer = PlayerManager.getByName(args[0])
            checkNotNull(
                civPlayer,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
            )
        } else civPlayer = PlayerManager.fromBukkitPlayer(player)
        civPlayer?.run { sendInfo(this) }
    }

    private fun sendInfo(civPlayer: CPlayer) {
        tellNoPrefix(
            "{1}============ {2}" + civPlayer.playerName + "{1} ============",
            "{1}Power: {2}" + civPlayer.power,
            "{1}Civilization: {2}" + if (civPlayer.civilization != null) civPlayer.civilization!!.name else "None",
            "{1}Raid Blocks Destroyed: {2}" + civPlayer.raidBlocksDestroyed,
            "{1}============================"
        )
    }

    init {
        usage = "[player]"
        setDescription("Get information for you or another player")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}