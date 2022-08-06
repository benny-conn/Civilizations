/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.permissions

import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable

class Toggleables : ConfigSerializable {
    var pvp = false
    var fire = false
    var explosion = false
    var mobs = false
    var public = true
    var inviteOnly = true
    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("PVP", pvp)
        map.put("Fire", fire)
        map.put("Explosion", explosion)
        map.put("Mobs", mobs)
        map.put("Public", public)
        map.put("Invite_Only", inviteOnly)
        return map
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Toggleables {
            val claimToggleables = Toggleables()
            claimToggleables.explosion = map.getBoolean("Explosion")
            claimToggleables.fire = map.getBoolean("Fire")
            claimToggleables.mobs = map.getBoolean("Mobs")
            claimToggleables.pvp = map.getBoolean("PVP")
            claimToggleables.public = map.getBoolean("Public")
            claimToggleables.inviteOnly = map.getBoolean("Invite_Only")

            return claimToggleables
        }
    }
}