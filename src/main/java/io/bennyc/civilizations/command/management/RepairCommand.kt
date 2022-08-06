/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.management

import io.bennyc.civilizations.PermissionChecker.canManageCiv
import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.war.Damages
import org.bukkit.Bukkit
import org.bukkit.Location
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.ChunkedTask
import java.text.DecimalFormat

class RepairCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "repair") {
    override fun onCommand() {
        checkConsole()
        io.bennyc.civilizations.manager.PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, io.bennyc.civilizations.settings.Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civ ->
                checkBoolean(
                    canManageCiv(civPlayer, civ),
                    io.bennyc.civilizations.settings.Localization.Warnings.CANNOT_MANAGE_CIV
                )
                checkBoolean(
                    civ.damages != null,
                    io.bennyc.civilizations.settings.Localization.Warnings.NULL_RESULT.replace("{item}", "damages")
                )
                var percentage = 100
                if (args.isNotEmpty()) {
                    percentage = findNumber(
                        0,
                        1,
                        100,
                        io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            io.bennyc.civilizations.settings.Localization.NUMBER + " 1-100"
                        )
                    )
                }
                val damages: Damages = civ.damages!!
                val locationList: List<Location> = ArrayList(damages.brokenBlocksMap.keys.sortedBy { it.y })

                repairDamages(damages, civ, locationList, percentage)

            }
        }
    }

    private fun repairDamages(
        damages: Damages,
        civ: Civilization,
        locationList: List<Location>,
        percentage: Int
    ) {

        val cost = locationList.size * io.bennyc.civilizations.settings.Settings.REPAIR_COST_PER_BLOCK
        checkBoolean(
            civ.bank.balance - cost >= 0,
            io.bennyc.civilizations.settings.Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace(
                "{cost}",
                cost.toString().format(DecimalFormat.getCurrencyInstance())
            )
        )
        civ.bank.removeBalance(cost)


        object : ChunkedTask(io.bennyc.civilizations.settings.Settings.BLOCKS_PER_SECONDS_REPAIR) {
            val handledLocations: MutableList<Location> = ArrayList()
            override fun onProcess(index: Int) {
                val location = locationList[index]
                if (io.bennyc.civilizations.PermissionChecker.isSwitchable(location.block.type))
                    return
                location.block.blockData = Bukkit.createBlockData(damages.brokenBlocksMap[location]!!)
                handledLocations.add(location)
            }

            override fun canContinue(index: Int): Boolean {
                return index < locationList.size * (percentage / 100)
            }

            override fun onFinish(gracefully: Boolean) {
                damages.brokenBlocksMap.keys.removeAll(handledLocations)
                if (damages.brokenBlocksMap.isEmpty()) civ.damages = null
                io.bennyc.civilizations.manager.CivManager.saveAsync(civ)
                tellSuccess(
                    io.bennyc.civilizations.settings.Localization.Notifications.SUCCESS_REPAIR.replace(
                        "{amount}",
                        handledLocations.size.toString()
                    ).replace("{cost}", cost.toString().format(DecimalFormat.getCurrencyInstance()))
                )
            }
        }.startChain()
    }


    init {
        usage = "[%]"
        setDescription("Repair the War Damages of your Civilization by the defined percentage. (100 if not defined)")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}