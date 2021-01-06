/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class PermissionCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "perms|permissions") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, "You do not have a Civilization to manage")
            civPlayer.civilization?.apply {
                val permissions = this.claimPermissions
                if (args.size == 1) {
                    if (args[0].equals("options", ignoreCase = true)) {
                        tell(
                            "${Settings.PRIMARY_COLOR}Valid Groups: ${Settings.SECONDARY_COLOR}Outsider, Member, Ally, Official",
                            "${Settings.PRIMARY_COLOR}Valid Permissions: ${Settings.SECONDARY_COLOR}Build, Break, Switch, Interact",
                            "${Settings.PRIMARY_COLOR}Valid values: ${Settings.SECONDARY_COLOR}True, False"
                        )
                    } else returnInvalidArgs()
                }
                if (args.size == 3) {
                    if (permissions.adjustPerm(args[0], args[1], args[2])) {
                        tellSuccess("${Settings.PRIMARY_COLOR}Successfully updated Civ Permissions")
                    } else returnInvalidArgs()
                } else
                    returnInvalidArgs()
            }
        }
    }

    override fun tabComplete(): List<String> {
        if (args.size == 1) return listOf("build", "break", "switch", "interact")
        if (args.size == 2) return listOf("options", "outsider", "member", "ally", "official")
        return if (args.size == 3) listOf("true", "false") else super.tabComplete()
    }

    init {
        minArguments = 1
        usage = "<permission> <group> <value>"
        setDescription("Allow different groups to perform various actions in your town. Use /civ perms options for a list of valid options.")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}