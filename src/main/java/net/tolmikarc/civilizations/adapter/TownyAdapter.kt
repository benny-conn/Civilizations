/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations.adapter

object TownyAdapter {

//    private val convertedTowns: MutableMap<Town, Civilization> = HashMap()
//
//
//    fun convertTownToCiv(town: Town): Civilization? {
//        val civ = Civilization(town.getUUID())
//        try {
//            civ.name = town.name
//            civ.home = town.spawn
//            val newRegions = getConvertedRegions(town, civ)
//            civ.claims.claims.addAll(newRegions)
//            civ.claims.idNumber = newRegions.size + 1
//            civ.toggleables = convertToggleables(town)
//            civ.power = getConvertedPower(civ)
//            convertPermissions(town, civ)
//            for (resident in town.residents) {
//                val uuid = UUIDFetcher.getUUIDOf(resident.name) ?: resident.uuid
//                val cache = PlayerManager.getByUUID(uuid)
//                cache.playerName = resident.name
//                cache.civilization = civ
//                civ.addCitizen(cache)
//                if (resident.isMayor)
//                    civ.leader = cache
//            }
//            civ.bank.balance = town.account.holdingBalance
//            civ.description = town.board
//            if (town.isTaxPercentage)
//                civ.bank.taxes = town.taxes
//            convertedTowns[town] = civ
//            return civ
//        } catch (e: Exception) {
//            Common.log(town.name + " was unable to be converted")
//            return null
//        }
//    }
//
//
//    fun getResidentsUUIDS() {
//        val uuidFetcher = UUIDFetcher(TownyAPI.getInstance().dataSource.residents.map { it.name }.toMutableList())
//        uuidFetcher.call()
//    }
//
//
//    fun adaptEnemiesAndAllies() {
//        for (town in convertedTowns.keys) {
//            try {
//                val allies: MutableSet<Civilization> = HashSet()
//                val enemies: MutableSet<Civilization> = HashSet()
//                if (town.hasNation()) {
//                    val nation: Nation = town.nation
//                    nation.allies.forEach(Consumer { nationAlly: Nation ->
//                        nationAlly.towns.forEach(Consumer { townAlly: Town ->
//                            convertedTowns[town]?.let {
//                                convertedTowns[townAlly]?.relationships?.allies?.add(
//                                    it
//                                )
//                            }
//                        })
//                    })
//                    nation.enemies.forEach(Consumer { nationEnemy: Nation ->
//                        nationEnemy.towns.forEach(Consumer { townEnemy: Town ->
//                            convertedTowns[town]?.let {
//                                convertedTowns[townEnemy]?.relationships?.enemies?.add(
//                                    it
//                                )
//                            }
//                        })
//                    })
//                    convertedTowns[town]?.relationships?.allies?.addAll(allies)
//                    convertedTowns[town]?.relationships?.enemies?.addAll(enemies)
//                }
//            } catch (e: NotRegisteredException) {
//                Common.log(town.name + " does not have a nation and therefore has no enemies or allies")
//            }
//        }
//    }
//
//    private fun getConvertedPower(newCiv: Civilization): Int {
//        return Settings.POWER_BLOCKS_WEIGHT * newCiv.claims.totalBlocksCount +
//                (Settings.POWER_MONEY_WEIGHT * newCiv.bank.balance).toInt()
//    }
//
//
//    private fun getConvertedRegions(town: Town, civ: Civilization): MutableSet<Region> {
//        val newRegions: MutableSet<Region> = HashSet()
//        val newColonies: MutableSet<Colony> = HashSet()
//        var id = 0
//
//        val iterator = town.townBlocks.iterator()
//        while (iterator.hasNext()) {
//            val townBlock = iterator.next()
//            if (townBlock.isOutpost) {
//                val world: World? = Bukkit.getWorld(townBlock.world.name)
//                val x: Int = townBlock.x * 16
//                val z: Int = townBlock.z * 16
//                val y = world?.getHighestBlockYAt(x, z)
//                val colony = y?.let { Location(world, x.toDouble(), it.toDouble(), z.toDouble()) }
//                    ?.let { Colony(civ, id, it) }
//                if (colony != null) {
//                    newColonies.add(colony)
//                }
//            }
//            val bottomLeftCorner: WorldCoord = townBlock.worldCoord
//            val topRightCorner: WorldCoord = townBlock.worldCoord
//            val newRegion = Region(
//                id,
//                Location(
//                    bottomLeftCorner.bukkitWorld,
//                    (bottomLeftCorner.x * 16.0),
//                    1.0,
//                    (bottomLeftCorner.z * 16.0)
//                ).block.location,
//                Location(
//                    topRightCorner.bukkitWorld,
//                    (topRightCorner.x * 16.0 + 15.5),
//                    1.0,
//                    (topRightCorner.z * 16.0 + 15.5)
//                ).block.location
//            )
//            newRegions.add(newRegion)
//            civ.claims.totalBlocksCount += 16
//            id++
//        }
//        civ.claims.colonies.addAll(newColonies)
//        return newRegions
//    }
//
//    private fun convertPermissions(town: Town, civ: Civilization) {
//        val ranks = civ.permissions
//        for (rank in TownyPerms.getTownRanks()) {
//            if (rank.equals("outsider", true)) continue
//            val newRank = Rank(rank.capitalize(), Settings.DEFAULT_GROUP.permissions)
//            ranks.ranks.add(newRank)
//        }
//        if (town.permissions.getResidentPerm(TownyPermission.ActionType.BUILD)) ranks.defaultRank.permissions.add(
//            PermissionType.BUILD
//        )
//        if (town.permissions.getResidentPerm(TownyPermission.ActionType.DESTROY)) ranks.defaultRank.permissions.add(
//            PermissionType.BREAK
//        )
//        if (town.permissions.getResidentPerm(TownyPermission.ActionType.SWITCH)) ranks.defaultRank.permissions.add(
//            PermissionType.SWITCH
//        )
//        if (town.permissions.getResidentPerm(TownyPermission.ActionType.ITEM_USE)) ranks.defaultRank.permissions.add(
//            PermissionType.INTERACT
//        )
//
//        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.BUILD)) ranks.outsiderRank.permissions.add(
//            PermissionType.BUILD
//        )
//        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.DESTROY)) ranks.outsiderRank.permissions.add(
//            PermissionType.BREAK
//        )
//        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.SWITCH)) ranks.outsiderRank.permissions.add(
//            PermissionType.SWITCH
//        )
//        if (town.permissions.getOutsiderPerm(TownyPermission.ActionType.ITEM_USE)) ranks.outsiderRank.permissions.add(
//            PermissionType.INTERACT
//        )
//
//        if (town.permissions.getAllyPerm(TownyPermission.ActionType.BUILD)) ranks.allyRank.permissions.add(
//            PermissionType.BUILD
//        )
//        if (town.permissions.getAllyPerm(TownyPermission.ActionType.DESTROY)) ranks.allyRank.permissions.add(
//            PermissionType.BREAK
//        )
//        if (town.permissions.getAllyPerm(TownyPermission.ActionType.SWITCH)) ranks.allyRank.permissions.add(
//            PermissionType.SWITCH
//        )
//        if (town.permissions.getAllyPerm(TownyPermission.ActionType.ITEM_USE)) ranks.allyRank.permissions.add(
//            PermissionType.INTERACT
//        )
//        ranks.enemyRank.permissions.addAll(ranks.outsiderRank.permissions)
//
//    }
//
//    private fun convertToggleables(town: Town): Toggleables {
//        val toggleables = Toggleables()
//        toggleables.explosion = town.isBANG
//        toggleables.fire = town.isFire
//        toggleables.pvp = town.isPVP
//        toggleables.mobs = town.hasMobs()
//        toggleables.public = town.isPublic
//        return toggleables
//    }
}