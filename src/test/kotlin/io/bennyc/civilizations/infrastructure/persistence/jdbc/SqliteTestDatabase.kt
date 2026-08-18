package io.bennyc.civilizations.infrastructure.persistence.jdbc

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists

internal class SqliteTestDatabase : AutoCloseable {
    private val directory: Path = Files.createTempDirectory("civilizations-sqlite-test-")
    private val databasePath: Path = directory.resolve("civilizations.db")

    val connectionFactory = SqliteConnectionFactory(databasePath)
    val migrator = SchemaMigrator(connectionFactory)
    val repository = JdbcCivilizationsRepository(connectionFactory)

    override fun close() {
        databasePath.resolveSibling("${databasePath.fileName}-wal").deleteIfExists()
        databasePath.resolveSibling("${databasePath.fileName}-shm").deleteIfExists()
        databasePath.deleteIfExists()
        directory.deleteIfExists()
    }
}
