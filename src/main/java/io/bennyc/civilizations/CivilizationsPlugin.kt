/*
 * Copyright (c) 2021-2021 Tolmikarc All Rights Reserved
 */

package io.bennyc.civilizations

import io.bennyc.civilizations.command.CivilizationsCommandGroup
import io.bennyc.civilizations.command.MapCommand
import io.bennyc.civilizations.command.admin.AdminCommandGroup
import io.bennyc.civilizations.db.CivDatastore
import io.bennyc.civilizations.db.PlayerDatastore
import io.bennyc.civilizations.listener.*
import io.bennyc.civilizations.manager.PlayerManager
import io.bennyc.civilizations.settings.Settings
import io.bennyc.civilizations.task.CooldownTask
import io.bennyc.civilizations.task.MobRemovalTask
import io.bennyc.civilizations.task.UpkeepTaxesTask
import io.papermc.lib.PaperLib
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Player
import org.mineacademy.fo.Common
import org.mineacademy.fo.SerializeUtil
import org.mineacademy.fo.model.HookManager
import org.mineacademy.fo.plugin.SimplePlugin
import java.io.File

class CivilizationsPlugin : SimplePlugin() {


    override fun onPluginStart() {
        loadDatabase()
        registerAllCommands()
        registerAllEvents()
        registerAllPlaceholders()
        registerAllTasks()
        Common.log("Civilizations by Tolmikarc up and running!")
        PaperLib.suggestPaper(this)


        SerializeUtil.addSerializer(Location::class.java) { t: Location -> SerializeUtil.serializeLoc(t) }
    }

    override fun onPluginStop() {
        removeMapRenderers()
        for (task in Bukkit.getScheduler().pendingTasks) {
            task.cancel()
        }
        Common.log("Saving Data and Closing Datastore Connections")

        CivDatastore.close()
        PlayerDatastore.close()

    }


    override fun getFoundedYear(): Int {
        return 2021
    }

    private fun registerAllTasks() {
        Common.runTimerAsync(20, CooldownTask())
        Common.runTimerAsync(20 * 60 * 60, UpkeepTaxesTask())
        Common.runTimer(20 * 10, MobRemovalTask())
    }

    private fun registerAllPlaceholders() {
        HookManager.addPlaceholder("civilization") { player: Player ->
            val cache = PlayerManager.fromBukkitPlayer(player)
            if (cache.civilization != null)
                cache.civilization!!.name
            else
                ""
        }
    }


    private fun registerAllCommands() {
        registerCommands(
            CivilizationsCommandGroup()
        )
        registerCommands(
            AdminCommandGroup()
        )
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
            }

            else -> {
                Common.error(Throwable("NoDataSource"), "No datasource for saving and loading, disabling plugin.")
                Bukkit.getPluginManager().disablePlugin(this)
            }
        }
        Common.runLaterAsync {
            PlayerDatastore.loadAll()
        }
    }


    private fun removeMapRenderers() {
        for (map in MapCommand.drawnMaps) {
            map.renderers.clear()
        }
    }

}