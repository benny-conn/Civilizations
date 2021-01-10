/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class EnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a Civilization")
            civPlayer.civilization?.apply {
                val enemyCiv = CivManager.getByName(args[1])
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
                        if (enemyCiv.enemies.contains(this)) {
                            Bukkit.getOnlinePlayers().forEach {
                                Common.tell(it, "&4${enemyCiv.name} &cis now at war with &4${this.name}")
                            }
                        }
                    }
                    "remove" -> {
                        checkBoolean(enemies.contains(enemyCiv), "This Civilization is not your enemy.")
                        // TODO make sure that there is no cooldown
                        if (warring.contains(enemyCiv))
                            returnTell("You must use /civ surrender to end the war")
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