/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.model

import io.bennyc.civilizations.manager.PlayerManager
import org.bukkit.Location
import java.util.*

data class CivPlayer(override val uuid: UUID) : UniquelyIdentifiable {
    var playerName: String? = null
        set(value) {
            if (value != null)
                PlayerManager.byName[value.lowercase(Locale.getDefault())] = this
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
        PlayerManager.saveAsync(this)
    }

    fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        PlayerManager.saveAsync(this)
    }

    fun addRaidBlocksDestroyed(amount: Int) {
        raidBlocksDestroyed += amount
        addPower(io.bennyc.civilizations.settings.Settings.POWER_RAID_BLOCK * amount)
    }


}