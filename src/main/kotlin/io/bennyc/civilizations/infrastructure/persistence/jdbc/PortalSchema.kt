package io.bennyc.civilizations.infrastructure.persistence.jdbc

internal object PortalSchema {
    val migration = SchemaMigration(14, "fixed_portal_network", listOf(
        """CREATE TABLE portal_network (
            singleton INTEGER PRIMARY KEY CHECK(singleton=1),
            season_id TEXT NOT NULL REFERENCES seasons(id), source_sha256 TEXT NOT NULL CHECK(length(source_sha256)=64),
            actor TEXT NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 128), imported_at_ms INTEGER NOT NULL CHECK(imported_at_ms>=0)
        )""".trimIndent(),
        """CREATE TABLE portal_sites (
            network_id INTEGER NOT NULL REFERENCES portal_network(singleton) CHECK(network_id=1),
            pair_id TEXT NOT NULL, side INTEGER NOT NULL CHECK(side IN (0,1)),
            world_key TEXT NOT NULL, world_uuid TEXT NOT NULL,
            min_x INTEGER NOT NULL, min_y INTEGER NOT NULL, min_z INTEGER NOT NULL,
            max_x INTEGER NOT NULL, max_y INTEGER NOT NULL, max_z INTEGER NOT NULL,
            PRIMARY KEY(pair_id,side), CHECK(min_x<=max_x AND min_y<max_y AND min_z<=max_z)
        )""".trimIndent(),
    ) + listOf("portal_network", "portal_sites").flatMap { table -> listOf("UPDATE", "DELETE").map {
        "CREATE TRIGGER ${table}_no_${it.lowercase()} BEFORE $it ON $table BEGIN SELECT RAISE(ABORT,'portal network immutable'); END"
    } })
}
