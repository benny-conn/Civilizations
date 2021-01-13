/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import org.bukkit.Bukkit
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AEnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")

        civ?.apply {
            val enemyCiv = CivManager.getByName(args[2])
            checkNotNull(enemyCiv, "Please specify a valid enemy Civilization")
            checkBoolean(enemyCiv != this, "You cannot enemy yourself")
            when (args[1].toLowerCase()) {
                "add" -> {
                    checkBoolean(
                        !relationships.enemies.contains(enemyCiv),
                        "You are already enemies with this civilization"
                    )
                    checkBoolean(!relationships.allies.contains(enemyCiv), "You cannot enemy an ally Civilization.")
                    relationships.addEnemy(enemyCiv!!)
                    tell("{1}$name is now enemies with {2}" + enemyCiv.name)
                    if (enemyCiv.relationships.enemies.contains(this)) {
                        Bukkit.getOnlinePlayers().forEach {
                            Common.tell(it, "&4${enemyCiv.name} {3}is now at war with &4${this.name}")
                        }
                    }
                }
                "remove" -> {
                    checkBoolean(relationships.enemies.contains(enemyCiv), "This Civilization is not an enemy.")
                    relationships.removeEnemy(enemyCiv!!)
                    tell("{1}$name is no longer enemies with {2}" + enemyCiv.name)
                }
                else -> {
                    returnInvalidArgs()
                }
            }
        }

    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 2) listOf("add", "remove") else super.tabComplete()
    }

    init {
        setDescription("Add or remove a Civ's enemy")
        minArguments = 3
        usage = "<civ> <add | remove> <other civ>"
    }
}