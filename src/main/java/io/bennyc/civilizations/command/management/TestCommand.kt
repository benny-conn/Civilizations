/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class TestCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "test") {
    override fun onCommand() {
        if (io.bennyc.civilizations.PermissionChecker.isSwitchable(player.getTargetBlock(null, 5).type))
            tellInfo("YES")

    }


}