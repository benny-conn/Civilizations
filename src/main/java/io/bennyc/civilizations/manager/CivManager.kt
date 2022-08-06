/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.manager

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.util.CivUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object CivManager : io.bennyc.civilizations.manager.Manager<Civilization> {
    override val all: Collection<Civilization>
        get() = io.bennyc.civilizations.manager.CivManager.cacheMap.values
    override val cacheMap: MutableMap<UUID, Civilization> = ConcurrentHashMap()
    override val byName: MutableMap<String, Civilization> = ConcurrentHashMap()
    override val queuedForSaving = mutableSetOf<Civilization>()

    val civNames: MutableSet<String>
        get() = io.bennyc.civilizations.manager.CivManager.byName.keys

    override fun getByUUID(uuid: UUID): Civilization {
        var civilization = io.bennyc.civilizations.manager.CivManager.cacheMap[uuid]
        if (civilization == null) {
            civilization = io.bennyc.civilizations.manager.CivManager.initialize(uuid)
            io.bennyc.civilizations.manager.CivManager.loadAsync(civilization)
        }
        return civilization
    }

    override fun getByName(name: String): Civilization? {
        return io.bennyc.civilizations.manager.CivManager.byName[name.toLowerCase()]
    }

    override fun save(saved: Civilization) {
        io.bennyc.civilizations.db.CivDatastore.save(saved)
    }

    override fun saveAsync(saved: Civilization) {
        io.bennyc.civilizations.AsyncEnvironment.run { io.bennyc.civilizations.manager.CivManager.save(saved) }
    }

    override fun load(loaded: Civilization) {
        io.bennyc.civilizations.db.CivDatastore.load(loaded)
    }

    override fun loadAsync(loaded: Civilization) {
        io.bennyc.civilizations.AsyncEnvironment.run { io.bennyc.civilizations.manager.CivManager.load(loaded) }
    }

//    override fun queueForSaving(vararg queued: Civilization) {
//        queuedForSaving.addAll(queued)
//    }

    override fun initialize(uuid: UUID): Civilization {
        val civilization = Civilization(uuid)
        io.bennyc.civilizations.manager.CivManager.cacheMap[uuid] = civilization
        return civilization
    }

    fun createCiv(name: String, player: io.bennyc.civilizations.model.CivPlayer): Civilization {
        val uuid = UUID.randomUUID()
        val civilization = io.bennyc.civilizations.manager.CivManager.initialize(uuid)
        civilization.name = name
        civilization.leader = player
        civilization.addCitizen(player)
        player.civilization = civilization
        player.addPower(CivUtil.calculateFormulaForCiv(io.bennyc.civilizations.settings.Settings.POWER_LEADER_FORMULA, civilization).toInt())
        player.addPower(CivUtil.calculateFormulaForCiv(io.bennyc.civilizations.settings.Settings.POWER_CITIZEN_FORMULA, civilization).toInt())
        io.bennyc.civilizations.manager.CivManager.byName[name] = civilization
        io.bennyc.civilizations.manager.CivManager.queuedForSaving.add(civilization)
        return civilization
    }

    fun createCiv(civilization: Civilization): Civilization {
        io.bennyc.civilizations.manager.CivManager.cacheMap[civilization.uuid] = civilization
        if (civilization.name != null)
            io.bennyc.civilizations.manager.CivManager.byName[civilization.name!!] = civilization
        io.bennyc.civilizations.manager.CivManager.queuedForSaving.add(civilization)
        return civilization
    }

    fun removeCiv(civ: Civilization) {
        for (c in io.bennyc.civilizations.manager.CivManager.cacheMap.values) {
            c.relationships.allies.remove(civ)
            c.relationships.enemies.remove(civ)
        }
        io.bennyc.civilizations.manager.CivManager.cacheMap.remove(civ.uuid)
        io.bennyc.civilizations.manager.CivManager.queuedForSaving.remove(civ)
        io.bennyc.civilizations.manager.CivManager.byName.remove(civ.name)
        io.bennyc.civilizations.db.CivDatastore.delete(civ.uuid)
    }

}