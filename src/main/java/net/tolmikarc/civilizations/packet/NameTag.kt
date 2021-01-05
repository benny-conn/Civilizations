/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.packet

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.EnumWrappers.NativeGameMode
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import org.bukkit.Bukkit
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer
import org.bukkit.entity.Player
import org.mineacademy.fo.Common
import org.mineacademy.fo.PlayerUtil
import org.mineacademy.fo.model.HookManager
import java.lang.reflect.InvocationTargetException


/**
 *
 * Adaption of FrostedSnowman's Resource at https://www.spigotmc.org/threads/protocollib-change-player-nametag.301280/
 *
 */


class NameTag(private val text: String) {

    fun applyTo(player: Player, constraintPlayers: List<Player>?) {
        val protocolManager = ProtocolLibrary.getProtocolManager()
        val id = player.entityId
        val ping = (player as CraftPlayer).handle.ping
        val location = player.getLocation()
        val wrappedGameProfile = WrappedGameProfile.fromPlayer(player)
        val nativeGameMode = NativeGameMode.fromBukkit(player.getGameMode())
        val tabName = WrappedChatComponent.fromText(player.getPlayerListName())
        val wrappedSignedProperty =
            PlayerInfoData(wrappedGameProfile, ping, nativeGameMode, tabName).profile.properties["textures"].iterator()
                .next()
        val removePlayer = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)
        val addPlayer = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)
        val destroyEntity = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY)
        val namedEntitySpawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN)
        removePlayer.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)
        removePlayer.playerInfoDataLists.write(0, getPlayerInfoDataList(player))
        addPlayer.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER)
        val playerInfoData = PlayerInfoData(wrappedGameProfile.withName(text), ping, nativeGameMode, tabName)
        playerInfoData.profile.properties.clear()
        playerInfoData.profile.properties["textures"].add(wrappedSignedProperty)
        addPlayer.playerInfoDataLists.write(0, listOf(playerInfoData))
        destroyEntity.integerArrays.write(0, intArrayOf(id))
        namedEntitySpawn.integers.write(0, id)
        namedEntitySpawn.uuiDs.write(0, player.getUniqueId())
        namedEntitySpawn.doubles.write(0, location.x)
        namedEntitySpawn.doubles.write(1, location.y)
        namedEntitySpawn.doubles.write(2, location.z)
        namedEntitySpawn.bytes.write(0, (location.yaw * 256.0f / 360.0f).toInt().toByte())
        namedEntitySpawn.bytes.write(1, (location.pitch * 256.0f / 360.0f).toInt().toByte())
        protocolManager.broadcastServerPacket(removePlayer)
        protocolManager.broadcastServerPacket(addPlayer)

        Bukkit.getOnlinePlayers().stream().filter { player != it }.filter { constraintPlayers?.contains(it) ?: true }
            .forEach { p: Player ->
                try {
                    HookManager.sendPacket(p, destroyEntity)
                    HookManager.sendPacket(p, namedEntitySpawn)
                } catch (exception: InvocationTargetException) {
                    exception.printStackTrace()
                }
            }


    }


    companion object {
        fun of(text: String): NameTag {
            return NameTag(Common.colorize(text))
        }

        fun remove(player: Player) {
            val protocolManager = ProtocolLibrary.getProtocolManager()
            val id = player.entityId
            val ping = (player as CraftPlayer).handle.ping
            val location = player.getLocation()
            val wrappedGameProfile = WrappedGameProfile.fromPlayer(player)
            val nativeGameMode = NativeGameMode.fromBukkit(player.getGameMode())
            val tabName = WrappedChatComponent.fromText(player.getPlayerListName())
            val wrappedSignedProperty =
                PlayerInfoData(
                    wrappedGameProfile,
                    ping,
                    nativeGameMode,
                    tabName
                ).profile.properties["textures"].iterator()
                    .next()
            val removePlayer = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)
            val addPlayer = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)
            val destroyEntity = protocolManager.createPacket(PacketType.Play.Server.ENTITY_DESTROY)
            val namedEntitySpawn = protocolManager.createPacket(PacketType.Play.Server.NAMED_ENTITY_SPAWN)
            removePlayer.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)
            removePlayer.playerInfoDataLists.write(0, getPlayerInfoDataList(player))
            addPlayer.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER)
            val playerInfoData = PlayerInfoData(wrappedGameProfile.withName(player.name), ping, nativeGameMode, tabName)
            playerInfoData.profile.properties.clear()
            playerInfoData.profile.properties["textures"].add(wrappedSignedProperty)
            addPlayer.playerInfoDataLists.write(0, listOf(playerInfoData))
            destroyEntity.integerArrays.write(0, intArrayOf(id))
            namedEntitySpawn.integers.write(0, id)
            namedEntitySpawn.uuiDs.write(0, player.getUniqueId())
            namedEntitySpawn.doubles.write(0, location.x)
            namedEntitySpawn.doubles.write(1, location.y)
            namedEntitySpawn.doubles.write(2, location.z)
            namedEntitySpawn.bytes.write(0, (location.yaw * 256.0f / 360.0f).toInt().toByte())
            namedEntitySpawn.bytes.write(1, (location.pitch * 256.0f / 360.0f).toInt().toByte())
            protocolManager.broadcastServerPacket(removePlayer)
            protocolManager.broadcastServerPacket(addPlayer)

            Bukkit.getOnlinePlayers().stream().filter { player != it }
                .forEach { p: Player ->
                    try {
                        protocolManager.sendServerPacket(p, destroyEntity)
                        protocolManager.sendServerPacket(p, namedEntitySpawn)
                    } catch (exception: InvocationTargetException) {
                        exception.printStackTrace()
                    }
                }
        }

        private fun getPlayerInfoData(player: Player): PlayerInfoData {
            return PlayerInfoData(
                WrappedGameProfile.fromPlayer(player),
                PlayerUtil.getPing(player),
                NativeGameMode.fromBukkit(player.gameMode),
                WrappedChatComponent.fromText(player.displayName)
            )
        }

        fun getPlayerInfoDataList(player: Player): List<PlayerInfoData?> {
            return listOf(getPlayerInfoData(player))
        }

    }


}