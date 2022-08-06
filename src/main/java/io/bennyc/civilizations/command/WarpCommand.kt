/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.papermc.lib.PaperLib
import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import io.bennyc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import io.bennyc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class WarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warp") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).run {
            checkNotNull(civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            val warp = civilization!!.warps[args[0]]
            checkNotNull(warp, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp"))
            checkBoolean(
                !hasCooldown(this, CooldownTask.CooldownType.TELEPORT),
                io.bennyc.civilizations.settings.Localization.Warnings.COOLDOWN_WAIT.replace(
                    "{duration}",
                    getCooldownRemaining(this, CooldownTask.CooldownType.TELEPORT).toString()
                )
            )
            PaperLib.teleportAsync(player, warp!!).thenAccept {
                if (it)
                    tellSuccess(io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_TELEPORT)
                else
                    tellError(io.bennyc.civilizations.settings.Localization.Warnings.FAILED_TELEPORT)
            }
            addCooldownTimer(this, CooldownTask.CooldownType.TELEPORT)
        }
    }

    override fun tabComplete(): List<String>? {
        val warps: MutableList<String> = ArrayList()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            if (civPlayer.civilization != null) {
                val civilization = civPlayer.civilization
                warps.addAll(civilization!!.warps.keys)
            }
            return if (args.size == 1) warps else super.tabComplete()
        }
    }

    init {
        setDescription("Teleport to a Civilization Warp")
        usage = "<warp>"
        minArguments = 1
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}