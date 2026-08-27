package io.bennyc.civilizations.infrastructure.paper

import io.bennyc.civilizations.domain.season.SeasonStatus
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
        """.trimIndent()
    }
}
