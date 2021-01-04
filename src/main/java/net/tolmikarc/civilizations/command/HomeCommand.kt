package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class HomeCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "home") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            if (args.isNotEmpty()) {
                Civilization.fromName(args[0])
                    ?.let { if (it.claimToggleables.public) it.home?.let { home -> player.teleport(home) } else tell("&cTown not public") }
                return
            }
            checkNotNull(civPlayer.civilization, "You do not have a Civilization")
            civPlayer.civilization?.let { civilization ->
                checkNotNull(civilization.home, "Your Civilization does not have a home.")
                checkBoolean(
                    !hasCooldown(civPlayer.playerUUID, CooldownTask.CooldownType.TELEPORT),
                    "Please wait " + getCooldownRemaining(
                        civPlayer.playerUUID,
                        CooldownTask.CooldownType.TELEPORT
                    ) + " seconds before teleporting again."
                )
                player.teleport(civilization.home!!)
                addCooldownTimer(civPlayer.playerUUID, CooldownTask.CooldownType.TELEPORT)
            }
        }
    }

    init {
        setDescription("Go to your civilization's home")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}