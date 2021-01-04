/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.util

import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import org.bukkit.entity.Player
import org.mineacademy.fo.PlayerUtil


object NmsUtil {
    private fun getPlayerInfoData(player: Player): PlayerInfoData {
        return PlayerInfoData(
            WrappedGameProfile.fromPlayer(player),
            PlayerUtil.getPing(player),
            EnumWrappers.NativeGameMode.fromBukkit(player.gameMode),
            WrappedChatComponent.fromText(player.displayName)
        )
    }

    fun getPlayerInfoDataList(player: Player): List<PlayerInfoData?> {
        return listOf(getPlayerInfoData(player))
    }
}