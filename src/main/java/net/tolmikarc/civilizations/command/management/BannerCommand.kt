/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Tag
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager

class BannerCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "banner") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let {
            checkNotNull(it.civilization, Localization.Warnings.NO_CIV)
            it.civilization?.apply {
                val param = args[0]
                if (param.equals("set", ignoreCase = true)) {
                    val banner = player.inventory.itemInMainHand
                    checkBoolean(Tag.BANNERS.isTagged(banner.type), Localization.Warnings.INVALID_HAND_ITEM)
                    checkBoolean(
                        canManageCiv(it, this),
                        Localization.Warnings.LEADER
                    )
                    this.banner = banner
                    tellSuccess("{2}Successfully set your Civilization's Banner to the Banner in your hand")
                } else if (param.equals("get", ignoreCase = true)) {
                    checkNotNull(this.banner, Localization.Warnings.NO_BANNER)
                    this.banner?.let { banner ->
                        val cost = 200
                        checkBoolean(
                            HookManager.getBalance(player) >= cost,
                            Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace("{cost}", cost.toString())
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
        return if (args.size == 1) listOf("set", "get") else super.tabComplete()
    }

    init {
        setDescription("Set the Banner of your Civilization to the Banner in your hand")
        minArguments = 1
        usage = "<set | get>"
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}