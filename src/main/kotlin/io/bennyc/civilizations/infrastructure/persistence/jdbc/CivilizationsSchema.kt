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
        SchemaMigration(
            version = 3,
            name = "wars_and_timed_battles",
            statements = listOf(
                """
                CREATE TABLE wars (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    declaring_civilization_id TEXT NOT NULL,
                    target_civilization_id TEXT NOT NULL,
                    declared_by_player_id TEXT NOT NULL,
                    status TEXT NOT NULL CHECK (status IN ('DECLARED', 'ACTIVE', 'CLOSED', 'CANCELLED')),
                    battle_trigger TEXT NOT NULL CHECK (battle_trigger = 'HOSTILE_CLAIM_ENTRY'),
                    destruction_scope TEXT NOT NULL CHECK (
                        destruction_scope = 'OPPOSING_CIVILIZATION_CLAIMS'
                    ),
                    battle_duration_seconds INTEGER NOT NULL CHECK (battle_duration_seconds > 0),
                    declared_at_ms INTEGER NOT NULL CHECK (declared_at_ms >= 0),
                    activated_at_ms INTEGER,
                    ended_at_ms INTEGER,
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= declared_at_ms),
                    pair_low_id TEXT NOT NULL,
                    pair_high_id TEXT NOT NULL,
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(declaring_civilization_id) = 36),
                    CHECK (length(target_civilization_id) = 36),
                    CHECK (length(declared_by_player_id) = 36),
                    CHECK (declaring_civilization_id <> target_civilization_id),
                    CHECK (pair_low_id < pair_high_id),
                    CHECK (
                        (pair_low_id = declaring_civilization_id AND
                            pair_high_id = target_civilization_id) OR
                        (pair_low_id = target_civilization_id AND
                            pair_high_id = declaring_civilization_id)
                    ),
                    CHECK (activated_at_ms IS NULL OR activated_at_ms >= declared_at_ms),
                    CHECK (ended_at_ms IS NULL OR ended_at_ms >= declared_at_ms),
                    CHECK (
                        (status = 'DECLARED' AND activated_at_ms IS NULL AND ended_at_ms IS NULL) OR
                        (status = 'ACTIVE' AND activated_at_ms IS NOT NULL AND ended_at_ms IS NULL) OR
                        (status = 'CLOSED' AND activated_at_ms IS NOT NULL AND ended_at_ms IS NOT NULL) OR
                        (status = 'CANCELLED' AND ended_at_ms IS NOT NULL)
                    ),
                    UNIQUE (season_id, id),
                    FOREIGN KEY (season_id, declaring_civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, target_civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE UNIQUE INDEX wars_one_open_pair
                ON wars(season_id, pair_low_id, pair_high_id)
                WHERE status IN ('DECLARED', 'ACTIVE')
                """.trimIndent(),
                """
                CREATE INDEX wars_by_season_and_status
                ON wars(season_id, status, declared_at_ms, id)
                """.trimIndent(),
                """
                CREATE TRIGGER wars_validate_declarer_insert
                BEFORE INSERT ON wars
                WHEN NOT EXISTS (
                    SELECT 1 FROM memberships membership
                    WHERE membership.season_id = NEW.season_id
                      AND membership.civilization_id = NEW.declaring_civilization_id
                      AND membership.player_id = NEW.declared_by_player_id
                      AND membership.role = 'LEADER'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'war declarer must lead the declaring civilization');
                END
                """.trimIndent(),
                """
                CREATE TABLE battles (
                    id TEXT PRIMARY KEY,
                    war_id TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    attacking_civilization_id TEXT NOT NULL,
                    defending_civilization_id TEXT NOT NULL,
                    triggered_by_player_id TEXT NOT NULL,
                    trigger_claim_id TEXT NOT NULL,
                    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'RESOLVING', 'CLOSED', 'CANCELLED')),
                    started_at_ms INTEGER NOT NULL CHECK (started_at_ms >= 0),
                    ends_at_ms INTEGER NOT NULL CHECK (ends_at_ms > started_at_ms),
                    resolving_at_ms INTEGER,
                    ended_at_ms INTEGER,
                    outcome TEXT CHECK (outcome IN ('ATTACKER_VICTORY', 'DEFENDER_VICTORY', 'DRAW')),
                    winner_civilization_id TEXT,
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= started_at_ms),
                    CHECK (length(id) = 36),
                    CHECK (length(war_id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(attacking_civilization_id) = 36),
                    CHECK (length(defending_civilization_id) = 36),
                    CHECK (length(triggered_by_player_id) = 36),
                    CHECK (length(trigger_claim_id) = 36),
                    CHECK (attacking_civilization_id <> defending_civilization_id),
                    CHECK (resolving_at_ms IS NULL OR resolving_at_ms >= started_at_ms),
                    CHECK (ended_at_ms IS NULL OR ended_at_ms >= started_at_ms),
                    CHECK (
                        (status = 'ACTIVE' AND resolving_at_ms IS NULL AND ended_at_ms IS NULL AND
                            outcome IS NULL AND winner_civilization_id IS NULL) OR
                        (status = 'RESOLVING' AND resolving_at_ms IS NOT NULL AND ended_at_ms IS NULL AND
                            outcome IS NULL AND winner_civilization_id IS NULL) OR
                        (status = 'CLOSED' AND resolving_at_ms IS NOT NULL AND ended_at_ms IS NOT NULL AND
                            outcome IS NOT NULL) OR
                        (status = 'CANCELLED' AND ended_at_ms IS NOT NULL AND outcome IS NULL AND
                            winner_civilization_id IS NULL)
                    ),
                    CHECK (
                        outcome IS NULL OR
                        (outcome = 'ATTACKER_VICTORY' AND winner_civilization_id IS NOT NULL AND
                            winner_civilization_id = attacking_civilization_id) OR
                        (outcome = 'DEFENDER_VICTORY' AND winner_civilization_id IS NOT NULL AND
                            winner_civilization_id = defending_civilization_id) OR
                        (outcome = 'DRAW' AND winner_civilization_id IS NULL)
                    ),
                    UNIQUE (season_id, id),
                    FOREIGN KEY (season_id, war_id)
                        REFERENCES wars(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, attacking_civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, defending_civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE UNIQUE INDEX battles_one_open_per_war
                ON battles(war_id)
                WHERE status IN ('ACTIVE', 'RESOLVING')
                """.trimIndent(),
                """
                CREATE INDEX battles_by_season_and_status
                ON battles(season_id, status, started_at_ms, id)
                """.trimIndent(),
                """
                CREATE TRIGGER battles_validate_entry_insert
                BEFORE INSERT ON battles
                WHEN NEW.status <> 'ACTIVE' OR NOT EXISTS (
                    SELECT 1
                    FROM wars war
                    JOIN claims claim ON claim.id = NEW.trigger_claim_id
                    JOIN memberships membership
                      ON membership.season_id = NEW.season_id
                     AND membership.player_id = NEW.triggered_by_player_id
                    WHERE war.id = NEW.war_id
                      AND war.season_id = NEW.season_id
                      AND war.status = 'ACTIVE'
                      AND (
                          (war.declaring_civilization_id = NEW.attacking_civilization_id AND
                           war.target_civilization_id = NEW.defending_civilization_id) OR
                          (war.target_civilization_id = NEW.attacking_civilization_id AND
                           war.declaring_civilization_id = NEW.defending_civilization_id)
                      )
                      AND claim.season_id = NEW.season_id
                      AND claim.civilization_id = NEW.defending_civilization_id
                      AND membership.civilization_id = NEW.attacking_civilization_id
                )
                BEGIN
                    SELECT RAISE(ABORT, 'battle does not represent a valid hostile claim entry');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER wars_prevent_end_with_open_battle
                BEFORE UPDATE OF status ON wars
                WHEN NEW.status IN ('CLOSED', 'CANCELLED') AND EXISTS (
                    SELECT 1 FROM battles battle
                    WHERE battle.war_id = NEW.id
                      AND battle.status IN ('ACTIVE', 'RESOLVING')
                )
                BEGIN
                    SELECT RAISE(ABORT, 'war cannot end with an open battle');
                END
                """.trimIndent(),
                """
                CREATE TABLE battle_participants (
                    season_id TEXT NOT NULL,
                    battle_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    civilization_id TEXT NOT NULL,
                    side TEXT NOT NULL CHECK (side IN ('ATTACKER', 'DEFENDER')),
                    joined_at_ms INTEGER NOT NULL CHECK (joined_at_ms >= 0),
                    CHECK (length(season_id) = 36),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(player_id) = 36),
                    CHECK (length(civilization_id) = 36),
                    PRIMARY KEY (battle_id, player_id),
                    FOREIGN KEY (season_id, battle_id)
                        REFERENCES battles(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX battle_participants_by_civilization
                ON battle_participants(battle_id, civilization_id, side, player_id)
                """.trimIndent(),
                """
                CREATE TRIGGER battle_participants_validate_insert
                BEFORE INSERT ON battle_participants
                WHEN NOT EXISTS (
                    SELECT 1
                    FROM battles battle
                    JOIN memberships membership
                      ON membership.season_id = NEW.season_id
                     AND membership.player_id = NEW.player_id
                    WHERE battle.id = NEW.battle_id
                      AND battle.season_id = NEW.season_id
                      AND membership.civilization_id = NEW.civilization_id
                      AND (
                          (NEW.side = 'ATTACKER' AND
                           NEW.civilization_id = battle.attacking_civilization_id) OR
                          (NEW.side = 'DEFENDER' AND
                           NEW.civilization_id = battle.defending_civilization_id)
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'battle participant does not match battle roster');
                END
                """.trimIndent(),
            ),
        ),
        SchemaMigration(
            version = 4,
            name = "first_write_wins_battle_damage_journal",
            statements = listOf(
                """
                CREATE TABLE battle_block_changes (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    battle_id TEXT NOT NULL,
                    claim_id TEXT NOT NULL,
                    world_id TEXT NOT NULL,
                    block_x INTEGER NOT NULL,
                    block_y INTEGER NOT NULL,
                    block_z INTEGER NOT NULL,
                    original_block_data TEXT NOT NULL,
                    first_mutation_cause TEXT NOT NULL CHECK (
                        first_mutation_cause IN (
                            'PLAYER_BREAK', 'PLAYER_PLACE', 'EXPLOSION', 'FIRE',
                            'FLUID', 'PISTON', 'ENTITY_CHANGE'
                        )
                    ),
                    first_actor_id TEXT NOT NULL,
                    recorded_at_ms INTEGER NOT NULL CHECK (recorded_at_ms >= 0),
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(claim_id) = 36),
                    CHECK (length(trim(world_id)) > 0),
                    CHECK (length(original_block_data) BETWEEN 1 AND 32768),
                    CHECK (length(first_actor_id) = 36),
                    UNIQUE (battle_id, world_id, block_x, block_y, block_z),
                    FOREIGN KEY (season_id, battle_id)
                        REFERENCES battles(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX battle_block_changes_by_battle
                ON battle_block_changes(battle_id, recorded_at_ms, id)
                """.trimIndent(),
                """
                CREATE TRIGGER battle_block_changes_validate_insert
                BEFORE INSERT ON battle_block_changes
                WHEN NOT EXISTS (
                    SELECT 1
                    FROM battles battle
                    JOIN seasons season ON season.id = NEW.season_id
                    JOIN claims claim ON claim.id = NEW.claim_id
                    JOIN battle_participants participant
                      ON participant.battle_id = NEW.battle_id
                     AND participant.player_id = NEW.first_actor_id
                    WHERE battle.id = NEW.battle_id
                      AND battle.season_id = NEW.season_id
                      AND battle.status = 'ACTIVE'
                      AND NEW.recorded_at_ms >= battle.started_at_ms
                      AND NEW.recorded_at_ms < battle.ends_at_ms
                      AND season.status = 'WAR'
                      AND claim.season_id = NEW.season_id
                      AND claim.civilization_id IN (
                          battle.attacking_civilization_id,
                          battle.defending_civilization_id
                      )
                      AND claim.world_id = NEW.world_id
                      AND NEW.block_x BETWEEN claim.min_x AND claim.max_x
                      AND NEW.block_z BETWEEN claim.min_z AND claim.max_z
                      AND participant.civilization_id IN (
                          battle.attacking_civilization_id,
                          battle.defending_civilization_id
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'block change is not valid for the active battle');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_block_changes_are_immutable
                BEFORE UPDATE ON battle_block_changes
                BEGIN
                    SELECT RAISE(ABORT, 'battle block changes are immutable');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_block_changes_cannot_be_deleted
                BEFORE DELETE ON battle_block_changes
                BEGIN
                    SELECT RAISE(ABORT, 'battle block changes cannot be deleted');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battles_one_open_per_civilization_insert
                BEFORE INSERT ON battles
                WHEN NEW.status IN ('ACTIVE', 'RESOLVING') AND EXISTS (
                    SELECT 1 FROM battles existing
                    WHERE existing.season_id = NEW.season_id
                      AND existing.status IN ('ACTIVE', 'RESOLVING')
                      AND (
                          existing.attacking_civilization_id IN (
                              NEW.attacking_civilization_id, NEW.defending_civilization_id
                          ) OR
                          existing.defending_civilization_id IN (
                              NEW.attacking_civilization_id, NEW.defending_civilization_id
                          )
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'civilization already participates in an open battle');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battles_one_open_per_civilization_update
                BEFORE UPDATE OF status, attacking_civilization_id, defending_civilization_id
                ON battles
                WHEN NEW.status IN ('ACTIVE', 'RESOLVING') AND EXISTS (
                    SELECT 1 FROM battles existing
                    WHERE existing.season_id = NEW.season_id
                      AND existing.id <> NEW.id
                      AND existing.status IN ('ACTIVE', 'RESOLVING')
                      AND (
                          existing.attacking_civilization_id IN (
                              NEW.attacking_civilization_id, NEW.defending_civilization_id
                          ) OR
                          existing.defending_civilization_id IN (
                              NEW.attacking_civilization_id, NEW.defending_civilization_id
                          )
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'civilization already participates in an open battle');
                END
                """.trimIndent(),
            ),
        ),
        SchemaMigration(
            version = 5,
            name = "immutable_battle_damage_reports",
            statements = listOf(
                """
                CREATE TABLE battle_damage_reports (
                    battle_id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    journaled_change_count INTEGER NOT NULL CHECK (journaled_change_count >= 0),
                    eligible_change_count INTEGER NOT NULL CHECK (eligible_change_count >= 0),
                    restored_during_battle_count INTEGER NOT NULL CHECK (
                        restored_during_battle_count >= 0
                    ),
                    restore_original_block_count INTEGER NOT NULL CHECK (
                        restore_original_block_count >= 0
                    ),
                    remove_placed_block_count INTEGER NOT NULL CHECK (
                        remove_placed_block_count >= 0
                    ),
                    generated_at_ms INTEGER NOT NULL CHECK (generated_at_ms >= 0),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (
                        journaled_change_count =
                            eligible_change_count + restored_during_battle_count
                    ),
                    CHECK (
                        eligible_change_count =
                            restore_original_block_count + remove_placed_block_count
                    ),
                    UNIQUE (season_id, battle_id),
                    FOREIGN KEY (season_id, battle_id)
                        REFERENCES battles(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE TABLE battle_damage_report_entries (
                    season_id TEXT NOT NULL,
                    battle_id TEXT NOT NULL,
                    block_change_id TEXT NOT NULL,
                    final_block_data TEXT NOT NULL,
                    eligibility TEXT NOT NULL CHECK (
                        eligibility IN ('ELIGIBLE', 'RESTORED_DURING_BATTLE')
                    ),
                    cost_category TEXT CHECK (
                        cost_category IN ('RESTORE_ORIGINAL_BLOCK', 'REMOVE_PLACED_BLOCK')
                    ),
                    CHECK (length(season_id) = 36),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(block_change_id) = 36),
                    CHECK (length(final_block_data) BETWEEN 1 AND 32768),
                    CHECK (
                        (eligibility = 'ELIGIBLE' AND cost_category IS NOT NULL) OR
                        (eligibility = 'RESTORED_DURING_BATTLE' AND cost_category IS NULL)
                    ),
                    PRIMARY KEY (battle_id, block_change_id),
                    FOREIGN KEY (block_change_id)
                        REFERENCES battle_block_changes(id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, battle_id)
                        REFERENCES battle_damage_reports(season_id, battle_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                        DEFERRABLE INITIALLY DEFERRED
                )
                """.trimIndent(),
                """
                CREATE INDEX battle_damage_report_entries_by_battle
                ON battle_damage_report_entries(battle_id, block_change_id)
                """.trimIndent(),
                """
                CREATE TRIGGER battle_damage_report_entries_validate_insert
                BEFORE INSERT ON battle_damage_report_entries
                WHEN EXISTS (
                    SELECT 1 FROM battle_damage_reports report
                    WHERE report.battle_id = NEW.battle_id
                ) OR NOT EXISTS (
                    SELECT 1 FROM battle_block_changes change
                    WHERE change.id = NEW.block_change_id
                      AND change.season_id = NEW.season_id
                      AND change.battle_id = NEW.battle_id
                      AND (
                          (NEW.eligibility = 'RESTORED_DURING_BATTLE' AND
                           NEW.cost_category IS NULL AND
                           NEW.final_block_data = change.original_block_data) OR
                          (NEW.eligibility = 'ELIGIBLE' AND
                           NEW.final_block_data <> change.original_block_data AND
                           (
                               (change.original_block_data IN (
                                   'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'
                                ) AND NEW.cost_category = 'REMOVE_PLACED_BLOCK') OR
                               (change.original_block_data NOT IN (
                                   'minecraft:air', 'minecraft:cave_air', 'minecraft:void_air'
                                ) AND NEW.cost_category = 'RESTORE_ORIGINAL_BLOCK')
                           ))
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'damage report entry is invalid or report is sealed');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_damage_reports_validate_insert
                BEFORE INSERT ON battle_damage_reports
                WHEN NOT EXISTS (
                    SELECT 1 FROM battles battle
                    WHERE battle.id = NEW.battle_id
                      AND battle.season_id = NEW.season_id
                      AND battle.status = 'RESOLVING'
                      AND NEW.generated_at_ms >= battle.resolving_at_ms
                ) OR NEW.journaled_change_count <> (
                    SELECT COUNT(*) FROM battle_block_changes change
                    WHERE change.battle_id = NEW.battle_id
                ) OR NEW.journaled_change_count <> (
                    SELECT COUNT(*) FROM battle_damage_report_entries entry
                    WHERE entry.battle_id = NEW.battle_id
                ) OR NEW.eligible_change_count <> (
                    SELECT COUNT(*) FROM battle_damage_report_entries entry
                    WHERE entry.battle_id = NEW.battle_id
                      AND entry.eligibility = 'ELIGIBLE'
                ) OR NEW.restored_during_battle_count <> (
                    SELECT COUNT(*) FROM battle_damage_report_entries entry
                    WHERE entry.battle_id = NEW.battle_id
                      AND entry.eligibility = 'RESTORED_DURING_BATTLE'
                ) OR NEW.restore_original_block_count <> (
                    SELECT COUNT(*) FROM battle_damage_report_entries entry
                    WHERE entry.battle_id = NEW.battle_id
                      AND entry.cost_category = 'RESTORE_ORIGINAL_BLOCK'
                ) OR NEW.remove_placed_block_count <> (
                    SELECT COUNT(*) FROM battle_damage_report_entries entry
                    WHERE entry.battle_id = NEW.battle_id
                      AND entry.cost_category = 'REMOVE_PLACED_BLOCK'
                )
                BEGIN
                    SELECT RAISE(ABORT, 'damage report is incomplete or battle is not resolving');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_damage_reports_are_immutable
                BEFORE UPDATE ON battle_damage_reports
                BEGIN
                    SELECT RAISE(ABORT, 'battle damage reports are immutable');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_damage_reports_cannot_be_deleted
                BEFORE DELETE ON battle_damage_reports
                BEGIN
                    SELECT RAISE(ABORT, 'battle damage reports cannot be deleted');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_damage_report_entries_are_immutable
                BEFORE UPDATE ON battle_damage_report_entries
                BEGIN
                    SELECT RAISE(ABORT, 'battle damage report entries are immutable');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_damage_report_entries_cannot_be_deleted
                BEFORE DELETE ON battle_damage_report_entries
                BEGIN
                    SELECT RAISE(ABORT, 'battle damage report entries cannot be deleted');
                END
                """.trimIndent(),
            ),
        ),
    )
}
