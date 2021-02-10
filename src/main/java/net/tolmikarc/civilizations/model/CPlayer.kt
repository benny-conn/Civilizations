/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.model.impl.Selection
import org.bukkit.Location

interface CPlayer : UniquelyIdentifiable {

    var playerName: String?
    var civilization: Civ?
    var civilizationInvite: Civ?
    var power: Int
    val selection: Selection
    var visualizing: Boolean
    var flying: Boolean
    var raidBlocksDestroyed: Int
    var lastLocationBeforeRaid: Location?

    fun addPower(power: Int)
    fun removePower(power: Int)
    fun addRaidBlocksDestroyed(amount: Int)
}