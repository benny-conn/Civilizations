/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.manager

import net.tolmikarc.civilizations.AsyncEnvironment
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.CivPlayer
import org.bukkit.entity.Player
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object PlayerManager : Manager<CPlayer> {
    override val all: MutableCollection<CPlayer>
        get() = cacheMap.values
    override val cacheMap: MutableMap<UUID, CPlayer> = HashMap()
    override val byName: MutableMap<String, CPlayer> = HashMap()
    override val queuedForSaving: MutableSet<CPlayer> = HashSet()

    override fun getByUUID(uuid: UUID): CPlayer {
        var civPlayer = cacheMap[uuid]
        if (civPlayer == null) {
            civPlayer = initialize(uuid)
            loadAsync(civPlayer)
        }
        return civPlayer
    }

    override fun getByName(name: String): CPlayer? {
        return byName[name.toLowerCase()]
    }

    override fun save(saved: CPlayer) {
        PlayerDatastore.save(saved)
    }

    override fun saveAsync(saved: CPlayer) {
        AsyncEnvironment.run { save(saved) }
    }

    override fun load(loaded: CPlayer) {
        PlayerDatastore.load(loaded)
    }


    override fun loadAsync(loaded: CPlayer) {
        AsyncEnvironment.run { load(loaded) }
    }

    override fun queueForSaving(vararg queued: CPlayer) {
        queuedForSaving.addAll(queued)
    }

    override fun initialize(uuid: UUID): CPlayer {
        val civPlayer = CivPlayer(uuid)
        cacheMap[uuid] = civPlayer
        return civPlayer
    }

    fun fromBukkitPlayer(player: Player): CPlayer {
        return cacheMap[player.uniqueId] ?: initialize(player.uniqueId)
    }

}