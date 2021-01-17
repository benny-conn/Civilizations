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

class EnemyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "enemy") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                val enemyCiv = CivManager.getByName(args[1])
                checkNotNull(
                    enemyCiv,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
                )
                checkBoolean(enemyCiv != this, Localization.Warnings.CANNOT_SPECIFY_SELF)
                when (args[0].toLowerCase()) {
                    "add" -> {
                        checkBoolean(
                            !relationships.enemies.contains(enemyCiv),
                            Localization.Warnings.ALREADY_ENEMIES
                        )
                        checkBoolean(!relationships.allies.contains(enemyCiv), Localization.Warnings.ENEMY_ALLY)
                        relationships.addEnemy(enemyCiv!!)
                        tellSuccess(Localization.Notifications.ENEMIES_TRUE.replace("{civ}", enemyCiv.name!!))
                        if (enemyCiv.relationships.enemies.contains(this))
                            Messenger.broadcastWarn(
                                Localization.Notifications.WAR.replace("{civ1}", this.name!!)
                                    .replace("{civ2}", enemyCiv.name!!)
                            )

                    }
                    "remove" -> {
                        checkBoolean(relationships.enemies.contains(enemyCiv), Localization.Warnings.ALLY_ENEMY)
                        // TODO make sure that there is no cooldown
                        if (relationships.warring.contains(enemyCiv))
                            returnTell(Localization.Warnings.SURRENDER)
                        relationships.removeEnemy(enemyCiv!!)
                        tellSuccess(Localization.Notifications.ENEMIES_FALSE.replace("{civ}", enemyCiv.name!!))
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