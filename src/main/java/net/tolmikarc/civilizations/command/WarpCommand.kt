package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.addCooldownTimer
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.util.*

class WarpCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "warp") {
    override fun onCommand() {
        checkConsole()
        CivPlayer.fromBukkitPlayer(player).run {
            checkNotNull(civilization, "You do not have a Civilization")
            val warp = civilization!!.warps[args[0]]
            checkNotNull(warp, "Your Civilization does not have a home.")
            checkBoolean(
                !hasCooldown(playerUUID, CooldownTask.CooldownType.TELEPORT),
                "Please wait " + getCooldownRemaining(
                    playerUUID,
                    CooldownTask.CooldownType.TELEPORT
                ) + " seconds before teleporting again."
            )
            player.teleport(warp!!)
            addCooldownTimer(playerUUID, CooldownTask.CooldownType.TELEPORT)
        }
    }

    override fun tabComplete(): List<String> {
        val warps: MutableList<String> = ArrayList()
        CivPlayer.fromBukkitPlayer(player).let { civPlayer ->
            if (civPlayer.civilization != null) {
                val civilization = civPlayer.civilization
                warps.addAll(civilization!!.warps.keys)
            }
            return if (args.size == 1) warps else super.tabComplete()
        }
        return super.tabComplete()
    }

    init {
        setDescription("Teleport to a Civilization Warp")
        usage = "<warp>"
        minArguments = 1
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}