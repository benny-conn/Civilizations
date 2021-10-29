/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class EnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCivilization = CivManager.getByName(args[1])
                checkNotNull(
                    enemyCivilization,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
                )
                checkBoolean(enemyCivilization != this, Localization.Warnings.CANNOT_SPECIFY_SELF)
                when (args[0].lowercase(Locale.getDefault())) {
                    "add" -> {
                        checkBoolean(
                            !relationships.enemies.contains(enemyCivilization),
                            Localization.Warnings.ALREADY_ENEMIES
                        )
                        checkBoolean(
                            !relationships.allies.contains(enemyCivilization),
                            Localization.Warnings.ENEMY_ALLY
                        )
                        relationships.addEnemy(enemyCivilization!!)
                        tellSuccess(Localization.Notifications.ENEMIES_TRUE.replace("{civ}", enemyCivilization.name!!))
                        if (enemyCivilization.relationships.enemies.contains(this))
                            Messenger.broadcastWarn(
                                Localization.Notifications.WAR.replace("{civ1}", this.name!!)
                                    .replace("{civ2}", enemyCivilization.name!!)
                            )

                    }
                    "remove" -> {
                        checkBoolean(
                            relationships.enemies.contains(enemyCivilization),
                            Localization.Warnings.ALLY_ENEMY
                        )
                        // TODO make sure that there is no cooldown
                        if (relationships.warring.contains(enemyCivilization))
                            returnTell(Localization.Warnings.SURRENDER)
                        relationships.removeEnemy(enemyCivilization!!)
                        tellSuccess(Localization.Notifications.ENEMIES_FALSE.replace("{civ}", enemyCivilization.name!!))
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