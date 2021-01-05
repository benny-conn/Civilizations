/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.api.CPlayer
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Location
import org.bukkit.entity.Player
import org.mineacademy.fo.Common
import java.util.*

data class CivPlayer(val playerUUID: UUID) : CPlayer {
    override var playerName: String? = null
        set(value) {
            byName[value] = this
            field = value
        }
    override var civilization: Civilization? = null
    override var civilizationInvite: Civilization? = null
    var power = 0
    override var vertex1: Location? = null
    override var vertex2: Location? = null
    override var completedTutorial: Boolean = false
    override var visualizing = false
    override var flying = false
    override var raidBlocksDestroyed = 0


    override fun addPower(power: Int) {
        this.power += power
        queueForSaving()
    }

    override fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        queueForSaving()
    }

    fun addRaidBlocksDestroyed(amount: Int) {
        raidBlocksDestroyed += amount
        addPower(Settings.POWER_RAID_BLOCK * amount)
    }

    fun queueForSaving() {
        QUEUED_FOR_SAVING.add(this)
    }


    companion object {
        private val QUEUED_FOR_SAVING: MutableSet<CivPlayer> = HashSet()
        val playerMap: MutableMap<UUID, CivPlayer> = HashMap()
        private val byName: MutableMap<String?, CivPlayer> = HashMap()

        fun fromBukkitPlayer(player: Player): CivPlayer {
            return playerMap[player.uniqueId] ?: initializeCivPlayer(player.uniqueId)
        }

        fun fromUUID(uuid: UUID): CivPlayer? {
            return playerMap[uuid]
        }

        fun fromName(displayName: String?): CivPlayer? {
            return byName[displayName]
        }

        fun initializeCivPlayer(uuid: UUID): CivPlayer {
            val civPlayer = CivPlayer(uuid)
            playerMap[uuid] = civPlayer
            return civPlayer
        }

        fun initialLoadFromDatabase(uuid: UUID) {
            var civPlayer = playerMap[uuid]
            if (civPlayer == null) {
                civPlayer = initializeCivPlayer(uuid)
                loadAsync(civPlayer)
            }
        }

        private fun load(player: CivPlayer) {
            PlayerDatastore.load(player)
        }

        private fun loadAsync(player: CivPlayer) {
            Common.runLaterAsync { load(player) }
        }

        private fun save(civPlayer: CivPlayer) {
            PlayerDatastore.save(civPlayer)
        }

        fun saveAsync(civPlayer: CivPlayer) {
            Common.runLaterAsync { save(civPlayer) }
        }

        fun saveQueuedForSaving() {
            for (civPlayer in QUEUED_FOR_SAVING) {
                save(civPlayer)
            }
        }
    }
}