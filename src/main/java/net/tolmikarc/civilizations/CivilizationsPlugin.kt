/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations

import net.tolmikarc.civilizations.command.CivilizationCommandGroup
import net.tolmikarc.civilizations.db.CivDatastore
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.listener.CivListener
import net.tolmikarc.civilizations.listener.EntityListener
import net.tolmikarc.civilizations.listener.PlayerListener
import net.tolmikarc.civilizations.listener.WorldListener
import net.tolmikarc.civilizations.model.CivPlayer
import net.tolmikarc.civilizations.model.Civilization
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.dynmap.DynmapAPI
import org.mineacademy.fo.ASCIIUtil
import org.mineacademy.fo.Common
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.plugin.SimplePlugin
import org.mineacademy.fo.settings.YamlStaticConfig
import java.io.File
import java.io.IOException
import java.util.*

class CivilizationsPlugin : SimplePlugin() {

    // TODO
    //  Permissions gui
    //  animal ownership feature (maybe needs to be its own plugin)
    //  placeholder support
    //  how to make perms and toggles better than towny
    //  WAR
    //  make sure players can get out of claims at all times
    //  find a natural way to introduce players to creating civs/claims
    //  Towny and Factions adapter
    //  maybe a system to restore land to original state before players touched it
    //  learn about kotlin coroutines
    //  make the conversation canceller a variable
    //  different war modes, peaceful, anarchist, etc. maybe one where u can actually lose land
    override fun onPluginStart() {
        makeFolders()
        loadDatabase()
        registerAllCommands()
        registerAllEvents()
        registerAllPlaceholders()
        registerAllTasks()
        Common.ADD_TELL_PREFIX = true
        Common.log("Civilizations by Tolmikarc up and running!")
        for (civ in Civilization.civilizationsMap.values) {
            civ.home?.let {
                net.tolmikarc.civilizations.hook.DynmapHook.doDynmapStuffWithCiv(civ)
            }
        }
    }

    override fun onPluginStop() {
        cleanUpExtraWarBlocks()
        Common.log("Saving Data and Closing Datastore Connections")
        CivPlayer.saveQueuedForSaving()
        Civilization.saveQueuedForSaving()
        CivDatastore.close()
        PlayerDatastore.close()

    }


    override fun getSettings(): List<Class<out YamlStaticConfig?>> {
        return listOf(Settings::class.java, Localization::class.java)
    }

    override fun getFoundedYear(): Int {
        return 2021
    }

    override fun getStartupLogo(): Array<String> {
        return Array(13) { i -> ASCIIUtil.generate("Civ")[i] }
    }

    private fun registerAllTasks() {
        Common.runTimerAsync(20, CooldownTask())
    }

    private fun registerAllPlaceholders() {
        HookManager.addPlaceholder("civilization") { player: Player ->
            val cache = CivPlayer.fromBukkitPlayer(player)
            if (cache.civilization != null) return@addPlaceholder cache.civilization!!.name
            ""
        }
    }

    private fun registerAllCommands() {
        registerCommands("civilizations|civ", CivilizationCommandGroup())
    }

    private fun registerAllEvents() {
        registerEvents(PlayerListener())
        registerEvents(EntityListener())
        registerEvents(WorldListener())
        registerEvents(CivListener())
    }

    private fun loadDatabase() {
        when {
            Settings.DB_TYPE.equals("sqlite", ignoreCase = true) -> {
                val playerFile = File(dataFolder, "players.db")
                if (!playerFile.exists()) {
                    try {
                        playerFile.createNewFile()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                val civFile = File(dataFolder, "civilizations.db")
                if (!civFile.exists()) {
                    try {
                        civFile.createNewFile()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
                PlayerDatastore.connect(
                    "jdbc:sqlite:${playerFile.absolutePath}",
                    "",
                    "",
                    "civ_players"
                )
                CivDatastore.connect(
                    "jdbc:sqlite:${civFile.absolutePath}",
                    "",
                    "",
                    "civ_civs"
                )
                PlayerDatastore.loadAll()
                return
            }
            Settings.DB_TYPE.equals("mysql", ignoreCase = true) -> {
                PlayerDatastore.connect(
                    Settings.DB_HOST,
                    Settings.DB_PORT,
                    Settings.DB_NAME,
                    Settings.DB_USER,
                    Settings.DB_PASS,
                    "civ_players"
                )
                CivDatastore.connect(
                    Settings.DB_HOST,
                    Settings.DB_PORT,
                    Settings.DB_NAME,
                    Settings.DB_USER,
                    Settings.DB_PASS,
                    "civ_civs"
                )
                PlayerDatastore.loadAll()
                return
            }
            else -> {
                Common.error(Throwable("NoDataSource"), "No datasource for saving and loading, disabling plugin.")
                Bukkit.getPluginManager().disablePlugin(this)
            }
        }
    }

    private fun cleanUpExtraWarBlocks() {
        for (civilization in Civilization.civilizationsMap.values) {
            civilization.regionDamages?.let {
                for (block in it.cleanUpSet) {
                    block.type = Material.AIR
                }
            }
        }
    }

    private fun makeFolders() {
        val regionsFolder = File(dataFolder, "regions" + File.separator)
        if (!regionsFolder.exists()) regionsFolder.mkdirs()
    }

    companion object {
        val instance: SimplePlugin
            get() = getInstance()


        val dynmapApi: DynmapAPI?
            get() {
                return if (Common.doesPluginExist("dynmap"))
                    Bukkit.getServer().pluginManager.getPlugin("dynmap") as DynmapAPI
                else
                    null
            }
    }

}