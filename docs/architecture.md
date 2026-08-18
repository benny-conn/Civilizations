# Civilizations architecture

Civilizations is a domain-centered Paper plugin. The architecture migration and removal of the former Foundation-centered implementation are complete; future changes extend the boundaries documented here rather than running a second model beside them.

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

Foundation, Vault, legacy serializers, global managers, and unstructured coroutine helpers are not part of the build. Paper and database implementations remain adapters around application-owned contracts.

## Runtime ownership

- Paper world/entity access and live-state mutation belong to the server thread.
- Persistence and other blocking I/O belong to plugin-owned background execution.
- Results cross that boundary as immutable domain values.
- The database is durable state; purpose-built in-memory indexes serve hot event queries.
- Long-running work such as war resolution and reconstruction is represented as persisted jobs rather than anonymous scheduler tasks.

## Configuration and rule ownership

Paper loads YAML and translates it at the infrastructure boundary into validated,
immutable values owned by the application layer. Bukkit configuration types and YAML
paths never enter domain or application code. Services consume typed rules, which keeps
the same behavior directly testable without a running server.

Configuration is appropriate for settled balancing and policy choices such as claim
limits and phase gates. Application constructors enforce a safe envelope around those
choices: configuration may make behavior more restrictive, but cannot enable lifecycle
combinations that invalidate snapshots, archived state, or durable authorization.

SQL remains authoritative for identities, lifecycle state, balances, journals, and
jobs. If a configurable value determines the meaning of a long-lived operation, the
effective value is copied into that operation's durable rules snapshot when it starts.
Changing YAML later therefore affects future operations rather than reinterpreting an
existing war, battle, charge, or repair.

The live configuration is installed once during plugin startup. There is no partial or
best-effort reload path: malformed values fail startup with their YAML path, and edits
require a restart until an explicit atomic reload operation is designed. The current key
reference is [configuration.md](configuration.md).

Season One repair economics follow this same boundary. YAML controls the initial
civilization balance, the prices for restore-original and remove-placement repair units,
the victor share, whether balances may enter debt, and which ordinary civilization roles
may initiate repair. The shipped defaults are `0`, `1`, `1`, `25%`, `false`, and
leader-only. A repair job snapshots the effective economic rules when it is created so a
later restart or configuration edit cannot change its price or proceeds.

Admin repair is a command authorization path, not an economic setting. Admin commands
name the civilization on whose behalf they act and invoke the same application operation
with explicit admin actor/audit context. An admin-sponsored repair charges no
civilization account and creates no victor proceeds; it must not bypass repair lifecycle,
world-conflict, idempotency, or persistence invariants.

More generally, when player and admin workflows perform the same civilization operation,
the admin adapter should select an explicit target civilization and reuse the application
service rather than directly editing durable state. Any admin-only override must be a
typed, auditable input with deliberately bounded effects.

## Claim model

The first migrated subsystem is the claim geometry and spatial index under `domain.claim` and `application.claim`.

- Claims are inclusive, immutable 2D rectangles identified by integer X/Z coordinates and a stable world identifier.
- Rectangle construction normalizes reversed selection corners once.
- Area, containment, overlap, and edge adjacency are pure operations.
- Corner-only contact is not edge adjacency.
- The spatial index maps world/chunk pairs to candidate claim IDs and performs an exact rectangle check after candidate lookup.
- Arbitrarily shaped territory remains a collection of rectangles. It is not converted into a materialized polygon or block set.
- The index is derived state and is rebuilt from authoritative claims during startup.

The live runtime loads authoritative claim rows, builds this index, and routes Paper protection events through it. The former `Region`, `ClaimUtil`, and full-height materialization implementation have been deleted.

## Relational persistence

The persistence boundary lives under `application.persistence`; JDBC is an implementation detail under `infrastructure.persistence.jdbc`.

