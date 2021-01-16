/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class PermissionCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "perms|permissions") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(PermissionChecker.canManageCiv(civPlayer, this), Localization.Warnings.CANNOT_MANAGE_CIV)
                if (args.size == 1) {
                    if (args[0].equals("options", ignoreCase = true)) {
                        tell(
                            "{1}Valid Groups: {2}${
                                Common.join(
                                    permissionGroups.groups.map { it.name },
                                    ", "
                                )
                            }",
                            "{1}Valid Permissions: {2}Build, Break, Switch, Interact",
                            "{1}Valid values: {2}True, False"
                        )
                    } else returnInvalidArgs()
                }
                if (args.size == 3) {
                    val group = permissionGroups.getGroupByName(args[0])
                    if (group == null) returnInvalidArgs()
                    group?.adjust(PermissionType.valueOf(args[1].toUpperCase()), args[2].toBoolean())
                    tellSuccess("{1}Successfully updated Civ Permissions")
                } else
                    returnInvalidArgs()
            }
        }
    }

    override fun tabComplete(): List<String>? {
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