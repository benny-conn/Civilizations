package io.bennyc.civilizations.infrastructure.persistence.jdbc

internal object CaneSchema {
    val migration = SchemaMigration(13, "immutable_cane_activation", listOf(
        """CREATE TABLE cane_activation (
            singleton INTEGER PRIMARY KEY CHECK(singleton=1),
            season_id TEXT NOT NULL REFERENCES seasons(id) ON DELETE RESTRICT,
            max_height INTEGER NOT NULL CHECK(max_height BETWEEN 1 AND 3),
            actor TEXT NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 128),
            reason TEXT NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 256),
            activated_at_ms INTEGER NOT NULL CHECK(activated_at_ms>=0)
        )""".trimIndent(),
        """CREATE TRIGGER cane_activation_setup BEFORE INSERT ON cane_activation
        WHEN NOT EXISTS (SELECT 1 FROM seasons s JOIN runtime_state r ON r.active_season_id=s.id WHERE s.id=NEW.season_id AND s.status='SETUP')
        BEGIN SELECT RAISE(ABORT,'cane activation requires active SETUP'); END""".trimIndent(),
        """CREATE TRIGGER cane_manifest_frozen BEFORE INSERT ON season_world_manifests
        WHEN EXISTS (SELECT 1 FROM cane_activation WHERE season_id=NEW.season_id)
        BEGIN SELECT RAISE(ABORT,'cane season manifests frozen'); END""".trimIndent(),
        """CREATE TRIGGER cane_zones_frozen BEFORE INSERT ON resource_zones
        WHEN EXISTS (SELECT 1 FROM cane_activation c JOIN season_world_manifests m ON m.season_id=c.season_id WHERE m.id=NEW.manifest_id)
        BEGIN SELECT RAISE(ABORT,'cane season zones frozen'); END""".trimIndent(),
    ) + listOf("UPDATE", "DELETE").map {
        "CREATE TRIGGER cane_activation_no_${it.lowercase()} BEFORE $it ON cane_activation BEGIN SELECT RAISE(ABORT,'cane activation immutable'); END"
    })
}
