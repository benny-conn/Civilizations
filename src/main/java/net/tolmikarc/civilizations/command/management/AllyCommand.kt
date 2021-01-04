package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AllyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "ally") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, "You do not have a Civilization")
            val civilization = it.civilization
            val allyCiv = Civilization.fromName(args[1])
            checkNotNull(allyCiv, "Please specify a valid enemy Civilization")
            checkBoolean(allyCiv != civilization, "You cannot ally yourself")
            when {
                args[0].equals("add", ignoreCase = true) -> {
                    checkBoolean(
                        !civilization!!.allies.contains(allyCiv),
                        "You are already allies with this civilization"
                    )
                    checkBoolean(!civilization.enemies.contains(allyCiv), "You cannot ally an enemy Civilization.")
                    civilization.addAlly(allyCiv!!)
                    tell("${Settings.PRIMARY_COLOR}Your Civilization is now allies with ${Settings.SECONDARY_COLOR}" + allyCiv.name)
                }
                args[0].equals("remove", ignoreCase = true) -> {
                    checkBoolean(civilization!!.allies.contains(allyCiv), "This Civilization is not your ally.")
                    civilization.removeAlly(allyCiv!!)
                    tell("${Settings.PRIMARY_COLOR}Your Civilization is no longer allies with ${Settings.SECONDARY_COLOR}" + allyCiv.name)
                }
                else -> {
                    returnInvalidArgs()
                }
            }
        }

    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 1) listOf("add", "remove") else null
    }

    init {
        setDescription("Ally a Civilization")
        minArguments = 2
        usage = "<add | remove> <Civilization>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}