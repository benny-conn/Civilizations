package io.bennyc.civilizations.infrastructure.persistence.jdbc

import io.bennyc.civilizations.application.mob.*
import io.bennyc.civilizations.domain.identity.SeasonId
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

internal class JdbcManagedMobs(private val connection: Connection) {
    fun find(id: UUID) = one("id", id)
    fun creation(id: UUID) = one("creation_id", id)
    fun entity(id: UUID) = one("entity_uuid", id)
    private fun one(column: String, id: UUID): ManagedMob? = connection.prepareStatement("SELECT * FROM managed_mobs WHERE $column=?").use {
        it.setString(1, id.toString())
        it.executeQuery().use { r -> if (r.next()) mob(r) else null }
    }
    fun page(season: SeasonId, after: UUID?, limit: Int): List<ManagedMob> {
        require(limit in 1..1000)
        return connection.prepareStatement("SELECT * FROM managed_mobs WHERE season_id=? AND id>? ORDER BY id LIMIT ?").use {
            it.setString(1, season.toString()); it.setString(2, after?.toString() ?: ""); it.setInt(3, limit)
            it.executeQuery().use { r -> buildList { while (r.next()) add(mob(r)) } }
        }
    }
    fun reserved(id: UUID): Boolean = connection.prepareStatement("SELECT 1 FROM managed_mob_parent_reservations WHERE parent_id=?").use {
        it.setString(1, id.toString()); it.executeQuery().use { r -> r.next() }
    }
    fun insert(m: ManagedMob) {
        val c = m.creation
        connection.prepareStatement("""INSERT INTO managed_mobs
            (id,creation_id,season_id,world_uuid,species,maturity_ms,cooldown_ms,actor,reason,parent_a,parent_b,prepared_at,mature_at,breed_after,life,entity_uuid)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""").use {
            it.setString(1,m.id.toString()); it.setString(2,c.operationId.toString()); it.setString(3,c.seasonId.toString())
            it.setString(4,c.worldUuid.toString()); it.setString(5,c.species.key); it.setLong(6,c.rules.maturityMillis)
            it.setLong(7,c.rules.breedingCooldownMillis); it.setString(8,c.actor); it.setString(9,c.reason)
            it.setString(10,c.parentA?.toString()); it.setString(11,c.parentB?.toString()); it.setLong(12,m.preparedAt.toEpochMilli())
            it.setLong(13,m.matureAt.toEpochMilli()); it.setLong(14,m.breedAfter.toEpochMilli()); it.setString(15,m.life.name)
            it.setString(16,m.entityUuid?.toString()); it.executeUpdate()
        }
    }
    fun update(m: ManagedMob) {
        connection.prepareStatement("UPDATE managed_mobs SET life=?,entity_uuid=?,breed_after=? WHERE id=?").use {
            it.setString(1,m.life.name); it.setString(2,m.entityUuid?.toString()); it.setLong(3,m.breedAfter.toEpochMilli()); it.setString(4,m.id.toString())
            check(it.executeUpdate()==1)
        }
    }
    fun deathForMob(id: UUID) = deathRow("mob_id", id)
    fun death(id: UUID) = deathRow("operation_id", id)
    private fun deathRow(column: String, id: UUID): MobDeath? = connection.prepareStatement("SELECT * FROM managed_mob_deaths WHERE $column=?").use {
        it.setString(1,id.toString()); it.executeQuery().use { r -> if (!r.next()) null else MobDeath(
            UUID.fromString(r.getString("operation_id")), UUID.fromString(r.getString("mob_id")), r.getString("actor"), r.getString("reason"),
            Instant.ofEpochMilli(r.getLong("prepared_at")), DeathStage.valueOf(r.getString("stage"))) }
    }
    fun insertDeath(d: MobDeath) {
        connection.prepareStatement("INSERT INTO managed_mob_deaths VALUES(?,?,?,?,?,?)").use {
            it.setString(1,d.operationId.toString()); it.setString(2,d.mobId.toString()); it.setString(3,d.actor); it.setString(4,d.reason)
            it.setLong(5,d.preparedAt.toEpochMilli()); it.setString(6,d.stage.name); it.executeUpdate()
        }
    }
    fun updateDeath(d: MobDeath) {
        connection.prepareStatement("UPDATE managed_mob_deaths SET stage=? WHERE operation_id=?").use {
            it.setString(1,d.stage.name); it.setString(2,d.operationId.toString()); check(it.executeUpdate()==1)
        }
    }
    private fun mob(r: ResultSet) = ManagedMob(
        MobCreation(UUID.fromString(r.getString("creation_id")), UUID.fromString(r.getString("id")), SeasonId(UUID.fromString(r.getString("season_id"))),
            UUID.fromString(r.getString("world_uuid")), MobSpecies(r.getString("species")), MobRules(r.getLong("maturity_ms"),r.getLong("cooldown_ms")),
            r.getString("actor"), r.getString("reason"), r.getString("parent_a")?.let(UUID::fromString), r.getString("parent_b")?.let(UUID::fromString)),
        Instant.ofEpochMilli(r.getLong("prepared_at")), Instant.ofEpochMilli(r.getLong("mature_at")), Instant.ofEpochMilli(r.getLong("breed_after")),
        MobLife.valueOf(r.getString("life")), r.getString("entity_uuid")?.let(UUID::fromString))
}
