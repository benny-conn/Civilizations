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
            assertEquals(4, first.currentVersion)
            assertEquals(listOf(1, 2, 3, 4), first.appliedVersions)
            assertEquals(4, second.previousVersion)
            assertEquals(4, second.currentVersion)
            assertTrue(second.appliedVersions.isEmpty())

            database.connectionFactory.open().use { connection ->
                connection.prepareStatement(
                    """
                    SELECT name
                    FROM sqlite_master
                    WHERE type = 'table' AND name IN (
                        'schema_migrations', 'seasons', 'civilizations', 'memberships', 'claims',
                        'runtime_state', 'wars', 'battles', 'battle_participants',
                        'battle_block_changes'
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
                                "battle_block_changes",
                            ),
                            tables,
                        )
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
