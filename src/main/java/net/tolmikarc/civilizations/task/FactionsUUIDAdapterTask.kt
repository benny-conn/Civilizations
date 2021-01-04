package net.tolmikarc.civilizations.task

import org.mineacademy.fo.model.ChunkedTask

class FactionsUUIDAdapterTask(processAmount: Int) : ChunkedTask(processAmount) {
    //var factions: List<Faction> = Factions.getInstance().getAllFactions()
    override fun onProcess(i: Int) {
        //Civilization.createCiv(FactionsUUIDAdapter.convertFactionToCiv(factions[i], true))
    }

    override fun canContinue(i: Int): Boolean {
        //return i < factions.size
        return false
    }
}