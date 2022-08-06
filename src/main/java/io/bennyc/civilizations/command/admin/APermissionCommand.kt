/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.permissions.PermissionType
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class APermissionCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "perms|permissions") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(civ, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.CIVILIZATION))
        civ?.apply {
            if (args.size == 1) {
                if (args[0].equals("options", ignoreCase = true)) {
                    tell(
                        "${io.bennyc.civilizations.settings.Settings.PRIMARY_COLOR}Valid Groups: ${io.bennyc.civilizations.settings.Settings.SECONDARY_COLOR}${
                            Common.join(
                                permissions.ranks.map { it.name },
                                ", "
                            )
                        }",
                        "${io.bennyc.civilizations.settings.Settings.PRIMARY_COLOR}Valid Permissions: ${io.bennyc.civilizations.settings.Settings.SECONDARY_COLOR}Build, Break, Switch, Interact",
                        "${io.bennyc.civilizations.settings.Settings.PRIMARY_COLOR}Valid values: ${io.bennyc.civilizations.settings.Settings.SECONDARY_COLOR}True, False"
                    )
                } else returnInvalidArgs()
            }
            if (args.size == 4) {
                val group = permissions.getGroupByName(args[0])
                if (group == null) returnInvalidArgs()
                group?.adjust(PermissionType.valueOf(args[1].toUpperCase()), args[2].toBoolean())
                tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
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