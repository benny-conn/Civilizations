/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class InviteCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "add|invite") {
    override fun onCommand() {
        checkConsole()
        val invitee = findPlayer(args[0], "First argument must be an online player.")
        checkBoolean(invitee != player, "You cannot invite yourself to your Civilization")
        val senderCache = CivPlayer.fromBukkitPlayer(player)
        val inviteeCache = CivPlayer.fromBukkitPlayer(invitee)!!
        checkNotNull(senderCache.civilization, "You must have a Civilization to invite another player to it.")
        val civilization = senderCache.civilization!!
        checkBoolean(!civilization.outlaws.contains(inviteeCache), "You cannot invite an outlaw of your Civilization.")
        inviteeCache.civilizationInvite = civilization
        tellSuccess("${Settings.SECONDARY_COLOR}Successfully sent an invite to ${Settings.PRIMARY_COLOR}" + invitee.name)
        Common.tell(
            invitee,
            "${Settings.SECONDARY_COLOR}Received a Civilization invite from ${Settings.PRIMARY_COLOR}" + inviteeCache.civilizationInvite!!.name
        )
    }

    init {
        usage = "<player>"
        setDescription("Invite a player to your Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}