package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/** Uses the enclosing repository transaction; never owns or opens another connection. */
internal class JdbcWorldManifests(private val connection: Connection) {
    fun list(): List<RegisteredWorldManifest> {
        val zones = connection.prepareStatement("SELECT * FROM resource_zones ORDER BY manifest_id, zone_id").use { statement ->
            statement.executeQuery().use { rs -> buildList {
                while (rs.next()) add(rs.getString("manifest_id") to ResourceZone(rs.getString("zone_id"), ResourceKind.valueOf(rs.getString("resource")), rs.bounds()))
            } }.groupBy({ it.first }, { it.second })
        }
        return connection.prepareStatement("SELECT * FROM season_world_manifests ORDER BY imported_at_ms, id").use { statement ->
            statement.executeQuery().use { rs -> buildList {
                while (rs.next()) add(RegisteredWorldManifest(
                    WorldManifest(UUID.fromString(rs.getString("id")), SeasonId(UUID.fromString(rs.getString("season_id"))),
                        WorldId(rs.getString("world_key")), UUID.fromString(rs.getString("world_uuid")), rs.getInt("revision"),
                        rs.getString("source_sha256"), rs.bounds(), zones[rs.getString("id")].orEmpty()),
                    Instant.ofEpochMilli(rs.getLong("imported_at_ms")), rs.getString("actor"),
                ))
            } }
        }
    }
    fun insert(record: RegisteredWorldManifest) {
        val m = record.manifest
        connection.prepareStatement("INSERT INTO season_world_manifests (id,season_id,world_key,world_uuid,revision,source_sha256,min_x,min_y,min_z,max_x,max_y,max_z,imported_at_ms,actor) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)").use {
            it.setString(1, m.id.toString()); it.setString(2, m.seasonId.toString()); it.setString(3, m.worldId.value)
            it.setString(4, m.worldUuid.toString()); it.setInt(5, m.revision); it.setString(6, m.sourceSha256)
            it.bounds(7, m.bounds); it.setLong(13, record.importedAt.toEpochMilli()); it.setString(14, record.actor); it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO resource_zones (manifest_id,zone_id,resource,min_x,min_y,min_z,max_x,max_y,max_z) VALUES (?,?,?,?,?,?,?,?,?)").use {
            m.zones.forEach { zone ->
                it.setString(1, m.id.toString()); it.setString(2, zone.id); it.setString(3, zone.resource.name)
                it.bounds(4, zone.bounds); it.addBatch()
            }
            it.executeBatch()
        }
    }
    private fun ResultSet.bounds() = ResourceBounds(getInt("min_x"),getInt("min_y"),getInt("min_z"),getInt("max_x"),getInt("max_y"),getInt("max_z"))
    private fun PreparedStatement.bounds(start: Int, b: ResourceBounds) {
        listOf(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ).forEachIndexed { offset, value -> setInt(start + offset, value) }
    }
}
