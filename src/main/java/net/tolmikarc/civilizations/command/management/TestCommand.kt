/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class TestCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "test") {
    override fun onCommand() {
        if (PermissionChecker.isSwitchable(player.getTargetBlock(null, 5).type))
            tellInfo("YES")

    }


}