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
        SchemaMigration(
            version = 6,
            name = "member_war_declarations_and_battle_surrenders",
            statements = listOf(
                "DROP TRIGGER wars_validate_declarer_insert",
                """
                CREATE TRIGGER wars_validate_declarer_insert
                BEFORE INSERT ON wars
                WHEN NOT EXISTS (
                    SELECT 1 FROM memberships membership
                    WHERE membership.season_id = NEW.season_id
                      AND membership.civilization_id = NEW.declaring_civilization_id
                      AND membership.player_id = NEW.declared_by_player_id
                )
                BEGIN
                    SELECT RAISE(ABORT, 'war declarer must belong to the declaring civilization');
                END
                """.trimIndent(),
                """
                CREATE TABLE battle_surrenders (
                    season_id TEXT NOT NULL,
                    battle_id TEXT PRIMARY KEY,
                    surrendered_civilization_id TEXT NOT NULL,
                    surrendered_by_player_id TEXT NOT NULL,
                    requested_outcome TEXT NOT NULL CHECK (
                        requested_outcome IN ('ATTACKER_VICTORY', 'DEFENDER_VICTORY')
                    ),
                    surrendered_at_ms INTEGER NOT NULL CHECK (surrendered_at_ms >= 0),
                    CHECK (length(season_id) = 36),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(surrendered_civilization_id) = 36),
                    CHECK (length(surrendered_by_player_id) = 36),
                    FOREIGN KEY (season_id, battle_id)
                        REFERENCES battles(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, surrendered_civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE TRIGGER battle_surrenders_validate_insert
                BEFORE INSERT ON battle_surrenders
                WHEN NOT EXISTS (
                    SELECT 1
                    FROM battles battle
                    JOIN memberships membership
                      ON membership.season_id = NEW.season_id
                     AND membership.civilization_id = NEW.surrendered_civilization_id
                     AND membership.player_id = NEW.surrendered_by_player_id
                     AND membership.role = 'LEADER'
                    WHERE battle.id = NEW.battle_id
                      AND battle.season_id = NEW.season_id
                      AND battle.status = 'RESOLVING'
                      AND NEW.surrendered_at_ms >= battle.started_at_ms
                      AND (
                          (NEW.surrendered_civilization_id = battle.attacking_civilization_id AND
                           NEW.requested_outcome = 'DEFENDER_VICTORY') OR
                          (NEW.surrendered_civilization_id = battle.defending_civilization_id AND
                           NEW.requested_outcome = 'ATTACKER_VICTORY')
                      )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'invalid battle surrender');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_surrenders_are_immutable
                BEFORE UPDATE ON battle_surrenders
                BEGIN
                    SELECT RAISE(ABORT, 'battle surrenders are immutable');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER battle_surrenders_cannot_be_deleted
                BEFORE DELETE ON battle_surrenders
                BEGIN
                    SELECT RAISE(ABORT, 'battle surrenders cannot be deleted');
                END
                """.trimIndent(),
            ),
        ),
        SchemaMigration(
            version = 7,
            name = "civilization_economy_ledger_and_player_bridge",
            statements = listOf(
                """
                CREATE TABLE season_economy_settings (
                    season_id TEXT PRIMARY KEY,
                    currency_scale INTEGER NOT NULL CHECK (currency_scale BETWEEN 0 AND 6),
                    opening_balance_minor INTEGER NOT NULL CHECK (
                        opening_balance_minor BETWEEN 0 AND 9000000000000000
                    ),
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    CHECK (length(season_id) = 36),
                    FOREIGN KEY (season_id) REFERENCES seasons(id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE TABLE civilization_accounts (
                    civilization_id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    balance_minor INTEGER NOT NULL CHECK (
                        typeof(balance_minor) = 'integer' AND
                        balance_minor BETWEEN -9000000000000000 AND 9000000000000000
                    ),
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= created_at_ms),
                    CHECK (length(civilization_id) = 36),
                    CHECK (length(season_id) = 36),
                    UNIQUE (season_id, civilization_id),
                    FOREIGN KEY (season_id) REFERENCES season_economy_settings(season_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilizations(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX civilization_accounts_by_season
                ON civilization_accounts(season_id, civilization_id)
                """.trimIndent(),
                """
                CREATE TABLE economy_ledger_transactions (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL UNIQUE,
                    kind TEXT NOT NULL CHECK (kind IN (
                        'OPENING_BALANCE', 'PLAYER_DEPOSIT', 'PLAYER_WITHDRAWAL',
                        'PLAYER_WITHDRAWAL_REVERSAL', 'CIVILIZATION_TRANSFER',
                        'REPAIR_PAYMENT', 'VICTOR_SHARE', 'ADMIN_ADJUSTMENT'
                    )),
                    reference_type TEXT,
                    reference_id TEXT,
                    actor_player_id TEXT,
                    description TEXT NOT NULL,
                    currency_scale INTEGER NOT NULL CHECK (currency_scale BETWEEN 0 AND 6),
                    posting_count INTEGER NOT NULL CHECK (posting_count >= 1),
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(idempotency_key) BETWEEN 1 AND 160),
                    CHECK (length(description) BETWEEN 1 AND 512),
                    CHECK (actor_player_id IS NULL OR length(actor_player_id) = 36),
                    CHECK (
                        (reference_type IS NULL AND reference_id IS NULL) OR
                        (length(reference_type) BETWEEN 1 AND 64 AND
                         length(reference_id) BETWEEN 1 AND 160)
                    ),
                    UNIQUE (season_id, id),
                    FOREIGN KEY (season_id) REFERENCES season_economy_settings(season_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX economy_ledger_transactions_by_season
                ON economy_ledger_transactions(season_id, created_at_ms, id)
                """.trimIndent(),
                """
                CREATE TABLE economy_ledger_postings (
                    transaction_id TEXT NOT NULL,
                    season_id TEXT NOT NULL,
                    civilization_id TEXT NOT NULL,
                    amount_minor INTEGER NOT NULL CHECK (
                        typeof(amount_minor) = 'integer' AND
                        amount_minor BETWEEN -9000000000000000 AND 9000000000000000
                    ),
                    PRIMARY KEY (transaction_id, civilization_id),
                    FOREIGN KEY (season_id, transaction_id)
                        REFERENCES economy_ledger_transactions(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilization_accounts(season_id, civilization_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX economy_ledger_postings_by_civilization
                ON economy_ledger_postings(civilization_id, transaction_id)
                """.trimIndent(),
                """
                CREATE TRIGGER economy_ledger_postings_respect_declared_count
                BEFORE INSERT ON economy_ledger_postings
                WHEN (
                    SELECT COUNT(*) FROM economy_ledger_postings
                    WHERE transaction_id = NEW.transaction_id
                ) >= (
                    SELECT posting_count FROM economy_ledger_transactions
                    WHERE id = NEW.transaction_id
                )
                BEGIN
                    SELECT RAISE(ABORT, 'economy ledger posting count exceeded');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER economy_ledger_postings_apply_balance
                AFTER INSERT ON economy_ledger_postings
                BEGIN
                    UPDATE civilization_accounts
                    SET balance_minor = balance_minor + NEW.amount_minor,
                        updated_at_ms = (
                            SELECT created_at_ms FROM economy_ledger_transactions
                            WHERE id = NEW.transaction_id
                        )
                    WHERE season_id = NEW.season_id
                      AND civilization_id = NEW.civilization_id;
                END
                """.trimIndent(),
                """
                CREATE TRIGGER economy_ledger_transactions_are_immutable
                BEFORE UPDATE ON economy_ledger_transactions
                BEGIN
                    SELECT RAISE(ABORT, 'economy ledger transactions are immutable');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER economy_ledger_transactions_cannot_be_deleted
                BEFORE DELETE ON economy_ledger_transactions
                BEGIN
                    SELECT RAISE(ABORT, 'economy ledger transactions cannot be deleted');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER economy_ledger_postings_are_immutable
                BEFORE UPDATE ON economy_ledger_postings
                BEGIN
                    SELECT RAISE(ABORT, 'economy ledger postings are immutable');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER economy_ledger_postings_cannot_be_deleted
                BEFORE DELETE ON economy_ledger_postings
                BEGIN
                    SELECT RAISE(ABORT, 'economy ledger postings cannot be deleted');
                END
                """.trimIndent(),
                """
                CREATE TABLE economy_bridge_transfers (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    civilization_id TEXT NOT NULL,
                    player_id TEXT NOT NULL,
                    direction TEXT NOT NULL CHECK (
                        direction IN ('DEPOSIT_TO_CIVILIZATION', 'WITHDRAW_TO_PLAYER')
                    ),
                    amount_minor INTEGER NOT NULL CHECK (
                        amount_minor BETWEEN 1 AND 9000000000000000
                    ),
                    currency_scale INTEGER NOT NULL CHECK (currency_scale BETWEEN 0 AND 6),
                    provider_name TEXT NOT NULL,
                    idempotency_key TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL CHECK (status IN (
                        'PREPARED', 'COMPLETED', 'EXTERNAL_FAILED',
                        'RECONCILIATION_REQUIRED', 'RECONCILED_CANCELLED'
                    )),
                    ledger_transaction_id TEXT,
                    reversal_transaction_id TEXT,
                    failure_message TEXT,
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= created_at_ms),
                    completed_at_ms INTEGER,
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(civilization_id) = 36),
                    CHECK (length(player_id) = 36),
                    CHECK (length(provider_name) BETWEEN 1 AND 128),
                    CHECK (length(idempotency_key) BETWEEN 1 AND 160),
                    CHECK (failure_message IS NULL OR length(failure_message) <= 512),
                    CHECK (completed_at_ms IS NULL OR completed_at_ms >= created_at_ms),
                    CHECK (
                        (status IN ('PREPARED', 'RECONCILIATION_REQUIRED') AND
                         completed_at_ms IS NULL) OR
                        (status IN ('COMPLETED', 'EXTERNAL_FAILED', 'RECONCILED_CANCELLED') AND
                         completed_at_ms IS NOT NULL)
                    ),
                    CHECK (
                        (direction = 'DEPOSIT_TO_CIVILIZATION' AND (
                            (status = 'COMPLETED' AND ledger_transaction_id IS NOT NULL AND
                             reversal_transaction_id IS NULL) OR
                            (status <> 'COMPLETED' AND ledger_transaction_id IS NULL AND
                             reversal_transaction_id IS NULL)
                        )) OR
                        (direction = 'WITHDRAW_TO_PLAYER' AND
                         ledger_transaction_id IS NOT NULL AND (
                            (status IN ('EXTERNAL_FAILED', 'RECONCILED_CANCELLED') AND
                             reversal_transaction_id IS NOT NULL) OR
                            (status NOT IN ('EXTERNAL_FAILED', 'RECONCILED_CANCELLED') AND
                             reversal_transaction_id IS NULL)
                         ))
                    ),
                    UNIQUE (season_id, id),
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilization_accounts(season_id, civilization_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, ledger_transaction_id)
                        REFERENCES economy_ledger_transactions(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, reversal_transaction_id)
                        REFERENCES economy_ledger_transactions(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE UNIQUE INDEX economy_bridge_one_open_per_player
                ON economy_bridge_transfers(player_id)
                WHERE status IN ('PREPARED', 'RECONCILIATION_REQUIRED')
                """.trimIndent(),
                """
                CREATE INDEX economy_bridge_by_status
                ON economy_bridge_transfers(status, created_at_ms, id)
                """.trimIndent(),
                """
                CREATE TRIGGER economy_bridge_validate_update
                BEFORE UPDATE ON economy_bridge_transfers
                WHEN NEW.id <> OLD.id OR
                     NEW.season_id <> OLD.season_id OR
                     NEW.civilization_id <> OLD.civilization_id OR
                     NEW.player_id <> OLD.player_id OR
                     NEW.direction <> OLD.direction OR
                     NEW.amount_minor <> OLD.amount_minor OR
                     NEW.currency_scale <> OLD.currency_scale OR
                     NEW.provider_name <> OLD.provider_name OR
                     NEW.idempotency_key <> OLD.idempotency_key OR
                     NEW.created_at_ms <> OLD.created_at_ms OR
                     NOT (
                        (OLD.status = 'PREPARED' AND NEW.status IN (
                            'COMPLETED', 'EXTERNAL_FAILED', 'RECONCILIATION_REQUIRED'
                        )) OR
                        (OLD.status = 'RECONCILIATION_REQUIRED' AND NEW.status IN (
                            'COMPLETED', 'RECONCILED_CANCELLED'
                        ))
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'invalid economy bridge transition');
                END
                """.trimIndent(),
            ),
        ),
        SchemaMigration(
            version = 8,
            name = "persisted_resumable_repair_jobs",
            statements = listOf(
                """
                CREATE TRIGGER civilization_accounts_reject_negative_insert
                BEFORE INSERT ON civilization_accounts
                WHEN NEW.balance_minor < 0
                BEGIN
                    SELECT RAISE(ABORT, 'civilization account balance cannot be negative');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER civilization_accounts_reject_negative_update
                BEFORE UPDATE OF balance_minor ON civilization_accounts
                WHEN NEW.balance_minor < 0
                BEGIN
                    SELECT RAISE(ABORT, 'civilization account balance cannot be negative');
                END
                """.trimIndent(),
                """
                CREATE TABLE repair_jobs (
                    id TEXT PRIMARY KEY,
                    season_id TEXT NOT NULL,
                    battle_id TEXT NOT NULL,
                    civilization_id TEXT NOT NULL,
                    initiated_by_player_id TEXT,
                    funding_mode TEXT NOT NULL CHECK (
                        funding_mode IN ('ORDINARY', 'ADMIN_SPONSORED')
                    ),
                    idempotency_key TEXT NOT NULL UNIQUE,
                    target_completion_basis_points INTEGER NOT NULL CHECK (
                        target_completion_basis_points BETWEEN 1 AND 10000
                    ),
                    total_eligible_count INTEGER NOT NULL CHECK (total_eligible_count > 0),
                    observed_restored_count INTEGER NOT NULL CHECK (
                        observed_restored_count >= 0
                    ),
                    observed_repairable_count INTEGER NOT NULL CHECK (
                        observed_repairable_count >= 0
                    ),
                    observed_conflict_count INTEGER NOT NULL CHECK (
                        observed_conflict_count >= 0
                    ),
                    selected_restore_original_count INTEGER NOT NULL CHECK (
                        selected_restore_original_count >= 0
                    ),
                    selected_remove_placement_count INTEGER NOT NULL CHECK (
                        selected_remove_placement_count >= 0
                    ),
                    restore_original_unit_price_minor INTEGER NOT NULL CHECK (
                        restore_original_unit_price_minor BETWEEN 0 AND 9000000000000000
                    ),
                    remove_placement_unit_price_minor INTEGER NOT NULL CHECK (
                        remove_placement_unit_price_minor BETWEEN 0 AND 9000000000000000
                    ),
                    gross_cost_minor INTEGER NOT NULL CHECK (
                        gross_cost_minor BETWEEN 0 AND 9000000000000000
                    ),
                    victor_share_basis_points INTEGER NOT NULL CHECK (
                        victor_share_basis_points BETWEEN 0 AND 10000
                    ),
                    victor_civilization_id TEXT,
                    victor_proceeds_minor INTEGER NOT NULL CHECK (
                        victor_proceeds_minor BETWEEN 0 AND gross_cost_minor
                    ),
                    payment_ledger_transaction_id TEXT,
                    status TEXT NOT NULL CHECK (status IN (
                        'QUEUED', 'RUNNING', 'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'
                    )),
                    next_item_ordinal INTEGER NOT NULL CHECK (next_item_ordinal >= 0),
                    restored_count INTEGER NOT NULL CHECK (restored_count >= 0),
                    skipped_conflict_count INTEGER NOT NULL CHECK (skipped_conflict_count >= 0),
                    failed_count INTEGER NOT NULL CHECK (failed_count >= 0),
                    created_at_ms INTEGER NOT NULL CHECK (created_at_ms >= 0),
                    updated_at_ms INTEGER NOT NULL CHECK (updated_at_ms >= created_at_ms),
                    completed_at_ms INTEGER,
                    failure_message TEXT,
                    CHECK (length(id) = 36),
                    CHECK (length(season_id) = 36),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(civilization_id) = 36),
                    CHECK (
                        initiated_by_player_id IS NULL OR length(initiated_by_player_id) = 36
                    ),
                    CHECK (length(idempotency_key) BETWEEN 1 AND 160),
                    CHECK (
                        victor_civilization_id IS NULL OR length(victor_civilization_id) = 36
                    ),
                    CHECK (
                        failure_message IS NULL OR
                        (status = 'FAILED' AND length(failure_message) <= 512)
                    ),
                    CHECK (
                        total_eligible_count = observed_restored_count +
                            observed_repairable_count + observed_conflict_count
                    ),
                    CHECK (
                        selected_restore_original_count + selected_remove_placement_count
                            BETWEEN 1 AND observed_repairable_count
                    ),
                    CHECK (
                        next_item_ordinal <=
                            selected_restore_original_count + selected_remove_placement_count
                    ),
                    CHECK (
                        next_item_ordinal = restored_count + skipped_conflict_count + failed_count
                    ),
                    CHECK (
                        (victor_civilization_id IS NULL AND victor_proceeds_minor = 0) OR
                        (victor_civilization_id IS NOT NULL AND
                         victor_civilization_id <> civilization_id)
                    ),
                    CHECK (
                        (funding_mode = 'ORDINARY' AND
                         payment_ledger_transaction_id IS NOT NULL) OR
                        (funding_mode = 'ADMIN_SPONSORED' AND
                         payment_ledger_transaction_id IS NULL AND gross_cost_minor = 0 AND
                         victor_proceeds_minor = 0)
                    ),
                    CHECK (
                        (status = 'COMPLETED' AND completed_at_ms IS NOT NULL AND
                         next_item_ordinal =
                            selected_restore_original_count + selected_remove_placement_count) OR
                        (status IN ('CANCELLED', 'FAILED') AND completed_at_ms IS NOT NULL) OR
                        (status IN ('QUEUED', 'RUNNING', 'PAUSED') AND completed_at_ms IS NULL)
                    ),
                    UNIQUE (season_id, id),
                    FOREIGN KEY (season_id, battle_id)
                        REFERENCES battle_damage_reports(season_id, battle_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, civilization_id)
                        REFERENCES civilization_accounts(season_id, civilization_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, victor_civilization_id)
                        REFERENCES civilization_accounts(season_id, civilization_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (season_id, payment_ledger_transaction_id)
                        REFERENCES economy_ledger_transactions(season_id, id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE UNIQUE INDEX repair_jobs_one_open_per_battle_civilization
                ON repair_jobs(battle_id, civilization_id)
                WHERE status IN ('QUEUED', 'RUNNING', 'PAUSED')
                """.trimIndent(),
                """
                CREATE INDEX repair_jobs_by_battle
                ON repair_jobs(battle_id, created_at_ms, id)
                """.trimIndent(),
                """
                CREATE INDEX repair_jobs_by_civilization
                ON repair_jobs(civilization_id, created_at_ms, id)
                """.trimIndent(),
                """
                CREATE TABLE repair_job_items (
                    repair_job_id TEXT NOT NULL,
                    battle_id TEXT NOT NULL,
                    block_change_id TEXT NOT NULL,
                    ordinal INTEGER NOT NULL CHECK (ordinal >= 0),
                    unit_price_minor INTEGER NOT NULL CHECK (
                        unit_price_minor BETWEEN 0 AND 9000000000000000
                    ),
                    status TEXT NOT NULL CHECK (
                        status IN ('PENDING', 'RESTORED', 'SKIPPED_CONFLICT', 'FAILED')
                    ),
                    processed_at_ms INTEGER,
                    failure_message TEXT,
                    CHECK (length(repair_job_id) = 36),
                    CHECK (length(battle_id) = 36),
                    CHECK (length(block_change_id) = 36),
                    CHECK (
                        failure_message IS NULL OR
                        (status = 'FAILED' AND length(failure_message) <= 512)
                    ),
                    CHECK (
                        (status = 'PENDING' AND processed_at_ms IS NULL AND
                         failure_message IS NULL) OR
                        (status <> 'PENDING' AND processed_at_ms IS NOT NULL)
                    ),
                    PRIMARY KEY (repair_job_id, block_change_id),
                    UNIQUE (repair_job_id, ordinal),
                    FOREIGN KEY (repair_job_id)
                        REFERENCES repair_jobs(id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT,
                    FOREIGN KEY (battle_id, block_change_id)
                        REFERENCES battle_damage_report_entries(battle_id, block_change_id)
                        ON UPDATE RESTRICT ON DELETE RESTRICT
                )
                """.trimIndent(),
                """
                CREATE INDEX repair_job_items_pending
                ON repair_job_items(repair_job_id, status, ordinal)
                """.trimIndent(),
                """
                CREATE TRIGGER repair_jobs_validate_insert
                BEFORE INSERT ON repair_jobs
                WHEN NOT EXISTS (
                    SELECT 1
                    FROM battles battle
                    WHERE battle.id = NEW.battle_id
                      AND battle.season_id = NEW.season_id
                      AND battle.status = 'CLOSED'
                      AND NEW.civilization_id IN (
                          battle.attacking_civilization_id,
                          battle.defending_civilization_id
                      )
                      AND (
                          (battle.winner_civilization_id IS NULL AND
                           NEW.victor_civilization_id IS NULL) OR
                          (battle.winner_civilization_id = NEW.civilization_id AND
                           NEW.victor_civilization_id IS NULL) OR
                          (battle.winner_civilization_id <> NEW.civilization_id AND
                           NEW.victor_civilization_id = battle.winner_civilization_id)
                      )
                ) OR NEW.total_eligible_count <> (
                    SELECT COUNT(*)
                    FROM battle_damage_report_entries entry
                    JOIN battle_block_changes change ON change.id = entry.block_change_id
                    JOIN claims claim ON claim.id = change.claim_id
                    WHERE entry.battle_id = NEW.battle_id
                      AND entry.eligibility = 'ELIGIBLE'
                      AND claim.civilization_id = NEW.civilization_id
                ) OR (
                    NEW.funding_mode = 'ORDINARY' AND NOT EXISTS (
                        SELECT 1
                        FROM economy_ledger_transactions transaction_header
                        JOIN economy_ledger_postings payer
                          ON payer.transaction_id = transaction_header.id
                         AND payer.civilization_id = NEW.civilization_id
                         AND payer.amount_minor = -NEW.gross_cost_minor
                        WHERE transaction_header.id = NEW.payment_ledger_transaction_id
                          AND transaction_header.season_id = NEW.season_id
                          AND transaction_header.kind = 'REPAIR_PAYMENT'
                          AND transaction_header.reference_type = 'REPAIR_JOB'
                          AND transaction_header.reference_id = NEW.id
                          AND transaction_header.actor_player_id IS
                              NEW.initiated_by_player_id
                          AND transaction_header.posting_count = CASE
                              WHEN NEW.victor_proceeds_minor > 0 THEN 2
                              ELSE 1
                          END
                          AND (
                              (NEW.victor_proceeds_minor = 0) OR EXISTS (
                                  SELECT 1
                                  FROM economy_ledger_postings victor
                                  WHERE victor.transaction_id = transaction_header.id
                                    AND victor.civilization_id =
                                        NEW.victor_civilization_id
                                    AND victor.amount_minor = NEW.victor_proceeds_minor
                              )
                          )
                    )
                )
                BEGIN
                    SELECT RAISE(ABORT, 'invalid repair job basis');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER repair_job_items_validate_insert
                BEFORE INSERT ON repair_job_items
                WHEN NOT EXISTS (
                    SELECT 1
                    FROM repair_jobs job
                    JOIN battle_damage_report_entries entry
                      ON entry.battle_id = NEW.battle_id
                     AND entry.block_change_id = NEW.block_change_id
                    JOIN battle_block_changes change ON change.id = entry.block_change_id
                    JOIN claims claim ON claim.id = change.claim_id
                    WHERE job.id = NEW.repair_job_id
                      AND job.battle_id = NEW.battle_id
                      AND job.status = 'QUEUED'
                      AND entry.eligibility = 'ELIGIBLE'
                      AND claim.civilization_id = job.civilization_id
                      AND NEW.ordinal <
                          job.selected_restore_original_count +
                          job.selected_remove_placement_count
                      AND NEW.unit_price_minor = CASE entry.cost_category
                          WHEN 'RESTORE_ORIGINAL_BLOCK'
                              THEN job.restore_original_unit_price_minor
                          WHEN 'REMOVE_PLACED_BLOCK'
                              THEN job.remove_placement_unit_price_minor
                      END
                )
                BEGIN
                    SELECT RAISE(ABORT, 'invalid repair job item');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER repair_jobs_validate_update
                BEFORE UPDATE ON repair_jobs
                WHEN NEW.id <> OLD.id OR
                     NEW.season_id <> OLD.season_id OR
                     NEW.battle_id <> OLD.battle_id OR
                     NEW.civilization_id <> OLD.civilization_id OR
                     NEW.initiated_by_player_id IS NOT OLD.initiated_by_player_id OR
                     NEW.funding_mode <> OLD.funding_mode OR
                     NEW.idempotency_key <> OLD.idempotency_key OR
                     NEW.target_completion_basis_points <>
                        OLD.target_completion_basis_points OR
                     NEW.total_eligible_count <> OLD.total_eligible_count OR
                     NEW.observed_restored_count <> OLD.observed_restored_count OR
                     NEW.observed_repairable_count <> OLD.observed_repairable_count OR
                     NEW.observed_conflict_count <> OLD.observed_conflict_count OR
                     NEW.selected_restore_original_count <>
                        OLD.selected_restore_original_count OR
                     NEW.selected_remove_placement_count <>
                        OLD.selected_remove_placement_count OR
                     NEW.restore_original_unit_price_minor <>
                        OLD.restore_original_unit_price_minor OR
                     NEW.remove_placement_unit_price_minor <>
                        OLD.remove_placement_unit_price_minor OR
                     NEW.gross_cost_minor <> OLD.gross_cost_minor OR
                     NEW.victor_share_basis_points <> OLD.victor_share_basis_points OR
                     NEW.victor_civilization_id IS NOT OLD.victor_civilization_id OR
                     NEW.victor_proceeds_minor <> OLD.victor_proceeds_minor OR
                     NEW.payment_ledger_transaction_id IS NOT
                        OLD.payment_ledger_transaction_id OR
                     NEW.created_at_ms <> OLD.created_at_ms OR
                     NEW.next_item_ordinal < OLD.next_item_ordinal OR
                     NEW.restored_count < OLD.restored_count OR
                     NEW.skipped_conflict_count < OLD.skipped_conflict_count OR
                     NEW.failed_count < OLD.failed_count OR
                     NOT (
                        (OLD.status = NEW.status AND OLD.status IN (
                            'QUEUED', 'RUNNING', 'PAUSED'
                        )) OR
                        (OLD.status = 'QUEUED' AND NEW.status IN (
                            'RUNNING', 'PAUSED', 'CANCELLED'
                        )) OR
                        (OLD.status = 'RUNNING' AND NEW.status IN (
                            'PAUSED', 'COMPLETED', 'CANCELLED', 'FAILED'
                        )) OR
                        (OLD.status = 'PAUSED' AND NEW.status IN (
                            'QUEUED', 'RUNNING', 'CANCELLED', 'FAILED'
                        ))
                     )
                BEGIN
                    SELECT RAISE(ABORT, 'invalid repair job transition');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER repair_jobs_validate_selection_before_execution
                BEFORE UPDATE ON repair_jobs
                WHEN OLD.status = 'QUEUED' AND NEW.status IN ('RUNNING', 'PAUSED') AND (
                    (SELECT COUNT(*) FROM repair_job_items item
                     WHERE item.repair_job_id = OLD.id) <>
                        OLD.selected_restore_original_count +
                            OLD.selected_remove_placement_count OR
                    (SELECT COUNT(*)
                     FROM repair_job_items item
                     JOIN battle_damage_report_entries entry
                       ON entry.battle_id = item.battle_id
                      AND entry.block_change_id = item.block_change_id
                     WHERE item.repair_job_id = OLD.id
                       AND entry.cost_category = 'RESTORE_ORIGINAL_BLOCK') <>
                        OLD.selected_restore_original_count OR
                    (SELECT COUNT(*)
                     FROM repair_job_items item
                     JOIN battle_damage_report_entries entry
                       ON entry.battle_id = item.battle_id
                      AND entry.block_change_id = item.block_change_id
                     WHERE item.repair_job_id = OLD.id
                       AND entry.cost_category = 'REMOVE_PLACED_BLOCK') <>
                        OLD.selected_remove_placement_count OR
                    (OLD.funding_mode = 'ORDINARY' AND
                     (SELECT COALESCE(SUM(item.unit_price_minor), 0)
                      FROM repair_job_items item
                      WHERE item.repair_job_id = OLD.id) <> OLD.gross_cost_minor)
                )
                BEGIN
                    SELECT RAISE(ABORT, 'repair job selection is incomplete');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER repair_jobs_cannot_be_deleted
                BEFORE DELETE ON repair_jobs
                BEGIN
                    SELECT RAISE(ABORT, 'repair jobs cannot be deleted');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER repair_job_items_validate_update
                BEFORE UPDATE ON repair_job_items
                WHEN NEW.repair_job_id <> OLD.repair_job_id OR
                     NEW.battle_id <> OLD.battle_id OR
                     NEW.block_change_id <> OLD.block_change_id OR
                     NEW.ordinal <> OLD.ordinal OR
                     NEW.unit_price_minor <> OLD.unit_price_minor OR
                     OLD.status <> 'PENDING' OR NEW.status = 'PENDING'
                BEGIN
                    SELECT RAISE(ABORT, 'invalid repair item transition');
                END
                """.trimIndent(),
                """
                CREATE TRIGGER repair_job_items_cannot_be_deleted
                BEFORE DELETE ON repair_job_items
                BEGIN
                    SELECT RAISE(ABORT, 'repair job items cannot be deleted');
                END
                """.trimIndent(),
            ),
        ),
    )
}
