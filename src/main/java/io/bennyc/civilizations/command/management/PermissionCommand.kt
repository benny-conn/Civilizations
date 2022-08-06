/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.permissions.PermissionType
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class PermissionCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "perms|permissions") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.apply {
                checkBoolean(
                    io.bennyc.civilizations.PermissionChecker.canManageCiv(civPlayer, this),
                    io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV
                )
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
                if (args.size == 3) {
                    val group = permissions.getGroupByName(args[0])
                    if (group == null) returnInvalidArgs()
                    group?.adjust(PermissionType.valueOf(args[1].uppercase(Locale.getDefault())), args[2].toBoolean())
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
                } else
                    returnInvalidArgs()
            }
        }
    }

    override fun tabComplete(): List<String>? {
        var civ: Civilization? = null
        if (io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).civilization != null)
            civ = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).civilization
        return when (args.size) {
            1 -> civ?.permissions?.ranks?.map { it.name } ?: super.tabComplete()
            2 -> listOf("build", "break", "switch", "interact")
            3 -> listOf("true", "false")
            else -> super.tabComplete()
        }
    }

    init {
        minArguments = 1
        usage = "<permission> <group> <value>"
        setDescription("Allow different groups to perform various actions in your town. Use /civ perms options for a list of valid options.")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}