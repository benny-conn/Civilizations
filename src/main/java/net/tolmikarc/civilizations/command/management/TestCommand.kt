/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import net.tolmikarc.civilizations.CivilizationsPlugin
import net.tolmikarc.civilizations.packet.NameTag
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.HookManager

class TestCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "test") {
    override fun onCommand() {
        HookManager.addPacketListener(object :
            PacketAdapter(CivilizationsPlugin.instance, PacketType.Play.Server.PLAYER_INFO) {
            override fun onPacketSending(event: PacketEvent) {
                println("PACKET")
                val packet = event.packet
                val ping = (player as CraftPlayer).handle.ping
                val wrappedGameProfile = WrappedGameProfile.fromPlayer(player)
                val nativeGameMode = EnumWrappers.NativeGameMode.fromBukkit(player.gameMode)
                val tabName = WrappedChatComponent.fromText(player.playerListName)
                val wrappedSignedProperty =
                    PlayerInfoData(
                        wrappedGameProfile,
                        ping,
                        nativeGameMode,
                        tabName
                    ).profile.properties["textures"].iterator()
                        .next()
                packet.playerInfoDataLists.write(0, NameTag.getPlayerInfoDataList(player))
                packet.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME)
                packet.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER)
                val playerInfoData = PlayerInfoData(wrappedGameProfile.withName("Poop"), ping, nativeGameMode, tabName)
                playerInfoData.profile.properties.clear()
                playerInfoData.profile.properties["textures"].add(wrappedSignedProperty)
                packet.playerInfoDataLists.write(0, listOf(playerInfoData))
                packet.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.UPDATE_DISPLAY_NAME)
            }
        })
    }
}