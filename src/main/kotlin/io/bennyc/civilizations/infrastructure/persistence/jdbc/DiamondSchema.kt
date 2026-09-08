package io.bennyc.civilizations.infrastructure.persistence.jdbc

internal object DiamondSchema {
    val migration = SchemaMigration(17, "immutable_diamond_activation", listOf(
        """CREATE TABLE diamond_activation (
            singleton INTEGER PRIMARY KEY CHECK(singleton=1),
            season_id TEXT NOT NULL REFERENCES seasons(id) ON DELETE RESTRICT,
            include_equipment INTEGER NOT NULL CHECK(include_equipment IN (0,1)),
            audit_sha256 TEXT NOT NULL CHECK(length(audit_sha256)=64 AND audit_sha256 NOT GLOB '*[^0-9a-f]*'),
            actor TEXT NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 128),
            reason TEXT NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 256),
            activated_at_ms INTEGER NOT NULL CHECK(activated_at_ms>=0)
        )""".trimIndent(),
        """CREATE TRIGGER diamond_activation_setup BEFORE INSERT ON diamond_activation
        WHEN NOT EXISTS (SELECT 1 FROM seasons s JOIN runtime_state r ON r.active_season_id=s.id WHERE s.id=NEW.season_id AND s.status='SETUP')
        BEGIN SELECT RAISE(ABORT,'diamond activation requires active SETUP'); END""".trimIndent(),
        """CREATE TRIGGER diamond_manifest_frozen BEFORE INSERT ON season_world_manifests
        WHEN EXISTS (SELECT 1 FROM diamond_activation WHERE season_id=NEW.season_id)
        BEGIN SELECT RAISE(ABORT,'diamond season manifests frozen'); END""".trimIndent(),
        """CREATE TRIGGER diamond_zones_frozen BEFORE INSERT ON resource_zones
        WHEN EXISTS (SELECT 1 FROM diamond_activation c JOIN season_world_manifests m ON m.season_id=c.season_id WHERE m.id=NEW.manifest_id)
        BEGIN SELECT RAISE(ABORT,'diamond season zones frozen'); END""".trimIndent(),
    ) + listOf("UPDATE", "DELETE").map {
        "CREATE TRIGGER diamond_activation_no_${it.lowercase()} BEFORE $it ON diamond_activation BEGIN SELECT RAISE(ABORT,'diamond activation immutable'); END"
    })
}
