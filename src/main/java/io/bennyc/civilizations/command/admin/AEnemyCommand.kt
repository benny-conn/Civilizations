/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.settings.Localization
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AEnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))

        civ?.apply {
            val enemyCiv = io.bennyc.civilizations.manager.CivManager.getByName(args[2])
            checkNotNull(
                enemyCiv,
                io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION)
            )
            checkBoolean(enemyCiv != this, io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_SPECIFY_SELF)
            when (args[1].toLowerCase()) {
                "add" -> {
                    checkBoolean(
                        !relationships.enemies.contains(enemyCiv),
                        io.bennyc.civilizations.settings.Localization.Warnings.ALREADY_ENEMIES
                    )
                    checkBoolean(!relationships.allies.contains(enemyCiv), io.bennyc.civilizations.settings.Localization.Warnings.ENEMY_ALLY)
                    relationships.addEnemy(enemyCiv!!)
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.ENEMIES_TRUE.replace("{civ}", enemyCiv.name!!))
                    if (enemyCiv.relationships.enemies.contains(this)) {
                        Messenger.broadcastWarn(
                            io.bennyc.civilizations.settings.Localization.Notifications.WAR.replace("{civ1}", this.name!!)
                                .replace("{civ2}", enemyCiv.name!!)
                        )
                    }
                }
                "remove" -> {
                    checkBoolean(relationships.enemies.contains(enemyCiv), io.bennyc.civilizations.settings.Localization.Warnings.NOT_ENEMY)
                    relationships.removeEnemy(enemyCiv!!)
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.ENEMIES_FALSE.replace("{civ}", enemyCiv.name!!))
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