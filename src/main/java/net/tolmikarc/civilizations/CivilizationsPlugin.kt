/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations

import io.papermc.lib.PaperLib
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.tolmikarc.civilizations.command.CivilizationsCommandGroup
import net.tolmikarc.civilizations.command.MapCommand
import net.tolmikarc.civilizations.db.CivDatastore
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.listener.CivListener
import net.tolmikarc.civilizations.listener.EntityListener
import net.tolmikarc.civilizations.listener.PlayerListener
import net.tolmikarc.civilizations.listener.WorldListener
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.UpkeepTaxesTask
import org.bukkit.Bukkit
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
    //  make the conversation canceller a variable
    //  different war modes, peaceful, anarchist, etc. maybe one where u can actually lose land
    //  WG integration
    //  Remain.setCustomName() instead of packet manipulation???
    //  Remain.setCooldown() for TNT placement in raid??
    //
    override fun onPluginStart() {
        loadDatabase()
        registerAllCommands()
        registerAllEvents()
        registerAllPlaceholders()
        registerAllTasks()
        Common.ADD_TELL_PREFIX = true
        Common.log("Civilizations by Tolmikarc up and running!")

        for (civ in CivManager.all) {
            civ.home?.let {
                DynmapHook.doDynmapStuffWithCiv(civ)
            }
        }
        PaperLib.suggestPaper(this)
        Common.runLater(10) {
            //  seedDatabase()
        }
    }

    override fun onPluginStop() {
        removeMapRenderers()
        Common.log("Saving Data and Closing Datastore Connections")
        PlayerManager.saveQueuedForSaving()
        CivManager.saveQueuedForSaving()
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
        Common.runTimerAsync(20 * 60 * 60, UpkeepTaxesTask())
    }

    private fun registerAllPlaceholders() {
        HookManager.addPlaceholder("civilization") { player: Player ->
            val cache = PlayerManager.fromBukkitPlayer(player)
            if (cache.civilization != null) return@addPlaceholder cache.civilization!!.name
            ""
        }
    }


    private fun registerAllCommands() {
        registerCommands("civilizations|civ", CivilizationsCommandGroup())
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
                Common.runLaterAsync {
                    PlayerDatastore.loadAll()
                }
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


    private fun removeMapRenderers() {
        for (map in MapCommand.drawnMaps) {
            map.renderers.clear()
        }
    }


    private fun seedDatabase() {
        GlobalScope.launch {
            repeat(100000)
            {
                val p = PlayerManager.initialize(UUID.randomUUID()).apply { playerName = UUID.randomUUID().toString() }
                PlayerManager.saveAsync(p)
                println("ADDED PLAYER")
            }
        }
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