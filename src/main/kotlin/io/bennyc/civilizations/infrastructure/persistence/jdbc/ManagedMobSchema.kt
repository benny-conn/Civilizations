package io.bennyc.civilizations.infrastructure.persistence.jdbc

internal object ManagedMobSchema {
    val migration = SchemaMigration(15, "managed_mob_lifecycle", listOf(
        """CREATE TABLE managed_mobs (
            id TEXT PRIMARY KEY, creation_id TEXT NOT NULL UNIQUE,
            season_id TEXT NOT NULL REFERENCES seasons(id), world_uuid TEXT NOT NULL,
            species TEXT NOT NULL CHECK(length(species) BETWEEN 3 AND 128),
            maturity_ms INTEGER NOT NULL CHECK(maturity_ms BETWEEN 1 AND 31536000000),
            cooldown_ms INTEGER NOT NULL CHECK(cooldown_ms BETWEEN 1 AND 31536000000),
            actor TEXT NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 128),
            reason TEXT NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 512),
            parent_a TEXT REFERENCES managed_mobs(id), parent_b TEXT REFERENCES managed_mobs(id),
            prepared_at INTEGER NOT NULL CHECK(prepared_at>=0),
            mature_at INTEGER NOT NULL CHECK(mature_at>=prepared_at),
            breed_after INTEGER NOT NULL CHECK(breed_after>=mature_at),
            life TEXT NOT NULL CHECK(life IN ('PREPARED','APPLYING','ALIVE','CANCELLED','DEATH_PENDING','DEAD')),
            entity_uuid TEXT UNIQUE,
            CHECK((parent_a IS NULL AND parent_b IS NULL) OR
                (parent_a IS NOT NULL AND parent_b IS NOT NULL AND parent_a<>parent_b AND parent_a<>id AND parent_b<>id)),
            CHECK((entity_uuid IS NULL AND life IN ('PREPARED','APPLYING','CANCELLED')) OR
                (entity_uuid IS NOT NULL AND life IN ('ALIVE','DEATH_PENDING','DEAD')))
        )""".trimIndent(),
        "CREATE INDEX managed_mobs_season_page ON managed_mobs(season_id,id)",
        """CREATE TABLE managed_mob_parent_reservations (
            parent_id TEXT PRIMARY KEY REFERENCES managed_mobs(id),
            child_id TEXT NOT NULL REFERENCES managed_mobs(id)
        )""".trimIndent(),
        """CREATE TABLE managed_mob_deaths (
            operation_id TEXT PRIMARY KEY, mob_id TEXT NOT NULL UNIQUE REFERENCES managed_mobs(id),
            actor TEXT NOT NULL CHECK(length(trim(actor)) BETWEEN 1 AND 128),
            reason TEXT NOT NULL CHECK(length(trim(reason)) BETWEEN 1 AND 512),
            prepared_at INTEGER NOT NULL CHECK(prepared_at>=0),
            stage TEXT NOT NULL CHECK(stage IN ('PREPARED','APPLYING','COMPLETED'))
        )""".trimIndent(),
        """CREATE TRIGGER managed_mob_initial BEFORE INSERT ON managed_mobs
            WHEN NEW.life<>'PREPARED' BEGIN SELECT RAISE(ABORT,'mob must be prepared'); END""",
        """CREATE TRIGGER managed_mob_reserve AFTER INSERT ON managed_mobs WHEN NEW.parent_a IS NOT NULL BEGIN
            INSERT INTO managed_mob_parent_reservations VALUES(NEW.parent_a,NEW.id);
            INSERT INTO managed_mob_parent_reservations VALUES(NEW.parent_b,NEW.id);
        END""",
        """CREATE TRIGGER managed_mob_release AFTER UPDATE OF life ON managed_mobs
            WHEN NEW.life IN ('ALIVE','CANCELLED') BEGIN
            DELETE FROM managed_mob_parent_reservations WHERE child_id=NEW.id;
        END""",
        """CREATE TRIGGER managed_mob_identity BEFORE UPDATE ON managed_mobs WHEN
            NEW.id<>OLD.id OR NEW.creation_id<>OLD.creation_id OR NEW.season_id<>OLD.season_id OR
            NEW.world_uuid<>OLD.world_uuid OR NEW.species<>OLD.species OR NEW.maturity_ms<>OLD.maturity_ms OR
            NEW.cooldown_ms<>OLD.cooldown_ms OR NEW.actor<>OLD.actor OR NEW.reason<>OLD.reason OR
            NEW.parent_a IS NOT OLD.parent_a OR NEW.parent_b IS NOT OLD.parent_b OR
            NEW.prepared_at<>OLD.prepared_at OR NEW.mature_at<>OLD.mature_at OR NEW.breed_after<OLD.breed_after OR
            (OLD.entity_uuid IS NOT NULL AND NEW.entity_uuid IS NOT OLD.entity_uuid)
            BEGIN SELECT RAISE(ABORT,'mob identity and snapshots immutable'); END""",
        """CREATE TRIGGER managed_mob_lifecycle BEFORE UPDATE OF life ON managed_mobs WHEN NOT (
            NEW.life=OLD.life OR (OLD.life='PREPARED' AND NEW.life IN ('APPLYING','CANCELLED')) OR
            (OLD.life='APPLYING' AND NEW.life='ALIVE') OR
            (OLD.life='ALIVE' AND NEW.life='DEATH_PENDING' AND EXISTS(SELECT 1 FROM managed_mob_deaths WHERE mob_id=OLD.id)) OR
            (OLD.life='DEATH_PENDING' AND NEW.life='DEAD' AND EXISTS(SELECT 1 FROM managed_mob_deaths WHERE mob_id=OLD.id AND stage='COMPLETED')))
            BEGIN SELECT RAISE(ABORT,'invalid mob lifecycle transition'); END""",
        """CREATE TRIGGER managed_mob_death_initial BEFORE INSERT ON managed_mob_deaths WHEN
            NEW.stage<>'PREPARED' OR NOT EXISTS(SELECT 1 FROM managed_mobs WHERE id=NEW.mob_id AND life='ALIVE') OR
            EXISTS(SELECT 1 FROM managed_mob_parent_reservations WHERE parent_id=NEW.mob_id)
            BEGIN SELECT RAISE(ABORT,'mob unavailable for death'); END""",
        """CREATE TRIGGER managed_mob_death_update BEFORE UPDATE ON managed_mob_deaths WHEN
            NEW.operation_id<>OLD.operation_id OR NEW.mob_id<>OLD.mob_id OR NEW.actor<>OLD.actor OR
            NEW.reason<>OLD.reason OR NEW.prepared_at<>OLD.prepared_at OR NOT (
                NEW.stage=OLD.stage OR (OLD.stage='PREPARED' AND NEW.stage='APPLYING') OR
                (OLD.stage='APPLYING' AND NEW.stage='COMPLETED'))
            BEGIN SELECT RAISE(ABORT,'invalid death transition'); END""",
    ) + listOf("managed_mobs", "managed_mob_deaths").map {
        "CREATE TRIGGER ${it}_no_delete BEFORE DELETE ON $it BEGIN SELECT RAISE(ABORT,'mob history retained'); END"
    })
}
