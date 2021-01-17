/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class APermissionCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "perms|permissions") {
    override fun onCommand() {
        val civ = CivManager.getByName(args[0])
        checkNotNull(civ, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION))
        civ?.apply {
            if (args.size == 1) {
                if (args[0].equals("options", ignoreCase = true)) {
                    tell(
                        "${Settings.PRIMARY_COLOR}Valid Groups: ${Settings.SECONDARY_COLOR}${
                            Common.join(
                                permissions.ranks.map { it.name },
                                ", "
                            )
                        }",
                        "${Settings.PRIMARY_COLOR}Valid Permissions: ${Settings.SECONDARY_COLOR}Build, Break, Switch, Interact",
                        "${Settings.PRIMARY_COLOR}Valid values: ${Settings.SECONDARY_COLOR}True, False"
                    )
                } else returnInvalidArgs()
            }
            if (args.size == 4) {
                val group = permissions.getGroupByName(args[0])
                if (group == null) returnInvalidArgs()
                group?.adjust(PermissionType.valueOf(args[1].toUpperCase()), args[2].toBoolean())
                tellSuccess(Localization.Notifications.SUCCESS_COMMAND)
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