# Civilizations architecture

Civilizations is being migrated incrementally from a Foundation-centered plugin into a domain-centered Paper plugin. Each migration slice should remain independently buildable and mergeable.

## Dependency direction

Dependencies point inward:

```text
Paper commands, listeners, and UI
                 |
                 v
        Application services
                 |
                 v
       Plain Kotlin domain model

Persistence, Paper world access, economy, configuration, and messaging implement ports owned by the application layer. They do not leak framework types into the domain.
```

The domain and application layers must not import Bukkit/Paper, Foundation, Vault, JDBC, configuration, command, or menu types. They should be testable with the ordinary JVM test task.

Foundation remains a temporary adapter for the legacy command, menu, settings, and localization code. New architectural code must not depend on it.

## Runtime ownership

- Paper world/entity access and live-state mutation belong to the server thread.
- Persistence and other blocking I/O belong to plugin-owned background execution.
- Results cross that boundary as immutable domain values.
- The database is durable state; purpose-built in-memory indexes serve hot event queries.
- Long-running work such as war resolution and reconstruction is represented as persisted jobs rather than anonymous scheduler tasks.

## Claim model

The first migrated subsystem is the claim geometry and spatial index under `domain.claim` and `application.claim`.

- Claims are inclusive, immutable 2D rectangles identified by integer X/Z coordinates and a stable world identifier.
- Rectangle construction normalizes reversed selection corners once.
- Area, containment, overlap, and edge adjacency are pure operations.
- Corner-only contact is not edge adjacency.
- The spatial index maps world/chunk pairs to candidate claim IDs and performs an exact rectangle check after candidate lookup.
- Arbitrarily shaped territory remains a collection of rectangles. It is not converted into a materialized polygon or block set.
- The index is derived state and is rebuilt from authoritative claims during startup.

The legacy `Region` and `ClaimUtil` remain in place for this first slice. A later slice will load authoritative claim rows, build this index, and route Paper protection events through it before deleting the legacy implementation.

## Relational persistence

The V2 persistence boundary lives under `application.persistence`; JDBC is an implementation detail under `infrastructure.persistence.jdbc`.

- `CivilizationsRepository` exposes scoped read contexts and atomic write transactions. Application code does not receive JDBC connections or SQL types.
- Schema changes are ordered, named, and recorded in `schema_migrations`. Startup refuses unknown or renamed migrations instead of guessing.
- The first schema models seasons, civilizations, memberships, and claims as separate relational records.
- Civilization display names are normalized before storage and unique within a season.
- Composite foreign keys prevent a membership or claim from referencing a civilization in a different season.
- The membership primary key permits one civilization per player per season while retaining membership history across seasons.
- A partial unique index permits at most one leader per civilization. Draft civilizations may intentionally have no leader or citizens.
- Claim rectangles remain immutable rows. Exact duplicate rectangles are rejected in SQL; geometric overlap and connected-settlement policy remain application concerns handled before insertion.
- All values use prepared statements, and multi-record changes commit or roll back as a unit.

SQLite is the first implementation and uses foreign keys, WAL mode, and a bounded busy timeout. Integration tests bring their own SQLite JDBC driver; Paper supplies the runtime driver used by the existing server. Driver packaging can be revisited when V2 storage is wired into plugin startup.

The V2 repository is not opened by the live plugin yet. This keeps the persistence slice independently mergeable and prevents two stores from competing for authority. A later cutover will create/migrate the V2 database during startup, load one season into memory, and retire the legacy JSON-blob datastore.
