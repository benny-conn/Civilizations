/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.model.impl

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.util.CivUtil
import net.tolmikarc.civilizations.util.MathUtil
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*
import kotlin.math.abs

data class Bank(val civilization: Civ) : ConfigSerializable {

    var balance: Double = 0.0
    val upkeep: Double
        get() = abs(MathUtil.doubleToMoney(CivUtil.calculateFormulaForCiv(Settings.UPKEEP_FORMULA, civilization)))
    var taxes: Double = 0.0


    fun addBalance(amount: Double) {
        balance += amount
        civilization.addPower(Settings.POWER_MONEY_WEIGHT * amount.toInt())
    }

    fun removeBalance(amount: Double) {
        if (balance - amount < 0) {
            balance = 0.0
            val newAmount = abs(0 - balance)
            civilization.removePower(Settings.POWER_MONEY_WEIGHT * newAmount.toInt())
        } else {
            balance -= amount
            civilization.removePower(Settings.POWER_MONEY_WEIGHT * amount.toInt())
        }
    }

    override fun serialize(): SerializedMap {
        val map = SerializedMap()
        map.put("Civilization", civilization.uuid)
        map.put("Balance", balance)
        return map
    }

    companion object {


        @JvmStatic
        fun deserialize(map: SerializedMap): Bank {
            return Bank(CivManager.getByUUID(map.get("Civilization", UUID::class.java))).apply {
                balance = map.getDouble("Balance")
            }
        }
    }

}