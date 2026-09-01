package io.bennyc.civilizations.infrastructure.persistence.jdbc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SchemaMigratorTest {
    @Test
    fun `applies the schema once and reports idempotent subsequent runs`() {
        SqliteTestDatabase().use { database ->
            val first = database.migrator.migrate()
            val second = database.migrator.migrate()

            assertEquals(0, first.previousVersion)
            assertEquals(11, first.currentVersion)
            assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11), first.appliedVersions)
            assertEquals(11, second.previousVersion)
            assertEquals(11, second.currentVersion)
            assertTrue(second.appliedVersions.isEmpty())

            database.connectionFactory.open().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT name
                    FROM sqlite_master
                    WHERE type = 'table' AND name IN (
                        'schema_migrations', 'seasons', 'civilizations', 'memberships', 'claims',
                        'runtime_state', 'wars', 'battles', 'battle_participants',
                        'battle_surrenders',
                        'battle_combat_states', 'battle_combatants', 'battle_life_events',
                        'battle_casualty_economics', 'battle_casualties',
                        'battle_block_changes', 'battle_damage_reports',
                        'battle_damage_report_entries', 'season_economy_settings',
                        'civilization_accounts', 'economy_ledger_transactions',
                        'economy_ledger_postings', 'economy_bridge_transfers',
                        'repair_jobs', 'repair_job_items', 'claim_groups',
                        'land_protection_states', 'land_upkeep_assessments',
                        'exposure_damage_sites', 'exposure_damage_events',
                        'protection_repair_jobs', 'protection_repair_job_items'
                    )
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { results ->
                        val tables = buildSet {
                            while (results.next()) {
                                add(results.getString("name"))
                            }
                        }
                        assertEquals(
                            setOf(
                                "schema_migrations",
                                "seasons",
                                "civilizations",
                                "memberships",
                                "claims",
                                "runtime_state",
                                "wars",
                                "battles",
                                "battle_participants",
                                "battle_surrenders",
                                "battle_combat_states",
                                "battle_combatants",
                                "battle_life_events",
                                "battle_casualty_economics",
                                "battle_casualties",
                                "battle_block_changes",
                                "battle_damage_reports",
                                "battle_damage_report_entries",
                                "season_economy_settings",
                                "civilization_accounts",
                                "economy_ledger_transactions",
                                "economy_ledger_postings",
                                "economy_bridge_transfers",
                                "repair_jobs",
                                "repair_job_items",
                                "claim_groups",
                                "land_protection_states",
                                "land_upkeep_assessments",
                                "exposure_damage_sites",
                                "exposure_damage_events",
                                "protection_repair_jobs",
                                "protection_repair_job_items",
                            ),
                            tables,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `claim group migration preserves connected components from existing land`() {
        SqliteTestDatabase().use { database ->
            SchemaMigrator(
                database.connectionFactory,
                CivilizationsSchema.migrations.take(10),
            ).migrate()
            val season = "00000000-0000-0000-0000-000000000001"
            val civilization = "00000000-0000-0000-0000-000000000002"
            val first = "00000000-0000-0000-0000-000000000010"
            val adjacent = "00000000-0000-0000-0000-000000000011"
            val separate = "00000000-0000-0000-0000-000000000012"
            database.connectionFactory.open().use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        "INSERT INTO seasons VALUES ('$season', 'One', 'SETUP', 1, 1)",
                    )
                    statement.executeUpdate(
                        "INSERT INTO civilizations VALUES " +
                            "('$civilization', '$season', 'North', 'north', 'ACTIVE', 1, 1)",
                    )
                    statement.executeUpdate(
                        "INSERT INTO claims VALUES " +
                            "('$first', '$season', '$civilization', 'minecraft:overworld', 0, 9, 0, 9)",
                    )
                    statement.executeUpdate(
                        "INSERT INTO claims VALUES " +
                            "('$adjacent', '$season', '$civilization', 'minecraft:overworld', 10, 19, 0, 9)",
                    )
                    statement.executeUpdate(
                        "INSERT INTO claims VALUES " +
                            "('$separate', '$season', '$civilization', 'minecraft:overworld', 40, 49, 0, 9)",
                    )
                }
            }

            database.migrator.migrate()

            database.connectionFactory.open().use { connection ->
                val groups = mutableMapOf<String, String>()
                connection.prepareStatement("SELECT id, group_id FROM claims").use { statement ->
                    statement.executeQuery().use { results ->
                        while (results.next()) groups[results.getString(1)] = results.getString(2)
                    }
                }
                assertEquals(groups[first], groups[adjacent])
                assertTrue(groups[first] != groups[separate])
                connection.prepareStatement("SELECT COUNT(*) FROM claim_groups").use { statement ->
                    statement.executeQuery().use { results ->
                        assertTrue(results.next())
                        assertEquals(2, results.getInt(1))
                    }
                }
            }
        }
    }

    @Test
    fun `upgrades an existing damage journal schema to immutable reports`() {
        SqliteTestDatabase().use { database ->
            val versionFourMigrator = SchemaMigrator(
                database.connectionFactory,
                CivilizationsSchema.migrations.take(4),
            )
            assertEquals(4, versionFourMigrator.migrate().currentVersion)

            val upgraded = database.migrator.migrate()

            assertEquals(4, upgraded.previousVersion)
            assertEquals(11, upgraded.currentVersion)
            assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), upgraded.appliedVersions)
            database.connectionFactory.open().use { connection ->
                connection.prepareStatement(
                    "SELECT COUNT(*) FROM battle_damage_reports",
                ).use { statement ->
                    statement.executeQuery().use { results ->
                        assertTrue(results.next())
                        assertEquals(0, results.getInt(1))
                    }
                }
            }
        }
    }

    @Test
    fun `refuses a database with an unknown migration`() {
        SqliteTestDatabase().use { database ->
            database.migrator.migrate()
            database.connectionFactory.open().use { connection ->
                connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, applied_at_ms) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setInt(1, 999)
                    statement.setString(2, "future_schema")
                    statement.setLong(3, 1L)
                    statement.executeUpdate()
                }
            }

            val failure = assertFailsWith<SchemaMigrationException> {
                database.migrator.migrate()
            }
            assertTrue(failure.message!!.contains("unknown migration version 999"))
        }
    }
}
