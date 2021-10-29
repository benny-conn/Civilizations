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
                val allyCivilization = CivManager.getByName(args[1])
                checkNotNull(
                    allyCivilization,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
                )
                checkBoolean(allyCivilization != this, Localization.Warnings.CANNOT_SPECIFY_SELF)
                when {
                    args[0].equals("add", ignoreCase = true) -> {
                        checkBoolean(
                            !relationships.allies.contains(allyCivilization),
                            Localization.Warnings.ALREADY_ALLIES
                        )
                        checkBoolean(
                            !relationships.enemies.contains(allyCivilization),
                            Localization.Warnings.ALLY_ENEMY
                        )
                        relationships.addAlly(allyCivilization!!)
                        tellSuccess(Localization.Notifications.ALLIES_TRUE.replace("{civ}", allyCivilization.name!!))
                    }
                    args[0].equals("remove", ignoreCase = true) -> {
                        checkBoolean(
                            relationships.allies.contains(allyCivilization),
                            Localization.Warnings.NOT_ALLY
                        )
                        relationships.removeAlly(allyCivilization!!)
                        tellSuccess(Localization.Notifications.ALLIES_FALSE.replace("{civ}", allyCivilization.name!!))
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