- `CivilizationsRepository` exposes scoped read contexts and atomic write transactions. Application code does not receive JDBC connections or SQL types.
- Schema changes are ordered, named, and recorded in `schema_migrations`. Startup refuses unknown or renamed migrations instead of guessing.
- The schema models seasons, civilizations, memberships, claims, wars, timed battles, battle-participant snapshots, immutable block-change journal rows, and sealed per-battle damage reports as separate relational records.
- Civilization display names are normalized before storage and unique within a season.
- Composite foreign keys prevent a membership or claim from referencing a civilization in a different season.
- The membership primary key permits one civilization per player per season while retaining membership history across seasons.
- A partial unique index permits at most one leader per civilization. Draft civilizations may intentionally have no leader or citizens.
- Claim rectangles remain immutable rows. Exact duplicate rectangles are rejected in SQL; geometric overlap and connected-settlement policy remain application concerns handled before insertion.
- All values use prepared statements, and multi-record changes commit or roll back as a unit.

SQLite is the first implementation and uses foreign keys, WAL mode, and a bounded busy timeout. The selected SQLite JDBC driver is packaged in the plugin so production behavior does not depend on server implementation details.

`runtime_state` durably identifies zero or one active season. The live plugin opens and migrates the relational database during startup, validates the selected season, and rebuilds its indexes. The legacy JSON-blob datastore and mutable serialized graph have been deleted.

## Application services

The mutation boundary is under `application.season`, `application.civilization`, `application.claim`, `application.war`, and `application.damage`.

- Services return `ApplicationResult.Applied`, `Unchanged`, or `Rejected`. Expected rule failures are immutable values that an adapter can translate into chat or console messages; infrastructure faults still throw and roll back.
- `SeasonService` owns phase transitions. `WAR` is a durable global gate rather than a scattered configuration boolean, and an admin may transition `WAR` back to `PEACE` to stop war without ending the season.
- `CivilizationService` supports empty drafts as well as atomic, idempotent provisioning from offline player UUIDs. Activation requires one leader and a roster but deliberately does not require a home or claim.
- A player may belong to at most one civilization per season. Reassignment is explicit, and a leader must transfer leadership before moving.
- Roster and claim mutations default to open in `SETUP` and `PEACE`. YAML may narrow either gate, but application validation prevents enabling them in `WAR`, `FINALE`, or `ARCHIVED` because those combinations are not yet safe.
- `ClaimService` validates status, area/count limits, exact overlap, and same-civilization edge connectivity before inserting a claim. Corner contact is not connectivity.
- `WarService` separates a durable relationship (`War`) from each timed engagement (`Battle`). Declaration, activation, hostile-entry battle start, expiry/resolution, and terminal transitions are explicit transactional operations.

Application services are synchronous because the repository port represents blocking durable work. The Paper mutation adapter invokes them on one plugin-owned storage executor, preserving submission order. After every operation it reloads authoritative active-season data and constructs replacement indexes/read models off-thread; the replacement snapshot is installed on the Paper thread before success is reported. Event-time protection reads only published memory and never waits for SQL.

## War and battle model

A `War` is the durable political relationship between two civilizations. A `Battle` is one timed engagement within that war. This avoids treating every war as a single transient raid and leaves room for multiple engagements, reparations, surrender, or later occupation rules without changing identity.

- Open wars are unique per civilization pair, and a civilization may participate in multiple political wars. The current safety invariant permits only one `ACTIVE`/`RESOLVING` battle per civilization, preventing overlapping journals from claiming incompatible original states for the same land.
- The current rules snapshot records `HOSTILE_CLAIM_ENTRY`, `OPPOSING_CIVILIZATION_CLAIMS`, and the battle duration. When a battle is eventually connected to movement, the entering side becomes attacker and the entered claim's owner becomes defender.
- “Battlefield” is not a separate region type. All claim lookup continues through the normal chunk index; the active read model maps each civilization to the opposing civilization's ordinary claim IDs.
- Both civilization rosters are snapshotted as battle participants when the battle starts. Later membership changes cannot rewrite the historical roster.
- Absolute timestamps, not decrementing task counters, drive expiry. Startup and every runtime refresh idempotently move expired `ACTIVE` battles to `RESOLVING`, which immediately removes their eligibility from live memory.
- The eligibility read model is intentionally inert. No Paper movement listener starts battles and no protection request receives a destructive capability until the two-phase mutation adapter is implemented.

