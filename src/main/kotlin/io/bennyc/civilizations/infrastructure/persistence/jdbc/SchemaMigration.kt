package io.bennyc.civilizations.infrastructure.persistence.jdbc

data class SchemaMigration(
    val version: Int,
    val name: String,
    val statements: List<String>,
) {
    init {
        require(version > 0) { "Migration version must be positive" }
        require(name.isNotBlank()) { "Migration name cannot be blank" }
        require(statements.isNotEmpty()) { "Migration must contain at least one statement" }
    }
}

data class SchemaMigrationReport(
    val previousVersion: Int,
    val currentVersion: Int,
    val appliedVersions: List<Int>,
)

class SchemaMigrationException(message: String) : IllegalStateException(message)
