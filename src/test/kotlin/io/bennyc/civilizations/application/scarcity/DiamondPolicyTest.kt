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

class DiamondPolicyTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-07T19:00:00Z"), ZoneOffset.UTC)
    private val world = WorldId("minecraft:diamond")
    private val uuid = UUID(0, 20)
    private val loaded = listOf(LoadedResourceWorld(world, uuid, -64, 320))
    private fun manifest(season: SeasonId) = WorldManifest(UUID(0, 10), season, world, uuid, 1, "a".repeat(64),
        ResourceBounds(-16, -64, -16, 16, 319, 16), listOf(
            ResourceZone("diamond", ResourceKind.DIAMOND, ResourceBounds(-16, -48, -16, 0, -32, 0))))

    @Test fun `raw and equipment policies distinguish supply from ordinary items`() {
        listOf("diamond", "diamond_ore", "deepslate_diamond_ore", "diamond_block").forEach {
            assertTrue(DiamondSupplyPolicy.excludes("minecraft:$it", false))
        }
        assertTrue(DiamondSupplyPolicy.excludes("minecraft:diamond_chestplate", true))
        assertFalse(DiamondSupplyPolicy.excludes("minecraft:diamond_chestplate", false))
        assertFalse(DiamondSupplyPolicy.excludes("minecraft:emerald", true))
        assertFalse(DiamondSupplyPolicy.excludes("example:diamond", true))
    }

    @Test fun `activation requires audited setup and survives migration and restart without a policy switch`() {
        SqliteTestDatabase().use { db ->
            SchemaMigrator(db.connectionFactory, CivilizationsSchema.migrations.take(16)).migrate()
            assertEquals(listOf(17), db.migrator.migrate().appliedVersions)
            assertNull(db.repository.read { findDiamondActivation() })
            val seasons = SeasonService(db.repository, SequentialIdGenerator(), clock)
            val season = seasons.create("Diamond").appliedValue()
            val service = DiamondActivationService(db.repository, clock)
            val hash = "b".repeat(64)
            service.enable(season.id, true, hash, "console", "test", loaded).rejection()
            val m = manifest(season.id)
            WorldManifestService(db.repository, clock).register(m, loaded, "console").appliedValue()
            service.enable(season.id, true, hash, "console", "test", emptyList()).rejection()
            service.enable(season.id, true, "bad hash", "console", "test", loaded).rejection()
            service.enable(season.id, true, hash, "console", " ", loaded).rejection()
            val activation = service.enable(season.id, true, hash, "console", "audit", loaded).appliedValue()
            assertEquals(activation, service.enable(season.id, true, hash, "console", "retry", loaded).unchangedValue())
            service.enable(season.id, false, hash, "console", "change", loaded).rejection()
            service.enable(season.id, true, "c".repeat(64), "console", "change", loaded).rejection()
            assertEquals(activation, JdbcCivilizationsRepository(db.connectionFactory).read { findDiamondActivation() })
            assertFailsWith<java.sql.SQLException> {
                db.connectionFactory.open().use { it.createStatement().executeUpdate("UPDATE diamond_activation SET include_equipment=0") }
            }
            seasons.transition(season.id, SeasonStatus.PEACE).appliedValue()
            assertEquals(activation, db.repository.read { findDiamondActivation() })
        }
    }
}
