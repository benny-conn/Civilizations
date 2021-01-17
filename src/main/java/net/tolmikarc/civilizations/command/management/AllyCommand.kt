/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AllyCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "ally") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(it, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                val allyCiv = CivManager.getByName(args[1])
                checkNotNull(
                    allyCiv,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
                )
                checkBoolean(allyCiv != this, Localization.Warnings.CANNOT_SPECIFY_SELF)
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
        return if (args.size == 1) listOf("add", "remove") else null
    }

    init {
        setDescription("Ally a Civilization")
        minArguments = 2
        usage = "<add | remove> <Civilization>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}