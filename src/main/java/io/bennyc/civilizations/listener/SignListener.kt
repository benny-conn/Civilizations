/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.listener

import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.CooldownTask.Companion.getCooldownRemaining
import io.bennyc.civilizations.task.CooldownTask.Companion.hasCooldown
import io.bennyc.civilizations.util.ClaimUtil
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Tag
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.Valid
import org.mineacademy.fo.model.HookManager

class SignListener : Listener {

    private val plainText = PlainTextComponentSerializer.plainText()

    @EventHandler
    fun onSignCreation(event: SignChangeEvent) {
        val firstLine = plainText.serialize(event.line(0) ?: Component.empty())
        val secondLine = plainText.serialize(event.line(1) ?: Component.empty())

        val player = event.player
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        val civ = civPlayer.civilization ?: return

        if (!ClaimUtil.isLocationInCiv(event.block.location, civ)) return

        if (firstLine.equals("[CivWarp]", true)) {
            if (!civ.warps.containsKey(secondLine)) {
                Messenger.error(player, io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", "warp"))
                event.isCancelled = true
                return
            }
            if (io.bennyc.civilizations.settings.Settings.WARP_SIGN_COST > 0) {
                Valid.checkBoolean(
                    HookManager.getBalance(player) - io.bennyc.civilizations.settings.Settings.WARP_SIGN_COST > 0,
                    io.bennyc.civilizations.settings.Localization.Warnings.INSUFFICIENT_PLAYER_FUNDS.replace(
                        "{cost}",
                        io.bennyc.civilizations.settings.Settings.WARP_SIGN_COST.toString()
                    )
                )
                HookManager.withdraw(player, io.bennyc.civilizations.settings.Settings.WARP_SIGN_COST)
            }
            event.line(
                0,
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(io.bennyc.civilizations.constants.Constants.WARP_SIGN_TAG)
            )
            event.line(2, Component.empty())
            event.line(3, Component.empty())

            Messenger.success(
                player,
                "{1}Successfully created Warp Sign" + if (io.bennyc.civilizations.settings.Settings.WARP_SIGN_COST > 0) " for ${io.bennyc.civilizations.settings.Settings.WARP_SIGN_COST}" else ""
            )
        }
    }

    @EventHandler
    fun onSignInteract(event: PlayerInteractEvent) {
        val player = event.player
        val civPlayer = io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player)
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clickedBlock = event.clickedBlock ?: return
        if (!Tag.SIGNS.isTagged(clickedBlock.type)) return
        val sign = clickedBlock.state as Sign
        val signSide = sign.getTargetSide(player)
        if (plainText.serialize(signSide.line(0)).equals("[CivWarp]", ignoreCase = true)) {
            Valid.checkBoolean(
                !hasCooldown(civPlayer, CooldownTask.CooldownType.TELEPORT),
                io.bennyc.civilizations.settings.Localization.Warnings.COOLDOWN_WAIT.replace(
                    "{duration}",
                    getCooldownRemaining(civPlayer, CooldownTask.CooldownType.TELEPORT).toString()
                )
            )
            ClaimUtil.getCivFromLocation(sign.location)?.warps?.get(plainText.serialize(signSide.line(1)))?.let {
                player.teleportAsync(it)
                    .thenAccept { result -> if (result) Messenger.success(player, "{1}Successfully teleported to warp!") }
            }
        }

    }


}
