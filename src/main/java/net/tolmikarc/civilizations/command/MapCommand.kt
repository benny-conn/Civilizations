/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.PlayerManager
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class MapCommand(parent: SimpleCommandGroup) : SimpleSubCommand(parent, "map") {
    override fun onCommand() {
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        civPlayer.mapping = !civPlayer.mapping
        tellSuccess("Successfully switched mapping to: ${civPlayer.mapping}")
    }

}