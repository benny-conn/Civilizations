/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */
package net.tolmikarc.civilizations.model

import org.bukkit.Location
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable

class Selection : ConfigSerializable {
    val primary: Location? = null
    val secondary: Location? = null
    val blockAtPrimary
        get() = primary?.block
    val blockAtSecondary
        get() = secondary?.block


    override fun serialize(): SerializedMap {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmStatic
        fun deserialize(map: SerializedMap): Selection {
            TODO("Not yet implemented")
        }
    }
}