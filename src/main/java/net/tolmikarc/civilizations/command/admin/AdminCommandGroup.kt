/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.admin

import org.mineacademy.fo.command.SimpleCommandGroup

class AdminCommandGroup : SimpleCommandGroup() {
    override fun registerSubcommands() {
        setCommandsPerPage(12)
        registerSubcommand(ADeleteCommand(this))
        registerSubcommand(ASetCommand(this))
        registerSubcommand(AAddCommand(this))
        registerSubcommand(AKickCommand(this))
        registerSubcommand(APermissionCommand(this))
        registerSubcommand(AToggleCommand(this))
        registerSubcommand(AAllyCommand(this))
        registerSubcommand(AEnemyCommand(this))
        registerSubcommand(AOutlawCommand(this))
        registerSubcommand(AClaimCommand(this))
        registerSubcommand(AUnclaimCommand(this))
        registerSubcommand(APlotCommand(this))
        registerSubcommand(ASetWarpCommand(this))
        registerSubcommand(ARemoveWarpCommand(this))
        registerSubcommand(AWarpCommand(this))
        registerSubcommand(AWarpsCommand(this))
        registerSubcommand(ARankCommand(this))
        registerSubcommand(ARepairCommand(this))
        registerSubcommand(ALeaderCommand(this))
        registerSubcommand(ARankCommand(this))
        registerSubcommand(ATownyAdaptCommand(this))
    }


    override fun getCredits(): String {
        return ""
    }
}