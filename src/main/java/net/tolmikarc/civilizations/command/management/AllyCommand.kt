/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AllyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "ally") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, "You do not have a Civilization")
            it.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(it, this), "You cannot manage this Civilization")
                val allyCiv = CivManager.getByName(args[1])
                checkNotNull(allyCiv, "Please specify a valid enemy Civilization")
                checkBoolean(allyCiv != this, "You cannot ally yourself")
                when {
                    args[0].equals("add", ignoreCase = true) -> {
                        checkBoolean(
                            !relationships.allies.contains(allyCiv),
                            "You are already allies with this civilization"
                        )
                        checkBoolean(
                            !relationships.enemies.contains(allyCiv),
                            "You cannot ally an enemy Civilization."
                        )
                        relationships.addAlly(allyCiv!!)
                        tell("{1}Your Civilization is now allies with {2}" + allyCiv.name)
                    }
                    args[0].equals("remove", ignoreCase = true) -> {
                        checkBoolean(
                            relationships.allies.contains(allyCiv),
                            "This Civilization is not your ally."
                        )
                        relationships.removeAlly(allyCiv!!)
                        tell("{1}Your Civilization is no longer allies with {2}" + allyCiv.name)
                    }
                    else -> {
                        returnInvalidArgs()
                    }
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