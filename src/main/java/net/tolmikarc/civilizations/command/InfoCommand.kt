/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.command

import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import org.mineacademy.fo.Common
import org.mineacademy.fo.command.SimpleCommandGroup
import org.mineacademy.fo.command.SimpleSubCommand

class InfoCommand(parent: SimpleCommandGroup?) : SimpleSubCommand(parent, "info") {
    override fun onCommand() {
        checkConsole()
        PlayerManager.fromBukkitPlayer(player).let { civPlayer ->
            val civilization: Civ?
            if (args.isNotEmpty()) {
                civilization = CivManager.getByName(args[0])
                checkNotNull(
                    civilization,
                    Localization.Warnings.INVALID_SPECIFIC_ARGUMENT.replace("{item}", Localization.CIVILIZATION)
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

    private fun sendInfo(civilization: Civ) {
        val permissions = civilization.ranks
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
            "{1}============ {2}" + civilization.name + "{1} ============",
            "{1}Description: {2}${civilization.description ?: "None"}",
            "" + if (Settings.SHOW_COORDS_IN_INFO && civilization.home != null) "{1}Home: {2}" + civilization.home!!.blockX + ", " + civilization.home!!.blockZ else "",
            "{1}Leader: {2}" + (civilization.leader?.playerName
                ?: "None"),
            "{1}Citizens: {2}" + Common.join(
                citizenNames,
                ", "
            ), "{1}Power: {2} ${civilization.power}",
            "{1}Balance: {2} ${Settings.CURRENCY_SYMBOL}${civilization.bank.balance}",
            "{1}Upkeep Cost: {2}${Settings.CURRENCY_SYMBOL}${civilization.bank.upkeep}",
            "{1}Tax Amount: {2}${Settings.CURRENCY_SYMBOL}${civilization.bank.taxes}",
            "{1}Total Blocks: {2}" + civilization.claims.totalBlocksCount,
            "{1}============================",
            "{1}PVP: {2}" + toggleables.pvp,
            "{1}Mob Spawning: {2}" + toggleables.mobs,
            "{1}Explosions: {2}" + toggleables.explosion,
            "{1}Fire Spread: {2}" + toggleables.fire,
            "{1}Public: {2}" + toggleables.public,
            "{1}Invite Only: {2}" + toggleables.inviteOnly,
            "{1}============================",
            "{1}Build: {2}" + Common.join(
                canBuild,
                ", "
            ),
            "{1}Break: {2}" + Common.join(
                canBreak,
                ", "
            ),
            "{1}Switch: {2}" + Common.join(
                canSwitch,
                ", "
            ),
            "{1}Interact: {2}" + Common.join(
                canInteract,
                ", "
            ),
            "{1}============================",
            "{1}Enemies: {2}" + Common.join(
                enemies,
                ", "
            ),
            "{1}Warring: {2}" + Common.join(
                warring,
                ", "
            ),
            "{1}Allies: {2}" + Common.join(
                allies,
                ", "
            ),
            "{1}Outlaws: {2}" + Common.join(
                outlaws,
                ", "
            )
        )
    }

    init {
        usage = "[civilization]"
        setDescription("Get information on your or another Civilization")
        if (!Settings.ALL_PERMISSIONS_ENABLED) permission = null
    }
}