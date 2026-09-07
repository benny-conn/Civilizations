package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.scarcity.CaneActivation
import io.bennyc.civilizations.domain.identity.SeasonId
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal class JdbcCaneActivation(private val connection: Connection) {
    fun find(): CaneActivation? = connection.prepareStatement("SELECT * FROM cane_activation WHERE singleton=1").use { s ->
        s.executeQuery().use { r ->
            if (!r.next()) null else CaneActivation(SeasonId(UUID.fromString(r.getString("season_id"))), r.getInt("max_height"),
                r.getString("actor"), r.getString("reason"), Instant.ofEpochMilli(r.getLong("activated_at_ms")))
        }
    }
    fun insert(r: CaneActivation) {
        connection.prepareStatement("INSERT INTO cane_activation(singleton,season_id,max_height,actor,reason,activated_at_ms) VALUES(1,?,?,?,?,?)").use {
            it.setString(1, r.seasonId.toString())
            it.setInt(2, r.maxHeight)
            it.setString(3, r.actor)
            it.setString(4, r.reason)
            it.setLong(5, r.activatedAt.toEpochMilli())
            it.executeUpdate()
        }
    }
}
