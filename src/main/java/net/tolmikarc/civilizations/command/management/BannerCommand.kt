/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Tag
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager

class BannerCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "banner") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, "You must have a Civilization to set a Civilization's banner.")
            it.civilization?.apply {
                val param = args[0]
                if (param.equals("set", ignoreCase = true)) {
                    val banner = player.inventory.itemInMainHand
                    checkBoolean(Tag.BANNERS.isTagged(banner.type), "You must be holding a banner to use this command.")
                    checkBoolean(
                        canManageCiv(it, this),
                        "You must be the leader of the Civilization to set the Banner"
                    )
                    this.banner = banner
                    tellSuccess("{2}Successfully set your Civilization's Banner to the Banner in your hand")
                } else if (param.equals("get", ignoreCase = true)) {
                    checkNotNull(this.banner, "Your Civilization does not have a banner.")
                    this.banner?.let { banner ->
                        checkBoolean(
                            HookManager.getBalance(player) >= 200,
                            "You need at least $200 to obtain this item."
                        )
                        HookManager.withdraw(player, 200.0)
                        tellSuccess("{2}Successfully obtained your Civilization's Banner")
                        player.inventory.addItem(banner)
                    }
                }
            }
        }

    }

    override fun tabComplete(): List<String>? {
        return if (args.size == 1) listOf("set", "get") else null
    }

    init {
        setDescription("Set the Banner of your Civilization to the Banner in your hand")
        minArguments = 1
        usage = "<set | get>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}