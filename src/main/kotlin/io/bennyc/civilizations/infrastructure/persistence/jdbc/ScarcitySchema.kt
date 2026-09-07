package io.bennyc.civilizations.infrastructure.persistence.jdbc

internal object ScarcitySchema {
    val migration = SchemaMigration(12, "immutable_season_world_resource_manifests", listOf(
        """CREATE TABLE season_world_manifests (
            id TEXT PRIMARY KEY CHECK(length(id)=36),
            season_id TEXT NOT NULL REFERENCES seasons(id) ON DELETE RESTRICT,
            world_key TEXT NOT NULL UNIQUE,
            world_uuid TEXT NOT NULL UNIQUE CHECK(length(world_uuid)=36),
            revision INTEGER NOT NULL CHECK(revision>0),
            source_sha256 TEXT NOT NULL CHECK(length(source_sha256)=64 AND source_sha256 NOT GLOB '*[^0-9a-f]*'),
            min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
            max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
            imported_at_ms INTEGER NOT NULL CHECK(imported_at_ms>=0),
            actor TEXT NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 128),
            CHECK(min_x<=max_x AND min_y<=max_y AND min_z<=max_z),
            CHECK(min_x>=-29999984 AND max_x<=29999984 AND min_z>=-29999984 AND max_z<=29999984),
            CHECK(min_y>=-2048 AND max_y<=2047),
            CHECK(max_x-min_x<8192 AND max_z-min_z<8192)
        )""".trimIndent(),
        """CREATE TABLE resource_zones (
            manifest_id TEXT NOT NULL REFERENCES season_world_manifests(id) ON DELETE RESTRICT,
            zone_id TEXT NOT NULL CHECK(length(zone_id) BETWEEN 1 AND 64),
            resource TEXT NOT NULL CHECK(resource IN ('DIAMOND','CATTLE','SUGAR_CANE')),
            min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
            max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
            PRIMARY KEY(manifest_id,zone_id),
            CHECK(min_x<=max_x AND min_y<=max_y AND min_z<=max_z)
        )""".trimIndent(),
        """CREATE TRIGGER resource_zone_bounds BEFORE INSERT ON resource_zones
        WHEN NOT EXISTS (SELECT 1 FROM season_world_manifests m WHERE m.id=NEW.manifest_id
            AND NEW.min_x>=m.min_x AND NEW.max_x<=m.max_x
            AND NEW.min_y>=m.min_y AND NEW.max_y<=m.max_y
            AND NEW.min_z>=m.min_z AND NEW.max_z<=m.max_z)
        OR EXISTS (SELECT 1 FROM resource_zones z WHERE z.manifest_id=NEW.manifest_id AND z.resource=NEW.resource
            AND z.min_x<=NEW.max_x AND z.max_x>=NEW.min_x AND z.min_y<=NEW.max_y AND z.max_y>=NEW.min_y
            AND z.min_z<=NEW.max_z AND z.max_z>=NEW.min_z)
        BEGIN SELECT RAISE(ABORT,'invalid resource zone bounds or overlap'); END""".trimIndent(),
    ) + listOf("season_world_manifests", "resource_zones").flatMap { table ->
        listOf("UPDATE", "DELETE").map { operation ->
            "CREATE TRIGGER ${table}_no_${operation.lowercase()} BEFORE $operation ON $table BEGIN SELECT RAISE(ABORT,'world manifest history is immutable'); END"
        }
    })
}
