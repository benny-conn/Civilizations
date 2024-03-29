/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.model.CivPlayer
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class PlayerInfoCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "player") {
    override fun onCommand() {
        checkConsole()
        val civPlayer: CivPlayer?
        if (args.isNotEmpty()) {
            civPlayer = PlayerManager.getByName(args[0])
            checkNotNull(
                civPlayer,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER)
            )
        } else civPlayer = PlayerManager.fromBukkitPlayer(player)
        civPlayer?.run { sendInfo(this) }
    }

    private fun sendInfo(civPlayer: CivPlayer) {
        var playerGroup = civPlayer.civilization?.permissions?.getPlayerGroup(civPlayer)?.name ?: "None"
        if (civPlayer.civilization?.leader == civPlayer) playerGroup = "Leader"


        tellNoPrefix(
            "${Settings.PRIMARY_COLOR}============ ${Settings.SECONDARY_COLOR}" + civPlayer.playerName + "${Settings.PRIMARY_COLOR} ============",
            "${Settings.PRIMARY_COLOR}Power: ${Settings.SECONDARY_COLOR}" + civPlayer.power,
            "${Settings.PRIMARY_COLOR}Civilization: ${Settings.SECONDARY_COLOR}" + if (civPlayer.civilization != null) civPlayer.civilization!!.name else "None",
            "${Settings.PRIMARY_COLOR}Rank: ${Settings.SECONDARY_COLOR}" + playerGroup,
            "${Settings.PRIMARY_COLOR}Raid Blocks Destroyed: ${Settings.SECONDARY_COLOR}" + civPlayer.raidBlocksDestroyed,
            "${Settings.PRIMARY_COLOR}============================"
        )
    }

    init {
        usage = "[player]"
        setDescription("Get information for you or another player")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}