/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Settings
import java.util.*

data class CivPlayer(override val uuid: UUID) : CPlayer {
    override var playerName: String? = null
        set(value) {
            if (value != null)
                PlayerManager.byName[value] = this
            field = value
        }
    override var civilization: Civ? = null
    override var civilizationInvite: Civ? = null
    override var power = 0
    override val selection: Selection = Selection()
    override var completedTutorial: Boolean = false
    override var visualizing = false
    override var mapping: Boolean = false
    override var flying = false
    override var raidBlocksDestroyed = 0


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