/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.manager

import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.db.CivDatastore
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object CivManager : Manager<Civilization> {
    override val all: Collection<Civilization>
        get() = cacheMap.values
    override val cacheMap: MutableMap<UUID, Civilization> = ConcurrentHashMap()
    override val byName: MutableMap<String, Civilization> = ConcurrentHashMap()
    override val queuedForSaving = mutableSetOf<Civilization>()

    val civNames: MutableSet<String>
        get() = byName.keys

    override fun getByUUID(uuid: UUID): Civilization {
        var civilization = cacheMap[uuid]
        if (civilization == null) {
            civilization = initialize(uuid)
            loadAsync(civilization)
        }
        return civilization
    }

    override fun getByName(name: String): Civilization? {
        return byName[name.toLowerCase()]
    }

    override fun save(saved: Civilization) {
        CivDatastore.save(saved)
    }

    override fun saveAsync(saved: Civilization) {
        AsyncEnvironment.run { save(saved) }
    }

    override fun load(loaded: Civilization) {
        CivDatastore.load(loaded)
    }

    override fun loadAsync(loaded: Civilization) {
        AsyncEnvironment.run { load(loaded) }
    }

    override fun queueForSaving(vararg queued: Civilization) {
        queuedForSaving.addAll(queued)
    }

    override fun initialize(uuid: UUID): Civilization {
        val civilization = Civilization(uuid)
        cacheMap[uuid] = civilization
        return civilization
    }

    fun createCiv(name: String, player: CivPlayer): Civilization {
        val uuid = UUID.randomUUID()
        val civilization = initialize(uuid)
        civilization.name = name
        civilization.leader = player
        civilization.addCitizen(player)
        player.civilization = civilization
        player.addPower(CivUtil.calculateFormulaForCiv(Settings.POWER_LEADER_FORMULA, civilization).toInt())
        player.addPower(CivUtil.calculateFormulaForCiv(Settings.POWER_CITIZEN_FORMULA, civilization).toInt())
        byName[name] = civilization
        queuedForSaving.add(civilization)
        return civilization
    }

    fun createCiv(civilization: Civilization): Civilization {
        cacheMap[civilization.uuid] = civilization
        if (civilization.name != null)
            byName[civilization.name!!] = civilization
        queuedForSaving.add(civilization)
        return civilization
    }

    fun removeCiv(civ: Civilization) {
        for (c in cacheMap.values) {
            c.relationships.allies.remove(civ)
            c.relationships.enemies.remove(civ)
        }
        cacheMap.remove(civ.uuid)
        queuedForSaving.remove(civ)
        byName.remove(civ.name)
        CivDatastore.delete(civ.uuid)
    }

}