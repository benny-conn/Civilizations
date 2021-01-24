/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.adapter

import com.palmergames.bukkit.towny.`object`.*
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException
import com.palmergames.bukkit.towny.permissions.TownyPerms
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.impl.Civilization
import net.tolmikarc.civilizations.model.impl.Colony
import net.tolmikarc.civilizations.model.impl.Region
import net.tolmikarc.civilizations.permissions.PermissionType
import net.tolmikarc.civilizations.permissions.Permissions
import net.tolmikarc.civilizations.permissions.Rank
import net.tolmikarc.civilizations.permissions.Toggleables
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.mineacademy.fo.Common
import java.util.*
import java.util.function.Consumer
import kotlin.collections.HashSet
import kotlin.collections.set

object TownyAdapter {

    private val convertedTowns: MutableMap<Town, Civ> = HashMap()


    fun convertTownToCiv(town: Town): Civ {
        val civ = Civilization(town.getUUID())
        civ.name = town.name
        civ.home = town.spawn
        for (resident in town.residents) {
            val cache = PlayerManager.getByUUID(resident.uuid)
            cache.civilization = civ
            civ.addCitizen(cache)
            if (town.mayor == resident)
                civ.leader = cache
        }
        val newRegions = getConvertedRegions(town, civ)
        civ.claims.claims.addAll(newRegions)
        civ.claims.idNumber = newRegions.size + 1
        civ.toggleables = convertToggleables(town)
        civ.power = getConvertedPower(civ)
        civ.permissions = getConvertedPermissions(town, civ)
        civ.bank.balance = town.account.holdingBalance
        civ.description = town.board
        if (town.isTaxPercentage)
            civ.bank.taxes = town.taxes
        convertedTowns[town] = civ
        return civ
    }


    fun adaptEnemiesAndAllies() {
        for (town in convertedTowns.keys) {
            try {
                val allies: MutableSet<Civ> = HashSet()
                val enemies: MutableSet<Civ> = HashSet()
                if (town.hasNation()) {
                    val nation: Nation = town.nation
                    nation.allies.forEach(Consumer { nationAlly: Nation ->
                        nationAlly.towns.forEach(Consumer { townAlly: Town ->
                            convertedTowns[town]?.let {
                                convertedTowns[townAlly]?.relationships?.allies?.add(
                                    it
                                )
                            }
                        })
                    })
                    nation.enemies.forEach(Consumer { nationEnemy: Nation ->
                        nationEnemy.towns.forEach(Consumer { townEnemy: Town ->
                            convertedTowns[town]?.let {
                                convertedTowns[townEnemy]?.relationships?.enemies?.add(
                                    it
                                )
                            }
                        })
                    })
                    convertedTowns[town]?.relationships?.allies?.addAll(allies)
                    convertedTowns[town]?.relationships?.enemies?.addAll(enemies)
                }
            } catch (e: NotRegisteredException) {
                Common.log(town.name + " does not have a nation and therefore has no enemies or allies")
            }
        }
    }

    private fun getConvertedPower(newCiv: Civ): Int {
        return Settings.POWER_BLOCKS_WEIGHT * newCiv.claims.totalBlocksCount +
                (Settings.POWER_MONEY_WEIGHT * newCiv.bank.balance).toInt()
    }


