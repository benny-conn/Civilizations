/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package io.bennyc.civilizations.manager

import io.bennyc.civilizations.AsyncEnvironment
import io.bennyc.civilizations.db.PlayerDatastore
import io.bennyc.civilizations.model.CivPlayer
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object PlayerManager : io.bennyc.civilizations.manager.Manager<io.bennyc.civilizations.model.CivPlayer> {
    override val all: Collection<io.bennyc.civilizations.model.CivPlayer>
        get() = io.bennyc.civilizations.manager.PlayerManager.cacheMap.values
    override val cacheMap: MutableMap<UUID, io.bennyc.civilizations.model.CivPlayer> = ConcurrentHashMap()
    override val byName: MutableMap<String, io.bennyc.civilizations.model.CivPlayer> = ConcurrentHashMap()
    override val queuedForSaving = mutableSetOf<io.bennyc.civilizations.model.CivPlayer>()

    override fun getByUUID(uuid: UUID): io.bennyc.civilizations.model.CivPlayer {
        var civPlayer = io.bennyc.civilizations.manager.PlayerManager.cacheMap[uuid]
        if (civPlayer == null) {
            civPlayer = io.bennyc.civilizations.manager.PlayerManager.initialize(uuid)
            io.bennyc.civilizations.manager.PlayerManager.loadAsync(civPlayer)
        }
        return civPlayer
    }

    override fun getByName(name: String): io.bennyc.civilizations.model.CivPlayer? {
        return io.bennyc.civilizations.manager.PlayerManager.byName[name.toLowerCase()]
    }

    override fun save(saved: io.bennyc.civilizations.model.CivPlayer) {
        io.bennyc.civilizations.db.PlayerDatastore.save(saved)
    }

    override fun saveAsync(saved: io.bennyc.civilizations.model.CivPlayer) {
        io.bennyc.civilizations.AsyncEnvironment.run { io.bennyc.civilizations.manager.PlayerManager.save(saved) }
    }

    override fun load(loaded: io.bennyc.civilizations.model.CivPlayer) {
        io.bennyc.civilizations.db.PlayerDatastore.load(loaded)
    }


    override fun loadAsync(loaded: io.bennyc.civilizations.model.CivPlayer) {
        io.bennyc.civilizations.AsyncEnvironment.run { io.bennyc.civilizations.manager.PlayerManager.load(loaded) }
    }


    override fun initialize(uuid: UUID): io.bennyc.civilizations.model.CivPlayer {
        val civPlayer = io.bennyc.civilizations.model.CivPlayer(uuid)
        io.bennyc.civilizations.manager.PlayerManager.cacheMap[uuid] = civPlayer
        return civPlayer
    }

    fun fromBukkitPlayer(player: Player): io.bennyc.civilizations.model.CivPlayer {
        return io.bennyc.civilizations.manager.PlayerManager.cacheMap[player.uniqueId] ?: io.bennyc.civilizations.manager.PlayerManager.initialize(
            player.uniqueId
        )
    }

}