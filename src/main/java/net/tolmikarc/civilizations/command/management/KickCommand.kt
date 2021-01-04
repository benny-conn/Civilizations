package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.PermissionUtil.canManageCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class KickCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "kick") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You must have a Civilization to manage it.")
            civPlayer.civilization?.apply {
                checkBoolean(
                    canManageCiv(civPlayer, this),
                    "You must be the Leader or an Official of your Civilization to use this command."
                )
                checkBoolean(!args[0].equals(player.name, ignoreCase = true), "You cannot kick yourself")
                CivPlayer.fromName(args[0])?.let { kicked -> executeCommand(this, kicked) }
            }
        }
    }

    private fun executeCommand(civilization: Civilization, kickedCache: CivPlayer) {
        checkNotNull(kickedCache, "Specify a valid player")
        checkBoolean(civilization.citizens.contains(kickedCache), "This player is not in your town.")
        civilization.removeCitizen(kickedCache)
        tellSuccess("${Settings.SECONDARY_COLOR}Successfully kicked player ${Settings.PRIMARY_COLOR}${args[0]}")
    }

    init {
        minArguments = 1
        usage = "<player>"
        setDescription("Kick a player from your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}