## Damage journal

`DamageJournalService` is the durable boundary that must run before a future Paper adapter mutates battle land. It accepts framework-free stable IDs, a 3D block coordinate, the state observed on the server thread, actor, claim, and cause.

- Journal identity is battle plus world/X/Y/Z. The first preparation atomically inserts the original state; later breaks, explosions, or placements at that coordinate return the existing row and can never overwrite the baseline.
- Both enemy and owner mutations in either side's land are journalable. This prevents defenders rebuilding during a battle from silently escaping the same pre-battle restoration history.
- Attacker placement is represented by journaling the replaced state—often `minecraft:air`—before placement. Restoring air later removes the attacker-created block.
- Rows retain season, battle, claim, first actor/cause/time, and canonical simple block data. They are immutable in SQL and can be read in bounded cursor pages without loading a city's damage into live runtime memory.
- The service validates an active, unexpired battle, global `WAR` phase, snapshotted participant, claim party, and exact X/Z containment. SQL triggers independently enforce the same durable boundary.
- A prepared result is a single-use handoff, not durable permission. The future Paper adapter must cancel the original event, prepare the journal off-thread, return to the server thread, confirm the block still matches the observed state and battle authorization, then apply exactly one mutation. A mismatch aborts rather than overwriting newer world state.
- `SimpleBlockSnapshot` deliberately excludes block-entity payloads. Containers and other unsupported block entities remain protected until inventory, text/NBT, loot, and duplication semantics are explicitly modeled.

`DamageReportService` owns the resolution-time readout of that journal. It accepts a complete set of final `SimpleBlockSnapshot` observations only while a battle is `RESOLVING`; it never reads Paper state itself. Each journal row is frozen as already restored or repair-eligible. Eligible rows are categorized as restoring an original block or removing a block placed over an air-like original, producing stable one-coordinate repair units without choosing monetary rates.

Schema migration 5 stores one sealed report per battle plus its final-state entries. Entries are staged and the summary row atomically seals the complete set; SQL triggers verify exact journal coverage, category/count consistency, battle state, and immutability. An identical retry returns the stored report, while changed observations fail as an explicit conflict. Repository reads page the joined report and journal rows in stable journal order so later repair work can resume without loading an unbounded report.

## Live runtime

`CivilizationsRuntime` is the owner of runtime state and structured background work.

- Startup migrations and reads run on the storage executor. The plugin remains in a visible `Starting` state until a `Ready` snapshot is published on the server thread.
- Runtime snapshots contain the active season, civilizations, memberships, wars, battles, participant snapshots, a read-only-by-convention claim index, derived active-battle eligibility, and a protection policy built over those values. Each publication replaces the entire snapshot; it never mutates an index while event code may be reading it.
- Startup verifies that an active season is not archived, every active civilization has exactly one leader, every claim has an active owner, no persisted claims overlap, open wars reference active parties, and open battles/participants match their war and trigger claim. Invalid durable state fails closed with actionable IDs instead of partially enabling gameplay.
- Mutations submitted before readiness are rejected. Infrastructure failures move the runtime to `Failed` and disable the plugin rather than falling back to another store.
- Shutdown stops new work and gives the storage executor a bounded drain period. Civilizations no longer cancels scheduler tasks owned by other plugins.

The live `/civadmin` adapter uses Paper's `BasicCommand` API, explicit UUIDs for offline roster operations, Adventure messages, and an operator-default permission. It parses and translates only; business decisions remain in application services.

The former commands, listeners, tasks, placeholders, menus, global managers, serializers, compatibility adapters, and datastores were deleted after the cutover. Git history preserves them for reference without allowing a second source of truth to compile or ship.

## Protection policy and Paper coverage

`ProtectionService` is an application-owned, pure policy over the active season's membership map and chunk spatial index. A Paper listener supplies a stable world key, integer X/Z target, actor, action, and explicit admin bypass. The policy returns a reasoned allow/deny value and never calls Paper, Foundation, or persistence.

