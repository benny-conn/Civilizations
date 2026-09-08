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

class CattleActivationTest {
    @Test fun `activation requires setup habitat and freezes rules slots and manifest registration`() {
        SqliteTestDatabase().use { db ->
            db.migrator.migrate()
            val clock=Clock.fixed(Instant.parse("2026-09-07T20:00:00Z"),ZoneOffset.UTC)
            val seasons=SeasonService(db.repository,SequentialIdGenerator(),clock)
            val season=seasons.create("Cattle").appliedValue()
            val world=UUID(0,3);val key=WorldId("minecraft:cattle")
            val slots=listOf(CattleSeed("a",world,0,64,0),CattleSeed("b",world,2,64,0))
            val rules=MobRules(43200000,21600000)
            val service=CattleActivationService(db.repository,clock)
            service.install(season.id,rules,slots,"console").rejection()
            val m=WorldManifest(UUID(0,4),season.id,key,world,1,"a".repeat(64),ResourceBounds(-16,0,-16,16,255,16),listOf(ResourceZone("herd",ResourceKind.CATTLE,ResourceBounds(-8,64,-8,8,70,8))))
            val manifests=WorldManifestService(db.repository,clock)
            manifests.register(m,listOf(LoadedResourceWorld(key,world,0,256)),"console").appliedValue()
            service.install(season.id,rules,listOf(slots[0],slots[1].copy(x=12)),"console").rejection()
            service.install(season.id,rules,listOf(slots[0],slots[0].copy(id="duplicate-position")),"console").rejection()
            val installed=service.install(season.id,rules,slots,"console").appliedValue()
            assertEquals(installed.seeds,db.repository.read { findCattleActivation()!!.seeds })
            assertEquals(installed.request(slots[0]),db.repository.read { findCattleActivation()!!.request(slots[0]) })
            service.install(season.id,rules.copy(maturityMillis=123),slots,"console").rejection()
            seasons.transition(season.id,SeasonStatus.PEACE).appliedValue()
            service.install(season.id,rules,slots,"other").unchangedValue()
            assertEquals(0,db.repository.read { listManagedMobs(season.id,null,10).size })
            db.connectionFactory.open().use { c ->
                for(sql in listOf("DELETE FROM cattle_seeds","UPDATE cattle_activation SET maturity_ms=1")) assertFailsWith<java.sql.SQLException> { c.createStatement().use { it.executeUpdate(sql) } }
            }
        }
    }
}
