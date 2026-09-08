package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class WorldManifestTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC)
    private val bounds = ResourceBounds(-32, -64, -32, 32, 319, 32)
    private val zone = ResourceZone("diamond", ResourceKind.DIAMOND, ResourceBounds(-17, -60, -17, 0, 0, 0))
    private fun manifest(season: SeasonId = SeasonId(UUID(0, 1)), id: UUID = UUID(0, 20),
                         world: WorldId = WorldId("minecraft:scarcity"), uuid: UUID = UUID(0, 30),
                         zones: List<ResourceZone> = listOf(zone), hash: String = "a".repeat(64)) =
        WorldManifest(id, season, world, uuid, 1, hash, bounds, zones)
    private fun loaded(m: WorldManifest) = listOf(LoadedResourceWorld(m.worldId, m.worldUuid, -64, 320))

    @Test fun `resource-free transit world persists without inventing a zone or permitting cane`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val season = SeasonService(db.repository, SequentialIdGenerator(), clock).create("Transit").appliedValue()
            val m = manifest(season = season.id, zones = emptyList())
            WorldManifestService(db.repository, clock).register(m, loaded(m), "console").appliedValue()
            val records = JdbcCivilizationsRepository(db.connectionFactory).read { listWorldManifests() }
            assertTrue(records.single().manifest.zones.isEmpty())
            val policy = CaneGrowthPolicy(CaneActivation(season.id, 3, "console", "test", clock.instant()), ResourceZoneIndex(records))
            assertFalse(policy.permits(m.worldId, m.worldUuid, 0, 0, 0, 1))
            CaneActivationService(db.repository, clock).enable(season.id, 3, "console", "test", loaded(m)).rejection()
            DiamondActivationService(db.repository, clock).enable(season.id, true, "b".repeat(64), "console", "test", loaded(m)).rejection()
        }
    }

    @Test fun `index handles negative chunks exact inclusive edges and identity isolation`() {
        val m = manifest()
        val index = ResourceZoneIndex(listOf(RegisteredWorldManifest(m, clock.instant(), "console")))
        fun at(x: Int, y: Int, z: Int) = index.at(m.seasonId, m.worldId, m.worldUuid, x, y, z)
        assertEquals(listOf(zone), at(-17, -60, -17))
        assertEquals(listOf(zone), at(0, 0, 0))
        assertTrue(at(-18, -60, -17).isEmpty())
        assertTrue(at(0, 1, 0).isEmpty())
        assertTrue(index.at(m.seasonId, m.worldId, UUID(0, 99), 0, 0, 0).isEmpty())
        assertTrue(index.at(SeasonId(UUID(0, 99)), m.worldId, m.worldUuid, 0, 0, 0).isEmpty())
        assertTrue(index.at(m.seasonId, WorldId("minecraft:other"), m.worldUuid, 0, 0, 0).isEmpty())
    }

    @Test fun `randomized index agrees with exhaustive bounds scan`() {
        val random = kotlin.random.Random(20260907)
        val zones = (0 until 40).map { i ->
            val x = random.nextInt(-30, 30)
            val z = random.nextInt(-30, 30)
            ResourceZone("zone-$i", ResourceKind.entries[i % 3], ResourceBounds(x, i * 3, z, x + 2, i * 3 + 1, z + 2))
        }
        val m = manifest(zones = zones)
        val index = ResourceZoneIndex(listOf(RegisteredWorldManifest(m, clock.instant(), "console")))
        repeat(10000) {
            val x = random.nextInt(-35, 36)
            val y = random.nextInt(-1, 122)
            val z = random.nextInt(-35, 36)
            assertEquals(m.zones.filter { it.bounds.contains(x, y, z) }, index.at(m.seasonId, m.worldId, m.worldUuid, x, y, z))
        }
    }

    @Test fun `geometry rejects ambiguous and unbounded definitions but allows different resources and heights`() {
        assertFailsWith<IllegalArgumentException> { manifest(zones = listOf(zone, zone)) }
        assertFailsWith<IllegalArgumentException> { manifest(zones = listOf(zone, zone.copy(id = "other"))) }
        assertFailsWith<IllegalArgumentException> { manifest(zones = listOf(zone.copy(bounds = ResourceBounds(0, 0, 0, 33, 1, 1)))) }
        assertFailsWith<IllegalArgumentException> { ResourceBounds(Int.MIN_VALUE, 0, 0, Int.MAX_VALUE, 1, 1) }
        assertFailsWith<IllegalArgumentException> { ResourceBounds(0, 0, 0, 8192, 1, 1) }
        assertFailsWith<IllegalArgumentException> { ResourceBounds(2, 0, 0, 1, 1, 1) }
        assertEquals(0L, manifest(zones = emptyList()).chunkEntries)
        assertFailsWith<IllegalArgumentException> { manifest(hash = "invalid") }
        val mutable = mutableListOf(zone, zone.copy(id = "cattle", resource = ResourceKind.CATTLE),
            zone.copy(id = "upper", bounds = ResourceBounds(-17, 1, -17, 0, 2, 0)))
        val m = manifest(zones = mutable)
        mutable.clear()
        assertEquals(3, m.zones.size)
        assertFailsWith<UnsupportedOperationException> { (m.zones as MutableList).clear() }
        val huge = ResourceBounds(0, -64, 0, 8191, 319, 8191)
        assertFailsWith<IllegalArgumentException> {
            WorldManifest(UUID(0, 1), m.seasonId, m.worldId, m.worldUuid, 1, "a".repeat(64), huge,
                listOf(ResourceZone("a", ResourceKind.DIAMOND, huge), ResourceZone("b", ResourceKind.CATTLE, huge)))
        }
    }

    @Test fun `validation writes nothing registration is durable immutable and idempotent after setup`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val seasons = SeasonService(db.repository, SequentialIdGenerator(), clock)
            val season = seasons.create("Scarcity").appliedValue()
            val service = WorldManifestService(db.repository, clock)
            val m = manifest(season.id)
            service.validate(m, loaded(m), "console").appliedValue()
            assertTrue(db.repository.read { listWorldManifests() }.isEmpty())
            val accepted = service.register(m, loaded(m), "console").appliedValue()
            assertNull(db.repository.read { findCaneActivation() })
            val restored = JdbcCivilizationsRepository(db.connectionFactory).read { listWorldManifests() }.single()
            assertTrue(restored.manifest.sameDefinition(m))
            assertEquals(accepted.importedAt, restored.importedAt)
            assertEquals("console", restored.actor)
            seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
            service.register(m, loaded(m), "other-actor").unchangedValue()
            assertTrue(service.register(manifest(season.id, hash = "b".repeat(64)), loaded(m), "console").rejection().description.contains("different"))
            val other = manifest(season.id, UUID(0, 21), WorldId("minecraft:other"), UUID(0, 31))
            assertTrue(service.register(other, loaded(other), "console").rejection().description.contains("SETUP"))
            db.connectionFactory.open().use { c ->
                listOf("UPDATE season_world_manifests SET revision=2", "DELETE FROM season_world_manifests",
                    "UPDATE resource_zones SET zone_id='changed'", "DELETE FROM resource_zones").forEach { sql ->
                    assertFailsWith<java.sql.SQLException> { c.createStatement().use { it.executeUpdate(sql) } }
                }
            }
            assertEquals(1, db.repository.read { listWorldManifests() }.size)
        }
    }

    @Test fun `rejects missing identity height season and world reassignment without partial writes`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val seasons = SeasonService(db.repository, SequentialIdGenerator(), clock)
            val first = seasons.create("First").appliedValue()
            val second = seasons.create("Second").appliedValue()
            val service = WorldManifestService(db.repository, clock)
            val m = manifest(first.id)
            service.register(m, emptyList(), "console").rejection()
            service.register(m, listOf(loaded(m).single().copy(uuid = UUID(0, 99))), "console").rejection()
            service.register(m, listOf(loaded(m).single().copy(minHeight = 0)), "console").rejection()
            service.register(manifest(SeasonId(UUID(0, 999))), loaded(m), "console").rejection()
            assertTrue(db.repository.read { listWorldManifests() }.isEmpty())
            assertFailsWith<IllegalStateException> { db.repository.transaction {
                insertWorldManifest(RegisteredWorldManifest(m, clock.instant(), "console"))
                error("rollback")
            } }
            assertTrue(db.repository.read { listWorldManifests() }.isEmpty())
            service.register(m, loaded(m), "console").appliedValue()
            listOf(manifest(second.id, UUID(0, 21)), manifest(second.id, UUID(0, 22), uuid = UUID(0, 99)),
                manifest(second.id, UUID(0, 23), world = WorldId("minecraft:other"))).forEach {
                assertTrue(service.register(it, loaded(it), "console").rejection().description.contains("already bound"))
            }
            assertEquals(1, db.repository.read { listWorldManifests() }.size)
        }
    }

    @Test fun `migration from eleven preserves existing season`() {
        SqliteTestDatabase().use { db ->
            SchemaMigrator(db.connectionFactory, CivilizationsSchema.migrations.take(11)).migrate()
            val season = SeasonService(db.repository, SequentialIdGenerator(), clock).create("Existing").appliedValue()
            val result = db.migrator.migrate()
            assertEquals(listOf(12, 13, 14, 15, 16, 17), result.appliedVersions)
            assertEquals(season, db.repository.read { findSeason(season.id) })
            assertTrue(db.repository.read { listWorldManifests() }.isEmpty())
        }
    }
}
