/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model.impl

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Location
import java.util.*

data class CivPlayer(override val uuid: UUID) : CPlayer {
    override var playerName: String? = null
        set(value) {
            if (value != null)
                PlayerManager.byName[value.toLowerCase()] = this
            field = value
        }
    override var civilization: Civ? = null
    override var civilizationInvite: Civ? = null
    override var power = 0
    override val selection: Selection = Selection()
    override var visualizing = false
    override var flying = false
    override var raidBlocksDestroyed = 0
    override var lastLocationBeforeRaid: Location? = null


    override fun addPower(power: Int) {
        this.power += power
        PlayerManager.queueForSaving(this)
    }

    override fun removePower(power: Int) {
        if (this.power - power >= 0)
            this.power -= power
        else
            this.power = 0
        PlayerManager.queueForSaving(this)
    }

    override fun addRaidBlocksDestroyed(amount: Int) {
        raidBlocksDestroyed += amount
        addPower(Settings.POWER_RAID_BLOCK * amount)
    }


}