/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import org.mineacademy.fo.command.SimpleCommandGroup

class AdminCommandGroup : SimpleCommandGroup() {
    override fun getLabel(): String {
        return "civadmin"
    }

    override fun registerSubcommands() {

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
        registerSubcommand(AMenuCommand(this))
        registerSubcommand(ALeaderCommand(this))
        registerSubcommand(ATownyAdaptCommand(this))
    }


    override fun getCredits(): String {
        return ""
    }
}