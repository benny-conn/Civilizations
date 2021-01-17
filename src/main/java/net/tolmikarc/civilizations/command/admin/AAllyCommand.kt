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
            checkBoolean(allyCiv != civ, Localization.Warnings.CANNOT_SPECIFY_SELF)
            civ?.apply {
                when {
                    args[0].equals("add", ignoreCase = true) -> {
                        checkBoolean(
                            !relationships.allies.contains(allyCiv),
                            Localization.Warnings.ALREADY_ALLIES
                        )
                        checkBoolean(
                            !relationships.enemies.contains(allyCiv),
                            Localization.Warnings.ALLY_ENEMY
                        )
                        relationships.addAlly(allyCiv!!)
                        tellSuccess(Localization.Notifications.ALLIES_TRUE.replace("{civ}", allyCiv.name!!))
                    }
                    args[0].equals("remove", ignoreCase = true) -> {
                        checkBoolean(
                            relationships.allies.contains(allyCiv),
                            Localization.Warnings.NOT_ALLY
                        )
                        relationships.removeAlly(allyCiv!!)
                        tellSuccess(Localization.Notifications.ALLIES_FALSE.replace("{civ}", allyCiv.name!!))
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