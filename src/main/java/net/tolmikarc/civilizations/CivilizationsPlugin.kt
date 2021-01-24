/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package net.tolmikarc.civilizations

import io.papermc.lib.PaperLib
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.tolmikarc.civilizations.command.CivilizationsCommandGroup
import net.tolmikarc.civilizations.command.MapCommand
import net.tolmikarc.civilizations.command.admin.AdminCommandGroup
import net.tolmikarc.civilizations.db.CivDatastore
import net.tolmikarc.civilizations.db.PlayerDatastore
import net.tolmikarc.civilizations.listener.*
import net.tolmikarc.civilizations.manager.CivManager
import net.tolmikarc.civilizations.manager.PlayerManager
import net.tolmikarc.civilizations.settings.Localization
import net.tolmikarc.civilizations.settings.Settings
import net.tolmikarc.civilizations.task.CooldownTask
import net.tolmikarc.civilizations.task.UpkeepTaxesTask
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.mineacademy.fo.ASCIIUtil
import org.mineacademy.fo.Common
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.plugin.SimplePlugin
import org.mineacademy.fo.settings.YamlStaticConfig
import java.io.File
import java.util.*

class CivilizationsPlugin : SimplePlugin() {

    // TODO
    //  placeholder support
    //  more integrations
    //
    override fun onPluginStart() {
        loadDatabase()
        registerAllCommands()
        registerAllEvents()
        registerAllPlaceholders()
        registerAllTasks()
        Common.ADD_TELL_PREFIX = true
        Common.log("Civilizations by Tolmikarc up and running!")
        PaperLib.suggestPaper(this)
    }

    override fun onPluginStop() {
        removeMapRenderers()
        for (task in Bukkit.getScheduler().pendingTasks) {
            task.cancel()
        }
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
        registerCommands(Settings.ALIASES, CivilizationsCommandGroup())
        registerCommands("cadmin|civadmin|civilizationsadmin", AdminCommandGroup())
    }

    private fun registerAllEvents() {
        registerEvents(PlayerListener())
        registerEvents(EntityListener())
        registerEvents(WorldListener())
        registerEvents(CivListener())
        registerEvents(SignListener())
    }

    private fun loadDatabase() {

        when {
            Settings.DB_TYPE.equals("sqlite", ignoreCase = true) -> {
                val playerFile = File(dataFolder, "players.db")
                playerFile.createNewFile()
                val civFile = File(dataFolder, "civilizations.db")
                civFile.createNewFile()

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

}