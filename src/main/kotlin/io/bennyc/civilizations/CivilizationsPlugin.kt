package io.bennyc.civilizations

import io.bennyc.civilizations.infrastructure.paper.CivilizationsAdminCommand
import io.bennyc.civilizations.infrastructure.paper.CivilizationsConfiguration
import io.bennyc.civilizations.infrastructure.paper.protection.PaperProtectionListener
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.RuntimeStartOutcome
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executor
import java.util.logging.Level

class CivilizationsPlugin : JavaPlugin() {
    private lateinit var runtime: CivilizationsRuntime

    override fun onEnable() {
        saveDefaultConfig()
        val runtimeConfiguration = CivilizationsConfiguration.load(dataFolder.toPath(), config)
        val serverThread = Executor { action ->
            if (Bukkit.isPrimaryThread()) {
                action.run()
            } else if (isEnabled) {
                server.scheduler.runTask(this, action)
            }
        }

        runtime = CivilizationsRuntime.sqlite(
            databasePath = runtimeConfiguration.databasePath,
            claimRules = runtimeConfiguration.claimRules,
            phaseRules = runtimeConfiguration.phaseRules,
            serverThread = serverThread,
            fatalFailureHandler = { failure ->
                logger.log(Level.SEVERE, "Civilizations failed closed", failure)
                if (isEnabled) {
                    server.pluginManager.disablePlugin(this)
                }
            },
        )

        registerCommand(
            "civadmin",
            "Administer Civilizations",
            listOf("civilizationsadmin"),
            CivilizationsAdminCommand(runtime),
        )
        server.pluginManager.registerEvents(PaperProtectionListener(runtime), this)
        runtime.start { outcome ->
            when (outcome) {
                is RuntimeStartOutcome.Ready -> {
                    val active = outcome.state.activeSeason
                    logger.info(
                        if (active == null) {
                            "Civilizations is ready; create or select an active season with /civadmin"
                        } else {
                            "Civilizations loaded '${active.season.name}' " +
                                "with ${active.civilizations.size} civilizations and " +
                                "${active.claimIndex.size} claims"
                        },
                    )
                }
                is RuntimeStartOutcome.Failed -> Unit
            }
        }
    }

    override fun onDisable() {
        if (::runtime.isInitialized) {
            runtime.close()
        }
    }
}
