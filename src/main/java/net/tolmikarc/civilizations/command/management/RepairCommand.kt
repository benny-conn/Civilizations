/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command.management

import net.tolmikarc.civilizations.PermissionChecker.canManageCiv
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.war.Damages
import org.bukkit.Bukkit
import org.bukkit.Location
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import org.mineacademy.fo.model.ChunkedTask
import java.util.*

class RepairCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "repair") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            checkNotNull(civPlayer.civilization, Localization.Warnings.NO_CIV)
            civPlayer.civilization?.let { civ ->
                checkBoolean(canManageCiv(civPlayer, civ), Localization.Warnings.CANNOT_MANAGE_CIV)
                checkBoolean(
                    civ.damages != null,
                    Localization.Warnings.NULL_RESULT.replace("{item}", "damages")
                )
                var percentage = 100
                if (args.isNotEmpty()) {
                    percentage = findNumber(
                        0,
                        1,
                        100,
                        Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                            "{item}",
                            Localization.NUMBER + " 1-100"
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
        civ: Civ,
        locationList: List<Location>,
        percentage: Int
    ) {

        val cost = locationList.size * Settings.REPAIR_COST_PER_BLOCK
        checkBoolean(
            civ.bank.balance - cost > 0,
            Localization.Warnings.INSUFFICIENT_CIV_FUNDS.replace("{cost}", cost.toString())
        )
        civ.bank.removeBalance(cost)


        object : ChunkedTask(Settings.BLOCKS_PER_SECONDS_REPAIR) {
            val handledLocations: MutableList<Location> = ArrayList()
            override fun onProcess(index: Int) {
                val location = locationList[index]
                if (Settings.SWITCHABLES.contains(location.block.type))
                    return
                location.block.blockData = Bukkit.createBlockData(damages.brokenBlocksMap[location]!!)
                handledLocations.add(location)
            }

            override fun canContinue(index: Int): Boolean {
                return index < locationList.size * (percentage / 100)
            }

            override fun onFinish() {
                damages.brokenBlocksMap.keys.removeAll(handledLocations)
                if (damages.brokenBlocksMap.isEmpty()) civ.damages = null
                CivManager.queueForSaving(civ)
                tellSuccess("{1}Successfully repaired " + handledLocations.size + " blocks for ${Settings.CURRENCY_SYMBOL}" + cost)
            }
        }.startChain()
    }


    init {
        usage = "[%]"
        setDescription("Repair the War Damages of your Civilization by the defined percentage. (100 if not defined)")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}