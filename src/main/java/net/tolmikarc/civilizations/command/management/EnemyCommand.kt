/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class EnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a Civilization")
            civPlayer.civilization?.apply {
                val enemyCiv = Civilization.fromName(args[1])
                checkNotNull(enemyCiv, "Please specify a valid enemy Civilization")
                checkBoolean(enemyCiv != this, "You cannot enemy yourself")
                when (args[0].toLowerCase()) {
                    "add" -> {
                        checkBoolean(
                            !enemies.contains(enemyCiv),
                            "You are already enemies with this civilization"
                        )
                        checkBoolean(!allies.contains(enemyCiv), "You cannot enemy an ally Civilization.")
                        addEnemy(enemyCiv!!)
                        tell("${Settings.PRIMARY_COLOR}Your Civilization is now enemies with ${Settings.SECONDARY_COLOR}" + enemyCiv.name)
                    }
                    "remove" -> {
                        checkBoolean(enemies.contains(enemyCiv), "This Civilization is not your enemy.")
                        removeEnemy(enemyCiv!!)
                        tell("${Settings.PRIMARY_COLOR}Your Civilization is no longer enemies with ${Settings.SECONDARY_COLOR}" + enemyCiv.name)
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
        setDescription("Enemy a Civilization")
        minArguments = 2
        usage = "<add | remove> <Civilization>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}