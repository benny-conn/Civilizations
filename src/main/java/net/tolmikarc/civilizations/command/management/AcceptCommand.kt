package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class AcceptCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "accept") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).let {
            checkNotNull(it.civilizationInvite, "You do not have any pending invites")
            it.civilizationInvite?.apply {
                this.addCitizen(it)
                it.civilization = this
                it.civilizationInvite = null
                tell("${Settings.SECONDARY_COLOR}Accepted invite from the Civilization ${Settings.PRIMARY_COLOR}" + it.civilization!!.name)
                tell("${Settings.SECONDARY_COLOR}Type " + "${Settings.PRIMARY_COLOR}/civ ? ${Settings.SECONDARY_COLOR}for a list of Civilizations commands.")
            }
        }

    }

    init {
        setDescription("Accept a Civilization's invite")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}