- Unclaimed coordinates retain vanilla behavior. Members and leaders may mutate their civilization's claims in the configured safe subset of `SETUP`, `PEACE`, and `WAR`; outsiders may not. `FINALE` and `ARCHIVED` always freeze claimed land except for explicit admin bypass.
- PVP in claimed land always requires a conflict capability. Merely putting the season in `WAR` does not authorize anybody to attack or destroy blocks.
- A conflict capability is bound to its actor, kind, allowed actions, eligible claim IDs, and PVP target participants. War capabilities are valid only in `WAR`; assassination capabilities are limited to targeted PVP in `PEACE` or `WAR`. The runtime publishes persisted battle eligibility and owns the journal service but does not convert eligibility into capabilities, so claimed destruction remains closed until the two-phase Paper adapter lands.
- Inventory-bearing blocks cannot appear in an MVP conflict capability. Block-break translation classifies them as container actions so a generic break grant cannot bypass that invariant.
- Explosions remove only claimed blocks from the event's block list. Autonomous fire and entity block changes are denied on claimed targets. Fluids, pistons, hopper transfers, and inventory pickup may move within one ownership area but not between civilizations or across wilderness boundaries.
- While runtime state is loading or failed, mutation listeners fail closed. Once a ready runtime has no active season, events retain vanilla behavior.

The current event matrix is:

| Surface | Paper events/policy | Current behavior |
| --- | --- | --- |
| Blocks | break, place/multi-place, clicked-block interact, bucket fill/empty, player ignition | Exact affected coordinate; owner/admin allowed, outsider denied |
| Containers | block interact/break, inventory open, automated move/pickup | Direct access follows membership; automation cannot cross ownership |
| Entities | interact/damage, armor stands, hanging place/break, vehicles, projectiles | Entity coordinate is authoritative; the responsible projectile/TNT player is resolved |
| PVP | entity damage by player or player-shot projectile | Vanilla in wilderness; denied in claims without a targeted conflict capability |
| Environment | entity/block explosions, burn/ignite/fire spread, entity block change | Claimed targets are removed or cancelled |
| Boundaries | fluid flow, piston head/moved blocks, inventory transfer | Every source/destination pair must have the same owner, including wilderness as no owner |
| Movement | ordinary movement and teleport | Intentionally unrestricted for MVP; land ownership is not a border-entry rule |

Later war integration must use the journal-before-mutation contract before it hands a conflict capability to any destructive Paper path. The visual TNT effect may never become the authoritative mutation.

## Delivery and worktree sequencing

The architecture rework has no remaining slice. Net-new MVP work is split into a durable-feature lane and a Paper-integration lane. Schema/repository migrations are ordered and must not be developed concurrently with another schema slice. Paper plugin/runtime/listener lifecycle changes are likewise serialized. A branch in each lane may proceed concurrently when both use an application-owned port already present on `main`.

[worktree-roadmap.md](worktree-roadmap.md) is the executable feature merge queue. In summary, damage reporting precedes the ledger, the ledger precedes repair jobs, and repair jobs precede the Paper repair runner. The simple-block Paper war adapter may proceed now against the existing first-write-wins journal, but it must not broaden support to containers, block entities, or cascading physics.

## Retired architecture

The 2026 architecture cleanup permanently removed the Foundation lifecycle, command framework, settings/localization framework, menus/conversations, Vault hooks, JitPack repository, coroutine helper, global managers, Towny/Factions adapters, mutable legacy civilization/claim/raid graph, JSON-blob datastore, and all legacy tasks/listeners/commands. `CivilizationsPlugin` is a native `JavaPlugin`; `/civadmin` is a native Paper `BasicCommand`; configuration uses Bukkit's configuration API at the Paper boundary; user-facing components use Adventure.

An architecture regression test scans all production sources and the build file for retired framework imports/dependencies. Reusing an old behavior means designing it against the current domain/services and persistence ports, not copying the deleted implementation back into production.
