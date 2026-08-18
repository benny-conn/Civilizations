package io.bennyc.civilizations.infrastructure.persistence.jdbc

import java.sql.Connection

fun interface JdbcConnectionFactory {
    fun open(): Connection
}
