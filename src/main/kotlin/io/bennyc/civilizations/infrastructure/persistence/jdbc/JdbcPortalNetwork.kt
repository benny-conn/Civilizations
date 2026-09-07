package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal class JdbcPortalNetwork(private val connection: Connection) {
    fun find(): PortalNetwork? = connection.prepareStatement("SELECT * FROM portal_network").use { s -> s.executeQuery().use { r ->
        if (!r.next()) return null
        val pairs = connection.prepareStatement("SELECT * FROM portal_sites ORDER BY pair_id,side").use { q -> q.executeQuery().use { rs ->
            buildList { while (rs.next()) add(rs.getString("pair_id") to PortalSite(WorldId(rs.getString("world_key")), UUID.fromString(rs.getString("world_uuid")),
                ResourceBounds(rs.getInt("min_x"),rs.getInt("min_y"),rs.getInt("min_z"),rs.getInt("max_x"),rs.getInt("max_y"),rs.getInt("max_z")))) }
        } }.groupBy({ it.first }, { it.second }).map { (id, sites) -> require(sites.size == 2); PortalPair(id, sites[0], sites[1]) }
        PortalNetwork(SeasonId(UUID.fromString(r.getString("season_id"))), pairs, r.getString("source_sha256"),r.getString("actor"),Instant.ofEpochMilli(r.getLong("imported_at_ms")))
    } }
    fun insert(n: PortalNetwork) {
        connection.prepareStatement("INSERT INTO portal_network VALUES(1,?,?,?,?)").use {
            it.setString(1,n.seasonId.toString());it.setString(2,n.sourceSha256);it.setString(3,n.actor);it.setLong(4,n.importedAt.toEpochMilli());it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO portal_sites VALUES(1,?,?,?,?,?,?,?,?,?,?)").use { s ->
            n.pairs.forEach { pair -> listOf(pair.first,pair.second).forEachIndexed { side, site ->
                s.setString(1,pair.id);s.setInt(2,side);s.setString(3,site.world.value);s.setString(4,site.uuid.toString())
                val b=site.bounds
                listOf(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ).forEachIndexed { i,v -> s.setInt(i+5,v) }
                s.addBatch()
            } }; s.executeBatch()
        }
    }
}
