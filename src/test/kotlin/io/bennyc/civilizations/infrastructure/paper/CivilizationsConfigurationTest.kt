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
            assertEquals(MoneyAmount(10_000), loaded.claimRules.baseClaimPrice)
            assertEquals(MoneyAmount(100), loaded.claimRules.pricePerBlock)
            assertEquals(3, loaded.claimRules.groupTiers.size)
            assertEquals(MoneyAmount(2_500_000), loaded.claimRules.groupTiers[1].establishmentCost)
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
            assertEquals(1, loaded.battleCombatRules.livesPerCombatant)
            assertEquals(200, loaded.battleResolutionRules.observationsPerTick)
            assertEquals(CurrencyScale(2), loaded.economyRules.currencyScale)
            assertEquals(MoneyAmount.ZERO, loaded.economyRules.openingCivilizationBalance)
            assertEquals(MoneyAmount(100), loaded.economyRules.repair.restoreOriginalUnitPrice)
            assertEquals(2_500, loaded.economyRules.repair.victorShareBasisPoints)
            assertEquals(
                MoneyAmount(250_000),
                loaded.economyRules.battleCasualties.attackerDeathCost,
            )
            assertEquals(
                MoneyAmount(100_000),
                loaded.economyRules.battleCasualties.defenderDeathCost,
            )
            assertEquals(true, loaded.economyRules.battleCasualties.requireAttackerCoverage)
            assertEquals(true, loaded.economyRules.battleCasualties.lockWithdrawalsDuringBattle)
            assertEquals(true, loaded.landProtectionRules.enabled)
            assertEquals(604_800, loaded.landProtectionRules.intervalSeconds)
            assertEquals(259_200, loaded.landProtectionRules.graceSeconds)
            assertEquals(MoneyAmount(100_000), loaded.landProtectionRules.baseCharge)
            assertEquals(MoneyAmount(500_000), loaded.landProtectionRules.baseReserve)
            assertEquals(500, loaded.landProtectionRules.damageLimitPerExposure)
            assertEquals(20, loaded.repairRunnerRules.blocksPerTick)
            assertEquals(200, loaded.repairRunnerRules.assessmentBlocksPerTick)
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
    fun `battle combat lives are path-validated`() {
        val override = load(
            validYaml.replace("lives-per-combatant: 1", "lives-per-combatant: 3"),
        )
        assertEquals(3, override.battleCombatRules.livesPerCombatant)

        val failure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("lives-per-combatant: 1", "lives-per-combatant: 0"))
        }
        assertContains(failure.message.orEmpty(), "gameplay.war.lives-per-combatant")
    }

    @Test
    fun `battle resolution observation budget is path-validated`() {
        val override = load(
            validYaml.replace(
                "resolution-observations-per-tick: 200",
                "resolution-observations-per-tick: 37",
            ),
        )
        assertEquals(37, override.battleResolutionRules.observationsPerTick)

        val failure = assertFailsWith<IllegalArgumentException> {
            load(
                validYaml.replace(
                    "resolution-observations-per-tick: 200",
                    "resolution-observations-per-tick: 0",
                ),
            )
        }
        assertContains(
            failure.message.orEmpty(),
            "gameplay.war.resolution-observations-per-tick",
        )
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

        val casualtyFailure = assertFailsWith<IllegalArgumentException> {
            load(
                validYaml.replace(
                    "attacker-death-cost: \"2500.00\"",
                    "attacker-death-cost: \"-1.00\"",
                ),
            )
        }
        assertContains(
            casualtyFailure.message.orEmpty(),
            "economy.battle-casualties.attacker-death-cost",
        )
    }

    @Test
    fun `repair tick budgets are path-validated`() {
        val override = load(
            validYaml
                .replaceFirst("blocks-per-tick: 20", "blocks-per-tick: 7")
                .replaceFirst("blocks-per-tick: 200", "blocks-per-tick: 350"),
        )
        assertEquals(7, override.repairRunnerRules.blocksPerTick)
        assertEquals(350, override.repairRunnerRules.assessmentBlocksPerTick)

        val runnerFailure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replaceFirst("blocks-per-tick: 20", "blocks-per-tick: 0"))
        }
        assertContains(runnerFailure.message.orEmpty(), "repair.runner.blocks-per-tick")

        val assessmentFailure = assertFailsWith<IllegalArgumentException> {
            load(
                validYaml.replaceFirst("blocks-per-tick: 200", "blocks-per-tick: 4001"),
            )
        }
        assertContains(
            assessmentFailure.message.orEmpty(),
            "repair.assessment.blocks-per-tick",
        )
    }

    @Test
    fun `claim group tiers and land protection bounds are path-validated`() {
        val tierFailure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("max-groups: 1", "max-groups: 2"))
        }
        assertContains(tierFailure.message.orEmpty(), "claims.groups.tiers")

        val graceFailure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("grace-seconds: 259200", "grace-seconds: 0"))
        }
        assertContains(graceFailure.message.orEmpty(), "gameplay.land-protection")

        val capFailure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("damage-limit-per-exposure: 500", "damage-limit-per-exposure: 0"))
        }
        assertContains(capFailure.message.orEmpty(), "gameplay.land-protection")

        val maximumPriceFailure = assertFailsWith<IllegalArgumentException> {
            load(validYaml.replace("max-area: 256", "max-area: 1000000000000000"))
        }
        assertContains(maximumPriceFailure.message.orEmpty(), "gameplay.land-protection")
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
              base-price: "100.00"
              price-per-block: "1.00"
              ordinary-initiator-roles: [LEADER]
              groups:
                tiers:
                  - max-groups: 1
                    minimum-members: 1
                    minimum-treasury-balance: "0.00"
                    establishment-cost: "0.00"
            gameplay:
              phase-gates:
                roster-changes: [SETUP, PEACE]
                claim-creation: [SETUP, PEACE]
                member-land-actions: [SETUP, PEACE, WAR]
              war:
                battle-duration-seconds: 1800
                lives-per-combatant: 1
                resolution-observations-per-tick: 200
              land-protection:
                enabled: true
                interval-seconds: 604800
                grace-seconds: 259200
                assessment-interval-seconds: 60
                base-charge: "1000.00"
                per-block-charge: "0.10"
                base-reserve: "5000.00"
                per-block-reserve: "0.25"
                damage-limit-per-exposure: 500
            economy:
              currency-scale: 2
              opening-civilization-balance: "0.00"
              repair:
                restore-original-unit-price: "1.00"
                remove-placement-unit-price: "1.00"
                victor-share-percent: "25.00"
                ordinary-initiator-roles: [LEADER]
              battle-casualties:
                attacker-death-cost: "2500.00"
                defender-death-cost: "1000.00"
                require-attacker-coverage: true
                lock-withdrawals-during-battle: true
            repair:
              runner:
                blocks-per-tick: 20
              assessment:
                blocks-per-tick: 200
        """.trimIndent()
    }
}
