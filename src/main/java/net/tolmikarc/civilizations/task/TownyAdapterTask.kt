package net.tolmikarc.civilizations.task

import com.palmergames.bukkit.towny.TownyUniverse
import com.palmergames.bukkit.towny.`object`.Town
import net.tolmikarc.civilizations.adapter.TownyAdapter
import net.tolmikarc.civilizations.model.Civilization
import org.mineacademy.fo.model.ChunkedTask
import java.util.*

class TownyAdapterTask(processAmount: Int) : ChunkedTask(processAmount) {
    var towns: List<Town> = ArrayList<Town>(TownyUniverse.getInstance().towns)
    override fun onProcess(i: Int) {
        Civilization.createCiv(TownyAdapter.convertTownToCiv(towns[i], true))
    }

    override fun canContinue(i: Int): Boolean {
        return i < towns.size
    }

    override fun onFinish() {
        TownyAdapter.convertSettingsToTowny()
        TownyAdapter.adaptEnemiesAndAllies()
    }
}