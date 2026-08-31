package io.bennyc.civilizations

import io.bennyc.civilizations.infrastructure.paper.CivilizationsAdminCommand
import io.bennyc.civilizations.infrastructure.paper.CivilizationsConfiguration
import io.bennyc.civilizations.infrastructure.paper.CivilizationsCommand
import io.bennyc.civilizations.infrastructure.paper.economy.PaperEconomyBridgeCoordinator
import io.bennyc.civilizations.infrastructure.paper.economy.VaultEconomyBootstrap
import io.bennyc.civilizations.infrastructure.paper.protection.PaperProtectionListener
import io.bennyc.civilizations.infrastructure.paper.repair.PaperRepairCoordinator
import io.bennyc.civilizations.infrastructure.paper.war.PaperBattleEntryListener
import io.bennyc.civilizations.infrastructure.paper.war.PaperBattleCombatListener
import io.bennyc.civilizations.infrastructure.paper.war.PaperBattleResolutionCoordinator
import io.bennyc.civilizations.infrastructure.runtime.CivilizationsRuntime
import io.bennyc.civilizations.infrastructure.runtime.RuntimeStartOutcome
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.Executor
import java.util.logging.Level

class CivilizationsPlugin : JavaPlugin() {
    private lateinit var runtime: CivilizationsRuntime
    private lateinit var protectionListener: PaperProtectionListener
    private lateinit var battleEntryListener: PaperBattleEntryListener
    private lateinit var battleCombatListener: PaperBattleCombatListener
    private lateinit var battleResolutionCoordinator: PaperBattleResolutionCoordinator
    private lateinit var repairCoordinator: PaperRepairCoordinator

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
            economyRules = runtimeConfiguration.economyRules,
            serverThread = serverThread,
            fatalFailureHandler = { failure ->
                logger.log(Level.SEVERE, "Civilizations failed closed", failure)
                if (isEnabled) {
                    server.pluginManager.disablePlugin(this)
                }
            },
        )
        val playerEconomy = if (server.pluginManager.isPluginEnabled("Vault")) {
            VaultEconomyBootstrap.discover(server)
        } else {
            null
        }
        if (playerEconomy == null) {
            logger.warning(
                "No Vault economy provider found; civilization treasuries work, but player " +
                    "deposits and withdrawals are unavailable",
            )
        } else {
            logger.info("Using ${playerEconomy.descriptor.providerName} for player wallets via Vault")
        }
        val economyBridge = PaperEconomyBridgeCoordinator(runtime, playerEconomy, logger)
        repairCoordinator = PaperRepairCoordinator(
            plugin = this,
            runtime = runtime,
            server = server,
            rules = runtimeConfiguration.repairRunnerRules,
            logger = logger,
        )
        battleResolutionCoordinator = PaperBattleResolutionCoordinator(
            plugin = this,
            runtime = runtime,
            server = server,
            rules = runtimeConfiguration.battleResolutionRules,
            logger = logger,
            repairCoordinator = repairCoordinator,
        )

        registerCommand(
            "civadmin",
            "Administer Civilizations",
            listOf("civilizationsadmin"),
            CivilizationsAdminCommand(
                runtime,
                logger,
                repairCoordinator,
                battleResolutionCoordinator,
            ),
        )
        registerCommand(
            "civilizations",
            "Civilizations player operations",
            listOf("civ"),
            CivilizationsCommand(
                runtime = runtime,
                rules = runtimeConfiguration.warRules,
                server = server,
                logger = logger,
                economyBridge = economyBridge,
                repairCoordinator = repairCoordinator,
                battleResolutionCoordinator = battleResolutionCoordinator,
            ),
        )
        battleCombatListener = PaperBattleCombatListener(
            plugin = this,
            runtime = runtime,
            server = server,
            logger = logger,
            resolutionCoordinator = battleResolutionCoordinator,
        )
        server.pluginManager.registerEvents(battleCombatListener, this)
        protectionListener = PaperProtectionListener(
            runtime,
            server,
            logger,
            battleCombatListener::isCapabilitySuppressed,
            battleCombatListener::markAuthorizedBattleLockLethalDamage,
        )
        server.pluginManager.registerEvents(protectionListener, this)
        battleEntryListener = PaperBattleEntryListener(
            runtime,
            server,
            logger,
            runtimeConfiguration.battleCombatRules,
        )
        server.pluginManager.registerEvents(battleEntryListener, this)
        runtime.start { outcome ->
            when (outcome) {
                is RuntimeStartOutcome.Ready -> {
                    battleResolutionCoordinator.recover(outcome.state)
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
        if (::battleCombatListener.isInitialized) {
            logger.info("Battle combat metrics: ${battleCombatListener.metricsSummary()}")
            battleCombatListener.close()
        }
        if (::battleResolutionCoordinator.isInitialized) {
            logger.info(
                "Battle resolution metrics: ${battleResolutionCoordinator.metricsSummary()}",
            )
            battleResolutionCoordinator.close()
        }
        if (::repairCoordinator.isInitialized) {
            logger.info("Repair runner metrics: ${repairCoordinator.metricsSummary()}")
            repairCoordinator.close()
        }
        if (::protectionListener.isInitialized) {
            logger.info(
                "Battle block mutation metrics: " +
                    protectionListener.battleMutationMetricsSummary(),
            )
        }
        if (::battleEntryListener.isInitialized) {
            logger.info(
                "Battle entry metrics: " + battleEntryListener.metricsSummary(),
            )
        }
        if (::runtime.isInitialized) {
            runtime.close()
        }
    }
}
