package io.bennyc.civilizations.infrastructure.persistence.jdbc

import java.time.Clock

class SchemaMigrator(
    private val connectionFactory: JdbcConnectionFactory,
    private val migrations: List<SchemaMigration> = CivilizationsSchema.migrations,
    private val clock: Clock = Clock.systemUTC(),
) {
    init {
        require(migrations.map { it.version }.distinct().size == migrations.size) {
            "Migration versions must be unique"
        }
        require(migrations.sortedBy { it.version } == migrations) {
            "Migrations must be ordered by version"
        }
    }

    fun migrate(): SchemaMigrationReport = connectionFactory.open().use { connection ->
        connection.autoCommit = false
        try {
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY,
                        name TEXT NOT NULL,
                        applied_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }

            val knownByVersion = migrations.associateBy { it.version }
            val applied = linkedMapOf<Int, String>()
            connection.prepareStatement(
                "SELECT version, name FROM schema_migrations ORDER BY version",
            ).use { statement ->
                statement.executeQuery().use { results ->
                    while (results.next()) {
                        applied[results.getInt("version")] = results.getString("name")
                    }
                }
            }

            for ((version, name) in applied) {
                val known = knownByVersion[version]
                    ?: throw SchemaMigrationException(
                        "Database contains unknown migration version $version ($name)",
                    )
                if (known.name != name) {
                    throw SchemaMigrationException(
                        "Migration $version name mismatch: database has '$name', code has '${known.name}'",
                    )
                }
            }

            val appliedVersions = applied.keys.toList()
            val expectedPrefix = migrations.take(appliedVersions.size).map { it.version }
            if (appliedVersions != expectedPrefix) {
                throw SchemaMigrationException(
                    "Applied migrations are not a valid prefix: expected $expectedPrefix, found $appliedVersions",
                )
            }

            val previousVersion = applied.keys.maxOrNull() ?: 0
            val newlyApplied = mutableListOf<Int>()
            for (migration in migrations) {
                if (migration.version in applied) {
                    continue
                }

                connection.createStatement().use { statement ->
                    for (sql in migration.statements) {
                        statement.executeUpdate(sql)
                    }
                }
                connection.prepareStatement(
                    "INSERT INTO schema_migrations(version, name, applied_at_ms) VALUES (?, ?, ?)",
                ).use { statement ->
                    statement.setInt(1, migration.version)
                    statement.setString(2, migration.name)
                    statement.setLong(3, clock.instant().toEpochMilli())
                    statement.executeUpdate()
                }
                newlyApplied.add(migration.version)
            }

            connection.commit()
            SchemaMigrationReport(
                previousVersion = previousVersion,
                currentVersion = migrations.lastOrNull()?.version ?: 0,
                appliedVersions = newlyApplied,
            )
        } catch (failure: Throwable) {
            try {
                connection.rollback()
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
            throw failure
        }
    }
}
