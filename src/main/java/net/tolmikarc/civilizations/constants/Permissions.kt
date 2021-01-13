/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.constants

import org.bukkit.permissions.Permission


object Permissions {
    val ADMIN = Permission("civilizations.admin")

    object Bypass {
        val BUILD = Permission("civilizations.bypass.build")
        val BREAK = Permission("civilizations.bypass.break")
        val SWITCH = Permission("civilizations.bypass.switch")
        val INTERACT = Permission("civilizations.bypass.interact")
    }
}