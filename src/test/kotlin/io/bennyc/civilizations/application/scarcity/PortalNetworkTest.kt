package io.bennyc.civilizations.application.scarcity

import io.bennyc.civilizations.application.season.SeasonService
import io.bennyc.civilizations.application.support.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.season.SeasonStatus
import io.bennyc.civilizations.infrastructure.persistence.jdbc.*
import java.time.Clock
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class PortalNetworkTest {
    private val a=PortalSite(WorldId("minecraft:overworld"),UUID(0,1),ResourceBounds(-3,64,0,-1,66,0))
    private val b=PortalSite(WorldId("minecraft:the_nether"),UUID(0,2),ResourceBounds(0,64,0,0,66,1))
    private val pair=PortalPair("crossing",a,b)
    @Test fun `geometry uses fixed opposite endpoints and rejects ambiguous or invalid sites`() {
        val n=PortalNetwork(SeasonId(UUID(0,10)),listOf(pair),"a".repeat(64),"console",Instant.EPOCH)
        assertEquals(-2,a.exitX)
        assertEquals(a to b,n.route(a.world,a.uuid,-2,64,0))
        assertEquals(b to a,n.route(b.world,b.uuid,0,66,1))
        assertNull(n.route(a.world,UUID(0,3),-2,64,0))
        assertNull(n.route(a.world,a.uuid,0,64,0))
        assertFailsWith<IllegalArgumentException> { PortalSite(a.world,a.uuid,ResourceBounds(0,0,0,1,2,1)) }
        assertFailsWith<IllegalArgumentException> { PortalSite(a.world,a.uuid,ResourceBounds(0,0,0,22,2,0)) }
        assertFailsWith<IllegalArgumentException> { PortalPair("same",a,a) }
        assertFailsWith<IllegalArgumentException> { PortalNetwork(n.seasonId,listOf(pair,pair.copy(id="near")),n.sourceSha256,"console",Instant.EPOCH) }
        val list=mutableListOf(pair)
        val copy=PortalNetwork(n.seasonId,list,n.sourceSha256,"console",Instant.EPOCH)
        list.clear();assertEquals(1,copy.pairs.size)
    }
    @Test fun `setup is atomic idempotent and survives season selection and schema upgrade`() {
        SqliteTestDatabase().use { db ->
            SchemaMigrator(db.connectionFactory,CivilizationsSchema.migrations.take(13)).migrate()
            val seasons=SeasonService(db.repository,SequentialIdGenerator(),Clock.systemUTC())
            val season=seasons.create("Portals").appliedValue()
            assertEquals(listOf(14, 15),db.migrator.migrate().appliedVersions)
            assertNull(db.repository.read { findPortalNetwork() })
            val service=PortalNetworkService(db.repository,Clock.systemUTC())
            val accepted=service.install(season.id,listOf(pair),"a".repeat(64),"console").appliedValue()
            val restored=db.repository.read { findPortalNetwork() }!!
            assertEquals(accepted.pairs,restored.pairs);assertEquals(accepted.importedAt,restored.importedAt)
            seasons.transition(season.id,SeasonStatus.PEACE).appliedValue()
            service.install(season.id,listOf(pair),"a".repeat(64),"console").unchangedValue()
            service.install(season.id,listOf(pair),"b".repeat(64),"console").rejection()
            val other=seasons.create("Other").appliedValue()
            seasons.selectActive(other.id).appliedValue()
            assertEquals(season.id,db.repository.read { findPortalNetwork() }?.seasonId)
            db.connectionFactory.open().use { c ->
                listOf("DELETE FROM portal_network","DELETE FROM portal_sites","UPDATE portal_sites SET min_y=1").forEach { sql ->
                    assertFailsWith<java.sql.SQLException> { c.createStatement().use { it.executeUpdate(sql) } }
                }
            }
        }
    }
    @Test fun `cannot bind a world already registered to another season`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val seasons=SeasonService(db.repository,SequentialIdGenerator(),Clock.systemUTC())
            val old=seasons.create("Old").appliedValue()
            val manifest=WorldManifest(UUID(0,20),old.id,a.world,a.uuid,1,"a".repeat(64),a.bounds,
                listOf(ResourceZone("zone",ResourceKind.DIAMOND,a.bounds)))
            WorldManifestService(db.repository,Clock.systemUTC()).register(manifest,
                listOf(LoadedResourceWorld(a.world,a.uuid,-64,320)),"console").appliedValue()
            val next=seasons.create("Next").appliedValue();seasons.selectActive(next.id).appliedValue()
            PortalNetworkService(db.repository,Clock.systemUTC()).install(next.id,listOf(pair),"a".repeat(64),"console").rejection()
            assertNull(db.repository.read { findPortalNetwork() })
        }
    }
    @Test fun `new setup outside setup phase rejects without writes`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val seasons=SeasonService(db.repository,SequentialIdGenerator(),Clock.systemUTC())
            val season=seasons.create("Portals").appliedValue()
            seasons.transition(season.id,SeasonStatus.PEACE).appliedValue()
            PortalNetworkService(db.repository,Clock.systemUTC()).install(season.id,listOf(pair),"a".repeat(64),"console").rejection()
            assertNull(db.repository.read { findPortalNetwork() })
        }
    }
}
