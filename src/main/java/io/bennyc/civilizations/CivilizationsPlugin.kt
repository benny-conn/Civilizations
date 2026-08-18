package io.bennyc.civilizations

import io.bennyc.civilizations.infrastructure.paper.V2AdminCommand
import io.bennyc.civilizations.infrastructure.paper.V2PluginConfiguration
import io.bennyc.civilizations.infrastructure.paper.protection.PaperProtectionListener
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.RuntimeStartOutcome
import org.bukkit.Bukkit
import org.mineacademy.fo.plugin.SimplePlugin
import java.util.concurrent.Executor
import java.util.logging.Level

class CivilizationsPlugin : SimplePlugin() {
    private lateinit var v2Runtime: CivilizationsRuntime

    override fun onPluginStart() {
        saveDefaultConfig()
        val runtimeConfiguration = V2PluginConfiguration.load(dataFolder.toPath(), config)
        val serverThread = Executor { action ->
            if (Bukkit.isPrimaryThread()) {
                action.run()
            } else if (isEnabled) {
                server.scheduler.runTask(this, action)
            }
        }

        v2Runtime = CivilizationsRuntime.sqlite(
            databasePath = runtimeConfiguration.databasePath,
            claimRules = runtimeConfiguration.claimRules,
            serverThread = serverThread,
            fatalFailureHandler = { failure ->
                logger.log(Level.SEVERE, "Civilizations V2 failed closed", failure)
                if (isEnabled) {
                    server.pluginManager.disablePlugin(this)
                }
            },
        )

        registerCommand(
            "civadmin",
            "Administer Civilizations V2",
            listOf("civilizationsadmin"),
            V2AdminCommand(v2Runtime),
        )
        server.pluginManager.registerEvents(PaperProtectionListener(v2Runtime), this)
        v2Runtime.start { outcome ->
            when (outcome) {
                is RuntimeStartOutcome.Ready -> {
                    val active = outcome.state.activeSeason
                    logger.info(
                        if (active == null) {
                            "Civilizations V2 is ready; create or select an active season with /civadmin"
                        } else {
                            "Civilizations V2 loaded '${active.season.name}' " +
                                "with ${active.civilizations.size} civilizations and " +
                                "${active.claimIndex.size} claims"
                        },
                    )
                }
                is RuntimeStartOutcome.Failed -> Unit
            }
        }

        logger.warning(
            "Legacy commands, tasks, and datastores remain quarantined; " +
                "V2 protection listeners are active",
        )
    }

    override fun onPluginStop() {
        if (::v2Runtime.isInitialized) {
            v2Runtime.close()
        }
    }

    override fun getFoundedYear(): Int = 2021
}
