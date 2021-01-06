/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*

data class CivBank(val civilization: Civ) : ConfigSerializable {

    var balance: Double = 0.0


    fun addBalance(amount: Double) {
        balance += amount
        civilization.addPower(Settings.POWER_MONEY_WEIGHT * amount.toInt())
    }

    fun removeBalance(amount: Double) {
        balance -= amount
        civilization.removePower(Settings.POWER_MONEY_WEIGHT * amount.toInt())
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civilization", civilization.uuid)
        map.put("Balance", balance)
        return map
    }

    companion object {


        @JvmStatic
        fun deserialize(map: SerializedMap): CivBank {
            return CivBank(CivManager.getByUUID(map.get("Civilization", UUID::class.java))).apply {
                balance = map.getDouble("Balance")
            }
        }
    }

}