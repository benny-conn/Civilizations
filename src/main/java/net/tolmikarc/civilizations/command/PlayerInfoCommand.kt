package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class PlayerInfoCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "player") {
    override fun onCommand() {
        checkConsole()
        val civPlayer: CivPlayer?
        if (args.isNotEmpty()) {
            civPlayer = CivPlayer.fromName(args[0])
            checkNotNull(civPlayer, "Please specify a valid player")
        } else civPlayer = CivPlayer.fromBukkitPlayer(player)
        civPlayer?.run { sendInfo(this) }
    }

    private fun sendInfo(civPlayer: CivPlayer) {
        tellNoPrefix(
            "${Settings.PRIMARY_COLOR}============ ${Settings.SECONDARY_COLOR}" + civPlayer.playerName + "${Settings.PRIMARY_COLOR} ============",
            "${Settings.PRIMARY_COLOR}Power: ${Settings.SECONDARY_COLOR}" + civPlayer.power,
            "${Settings.PRIMARY_COLOR}Civilization: ${Settings.SECONDARY_COLOR}" + if (civPlayer.civilization != null) civPlayer.civilization!!.name else "None",
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