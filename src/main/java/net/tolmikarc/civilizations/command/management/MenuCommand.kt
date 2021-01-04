package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.menu.CivilizationMenu
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class MenuCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "menu|gui") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).run {
            checkNotNull(civilization, "You must have a Civilization to use this command.")
            civilization?.run { CivilizationMenu(this).displayTo(player) }
        }
    }

    init {
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}