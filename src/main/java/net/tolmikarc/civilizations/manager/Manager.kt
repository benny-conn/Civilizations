/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.manager

import java.util.*

interface Manager<T> {
    val all: Collection<T>
    val cacheMap: MutableMap<UUID, T>
    val byName: MutableMap<String, T>
    val queuedForSaving: MutableSet<T>
    fun getByUUID(uuid: UUID): T
    fun getByName(name: String): T?
    fun initialize(uuid: UUID): T
    fun save(saved: T)
    fun saveAsync(saved: T)
    fun load(loaded: T)
    fun loadAsync(loaded: T)
//    fun queueForSaving(vararg queued: T)
//    fun saveQueuedForSaving() {
//        for (saved in queuedForSaving) {
//            save(saved)
//        }
//    }
}