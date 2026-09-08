package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.scarcity.DiamondActivation
import io.bennyc.civilizations.domain.identity.SeasonId
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal class JdbcDiamondActivation(private val connection: Connection) {
    fun find(): DiamondActivation? = connection.prepareStatement("SELECT * FROM diamond_activation WHERE singleton=1").use { s ->
        s.executeQuery().use { r ->
            if (!r.next()) null else DiamondActivation(SeasonId(UUID.fromString(r.getString("season_id"))), r.getInt("include_equipment") == 1, r.getString("audit_sha256"),
                r.getString("actor"), r.getString("reason"), Instant.ofEpochMilli(r.getLong("activated_at_ms")))
        }
    }
    fun insert(r: DiamondActivation) {
        connection.prepareStatement("INSERT INTO diamond_activation(singleton,season_id,include_equipment,audit_sha256,actor,reason,activated_at_ms) VALUES(1,?,?,?,?,?,?)").use {
            it.setString(1, r.seasonId.toString())
            it.setInt(2, if (r.includeEquipment) 1 else 0)
            it.setString(3, r.auditSha256)
            it.setString(4, r.actor)
            it.setString(5, r.reason)
            it.setLong(6, r.activatedAt.toEpochMilli())
            it.executeUpdate()
        }
    }
}
