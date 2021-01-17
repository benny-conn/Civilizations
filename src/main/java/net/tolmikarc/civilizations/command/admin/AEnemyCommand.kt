/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Localization
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AEnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))

        civ?.apply {
            val enemyCiv = CivManager.getByName(args[2])
            checkNotNull(
                enemyCiv,
                Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
            )
            checkBoolean(enemyCiv != this, Localization.Warnings.CANNOT_SPECIFY_SELF)
            when (args[1].toLowerCase()) {
                "add" -> {
                    checkBoolean(
                        !relationships.enemies.contains(enemyCiv),
                        Localization.Warnings.ALREADY_ENEMIES
                    )
                    checkBoolean(!relationships.allies.contains(enemyCiv), Localization.Warnings.ENEMY_ALLY)
                    relationships.addEnemy(enemyCiv!!)
                    tellSuccess(Localization.Notifications.ENEMIES_TRUE.replace("{civ}", enemyCiv.name!!))
                    if (enemyCiv.relationships.enemies.contains(this)) {
                        Messenger.broadcastWarn(
                            Localization.Notifications.WAR.replace("{civ1}", this.name!!)
                                .replace("{civ2}", enemyCiv.name!!)
                        )
                    }
                }
                "remove" -> {
                    checkBoolean(relationships.enemies.contains(enemyCiv), Localization.Warnings.NOT_ENEMY)
                    relationships.removeEnemy(enemyCiv!!)
                    tellSuccess(Localization.Notifications.ENEMIES_FALSE.replace("{civ}", enemyCiv.name!!))
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