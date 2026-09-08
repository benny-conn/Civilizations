package io.bennyc.civilizations.application.mob

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.*
import java.time.*
import java.util.UUID
import kotlin.test.*

class ManagedMobServiceTest {
    private class Fixture : AutoCloseable {
        val db = SqliteTestDatabase()
        var now = Instant.parse("2026-09-07T20:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant() = now
        }
        val seasons = SeasonService(db.repository, SequentialIdGenerator(), clock)
        val season = run { db.migrator.migrate(); seasons.create("Mobs").appliedValue() }
        val world = UUID(0, 100)
        var serial = 1000L
        val service = ManagedMobService(db.repository, clock)
        init {
            val key = WorldId("minecraft:mobs")
            val manifest = WorldManifest(UUID(0,101), season.id, key, world, 1, "a".repeat(64),
                ResourceBounds(0,0,0,15,255,15), listOf(ResourceZone("cattle", ResourceKind.CATTLE, ResourceBounds(0,60,0,15,70,15))))
            WorldManifestService(db.repository, clock).register(manifest, listOf(LoadedResourceWorld(key,world,0,256)),"console").appliedValue()
        }
        fun id() = UUID(0, serial++)
        fun request(species: String = "minecraft:cow", a: UUID? = null, b: UUID? = null) = MobCreation(
            id(), id(), season.id, world, MobSpecies(species), MobRules(12_000,6_000), "console","fixture",a,b)
        fun spawn(request: MobCreation = request()): ManagedMob {
            service.prepare(request).appliedValue()
            service.beginSpawn(request.operationId).appliedValue()
            return service.acknowledgeSpawn(request.operationId,id(),request.species,world).appliedValue()
        }
        fun mob(id: UUID) = db.repository.read { findManagedMob(id)!! }
        fun recovered() = ManagedMobService(JdbcCivilizationsRepository(db.connectionFactory),clock)
        override fun close() = db.close()
    }

