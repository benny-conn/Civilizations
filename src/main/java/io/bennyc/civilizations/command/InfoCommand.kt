/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations.command

import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.model.Civilization
import io.bennyc.civilizations.permissions.PermissionType
import io.bennyc.civilizations.settings.Localization
import io.bennyc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand
import java.text.DecimalFormat

class InfoCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "info") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            val civilization: Civilization?
            if (args.isNotEmpty()) {
                civilization = io.bennyc.civilizations.manager.CivManager.getByName(args[0])
                checkNotNull(
                    civilization,
                    io.bennyc.civilizations.settings.Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace(
                        "{item}",
                        io.bennyc.civilizations.settings.Localization.CIVILIZATION
                    )
                )
            } else {
                checkNotNull(
                    civPlayer.civilization,
                    Localization.Warnings.NO_CIV
                )
                civilization = civPlayer.civilization
            }
            civilization?.run { sendInfo(this) }
        }
    }

    private fun sendInfo(civilization: Civilization) {
        val permissions = civilization.permissions
        val toggleables = civilization.toggleables
        val citizenNames: MutableList<String?> = ArrayList()
        val canBuild: MutableList<String> = ArrayList()
        val canBreak: MutableList<String> = ArrayList()
        val canSwitch: MutableList<String> = ArrayList()
        val canInteract: MutableList<String> = ArrayList()
        val enemies: MutableList<String?> = ArrayList()
        val warring: MutableList<String?> = ArrayList()
        val allies: MutableList<String?> = ArrayList()
        val outlaws: MutableList<String?> = ArrayList()
        for (player in civilization.citizens) {
            citizenNames.add(player.playerName)
        }
        for (enemy in civilization.relationships.enemies) {
            enemies.add(enemy.name)
        }
        for (enemy in civilization.relationships.warring) {
            warring.add(enemy.name)
        }
        for (ally in civilization.relationships.allies) {
            allies.add(ally.name)
        }
        for (outlaw in civilization.relationships.outlaws) {
            outlaws.add(outlaw.playerName)
        }

        canBuild.addAll(permissions.ranks.filter { it.permissions.contains(PermissionType.BUILD) }.map { it.name }
            .toMutableList())
        canBreak.addAll(permissions.ranks.filter { it.permissions.contains(PermissionType.BREAK) }.map { it.name }
            .toMutableList())
        canSwitch.addAll(permissions.ranks.filter { it.permissions.contains(PermissionType.SWITCH) }.map { it.name }
            .toMutableList())
        canInteract.addAll(permissions.ranks.filter { it.permissions.contains(PermissionType.INTERACT) }
            .map { it.name }.toMutableList())


        tellNoPrefix(
            "${Settings.PRIMARY_COLOR}============ ${Settings.SECONDARY_COLOR}" + civilization.name + "${Settings.PRIMARY_COLOR} ============",
            "${Settings.PRIMARY_COLOR}Description: ${Settings.SECONDARY_COLOR}${civilization.description ?: "None"}",
            "" + if (Settings.SHOW_COORDS_IN_INFO && civilization.home != null) "${Settings.PRIMARY_COLOR}Home: ${Settings.SECONDARY_COLOR}" + civilization.home!!.blockX + ", " + civilization.home!!.blockZ else "",
            "${Settings.PRIMARY_COLOR}Leader: ${Settings.SECONDARY_COLOR}" + (civilization.leader?.playerName
                ?: "None"),
            "${Settings.PRIMARY_COLOR}Citizens: ${Settings.SECONDARY_COLOR}" + Common.join(
                citizenNames,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Power: ${Settings.SECONDARY_COLOR} ${civilization.power}",
            "${Settings.PRIMARY_COLOR}Balance: ${Settings.SECONDARY_COLOR} ${
                civilization.bank.balance.toString().format(
                    DecimalFormat.getCurrencyInstance()
                )
            }",
            "${Settings.PRIMARY_COLOR}Upkeep Cost: ${Settings.SECONDARY_COLOR}${
                civilization.bank.upkeep.toString().format(DecimalFormat.getCurrencyInstance())
            } ${Settings.PRIMARY_COLOR}Tax Amount: ${Settings.SECONDARY_COLOR}${
                civilization.bank.taxes.toString().format(DecimalFormat.getCurrencyInstance())
            }",
            "${Settings.PRIMARY_COLOR}Total Blocks: ${Settings.SECONDARY_COLOR}" + civilization.claims.totalBlocksCount,
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}PVP: ${Settings.SECONDARY_COLOR}" + toggleables.pvp + " ${Settings.PRIMARY_COLOR}Mob Spawning: ${Settings.SECONDARY_COLOR}" + toggleables.mobs + " ${Settings.PRIMARY_COLOR}Explosions: ${Settings.SECONDARY_COLOR}" + toggleables.explosion + " ${Settings.PRIMARY_COLOR}Fire Spread: ${Settings.SECONDARY_COLOR}" + toggleables.fire + " ${Settings.PRIMARY_COLOR}Public: ${Settings.SECONDARY_COLOR}" + toggleables.public + " ${Settings.PRIMARY_COLOR}Invite Only: ${Settings.SECONDARY_COLOR}" + toggleables.inviteOnly,
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}Build: ${Settings.SECONDARY_COLOR}" + Common.join(
                canBuild,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Break: ${Settings.SECONDARY_COLOR}" + Common.join(
                canBreak,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Switch: ${Settings.SECONDARY_COLOR}" + Common.join(
                canSwitch,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}Interact: ${Settings.SECONDARY_COLOR}" + Common.join(
                canInteract,
                ", "
            ),
            "${Settings.PRIMARY_COLOR}============================",
            "${Settings.PRIMARY_COLOR}Enemies: ${Settings.SECONDARY_COLOR}" + Common.join(
                enemies,
                ", "
            ) +
                    " ${Settings.PRIMARY_COLOR}Warring: ${Settings.SECONDARY_COLOR}" + Common.join(
                warring,
                ", "
            ) +
                    " ${Settings.PRIMARY_COLOR}Allies: ${Settings.SECONDARY_COLOR}" + Common.join(
                allies,
                ", "
            ) +
                    " ${Settings.PRIMARY_COLOR}Outlaws: ${Settings.SECONDARY_COLOR}" + Common.join(
                outlaws,
                ", "
            )
        )
    }

    init {
        usage = "[civilization]"
        setDescription("Get information on your or another Civilization")
        if (!io.bennyc.civilizations.settings.Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}