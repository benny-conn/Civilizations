/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.permissions.PermissionType
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class APermissionCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "perms|permissions") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, "Please specify a valid Civilization.")
        civ?.apply {
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
            if (args.size == 4) {
                val group = permissionGroups.getGroupByName(args[0])
                if (group == null) returnInvalidArgs()
                group?.adjust(PermissionType.valueOf(args[1].toUpperCase()), args[2].toBoolean())
                tellSuccess("{1}Successfully updated Civ Permissions")
            } else
                returnInvalidArgs()

        }
    }

    override fun tabComplete(): List<String>? {
        if (args.size == 2) return listOf("build", "break", "switch", "interact")
        if (args.size == 3) return listOf("options", "outsider", "member", "ally", "official")
        return if (args.size == 4) listOf("true", "false") else super.tabComplete()
    }

    init {
        minArguments = 1
        usage = "<civ> <permission> <group> <value>"
        setDescription("Set the permissions of a given Civilization.")
    }
}