/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.command.management.*
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.command.SimpleCommandGroup

class CivilizationsCommandGroup : SimpleCommandGroup() {
    override fun registerSubcommands() {
        setCommandsPerPage(12)
        registerSubcommand(CreateCommand(this))
        registerSubcommand(AcceptCommand(this))
        registerSubcommand(InviteCommand(this))
        registerSubcommand(LeaveCommand(this))
        registerSubcommand(DepositCommand(this))
        registerSubcommand(WithdrawCommand(this))
        registerSubcommand(DeleteCommand(this))
        registerSubcommand(DenyCommand(this))
        registerSubcommand(MenuCommand(this))
        registerSubcommand(ClaimCommand(this))
        registerSubcommand(UnclaimCommand(this))
        registerSubcommand(PermissionCommand(this))
        registerSubcommand(RankCommand(this))
        registerSubcommand(ToggleCommand(this))
        registerSubcommand(KickCommand(this))
        registerSubcommand(LeaderCommand(this))
        registerSubcommand(ChatCommand(this))
        registerSubcommand(InfoCommand(this))
        registerSubcommand(ListCommand(this))
        registerSubcommand(PlotCommand(this))
        registerSubcommand(SetHomeCommand(this))
        registerSubcommand(HomeCommand(this))
        registerSubcommand(DescriptionCommand(this))
        registerSubcommand(EnemyCommand(this))
        registerSubcommand(SurrenderCommand(this))
        registerSubcommand(RepairCommand(this))
        registerSubcommand(RaidCommand(this))
        registerSubcommand(TaxesCommand(this))
        registerSubcommand(ColonyCommand(this))
        registerSubcommand(AllyCommand(this))
        registerSubcommand(PlayerInfoCommand(this))
        registerSubcommand(OutlawCommand(this))
        registerSubcommand(TopCommand(this))
        registerSubcommand(SetWarpCommand(this))
        registerSubcommand(RemoveWarpCommand(this))
        registerSubcommand(WarpCommand(this))
        registerSubcommand(WarpsCommand(this))
        registerSubcommand(MapCommand(this))
        registerSubcommand(HereCommand(this))
        if (Settings.FLY_ENABLED) registerSubcommand(FlyCommand(this))
        registerSubcommand(TestCommand(this))
    }


    override fun getCredits(): String {
        return ""
    }
}