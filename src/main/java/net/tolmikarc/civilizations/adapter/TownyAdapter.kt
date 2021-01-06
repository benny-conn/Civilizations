/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.adapter

import com.palmergames.bukkit.towny.TownySettings
import com.palmergames.bukkit.towny.TownyUniverse
import com.palmergames.bukkit.towny.`object`.*
import com.palmergames.bukkit.towny.exceptions.EconomyException
import com.palmergames.bukkit.towny.exceptions.NotRegisteredException
import com.palmergames.bukkit.towny.exceptions.TownyException
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.model.CPlayer
import net.tolmikarc.civilizations.model.Civ
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.model.Colony
import net.tolmikarc.civilizations.permissions.ClaimPermissions
import net.tolmikarc.civilizations.permissions.ClaimToggleables
import net.tolmikarc.civilizations.settings.Settings
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.mineacademy.fo.Common
import org.mineacademy.fo.region.Region
import java.util.*
import java.util.function.Consumer
import kotlin.collections.HashSet
import kotlin.collections.set

object TownyAdapter {

    // TODO handle settings adjustment to account for the amount of claims and land that there are
    //  convert outlaws enemies and allies from the nations of each town
    private val convertedTowns: MutableMap<Town, Civ> = HashMap()

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
                                convertedTowns[townAlly]?.allies?.add(
                                    it
                                )
                            }
                        })
                    })
                    nation.enemies.forEach(Consumer { nationEnemy: Nation ->
                        nationEnemy.towns.forEach(Consumer { townEnemy: Town ->
                            convertedTowns[town]?.let {
                                convertedTowns[townEnemy]?.enemies?.add(
                                    it
                                )
                            }
                        })
                    })
                    convertedTowns[town]?.allies = allies
                    convertedTowns[town]?.enemies = enemies
                }
            } catch (e: NotRegisteredException) {
                Common.log(town.name + " does not have a nation and therefore has no enemies or allies")
            }
        }
    }

    fun convertSettingsToTowny() {
        Settings.convert("Claim.Claim_Cost", (TownySettings.getClaimPrice() / (16 * 16)).toString())
    }


    fun convertTownToCiv(town: Town, deleteAfterConversion: Boolean): Civ {
        val civ = Civilization(town.getUUID())
        civ.name = town.name
        try {
            civ.bank.balance = town.account.holdingBalance
        } catch (e: EconomyException) {
            Common.error(e, "Unable to convert town money for town " + town.name + ".")
        }
        try {
            civ.home = town.spawn
        } catch (e: TownyException) {
            Common.error(e, "Unable to convert town spawn for town " + town.name)
        }
        val newCivMembers: MutableSet<CPlayer> = HashSet()
        for (resident in town.residents) {
            val cache: CPlayer =
                PlayerManager.getByUUID(resident.uuid)
            newCivMembers.add(cache)
            cache.civilization = civ
        }
        civ.citizens = newCivMembers
        val newRegions = getConvertedRegions(town, civ)
        civ.claims = newRegions
        civ.idNumber = newRegions.size + 1
        civ.claimPermissions = convertPermissions(town)
        civ.claimToggleables = convertToggleables(town)
        convertedTowns[town] = civ
        if (deleteAfterConversion) TownyUniverse.getInstance().dataSource.removeTown(town)
        return civ
        TODO("figure out how to make the power calculated")
    }

    private fun getConvertedRegions(town: Town, civ: Civ): MutableSet<Region> {
        val handledTownBlocks: MutableSet<TownBlock> = HashSet()
        val newRegions: MutableSet<Region> = HashSet()
        val newColonies: MutableSet<Colony> = HashSet()
        var id = 0
        for (townBlock in town.townBlocks) {
            if (townBlock.isOutpost) {
                val world: World? = Bukkit.getWorld(townBlock.world.name)
                val x: Int = townBlock.x * 16
                val z: Int = townBlock.z * 16
                val y = world?.getHighestBlockYAt(x, z)
                val colony = y?.let { Location(world, x.toDouble(), it.toDouble(), z.toDouble()) }
                    ?.let { Colony(civ, civ.idNumber, it) }
                if (colony != null) {
                    newColonies.add(colony)
                }
            }
            if (handledTownBlocks.contains(townBlock)) continue
            var lowestLocation: WorldCoord = townBlock.worldCoord
            var highestLocation: WorldCoord = townBlock.worldCoord
            while (town.townBlockMap[highestLocation.add(1, 0)] != null) {
                val nextTownBlock: TownBlock? = town.townBlockMap[highestLocation.add(1, 0)]
                if (nextTownBlock != null) {
                    highestLocation = nextTownBlock.worldCoord
                    handledTownBlocks.add(nextTownBlock)
                }
            }
            while (town.townBlockMap[lowestLocation.add(0, 1)] != null) {
                val nextTownBlock: TownBlock = town.townBlockMap[lowestLocation.add(0, 1)]!!
                var nextTownBlockWorldCoord: WorldCoord = nextTownBlock.worldCoord
                val handledTownBlocksMovingRight: MutableSet<TownBlock> = HashSet()
                while (town.townBlockMap[nextTownBlockWorldCoord.add(1, 0)] != null) {
                    val nextNextTownBlock: TownBlock? = town.townBlockMap[nextTownBlockWorldCoord.add(1, 0)]
                    if (nextNextTownBlock != null) {
                        nextTownBlockWorldCoord = nextNextTownBlock.worldCoord

                        handledTownBlocksMovingRight.add(nextNextTownBlock)
                    }
                    if (nextTownBlockWorldCoord == highestLocation.add(0, 1)) break
                }
                if (nextTownBlockWorldCoord != highestLocation.add(0, 1)) break
                highestLocation = nextTownBlockWorldCoord.add(0, 1)
                lowestLocation = lowestLocation.add(0, 1)
                handledTownBlocks.add(nextTownBlock)
                handledTownBlocks.addAll(handledTownBlocksMovingRight)
            }
            lowestLocation = townBlock.worldCoord
            val newRegion = Region(
                civ.uuid.toString() + "CLAIM" + id,
                Location(
                    lowestLocation.bukkitWorld,
                    (lowestLocation.x * 16).toDouble(),
                    0.0,
                    (lowestLocation.z * 16).toDouble()
                ),
                Location(
                    highestLocation.bukkitWorld,
                    (highestLocation.x * 16 + 15).toDouble(),
                    256.0,
                    (highestLocation.z * 16 + 15).toDouble()
                )
            )
            newRegions.add(newRegion)
            id++
        }
        civ.colonies = (newColonies)
        return newRegions
    }

    private fun convertPermissions(town: Town): ClaimPermissions {
        val permissions = ClaimPermissions()
        val perms: Array<BooleanArray> = permissions.permissions
        perms[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.BUILD.id] =
            town.permissions.getOutsiderPerm(TownyPermission.ActionType.BUILD)
        perms[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.BREAK.id] =
            town.permissions.getOutsiderPerm(TownyPermission.ActionType.DESTROY)
        perms[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.SWITCH.id] =
            town.permissions.getOutsiderPerm(TownyPermission.ActionType.SWITCH)
        perms[ClaimPermissions.PermGroup.OUTSIDER.id][ClaimPermissions.PermType.INTERACT.id] =
            town.permissions.getOutsiderPerm(TownyPermission.ActionType.ITEM_USE)
        perms[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.BUILD.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.BUILD)
        perms[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.BREAK.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.DESTROY)
        perms[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.SWITCH.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.SWITCH)
        perms[ClaimPermissions.PermGroup.MEMBER.id][ClaimPermissions.PermType.INTERACT.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.ITEM_USE)
        perms[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.BUILD.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.BUILD)
        perms[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.BREAK.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.DESTROY)
        perms[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.SWITCH.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.SWITCH)
        perms[ClaimPermissions.PermGroup.OFFICIAL.id][ClaimPermissions.PermType.INTERACT.id] =
            town.permissions.getResidentPerm(TownyPermission.ActionType.ITEM_USE)
        perms[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.BUILD.id] =
            town.permissions.getAllyPerm(TownyPermission.ActionType.BUILD)
        perms[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.BREAK.id] =
            town.permissions.getAllyPerm(TownyPermission.ActionType.DESTROY)
        perms[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.SWITCH.id] =
            town.permissions.getAllyPerm(TownyPermission.ActionType.SWITCH)
        perms[ClaimPermissions.PermGroup.ALLY.id][ClaimPermissions.PermType.INTERACT.id] =
            town.permissions.getAllyPerm(TownyPermission.ActionType.ITEM_USE)
        permissions.permissions = (perms)
        return permissions
    }

    private fun convertToggleables(town: Town): ClaimToggleables {
        val toggleables = ClaimToggleables()
        toggleables.explosion = town.permissions.explosion
        toggleables.fire = town.permissions.fire
        toggleables.pvp = town.permissions.pvp
        toggleables.mobs = town.permissions.mobs
        toggleables.public = town.isPublic
        return toggleables
    }
}