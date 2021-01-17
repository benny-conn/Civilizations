/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.listener

import io.papermc.lib.PaperLib
import net.tolmikarc.civilizations.constants.Constants
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import net.tolmikarc.civilizations.task.CooldownTask.Companion.hasCooldown
import net.tolmikarc.civilizations.util.ClaimUtil
import org.bukkit.Tag
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.mineacademy.fo.Common
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.Valid
import org.mineacademy.fo.model.HookManager

class SignListener : Listener {

    @EventHandler
    fun onSignCreation(event: SignChangeEvent) {
        val firstLine = event.getLine(0) ?: return
        val secondLine = event.getLine(1) ?: return

        val player = event.player
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization ?: return

        if (!ClaimUtil.isLocationInCiv(event.block.location, civ)) return

        if (firstLine.equals("[CivWarp]", true)) {
            if (!civ.warps.containsKey(secondLine)) {
                Messenger.error(player, Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp"))
                event.isCancelled = true
                return
            }
            if (Settings.WARP_SIGN_COST > 0) {
                Valid.checkBoolean(
                    HookManager.getBalance(player) - Settings.WARP_SIGN_COST > 0,
                    Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace(
                        "{cost}",
                        Settings.WARP_SIGN_COST.toString()
                    )
                )
                HookManager.withdraw(player, Settings.WARP_SIGN_COST)
            }
            event.setLine(0, Common.colorize(Constants.WARP_SIGN_TAG))
            event.setLine(2, "")
            event.setLine(3, "")

            Messenger.success(
                player,
                "{1}Successfully created Warp Sign" + if (Settings.WARP_SIGN_COST > 0) " for ${Settings.CURRENCY_SYMBOL}${Settings.WARP_SIGN_COST}" else ""
            )
        }
    }

    @EventHandler
    fun onSignInteract(event: PlayerInteractEvent) {
        val player = event.player
        val civPlayer = PlayerManager.fromBukkitPlayer(player)
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        if (!Tag.SIGNS.isTagged(event.clickedBlock!!.type)) return
        val sign = event.clickedBlock!!.state as Sign
        if (sign.getLine(0) == Common.colorize(Constants.WARP_SIGN_TAG)) {
            Valid.checkBoolean(
                !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                Localization.Warnings.COOLDOWN_WAIT.replace(
                    "{duration}",
                    getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                )
            )
            ClaimUtil.getCivFromLocation(sign.location)?.warps?.get(sign.getLine(1))?.let {
                PaperLib.teleportAsync(
                    player,
                    it
                ).thenAccept { result -> if (result) Messenger.success(player, "{1}Successfully teleported to warp!") }
            }
        }

    }


}