/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class InviteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "add|invite") {
    override fun onCommand() {
        checkConsole()
        val invitee =
            findPlayer(args[0], Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.PLAYER))
        checkBoolean(invitee != player, Localization.Warnings.CANNOT_SPECIFY_SELF)
        val senderCache = PlayerManager.fromBukkitPlayer(player)
        val inviteeCache = PlayerManager.fromBukkitPlayer(invitee)
        checkNotNull(senderCache.civilization, Localization.Warnings.NO_CIV)
        val civilization = senderCache.civilization!!
        checkBoolean(
            !civilization.relationships.outlaws.contains(inviteeCache),
            Localization.Warnings.CANNOT_INVITE_OUTLAW
        )
        inviteeCache.civilizationInvite = civilization
        tellSuccess("{2}Successfully sent an invite to {1}" + invitee.name)
        Common.tell(
            invitee,
            "{2}Received a Civilization invite from {1}" + inviteeCache.civilizationInvite!!.name
        )
    }

    init {
        usage = "<player>"
        setDescription("Invite a player to your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}