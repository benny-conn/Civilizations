/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class InviteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "add|invite") {
    override fun onCommand() {
        checkConsole()
        val invitee =
            findPlayer(args[0], io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", io.bennyc.civilizations.settings.Localization.PLAYER))
        checkBoolean(invitee != player, io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_SPECIFY_SELF)
        val senderCache = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val inviteeCache = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(invitee)
        checkNotNull(senderCache.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
        val civilization = senderCache.civilization!!
        checkBoolean(
            !civilization.relationships.outlaws.contains(inviteeCache),
            io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_INVITE_OUTLAW
        )
        inviteeCache.civilizationInvite = civilization
        tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_COMMAND)
        Messenger.info(
            invitee,
            io.bennyc.civilizations.settings.Localization.Notifications.INVITE_RECEIVED.replace("{civ}", civilization.name!!)
        )
    }

    init {
        usage = "<player>"
        setDescription("Invite a player to your Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}