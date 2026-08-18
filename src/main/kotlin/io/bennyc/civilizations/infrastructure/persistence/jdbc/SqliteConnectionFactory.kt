package io.bennyc.civilizations.infrastructure.persistence.jdbc

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

class SqliteConnectionFactory(
    databasePath: Path,
    private val busyTimeoutMilliseconds: Int = DEFAULT_BUSY_TIMEOUT_MILLISECONDS,
) : JdbcConnectionFactory {
    private val jdbcUrl = "jdbc:sqlite:${databasePath.toAbsolutePath().normalize()}"

    init {
        require(busyTimeoutMilliseconds >= 0) { "SQLite busy timeout cannot be negative" }
        Class.forName(SQLITE_DRIVER_CLASS)
    }

    override fun open(): Connection {
        val connection = DriverManager.getConnection(jdbcUrl)
        try {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA busy_timeout = $busyTimeoutMilliseconds")
                statement.execute("PRAGMA journal_mode = WAL")
            }
            return connection
        } catch (failure: Throwable) {
            try {
                connection.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }

    private companion object {
        const val SQLITE_DRIVER_CLASS = "org.sqlite.JDBC"
        const val DEFAULT_BUSY_TIMEOUT_MILLISECONDS = 5_000
    }
}
