package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.mob.*
import io.bennyc.civilizations.domain.identity.SeasonId
import java.sql.Connection
import java.time.Instant
import java.util.UUID

internal class JdbcCattleActivation(private val connection: Connection) {
    fun find(): CattleActivation? = connection.createStatement().use { s -> s.executeQuery("SELECT * FROM cattle_activation").use { r ->
        if(!r.next()) null else {
            val seeds=connection.createStatement().use { ss -> ss.executeQuery("SELECT * FROM cattle_seeds ORDER BY ordinal").use { rr -> buildList {
                while(rr.next()) add(CattleSeed(rr.getString("id"),UUID.fromString(rr.getString("world_uuid")),rr.getInt("x"),rr.getInt("y"),rr.getInt("z")))
            } } }
            CattleActivation(SeasonId(UUID.fromString(r.getString("season_id"))),MobRules(r.getLong("maturity_ms"),r.getLong("cooldown_ms")),seeds,r.getString("actor"),Instant.ofEpochMilli(r.getLong("activated_at")))
        }
    } }
    fun insert(a: CattleActivation) {
        connection.prepareStatement("INSERT INTO cattle_activation VALUES(1,?,?,?,?,?)").use {
            it.setString(1,a.seasonId.toString());it.setLong(2,a.rules.maturityMillis);it.setLong(3,a.rules.breedingCooldownMillis);it.setString(4,a.actor);it.setLong(5,a.activatedAt.toEpochMilli());it.executeUpdate()
        }
        connection.prepareStatement("INSERT INTO cattle_seeds VALUES(?,1,?,?,?,?,?)").use { s -> a.seeds.forEachIndexed { i,seed ->
            s.setString(1,seed.id);s.setInt(2,i);s.setString(3,seed.worldUuid.toString());s.setInt(4,seed.x);s.setInt(5,seed.y);s.setInt(6,seed.z);s.executeUpdate()
        } }
    }
}