    private fun getConvertedRegions(town: Town, civ: Civ): MutableSet<Region> {
//        val handledTownBlocks: MutableSet<TownBlock> = HashSet()
        val newRegions: MutableSet<Region> = HashSet()
        val newColonies: MutableSet<Colony> = HashSet()
        var id = 0

        val iterator = town.townBlocks.iterator()
        while (iterator.hasNext()) {
            val townBlock = iterator.next()
            if (townBlock.isOutpost) {
                val world: World? = Bukkit.getWorld(townBlock.world.name)
                val x: Int = townBlock.x * 16
                val z: Int = townBlock.z * 16
                val y = world?.getHighestBlockYAt(x, z)
                val colony = y?.let { Location(world, x.toDouble(), it.toDouble(), z.toDouble()) }
                    ?.let { Colony(civ, civ.claims.idNumber, it) }
                if (colony != null) {
                    newColonies.add(colony)
                }
            }
            val bottomLeftCorner: WorldCoord = townBlock.worldCoord
//            var topLeftCorner: WorldCoord = townBlock.worldCoord
            val topRightCorner: WorldCoord = townBlock.worldCoord

//            while (town.townBlockMap[topRightCorner.add(1, 0)] != null) {
//                val nextTownBlock: TownBlock = town.townBlockMap[topRightCorner.add(1, 0)]!!
//                topRightCorner = nextTownBlock.worldCoord
//                handledTownBlocks.add(nextTownBlock)
//            }
//            outerWhile@ while (town.townBlockMap[topLeftCorner.add(0, 1)] != null) {
//                var nextTownBlock: TownBlock = town.townBlockMap[topLeftCorner.add(0, 1)]!!
//                val handledTownBlocksMovingRight = mutableSetOf(nextTownBlock)
//                innerWhile@ while (town.townBlockMap[nextTownBlock.worldCoord.add(1, 0)] != null) {
//                    nextTownBlock = town.townBlockMap[nextTownBlock.worldCoord.add(1, 0)]!!
//                    handledTownBlocksMovingRight.add(nextTownBlock)
//                    if (nextTownBlock.worldCoord == topRightCorner.add(0, 1)) break@innerWhile
//                }
//                if (nextTownBlock.worldCoord != topRightCorner.add(0, 1)) break@outerWhile
//                topRightCorner = nextTownBlock.worldCoord
//                topLeftCorner = topLeftCorner.add(0, 1)
//                handledTownBlocks.addAll(handledTownBlocksMovingRight)
//            }
            val newRegion = Region(
                id,
                Location(
                    bottomLeftCorner.bukkitWorld,
                    (bottomLeftCorner.x * 16.0),
                    1.0,
                    (bottomLeftCorner.z * 16.0)
                ).block.location,
                Location(
                    topRightCorner.bukkitWorld,
                    (topRightCorner.x * 16.0 + 15.5),
                    1.0,
                    (topRightCorner.z * 16.0 + 15.5)
                ).block.location
            )
            newRegions.add(newRegion)
            id++
        }
        civ.claims.colonies.addAll(newColonies)
        return newRegions
    }

    private fun getConvertedPermissions(town: Town, civ: Civ): Permissions {
        val ranks = Permissions(civ)
        for (rank in TownyPerms.getTownRanks()) {
            val newRank = Rank(rank.capitalize(), Settings.DEFAULT_GROUP.permissions)
            ranks.ranks.add(newRank)
        }
        val defaultPerms = mutableSetOf<PermissionType>()
        if (town.permissions.getResidentPerm(TownyPermission.ActionType.BUILD)) defaultPerms.add(PermissionType.BUILD)
        if (town.permissions.getResidentPerm(TownyPermission.ActionType.DESTROY)) defaultPerms.add(PermissionType.BREAK)
        if (town.permissions.getResidentPerm(TownyPermission.ActionType.SWITCH)) defaultPerms.add(PermissionType.SWITCH)
        if (town.permissions.getResidentPerm(TownyPermission.ActionType.ITEM_USE)) defaultPerms.add(PermissionType.INTERACT)
        val defaultRank = Rank(Settings.DEFAULT_GROUP.name, defaultPerms)
        ranks.defaultRank = defaultRank

        val outsiderPerms = mutableSetOf<PermissionType>()
        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.BUILD)) outsiderPerms.add(PermissionType.BUILD)
        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.DESTROY)) outsiderPerms.add(PermissionType.BREAK)
        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.SWITCH)) outsiderPerms.add(PermissionType.SWITCH)
        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.ITEM_USE)) outsiderPerms.add(PermissionType.INTERACT)
        val outsiderRank = Rank(Settings.OUTSIDER_GROUP.name, outsiderPerms)
        ranks.outsiderRank = outsiderRank

        val allyPerms = mutableSetOf<PermissionType>()
        if (town.permissions.getAllyPerm(TownyPermission.ActionType.BUILD)) allyPerms.add(PermissionType.BUILD)
        if (town.permissions.getAllyPerm(TownyPermission.ActionType.DESTROY)) allyPerms.add(PermissionType.BREAK)
        if (town.permissions.getAllyPerm(TownyPermission.ActionType.SWITCH)) allyPerms.add(PermissionType.SWITCH)
        if (town.permissions.getAllyPerm(TownyPermission.ActionType.ITEM_USE)) allyPerms.add(PermissionType.INTERACT)
        val allyRank = Rank(Settings.OUTSIDER_GROUP.name, allyPerms)
        ranks.allyRank = allyRank

        val enemyRank = Rank(Settings.OUTSIDER_GROUP.name, outsiderPerms)
        ranks.enemyRank = enemyRank

        ranks.ranks.add(defaultRank)
        ranks.ranks.add(outsiderRank)
        ranks.ranks.add(allyRank)
        ranks.ranks.add(enemyRank)

        return ranks
    }

    private fun convertToggleables(town: Town): Toggleables {
        val toggleables = Toggleables()
        toggleables.explosion = town.isBANG
        toggleables.fire = town.isFire
        toggleables.pvp = town.isPVP
        toggleables.mobs = town.hasMobs()
        toggleables.public = town.isPublic
        return toggleables
    }
}