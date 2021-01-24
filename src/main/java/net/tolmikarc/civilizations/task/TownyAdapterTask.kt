/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.task

import com.palmergames.bukkit.towny.TownyUniverse
import com.palmergames.bukkit.towny.`object`.Town
import net.tolmikarc.civilizations.adapter.TownyAdapter
import net.tolmikarc.civilizations.manager.CivManager
import org.mineacademy.fo.Messenger
import org.mineacademy.fo.model.ChunkedTask
import java.util.*

class TownyAdapterTask(processAmount: Int) : ChunkedTask(processAmount) {
    private val towns: List<Town> = ArrayList(TownyUniverse.getInstance().towns.filter { it.hasValidUUID() && it.hasSpawn() })
    override fun onProcess(i: Int) {
        val civ = TownyAdapter.convertTownToCiv(towns[i])
        if (civ != null)
        CivManager.createCiv(civ)
    }

    override fun canContinue(i: Int): Boolean {
        return i < towns.size
    }

    override fun onFinish() {
        TownyAdapter.adaptEnemiesAndAllies()
        Messenger.broadcastAnnounce("DONE ADAPTING TOWNY")
    }
}