package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.domain.economy.CurrencyScale
import io.bennyc.civilizations.domain.economy.MoneyAmount
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CivilizationsConfigurationTest {
    @Test
    fun `shipped yaml loads into typed validated rules`() {
        val yaml = checkNotNull(javaClass.classLoader.getResourceAsStream("config.yml"))
            .bufferedReader()
            .use { it.readText() }
        val dataFolder = Files.createTempDirectory("civilizations-config-test")
        try {
            val loaded = CivilizationsConfiguration.load(
                dataFolder,
                YamlConfiguration().apply { loadFromString(yaml) },
            )

            assertEquals(dataFolder.resolve("civilizations-v2.db"), loaded.databasePath)
            assertEquals(65_536, loaded.claimRules.maxArea)
            assertEquals(32, loaded.claimRules.maxClaimsPerCivilization)
            assertEquals(
                setOf(SeasonStatus.SETUP, SeasonStatus.PEACE, SeasonStatus.WAR),
                loaded.phaseRules.rosterChangesAllowedIn,
            )
            assertEquals(
                setOf(SeasonStatus.SETUP, SeasonStatus.PEACE),
                loaded.phaseRules.claimCreationAllowedIn,
            )
            assertEquals(
                setOf(SeasonStatus.SETUP, SeasonStatus.PEACE, SeasonStatus.WAR),
                loaded.phaseRules.memberLandActionsAllowedIn,
            )
            assertEquals(1_800, loaded.warRules.battleDurationSeconds)
            assertEquals(CurrencyScale(2), loaded.economyRules.currencyScale)
            assertEquals(MoneyAmount.ZERO, loaded.economyRules.openingCivilizationBalance)
            assertEquals(MoneyAmount(100), loaded.economyRules.repair.restoreOriginalUnitPrice)
            assertEquals(2_500, loaded.economyRules.repair.victorShareBasisPoints)
        } finally {
            dataFolder.toFile().deleteRecursively()
        }
    }

    @Test
    fun `unsafe configured phase fails with its yaml path`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            load(
                validYaml.replace(
                    "claim-creation: [SETUP, PEACE]",
                    "claim-creation: [SETUP, WAR]",
                ),
            )
        }

        assertContains(failure.message.orEmpty(), "gameplay.phase-gates.claim-creation")
        assertContains(failure.message.orEmpty(), "WAR")
    }

    @Test
    fun `explicit legacy keys win while new phase gates use resource defaults`() {
        val defaults = YamlConfiguration().apply { loadFromString(shippedYaml()) }
        val legacy = YamlConfiguration().apply {
            loadFromString(
                """
                    v2:
                      database-file: legacy.db
                      claims:
                        max-area: 100
                        max-count: 2
                        require-edge-connection: false
                """.trimIndent(),
            )
            setDefaults(defaults)
        }
        val dataFolder = Files.createTempDirectory("civilizations-config-test")
        try {
            val loaded = CivilizationsConfiguration.load(dataFolder, legacy)

            assertEquals(dataFolder.resolve("legacy.db"), loaded.databasePath)
            assertEquals(100, loaded.claimRules.maxArea)
            assertEquals(2, loaded.claimRules.maxClaimsPerCivilization)
            assertEquals(false, loaded.claimRules.requireEdgeConnection)
            assertEquals(
                setOf(SeasonStatus.SETUP, SeasonStatus.PEACE, SeasonStatus.WAR),
                loaded.phaseRules.rosterChangesAllowedIn,
            )
        } finally {
            dataFolder.toFile().deleteRecursively()
        }
    }

    @Test
    fun `malformed scalar fails with its yaml path`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("max-area: 256", "max-area: enormous"))
        }

        assertContains(failure.message.orEmpty(), "claims.max-area")
        assertContains(failure.message.orEmpty(), "integer")
    }

    @Test
    fun `battle duration is path-validated`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("battle-duration-seconds: 1800", "battle-duration-seconds: 0"))
        }

        assertContains(failure.message.orEmpty(), "gameplay.war.battle-duration-seconds")
    }

    @Test
    fun `economy scale and exact amounts are path-validated`() {
        val scaleFailure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("currency-scale: 2", "currency-scale: 9"))
        }
        assertContains(scaleFailure.message.orEmpty(), "economy.currency-scale")

        val amountFailure = assertFailsWith<IllegalArgumentException> {
            load(
                validYaml.replace(
                    "opening-civilization-balance: \"0.00\"",
                    "opening-civilization-balance: \"0.001\"",
                ),
            )
        }
        assertContains(amountFailure.message.orEmpty(), "economy.opening-civilization-balance")
    }

    private fun load(yaml: String): CivilizationsConfiguration {
        val dataFolder = Files.createTempDirectory("civilizations-config-test")
        return try {
            CivilizationsConfiguration.load(
                dataFolder,
                YamlConfiguration().apply { loadFromString(yaml) },
            )
        } finally {
            dataFolder.toFile().deleteRecursively()
        }
    }

    private fun shippedYaml(): String =
        checkNotNull(javaClass.classLoader.getResourceAsStream("config.yml"))
            .bufferedReader()
            .use { it.readText() }

    private companion object {
        val validYaml = """
            storage:
              database-file: civilizations.db
            claims:
              max-area: 256
              max-count: 4
              require-edge-connection: true
            gameplay:
              phase-gates:
                roster-changes: [SETUP, PEACE]
                claim-creation: [SETUP, PEACE]
                member-land-actions: [SETUP, PEACE, WAR]
              war:
                battle-duration-seconds: 1800
            economy:
              currency-scale: 2
              opening-civilization-balance: "0.00"
              repair:
                restore-original-unit-price: "1.00"
                remove-placement-unit-price: "1.00"
                victor-share-percent: "25.00"
                ordinary-initiator-roles: [LEADER]
        """.trimIndent()
    }
}
