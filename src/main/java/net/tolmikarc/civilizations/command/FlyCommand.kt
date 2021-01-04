package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.ClaimUtil.isLocationInCiv
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class FlyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "fly") {
    override fun onCommand() {
        checkConsole()
        player.allowFlight = true
        val civPlayer = CivPlayer.fromBukkitPlayer(player).apply {
            checkNotNull(civilization, "You must have a Civilization to use this command.")
            flying = !flying
            tellSuccess("${Settings.PRIMARY_COLOR}Enabled flight while you are in your Civilization.")
            if (isLocationInCiv(player.location, civilization!!)) player.isFlying = flying
        }
    }

    init {
        setDescription("Allows flight within your Civilization's Borders")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}