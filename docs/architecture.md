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

SQLite is the first implementation and uses foreign keys, WAL mode, and a bounded busy timeout. The selected SQLite JDBC driver is packaged in the plugin so production behavior does not depend on server implementation details.

`runtime_state` durably identifies zero or one active season. The live plugin opens and migrates the V2 database during startup, validates the selected season, and rebuilds its indexes. The legacy JSON-blob datastore is not opened.

## Application services

The V2 mutation boundary is under `application.season`, `application.civilization`, and `application.claim`.

- Services return `ApplicationResult.Applied`, `Unchanged`, or `Rejected`. Expected rule failures are immutable values that an adapter can translate into chat or console messages; infrastructure faults still throw and roll back.
- `SeasonService` owns phase transitions. `WAR` is a durable global gate rather than a scattered configuration boolean, and an admin may transition `WAR` back to `PEACE` to stop war without ending the season.
- `CivilizationService` supports empty drafts as well as atomic, idempotent provisioning from offline player UUIDs. Activation requires one leader and a roster but deliberately does not require a home or claim.
- A player may belong to at most one civilization per season. Reassignment is explicit, and a leader must transfer leadership before moving.
- Roster and claim mutations are open in `SETUP` and `PEACE` and closed in `WAR`, `FINALE`, and `ARCHIVED`. This is an initial safe MVP policy, not a promise that future season rules cannot make it configurable.
- `ClaimService` validates status, area/count limits, exact overlap, and same-civilization edge connectivity before inserting a claim. Corner contact is not connectivity.

Application services are synchronous because the repository port represents blocking durable work. The Paper mutation adapter invokes them on one plugin-owned storage executor, preserving submission order. After every operation it reloads authoritative active-season data and constructs a replacement spatial index off-thread; the replacement snapshot is installed on the Paper thread before success is reported. Event-time protection reads only the published index and never waits for SQL.

## Live runtime cutover

`CivilizationsRuntime` is the owner of V2 runtime state and structured background work.

- Startup migrations and reads run on the storage executor. The plugin remains in a visible `Starting` state until a `Ready` snapshot is published on the server thread.
- Runtime snapshots contain the active season, civilizations, memberships, and a read-only-by-convention claim index. Each publication replaces the entire snapshot; it never mutates an index while event code may be reading it.
- Startup verifies that an active season is not archived, every active civilization has exactly one leader, every claim has an active owner, and no persisted claims overlap. Invalid durable state fails closed with actionable IDs instead of partially enabling gameplay.
- Mutations submitted before readiness are rejected. Infrastructure failures move the runtime to `Failed` and disable the plugin rather than falling back to legacy data.
- Shutdown stops new work and gives the storage executor a bounded drain period. Civilizations no longer cancels scheduler tasks owned by other plugins.

The live `/civadmin` adapter uses Paper's `BasicCommand` API, explicit UUIDs for offline roster operations, Adventure messages, and an operator-default permission. It parses and translates only; business decisions remain in application services.

Legacy commands, listeners, tasks, placeholders, and datastores are deliberately not registered during the cutover. Keeping their source temporarily is cheaper than porting unfinished features, while disabling their entry points prevents split-brain state. The next slice will introduce V2 protection listeners against the published claim index.
