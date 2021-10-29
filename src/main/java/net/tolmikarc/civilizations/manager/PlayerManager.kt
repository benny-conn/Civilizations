/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.manager

import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.model.CivPlayer
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlayerManager : Manager<CivPlayer> {
    override val all: Collection<CivPlayer>
        get() = cacheMap.values
    override val cacheMap: MutableMap<UUID, CivPlayer> = ConcurrentHashMap()
    override val byName: MutableMap<String, CivPlayer> = ConcurrentHashMap()
    override val queuedForSaving = mutableSetOf<CivPlayer>()

    override fun getByUUID(uuid: UUID): CivPlayer {
        var civPlayer = cacheMap[uuid]
        if (civPlayer == null) {
            civPlayer = initialize(uuid)
            loadAsync(civPlayer)
        }
        return civPlayer
    }

    override fun getByName(name: String): CivPlayer? {
        return byName[name.toLowerCase()]
    }

    override fun save(saved: CivPlayer) {
        PlayerDatastore.save(saved)
    }

    override fun saveAsync(saved: CivPlayer) {
        AsyncEnvironment.run { save(saved) }
    }

    override fun load(loaded: CivPlayer) {
        PlayerDatastore.load(loaded)
    }


    override fun loadAsync(loaded: CivPlayer) {
        AsyncEnvironment.run { load(loaded) }
    }

    override fun queueForSaving(vararg queued: CivPlayer) {
        queuedForSaving.addAll(queued)
    }

    override fun initialize(uuid: UUID): CivPlayer {
        val civPlayer = CivPlayer(uuid)
        cacheMap[uuid] = civPlayer
        return civPlayer
    }

    fun fromBukkitPlayer(player: Player): CivPlayer {
        return cacheMap[player.uniqueId] ?: initialize(player.uniqueId)
    }

}