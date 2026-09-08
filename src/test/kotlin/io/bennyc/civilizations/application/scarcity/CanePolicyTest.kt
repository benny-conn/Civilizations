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

class CanePolicyTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-07T19:00:00Z"), ZoneOffset.UTC)
    private val world = WorldId("minecraft:cane")
    private val uuid = UUID(0, 20)
    private val loaded = listOf(LoadedResourceWorld(world, uuid, -64, 320))
    private fun manifest(season: SeasonId) = WorldManifest(UUID(0, 10), season, world, uuid, 1, "a".repeat(64),
        ResourceBounds(-16, -64, -16, 16, 319, 16), listOf(
            ResourceZone("cane", ResourceKind.SUGAR_CANE, ResourceBounds(-16, 60, -16, 0, 62, 0)),
            ResourceZone("upper", ResourceKind.SUGAR_CANE, ResourceBounds(-16, 63, -16, 0, 65, 0))))

    @Test fun `column requires one zone identity and height while disabled leaves behavior alone`() {
        val m = manifest(SeasonId(UUID(0, 1)))
        val index = ResourceZoneIndex(listOf(RegisteredWorldManifest(m, clock.instant(), "console")))
        val policy = CaneGrowthPolicy(CaneActivation(m.seasonId, 3, "console", "test", clock.instant()), index)
        assertTrue(policy.permits(world, uuid, -16, 60, -16, 62))
        assertFalse(policy.permits(world, uuid, 1, 60, 0, 62))
        assertFalse(policy.permits(world, UUID(0, 99), 0, 60, 0, 62))
        assertFalse(policy.permits(WorldId("minecraft:the_nether"), uuid, 0, 60, 0, 62))
        assertFalse(policy.permits(world, uuid, 0, 61, 0, 63)) // Cannot bridge two zones.
        assertFalse(policy.permits(world, uuid, 0, 60, 0, 63))
        assertFalse(policy.permits(world, uuid, 0, 62, 0, 60))
        assertTrue(CaneGrowthPolicy(null, index).permits(world, uuid, 2000, 0, 2000, 99))
        for (height in listOf(0, 4, Int.MAX_VALUE)) assertFailsWith<IllegalArgumentException> {
            CaneActivation(m.seasonId, height, "console", "test", clock.instant())
        }
        assertFailsWith<IllegalArgumentException> { CaneActivation(m.seasonId, 3, "console", " ", clock.instant()) }
    }

    @Test fun `activation requires setup registered zones and loaded identity then freezes history`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val seasons = SeasonService(db.repository, SequentialIdGenerator(), clock)
            val season = seasons.create("Cane").appliedValue()
            val service = CaneActivationService(db.repository, clock)
            service.enable(season.id, 3, "console", "test", loaded).rejection()
            val m = manifest(season.id)
            WorldManifestService(db.repository, clock).register(m, loaded, "console").appliedValue()
            service.enable(season.id, 3, "console", "test", emptyList()).rejection()
            service.enable(season.id, 0, "console", "test", loaded).rejection()
            service.enable(season.id, 3, "console", " ", loaded).rejection()
            val activation = service.enable(season.id, 2, "console", "crop test", loaded).appliedValue()
            assertEquals(activation, db.repository.read { findCaneActivation() })
            val extra = WorldManifest(UUID(0, 11), season.id, WorldId("minecraft:extra"), UUID(0, 21), 1,
                m.sourceSha256, m.bounds, m.zones)
            assertTrue(WorldManifestService(db.repository, clock).register(extra,
                listOf(LoadedResourceWorld(extra.worldId, extra.worldUuid, -64, 320)), "console").rejection().description.contains("frozen"))
            seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
            assertEquals(activation, service.enable(season.id, 2, "other", "retry", loaded).unchangedValue())
            service.enable(season.id, 3, "console", "changed", loaded).rejection()
            val other = seasons.create("Other").appliedValue()
            seasons.selectActive(other.id).appliedValue()
            assertEquals(activation, db.repository.read { findCaneActivation() })
            service.enable(other.id, 2, "console", "replace", loaded).rejection()
            db.connectionFactory.open().use { c ->
                for (sql in listOf("DELETE FROM cane_activation", "UPDATE cane_activation SET max_height=3",
                    "INSERT INTO resource_zones SELECT manifest_id,'extra',resource,min_x,70,min_z,max_x,72,max_z FROM resource_zones LIMIT 1")) {
                    assertFailsWith<java.sql.SQLException> { c.createStatement().use { it.executeUpdate(sql) } }
                }
            }
        }
    }

    @Test fun `initial activation outside active setup rejects`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val seasons = SeasonService(db.repository, SequentialIdGenerator(), clock)
            val season = seasons.create("Cane").appliedValue()
            WorldManifestService(db.repository, clock).register(manifest(season.id), loaded, "console").appliedValue()
            seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
            CaneActivationService(db.repository, clock).enable(season.id, 3, "console", "test", loaded).rejection()
            assertNull(db.repository.read { findCaneActivation() })
        }
    }

    @Test fun `schema twelve upgrade preserves manifests and keeps enforcement off`() {
        SqliteTestDatabase().use { db ->
            SchemaMigrator(db.connectionFactory, CivilizationsSchema.migrations.take(12)).migrate()
            val season = SeasonService(db.repository, SequentialIdGenerator(), clock).create("Old").appliedValue()
            val m = manifest(season.id)
            db.repository.transaction { insertWorldManifest(RegisteredWorldManifest(m, clock.instant(), "console")) }
            assertEquals(listOf(13, 14, 15), db.migrator.migrate().appliedVersions)
            assertTrue(db.repository.read { listWorldManifests() }.single().manifest.sameDefinition(m))
            assertNull(db.repository.read { findCaneActivation() })
        }
    }
}
