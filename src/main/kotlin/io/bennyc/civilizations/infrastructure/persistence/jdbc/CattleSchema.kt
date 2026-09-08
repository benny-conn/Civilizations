package io.bennyc.civilizations.infrastructure.persistence.jdbc

internal object CattleSchema {
    val migration=SchemaMigration(16,"cattle_activation",listOf(
        """CREATE TABLE cattle_activation (
            singleton INTEGER PRIMARY KEY CHECK(singleton=1), season_id TEXT NOT NULL REFERENCES seasons(id),
            maturity_ms INTEGER NOT NULL CHECK(maturity_ms BETWEEN 1 AND 31536000000),
            cooldown_ms INTEGER NOT NULL CHECK(cooldown_ms BETWEEN 1 AND 31536000000),
            actor TEXT NOT NULL, activated_at INTEGER NOT NULL
        )""",
        """CREATE TABLE cattle_seeds (id TEXT PRIMARY KEY, activation_id INTEGER NOT NULL REFERENCES cattle_activation(singleton) CHECK(activation_id=1),
            ordinal INTEGER NOT NULL UNIQUE, world_uuid TEXT NOT NULL, x INTEGER NOT NULL,y INTEGER NOT NULL,z INTEGER NOT NULL,
            UNIQUE(world_uuid,x,y,z))""",
        """CREATE TRIGGER cattle_freeze_manifests BEFORE INSERT ON season_world_manifests
            WHEN EXISTS(SELECT 1 FROM cattle_activation WHERE season_id=NEW.season_id)
            BEGIN SELECT RAISE(ABORT,'cattle manifests frozen'); END""",
    )+listOf("cattle_activation","cattle_seeds").flatMap { table -> listOf("UPDATE","DELETE").map {
        "CREATE TRIGGER ${table}_no_${it.lowercase()} BEFORE $it ON $table BEGIN SELECT RAISE(ABORT,'cattle activation immutable'); END"
    } })
}
