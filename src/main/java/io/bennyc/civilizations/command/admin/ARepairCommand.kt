/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command.admin

import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.war.Damages
import org.bukkit.Bukkit
import org.bukkit.Location
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.ChunkedTask
import java.text.DecimalFormat

class ARepairCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "repair") {
    override fun onCommand() {
        val civ = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
        checkNotNull(
            civ,
            io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                "{item}",
                io.bennyc.civilizations.settings.Localization.CIVILIZATION
            )
        )

        checkBoolean(
            civ!!.damages != null,
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

    private fun repairDamages(
        damages: Damages,
        civ: Civilization,
        locationList: List<Location>,
        percentage: Int
    ) {

        val cost = locationList.size * io.bennyc.civilizations.settings.Settings.REPAIR_COST_PER_BLOCK


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
                        "{blocks}",
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