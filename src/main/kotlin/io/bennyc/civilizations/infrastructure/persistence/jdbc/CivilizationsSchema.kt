package io.bennyc.civilizations.infrastructure.persistence.jdbc

object CivilizationsSchema {
    val migrations: List<SchemaMigration> = listOf(
        SchemaMigration(
            version = 1,
            name = "initial_seasons_civilizations_memberships_claims",
            statements = listOf(
                """
                CREATE TABLE seasons (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    status TEXT NOT NULL CHECK (status IN ('SETUP', 'PEACE', 'WAR', 'FINALE', 'ARCHIVED')),
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= created_at_ms),
                    CHECK (length(id) = 36),
                    CHECK (length(trim(name)) BETWEEN 1 AND 64)
                )
                """.trimIndent(),
                """
                CREATE TABLE civilizations (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    name TEXT NOT NULL,
                    normalized_name TEXT NOT NULL,
                    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'DISSOLVED')),
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= created_at_ms),
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(trim(name)) BETWEEN 1 AND 64),
                    CHECK (length(normalized_name) BETWEEN 1 AND 64),
                    UNIQUE (season_id, normalized_name),
                    UNIQUE (season_id, id),
                    FOREIGN KEY (season_id) REFERENCES seasons(id) ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE TABLE memberships (
                    season_id TEXT NOT NULL,
                    civilization_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    role TEXT NOT NULL CHECK (role IN ('LEADER', 'MEMBER')),
                    joined_at_ms INTEGER NOT NULL CHECK (joined_at_ms >= 0),
                    CHECK (length(season_id) = 36),
                    CHECK (length(civilization_id) = 36),
                    CHECK (length(player_id) = 36),
                    PRIMARY KEY (season_id, player_id),
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE UNIQUE INDEX memberships_one_leader_per_civilization
                ON memberships(civilization_id)
                WHERE role = 'LEADER'
                """.trimIndent(),
                """
                CREATE INDEX memberships_by_civilization
                ON memberships(civilization_id, role, joined_at_ms)
                """.trimIndent(),
                """
                CREATE TABLE claims (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    civilization_id TEXT NOT NULL,
                    world_id TEXT NOT NULL,
                    min_x INTEGER NOT NULL,
                    max_x INTEGER NOT NULL,
                    min_z INTEGER NOT NULL,
                    max_z INTEGER NOT NULL,
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(civilization_id) = 36),
                    CHECK (length(trim(world_id)) > 0),
                    CHECK (min_x <= max_x),
                    CHECK (min_z <= max_z),
                    UNIQUE (season_id, world_id, min_x, max_x, min_z, max_z),
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX claims_by_civilization
                ON claims(civilization_id)
                """.trimIndent(),
                """
                CREATE INDEX claims_by_season_and_world
                ON claims(season_id, world_id, min_x, max_x, min_z, max_z)
                """.trimIndent(),
            ),
        ),
        SchemaMigration(
            version = 2,
            name = "active_season_runtime_state",
            statements = listOf(
                """
                CREATE TABLE runtime_state (
                    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
                    active_season_id TEXT,
                    FOREIGN KEY (active_season_id)
                        REFERENCES seasons(id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                "INSERT INTO runtime_state(singleton_id, active_season_id) VALUES (1, NULL)",
            ),
        ),
    )
}
