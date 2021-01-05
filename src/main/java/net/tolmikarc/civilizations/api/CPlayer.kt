/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.api

import net.tolmikarc.civilizations.model.Civilization
import org.bukkit.Location

interface CPlayer {

    var playerName: String?
    var civilization: Civilization?
    var civilizationInvite: Civilization?
    var vertex1: Location?
    var vertex2: Location?
    var completedTutorial: Boolean
    var visualizing: Boolean
    var flying: Boolean
    var raidBlocksDestroyed: Int

    fun addPower(power: Int)
    fun removePower(power: Int)
}