    @Test fun `species keys support resource mobs without cattle-only identity or ticking rules`() {
        Fixture().use { f ->
            for (species in listOf("minecraft:cow","minecraft:sheep","minecraft:chicken","minecraft:villager","minecraft:zombie")) {
                val mob=f.spawn(f.request(species))
                assertEquals(species,f.mob(mob.id).creation.species.key)
                assertEquals(MobObservation.MANAGED,mob.observe(mob.entityUuid!!,mob.creation.species))
            }
            val first=f.db.repository.read { listManagedMobs(f.season.id,null,2) }
            val next=f.db.repository.read { listManagedMobs(f.season.id,first.last().id,1000) }
            assertEquals(5,(first+next).map { it.id }.distinct().size)
            assertFailsWith<IllegalArgumentException> { f.db.repository.read { listManagedMobs(f.season.id,null,1001) } }
        }
        for (bad in listOf("cow","Minecraft:Cow","minecraft:","a:"+"b".repeat(127))) assertFailsWith<IllegalArgumentException> { MobSpecies(bad) }
        for (bad in listOf(0L,-1L,Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { MobRules(bad,1) }
            assertFailsWith<IllegalArgumentException> { MobRules(1,bad) }
        }
    }

    @Test fun `creation identity retry conflicts and entity binding survive new repository`() {
        Fixture().use { f ->
            val r=f.request(); val prepared=f.service.prepare(r).appliedValue()
            assertEquals(prepared,f.recovered().prepare(r).unchangedValue())
            f.service.prepare(r.copy(species=MobSpecies("minecraft:sheep"))).rejection()
            f.service.prepare(r.copy(operationId=f.id())).rejection()
            f.service.acknowledgeSpawn(r.operationId,f.id(),r.species,f.world).rejection()
            f.service.beginSpawn(r.operationId).appliedValue()
            assertEquals(MobLife.APPLYING,f.recovered().beginSpawn(r.operationId).unchangedValue().life)
            f.service.cancelPrepared(r.operationId).rejection()
            f.service.acknowledgeSpawn(r.operationId,f.id(),r.species,f.id()).rejection()
            val entity=f.id()
            val alive=f.recovered().acknowledgeSpawn(r.operationId,entity,r.species,f.world).appliedValue()
            assertEquals(alive,f.recovered().acknowledgeSpawn(r.operationId,entity,r.species,f.world).unchangedValue())
            f.service.acknowledgeSpawn(r.operationId,f.id(),r.species,f.world).rejection()
            val other=f.request();f.service.prepare(other);f.service.beginSpawn(other.operationId)
            f.service.acknowledgeSpawn(other.operationId,entity,other.species,f.world).rejection()
            assertEquals(MobObservation.QUARANTINE,alive.observe(f.id(),r.species))
            assertEquals(MobObservation.QUARANTINE,(null as ManagedMob?).observe(entity,r.species))
        }
    }

    @Test fun `both parents remain reserved across restart until definite cancellation or acknowledgment`() {
        Fixture().use { f ->
            val a=f.spawn();val b=f.spawn();val c=f.spawn()
            val birth=f.request(a=a.id,b=b.id)
            f.service.prepare(birth).appliedValue()
            f.recovered().prepare(f.request(a=c.id,b=b.id)).rejection()
            f.service.prepareDeath(f.id(),a.id,"console","death while birth pending").rejection()
            f.recovered().cancelPrepared(birth.operationId).appliedValue()
            assertFalse(f.db.repository.read { hasMobReservation(a.id) })
            val next=f.request(a=a.id,b=b.id);f.service.prepare(next).appliedValue()
            f.service.beginSpawn(next.operationId).appliedValue()
            f.now=f.now.plusSeconds(60)
            f.recovered().prepare(f.request(a=a.id,b=c.id)).rejection()
            val child=f.recovered().acknowledgeSpawn(next.operationId,f.id(),next.species,f.world).appliedValue()
            assertEquals(f.now.plusMillis(6000),f.mob(a.id).breedAfter)
            assertFalse(f.db.repository.read { hasMobReservation(a.id) })
            val cooldown=f.mob(a.id).breedAfter
            f.now=f.now.plusSeconds(2)
            f.recovered().acknowledgeSpawn(next.operationId,child.entityUuid!!,next.species,f.world).unchangedValue()
            assertEquals(cooldown,f.mob(a.id).breedAfter)
            f.service.prepare(f.request(a=a.id,b=b.id)).rejection()
            f.now=cooldown
            f.service.prepare(f.request(a=a.id,b=b.id)).appliedValue()
        }
    }

    @Test fun `immature mixed species and inactive parents reject while deadlines remain snapshotted`() {
        Fixture().use { f ->
            val a=f.spawn();val b=f.spawn();val sheep=f.spawn(f.request("minecraft:sheep"))
            f.service.prepare(f.request(a=a.id,b=sheep.id)).rejection()
            val birth=f.request(a=a.id,b=b.id);val child=f.spawn(birth)
            assertEquals(f.now.plusMillis(12000),child.matureAt)
            f.now=f.now.plusMillis(6000)
            f.service.prepare(f.request(a=child.id,b=a.id)).rejection()
            f.now=f.now.plusMillis(6000)
            f.service.prepare(f.request(a=child.id,b=a.id)).appliedValue()
            assertFailsWith<IllegalArgumentException> { f.request(a=a.id,b=a.id) }
            val other=f.seasons.create("Other").appliedValue();f.seasons.selectActive(other.id)
            f.service.prepare(f.request()).rejection()
        }
    }

    @Test fun `seeding rechecks setup before issuing world attempt and unregistered worlds reject`() {
        Fixture().use { f ->
            f.service.prepare(f.request().copy(worldUuid=f.id())).rejection()
            val r=f.request();f.service.prepare(r)
            f.seasons.transition(f.season.id,SeasonStatus.PEACE).appliedValue()
            f.service.beginSpawn(r.operationId).rejection()
            f.service.prepare(f.request()).rejection()
            f.service.cancelPrepared(r.operationId).appliedValue()
            f.service.beginSpawn(r.operationId).rejection()
        }
    }

    @Test fun `death attempts and tombstones are durable idempotent and never grant reward replay`() {
        Fixture().use { f ->
            val mob=f.spawn();val op=f.id()
            f.service.prepareDeath(op,mob.id,"console","lethal damage").appliedValue()
            assertEquals(MobLife.DEATH_PENDING,f.mob(mob.id).life)
            assertEquals(op,f.db.repository.read { findMobDeathForMob(mob.id)!!.operationId })
            f.recovered().prepareDeath(op,mob.id,"console","lethal damage").unchangedValue()
            f.service.prepareDeath(op,mob.id,"console","changed").rejection()
            f.service.prepareDeath(f.id(),mob.id,"console","duplicate").rejection()
            f.service.acknowledgeDeath(op).rejection()
            f.service.beginDeath(op).appliedValue()
            f.recovered().beginDeath(op).unchangedValue()
            f.recovered().acknowledgeDeath(op).appliedValue()
            f.service.acknowledgeDeath(op).unchangedValue()
            assertEquals(MobObservation.SUPPRESS_TOMBSTONE,f.mob(mob.id).observe(mob.entityUuid!!,mob.creation.species))
            assertEquals(MobLife.DEAD,f.recovered().beginSpawn(mob.creation.operationId).unchangedValue().life)
        }
    }

    @Test fun `failed writes roll back reservations and lifecycle cannot resurrect or delete history`() {
        Fixture().use { f ->
            val a=f.spawn();val b=f.spawn();val r=f.request(a=a.id,b=b.id)
            assertFailsWith<IllegalStateException> {
                f.db.repository.transaction {
                    insertManagedMob(ManagedMob(r,f.now,f.now.plusMillis(12000),f.now.plusMillis(12000),MobLife.PREPARED))
                    error("injected before commit")
                }
            }
            assertNull(f.db.repository.read { findMobCreation(r.operationId) })
            assertFalse(f.db.repository.read { hasMobReservation(a.id) })
            f.service.prepare(r).appliedValue()
            f.db.connectionFactory.open().use { c ->
                for (sql in listOf("DELETE FROM managed_mobs WHERE id='${a.id}'", "UPDATE managed_mobs SET species='minecraft:pig' WHERE id='${a.id}'",
                    "UPDATE managed_mobs SET life='PREPARED',entity_uuid=NULL WHERE id='${a.id}'")) {
                    assertFailsWith<java.sql.SQLException> { c.createStatement().use { it.executeUpdate(sql) } }
                }
            }
            assertTrue(f.db.repository.read { hasMobReservation(a.id) })
        }
    }

    @Test fun `competing births cannot reserve the same parent twice`() {
        Fixture().use { f ->
            val a=f.spawn(); val b=f.spawn(); val c=f.spawn()
            val requests=listOf(f.request(a=a.id,b=b.id),f.request(a=c.id,b=b.id))
            val gate=java.util.concurrent.CountDownLatch(1)
            val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
            try {
                val futures=requests.map { request -> pool.submit<Any> {
                    gate.await()
                    try { f.recovered().prepare(request) } catch (busy: java.sql.SQLException) { busy }
                } }
                gate.countDown()
                val results=futures.map { it.get(10,java.util.concurrent.TimeUnit.SECONDS) }
                assertEquals(1,results.count { it is io.bennyc.civilizations.application.ApplicationResult.Applied<*> })
                assertEquals(1,requests.count { f.db.repository.read { findMobCreation(it.operationId) } != null })
                assertTrue(f.db.repository.read { hasMobReservation(b.id) })
            } finally { pool.shutdownNow() }
        }
    }

    @Test fun `schema fourteen upgrades without registering any mobs and restart is idempotent`() {
        SqliteTestDatabase().use { db ->
            SchemaMigrator(db.connectionFactory,CivilizationsSchema.migrations.filter { it.version<=14 }).migrate()
            assertEquals(listOf(15, 16, 17),db.migrator.migrate().appliedVersions)
            assertTrue(db.migrator.migrate().appliedVersions.isEmpty())
            db.connectionFactory.open().use { c ->
                c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM managed_mobs").use { r -> assertTrue(r.next());assertEquals(0,r.getInt(1)) } }
            }
        }
    }
}
