/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

interface CPlayer : UniquelyIdentifiable {

    var playerName: String?
    var civilization: Civ?
    var civilizationInvite: Civ?
    var power: Int
    val selection: Selection
    var completedTutorial: Boolean
    var visualizing: Boolean
    var flying: Boolean
    var mapping: Boolean
    var raidBlocksDestroyed: Int

    fun addPower(power: Int)
    fun removePower(power: Int)
    fun addRaidBlocksDestroyed(amount: Int)
}