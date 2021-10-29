/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Location
import java.util.*

data class CivPlayer(override val uuid: UUID) : UniquelyIdentifiable {
    var playerName: String? = null
        set(value) {
            if (value != null)
                PlayerManager.byName[value.toLowerCase()] = this
            field = value
        }
    var civilization: Civilization? = null
    var civilizationInvite: Civilization? = null
    var power = 0
    val selection: Selection = Selection()
    var visualizing = false
    var flying = false
    var raidBlocksDestroyed = 0
    var lastLocationBeforeRaid: Location? = null


    fun addPower(power: Int) {
        this.power += power
        PlayerManager.queueForSaving(this)
    }

    fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        PlayerManager.queueForSaving(this)
    }

    fun addRaidBlocksDestroyed(amount: Int) {
        raidBlocksDestroyed += amount
        addPower(Settings.POWER_RAID_BLOCK * amount)
    }


}