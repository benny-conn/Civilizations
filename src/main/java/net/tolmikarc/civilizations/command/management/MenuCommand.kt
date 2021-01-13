/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.menu.CivilizationMenu
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class MenuCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "menu|gui") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).run {
            checkNotNull(civilization, "You must have a Civilization to use this command.")
            checkBoolean(PermissionChecker.canManageCiv(this, civilization!!), "You cannot manage this Civilization")
            civilization?.run { CivilizationMenu(this).displayTo(player) }
        }
    }

    init {
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}