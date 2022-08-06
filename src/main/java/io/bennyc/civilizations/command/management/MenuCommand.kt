/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.menu.CivilizationMenu
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class MenuCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "menu|gui") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).run {
            checkNotNull(civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            checkBoolean(io.bennyc.civilizations.PermissionChecker.canManageCiv(this, civilization!!), io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV)
            civilization?.run { io.bennyc.civilizations.menu.CivilizationMenu(this).displayTo(player) }
        }
    }

    init {
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}