/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AAllyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "ally") {
    override fun onCommand() {
        PlayerManager.fromBukkitPlayer(player).let {
            checkConsole()
            val civ = CivManager.getByName(args[0])
            checkNotNull(
                civ,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
            )
            val allyCiv = CivManager.getByName(args[1])
            checkNotNull(
                allyCiv,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
            )
            checkBoolean(allyCiv != civ, "You cannot make this civ ally itself")
            civ?.apply {
                when {
                    args[0].equals("add", ignoreCase = true) -> {
                        checkBoolean(
                            !relationships.allies.contains(allyCiv),
                            "$name is already allies with this civilization"
                        )
                        checkBoolean(
                            !relationships.enemies.contains(allyCiv),
                            "$name cannot ally an enemy Civilization."
                        )
                        relationships.addAlly(allyCiv!!)
                        tell("{1}$name is now allies with {2}" + allyCiv.name)
                    }
                    args[0].equals("remove", ignoreCase = true) -> {
                        checkBoolean(
                            relationships.allies.contains(allyCiv),
                            "This Civilization is not an ally."
                        )
                        relationships.removeAlly(allyCiv!!)
                        tell("{1}$name is no longer allies with {2}" + allyCiv.name)
                    }
                    else -> {
                        returnInvalidArgs()
                    }
                }
            }
        }

    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 2) listOf("add", "remove") else null
    }

    init {
        setDescription("Ally a Civilization")
        minArguments = 2
        usage = "<civ> <add | remove> <other civ>"
    }
}