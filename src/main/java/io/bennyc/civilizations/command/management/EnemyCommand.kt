/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import io.bennyc.civilizations.manager.CivManager
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class EnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCivilization = io.bennyc.civilizations.manager.CivManager.getByName(args[1])
                checkNotNull(
                    enemyCivilization,
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION)
                )
                checkBoolean(enemyCivilization != this, io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_SPECIFY_SELF)
                when (args[0].lowercase(Locale.getDefault())) {
                    "add" -> {
                        checkBoolean(
                            !relationships.enemies.contains(enemyCivilization),
                            io.bennyc.civilizations.settings.Localization.Warnings.ALREADY_ENEMIES
                        )
                        checkBoolean(
                            !relationships.allies.contains(enemyCivilization),
                            io.bennyc.civilizations.settings.Localization.Warnings.ENEMY_ALLY
                        )
                        relationships.addEnemy(enemyCivilization!!)
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.ENEMIES_TRUE.replace("{civ}", enemyCivilization.name!!))
                        if (enemyCivilization.relationships.enemies.contains(this))
                            Messenger.broadcastWarn(
                                io.bennyc.civilizations.settings.Localization.Notifications.WAR.replace("{civ1}", this.name!!)
                                    .replace("{civ2}", enemyCivilization.name!!)
                            )

                    }
                    "remove" -> {
                        checkBoolean(
                            relationships.enemies.contains(enemyCivilization),
                            io.bennyc.civilizations.settings.Localization.Warnings.ALLY_ENEMY
                        )
                        // TODO make sure that there is no cooldown
                        if (relationships.warring.contains(enemyCivilization))
                            returnTell(io.bennyc.civilizations.settings.Localization.Warnings.SURRENDER)
                        relationships.removeEnemy(enemyCivilization!!)
                        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.ENEMIES_FALSE.replace("{civ}", enemyCivilization.name!!))
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
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}