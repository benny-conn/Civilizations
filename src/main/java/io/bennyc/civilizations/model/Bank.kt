/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.model

import io.bennyc.civilizations.util.CivUtil
import io.bennyc.civilizations.util.MathUtil
import org.mineacademy.fo.collection.SerializedMap
import org.mineacademy.fo.model.ConfigSerializable
import java.util.*
import kotlin.math.abs

data class Bank(val civilization: io.bennyc.civilizations.model.Civilization) : ConfigSerializable {

    var balance: Double = 0.0
    val upkeep: Double
        get() = abs(MathUtil.doubleToMoney(CivUtil.calculateFormulaForCiv(io.bennyc.civilizations.settings.Settings.UPKEEP_FORMULA, civilization)))
    var taxes: Double = 0.0


    fun addBalance(amount: Double) {
        balance += amount
        civilization.addPower(io.bennyc.civilizations.settings.Settings.POWER_MONEY_WEIGHT * amount.toInt())
    }

    fun removeBalance(amount: Double) {
        if (balance - amount < 0) {
            balance = 0.0
            val newAmount = abs(0 - balance)
            civilization.removePower(io.bennyc.civilizations.settings.Settings.POWER_MONEY_WEIGHT * newAmount.toInt())
        } else {
            balance -= amount
            civilization.removePower(io.bennyc.civilizations.settings.Settings.POWER_MONEY_WEIGHT * amount.toInt())
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
        fun deserialize(map: SerializedMap): io.bennyc.civilizations.model.Bank {
            return io.bennyc.civilizations.model.Bank(
                io.bennyc.civilizations.manager.CivManager.getByUUID(
                    map.get(
                        "Civilization",
                        UUID::class.java
                    )
                )
            ).apply {
                balance = map.getDouble("Balance")
            }
        }
    }

}