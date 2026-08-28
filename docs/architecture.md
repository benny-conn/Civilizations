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

Foundation, legacy serializers, global managers, and unstructured coroutine helpers are not part of the build. VaultAPI is compile-only: Vault types are confined to the optional `infrastructure.paper.economy` player-wallet adapter, behind an application-owned port. Paper and database implementations remain adapters around application-owned contracts.

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
the victor share, and which ordinary civilization roles may initiate repair. The shipped
defaults are `0`, `1`, `1`, `25%`, and leader-only. A repair job snapshots the effective
economic rules when it is created so a later restart or configuration edit cannot change
its price or proceeds.

Civilizations owns civilization money as exact fixed-point SQL balances. Schema migration
7 creates one account per civilization, immutable ledger transaction headers and postings,
and balance-application triggers inside the same database transaction. Caller-supplied
idempotency keys make opening balances, transfers, repair payments, rewards, reversals,
and admin adjustments safe to repeat without applying money twice. The season's currency
scale and opening balance are snapshotted when its accounts initialize.
Every debit is checked against the current treasury balance in the same transaction;
no operation may take a civilization balance below zero.

Player wallets remain owned by the server's external economy plugin. The optional Vault
adapter translates only at the Paper boundary: a deposit durably prepares, withdraws the
player through Vault once, then credits the civilization ledger; a withdrawal durably
reserves the treasury, credits the player once, then completes or reverses on a definite
failure. Vault banks are never civilization accounts. `PREPARED` operations surviving a
restart and provider exceptions become `RECONCILIATION_REQUIRED`; they are never blindly
retried. An audited admin decision records whether the external side succeeded and applies
the corresponding ledger credit, retained debit, or compensating reversal.

Admin repair is a command authorization path, not an economic setting. Admin commands
name the civilization on whose behalf they act and invoke the same application operation
with explicit admin actor/audit context. An admin-sponsored repair charges no
civilization account and creates no victor proceeds; it must not bypass repair lifecycle,
world-conflict, idempotency, or persistence invariants.

More generally, when player and admin workflows perform the same civilization operation,
the admin adapter should select an explicit target civilization and reuse the application
service rather than directly editing durable state. Any admin-only override must be a
typed, auditable input with deliberately bounded effects.

External permission plugins such as LuckPerms gate global access to player/admin command
surfaces; operator-default permissions are safe shipped defaults. They are not the
authoritative model for civilization-scoped authority. Future leader-defined roles and
capabilities such as claiming or managing local PVP are durable Civilizations records
evaluated by application policy, with commands and inventory GUIs acting only as adapters.

## Claim model

The first migrated subsystem is the claim geometry and spatial index under `domain.claim` and `application.claim`.

- Claims are inclusive, immutable 2D rectangles identified by integer X/Z coordinates and a stable world identifier.
- Rectangle construction normalizes reversed selection corners once.
- Area, containment, overlap, and edge adjacency are pure operations.
- Corner-only contact is not edge adjacency.
- The spatial index maps world/chunk pairs to candidate claim IDs and performs an exact rectangle check after candidate lookup.
- Arbitrarily shaped territory remains a collection of rectangles. It is not converted into a materialized polygon or block set.
- The index is derived state and is rebuilt from authoritative claims during startup.

The accepted product model permits more than one disconnected territory per civilization.
Future persistence represents each connected territory as an explicit claim group:
rectangles inside a group obey edge-connectivity, while creating another group atomically
checks a configurable group limit, a substantial establishment price, and optional
membership/treasury progression thresholds. Those thresholds authorize creation; falling
below one later does not silently delete durable land. The current live claim model still
supports only a single connected set and must not emulate groups with a boolean bypass.

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
- Roster mutations default to open in `SETUP`, `PEACE`, and `WAR`; claim mutations default to `SETUP` and `PEACE`. YAML may narrow either gate, but application validation prevents either operation in `FINALE` or `ARCHIVED` and prevents claim creation in `WAR`.
- Political-war rosters remain mutable during `WAR`, while every already-started battle retains its immutable participant and side snapshot. Switching civilizations never rewrites or grants eligibility in the active battle and affects only later battles.
- `ClaimService` validates status, area/count limits, exact overlap, and same-civilization edge connectivity before inserting a claim. Corner contact is not connectivity.
- `WarService` separates a durable relationship (`War`) from each timed engagement (`Battle`). Declaration, activation, hostile-entry battle start, expiry/resolution, and terminal transitions are explicit transactional operations.

Application services are synchronous because the repository port represents blocking durable work. The Paper mutation adapter invokes them on one plugin-owned storage executor, preserving submission order. After every operation it reloads authoritative active-season data and constructs replacement indexes/read models off-thread; the replacement snapshot is installed on the Paper thread before success is reported. Event-time protection reads only published memory and never waits for SQL.

## War and battle model

A `War` is the durable political relationship between two civilizations. A `Battle` is one timed engagement within that war. This avoids treating every war as a single transient raid and leaves room for multiple engagements, reparations, surrender, or later occupation rules without changing identity.

Wars are winnerless relationships. A civilization member with global access to the
declaration command may declare during `SETUP`, `PEACE`, or `WAR` without admin approval
or a preparation countdown, but only the global `WAR` phase can start or sustain a
battle. A dedicated global-phase permission, operator/admin by default, controls that
combat gate. A current civilization leader may surrender its side in a battle; an admin
force-resolution is an explicit audited recovery operation, not an ordinary victory rule.

- Open wars are unique per civilization pair, and a civilization may participate in multiple political wars. The current safety invariant permits only one `ACTIVE`/`RESOLVING` battle per civilization, preventing overlapping journals from claiming incompatible original states for the same land.
- The rules snapshot records `HOSTILE_CLAIM_ENTRY`, `OPPOSING_CIVILIZATION_CLAIMS`, and the configured battle duration. A horizontal block transition or teleport into a hostile claim starts an eligible battle: the entering side becomes attacker and the entered claim's owner becomes defender.
- “Battlefield” is not a separate region type. All claim lookup continues through the normal chunk index; the active read model maps each civilization to the opposing civilization's ordinary claim IDs.
- Both civilization rosters are snapshotted as battle participants when the battle starts. Later membership changes cannot rewrite the historical roster.
- Absolute timestamps, not decrementing task counters, drive expiry. Startup and every runtime refresh idempotently move expired `ACTIVE` battles to `RESOLVING`, which immediately removes their eligibility from live memory.
- The Paper resolution coordinator also checks those absolute deadlines while the server
  remains online. It bounds live block observations per tick, loads existing chunks
  asynchronously without generation, and serializes its single chunk lease with repair
  world work. A surrender's requested outcome is already durable, so restart recovery can
  seal and close it automatically. A timeout has no invented winner: its report is sealed
  while the battle remains `RESOLVING` until an approved ordinary rule or audited admin
  outcome completes it.
- The runtime precomputes membership, open-war-pair, open-battle, and active-battle lookups. The Paper entry listener uses only those published values and the claim index on movement/teleport paths, coalesces per-player attempts behind a bounded gate, and submits durable battle start work off-thread. PVP or other destructive capabilities remain disconnected until their own slices define and enforce them.

## Damage journal

`DamageJournalService` is the durable boundary that runs before the Paper adapter mutates battle land. It accepts framework-free stable IDs, a 3D block coordinate, the state observed on the server thread, actor, claim, and cause.

- Journal identity is battle plus world/X/Y/Z. The first preparation atomically inserts the original state; later breaks, explosions, or placements at that coordinate return the existing row and can never overwrite the baseline.
- Both enemy and owner mutations in either side's land are journalable. This prevents defenders rebuilding during a battle from silently escaping the same pre-battle restoration history.
- Attacker placement is represented by journaling the replaced state—often `minecraft:air`—before placement. Restoring air later removes the attacker-created block.
- Rows retain season, battle, claim, first actor/cause/time, and canonical simple block data. They are immutable in SQL and can be read in bounded cursor pages without loading a city's damage into live runtime memory.
- The service validates an active, unexpired battle, global `WAR` phase, snapshotted participant, claim party, and exact X/Z containment. SQL triggers independently enforce the same durable boundary.
- A prepared result is a single-use handoff, not durable permission. The Paper adapter cancels the original event, prepares the journal off-thread, returns to the server thread, confirms the block still matches the observed state and live battle authorization, then applies at most one mutation. A mismatch aborts rather than overwriting newer world state.
- `SimpleBlockSnapshot` deliberately excludes block-entity payloads. Season One conflict mutation permits only simple, independently mutable building blocks whose relevant state it fully represents. Containers, signs, banners, lecterns, spawners, beds, every other block entity, and cascading/multi-block mutations remain protected. Entity damage is not part of block destruction and requires its own targeted participant-PVP capability.

`DamageReportService` owns the resolution-time readout of that journal. It accepts a complete set of final `SimpleBlockSnapshot` observations only while a battle is `RESOLVING`; it never reads Paper state itself. Each journal row is frozen as already restored or repair-eligible. Eligible rows are categorized as restoring an original block or removing a block placed over an air-like original, producing stable one-coordinate repair units without choosing monetary rates.

Application battle closure requires that immutable report to exist. The admin
force-resolution adapter may force the transition into `RESOLVING`, but it cannot bypass
the report: it uses the same bounded Paper observation and sealing coordinator as
surrender. If a restart occurs after report sealing and before closure, recovery uses the
sealed report without reinterpreting later world changes.
An audited retry may reopen the report-less `CLOSED` state produced by the older admin
adapter, but only for that battle's already-recorded outcome and only when neither party
has another open battle. It then follows the ordinary report-sealing path.

Schema migration 5 stores one sealed report per battle plus its final-state entries. Entries are staged and the summary row atomically seals the complete set; SQL triggers verify exact journal coverage, category/count consistency, battle state, and immutability. An identical retry returns the stored report, while changed observations fail as an explicit conflict. Repository reads page the joined report and journal rows in stable journal order so later repair work can resume without loading an unbounded report.

Schema migration 6 replaces the former leader-only war-declarer trigger with a same-season civilization-member check and adds immutable battle-surrender records. A surrender preserves its leader, civilization, time, and requested opponent victory while the battle remains `RESOLVING` for damage-report sealing. Application validation still requires global declaration permission and an allowed gameplay phase; the database preserves the durable membership and surrender invariants independently of Paper commands.

`RepairJobService` accepts a complete current-world observation set for one closed
battle and one party's claimed damage. It classifies each eligible coordinate as exactly
restored to its original state, still equal to the sealed damaged state and therefore
repairable, or altered/conflicted. A target percentage is an absolute completion target,
rounded up to whole blocks. For example, after a 50% job and 3% exact manual rebuilding,
a 100% request selects and charges only the remaining 47%. Conflicted coordinates are
reported but cannot be selected or overwritten.

Schema migration 8 persists the repair target, observation counts, deterministic selected
change IDs and order, economic snapshot, atomic payment/victor proceeds, lifecycle, result
counts, and durable cursor. Ordinary payments debit the damaged civilization, credit the
other party only when that party won the battle, and leave the remainder as a currency
sink. The configurable victor share may be zero. Admin-sponsored jobs record their actor
and target but have zero cost and proceeds. One open job is allowed per battle and damaged
civilization; idempotency keys prevent duplicate jobs or payments. On startup, a `RUNNING`
job becomes `PAUSED` without advancing its cursor because world completion cannot be
inferred across a stop.

Damage does not lock a coordinate against manual rebuilding. The Paper repair runner uses
the report's sealed final snapshot as an optimistic compare value: it restores only when
the live state still matches, records a conflict/skip otherwise, and never overwrites a
player's later manual change. Falling-block or block-display reconstruction effects may
decorate an accepted repair mutation, but they are never authoritative, may not place
blocks themselves, and must be bounded and restart-clean.

The runner owns one global execution queue and one bounded live-assessment queue. It reads
and writes world state only on the server thread, holds at most one plugin chunk ticket,
loads existing chunks asynchronously without generation, and persists each ordered prefix
before advancing. A solid restoration intersecting a player is deferred without moving the
cursor. Missing worlds/chunks pause the job, and startup converts any interrupted `RUNNING`
job to `PAUSED`; an explicit resume safely re-reads the same cursor and compares again.
Repair lifecycle/cursor storage uses the runtime's serialized worker without rebuilding the
unrelated hot gameplay snapshot after every batch. New paid jobs still refresh that snapshot
because their atomic ledger transaction changes a treasury balance.

## Live runtime

`CivilizationsRuntime` is the owner of runtime state and structured background work.

- Startup migrations and reads run on the storage executor. The plugin remains in a visible `Starting` state until a `Ready` snapshot is published on the server thread.
- Runtime snapshots contain the active season, civilizations, memberships, wars, battles, participant snapshots, a read-only-by-convention claim index, derived active-battle eligibility, and a protection policy built over those values. Each publication replaces the entire snapshot; it never mutates an index while event code may be reading it.
- Startup verifies that an active season is not archived, every active civilization has exactly one leader, every claim has an active owner, no persisted claims overlap, open wars reference active parties, and open battles/participants match their war and trigger claim. Invalid durable state fails closed with actionable IDs instead of partially enabling gameplay.
- Mutations submitted before readiness are rejected. Infrastructure failures move the runtime to `Failed` and disable the plugin rather than falling back to another store.
- Shutdown stops new work and gives the storage executor a bounded drain period. Civilizations no longer cancels scheduler tasks owned by other plugins.

The live `/civadmin` and `/civ` adapters use Paper's `BasicCommand` API, explicit UUIDs for offline roster operations, Adventure messages, and explicit global permissions. `/civadmin` exposes focused setup, war/battle inspection, and repair sponsorship/lifecycle recovery; `/civ` exposes player war operations plus repair status and absolute-target start commands. Both parse and translate only; business decisions remain in application services.

The former commands, listeners, tasks, placeholders, menus, global managers, serializers, compatibility adapters, and datastores were deleted after the cutover. Git history preserves them for reference without allowing a second source of truth to compile or ship.

## Protection policy and Paper coverage

`ProtectionService` is an application-owned, pure policy over the active season's membership map and chunk spatial index. A Paper listener supplies a stable world key, integer X/Z target, actor, action, and explicit admin bypass. The policy returns a reasoned allow/deny value and never calls Paper, Foundation, or persistence.

- Unclaimed coordinates retain vanilla behavior. Members and leaders may mutate their civilization's claims in the configured safe subset of `SETUP`, `PEACE`, and `WAR`; outsiders may not. `FINALE` and `ARCHIVED` always freeze claimed land except for explicit admin bypass.
- PVP in claimed land always requires a conflict capability. Merely putting the season in `WAR` does not authorize anybody to attack or destroy blocks.
- A conflict capability is bound to its actor, kind, allowed actions, eligible claim IDs, and PVP target participants. War capabilities are valid only in `WAR`; assassination capabilities are limited to targeted PVP in `PEACE` or `WAR`. The runtime converts persisted active-battle eligibility into a claim-scoped break/place capability only for the journal-first adapter. It does not grant PVP, entity, container, explosion, or other destructive capabilities.
- No block entity can appear in an MVP conflict capability. Block-break translation must classify and deny containers and other persistent-data blocks so a generic break grant cannot bypass that invariant.
- Explosions remove only claimed blocks from the event's block list. Autonomous fire and entity block changes are denied on claimed targets. Fluids, pistons, hopper transfers, and inventory pickup may move within one ownership area but not between civilizations or across wilderness boundaries.
- While runtime state is loading or failed, mutation listeners fail closed. Once a ready runtime has no active season, events retain vanilla behavior.

The current event matrix is:

| Surface | Paper events/policy | Current behavior |
| --- | --- | --- |
| Blocks | break, place/multi-place, clicked-block interact, bucket fill/empty, player ignition | Exact affected coordinate; owner/admin allowed, outsider denied; active battle participants receive journal-first simple single-block break/place |
| Containers | block interact/break, inventory open, automated move/pickup | Direct access follows membership; automation cannot cross ownership |
| Entities | interact/damage, armor stands, hanging place/break, vehicles, projectiles | Entity coordinate is authoritative; the responsible projectile/TNT player is resolved |
| PVP | entity damage by player or player-shot projectile | Vanilla in wilderness; denied in claims without a targeted conflict capability |
| Environment | entity/block explosions, burn/ignite/fire spread, entity block change | Claimed targets are removed or cancelled |
| Boundaries | fluid flow, piston head/moved blocks, inventory transfer | Every source/destination pair must have the same owner, including wilderness as no owner |
| Movement | horizontal block transitions and teleport | Movement remains physically unrestricted; entering a hostile claim resolves the candidate entirely from published memory, gives immediate phase/permission/battle feedback, and submits an eligible battle start through a bounded/coalesced off-thread path |

Every later war integration must preserve the journal-before-mutation contract before it hands a conflict capability to another destructive Paper path. The visual TNT effect may never become the authoritative mutation.

## Delivery and worktree sequencing

The architecture rework has no remaining slice. Net-new MVP work is split into a durable-feature lane and a Paper-integration lane. Schema/repository migrations are ordered and must not be developed concurrently with another schema slice. Paper plugin/runtime/listener lifecycle changes are likewise serialized. A branch in each lane may proceed concurrently when both use an application-owned port already present on `main`.

[worktree-roadmap.md](worktree-roadmap.md) is the executable feature merge queue. In summary, damage reporting precedes the ledger, the ledger precedes repair jobs, and repair jobs precede the Paper repair runner. The live simple-block Paper war adapter uses only published in-memory authorization on its event path, captures immutable input without loading chunks, and cancels immediately. Pending journal work is bounded and duplicate battle/coordinate attempts are coalesced; saturation fails closed. After durable preparation, the adapter returns to the server thread, revalidates the unchanged block and current capability, and applies at most once. The hostile-entry adapter similarly resolves candidates from memory, rate-limits and coalesces per-player attempts, and performs durable activation through the runtime executor. Queue depth, latency, stale attempts, and backpressure are observable without per-event normal-verbosity logs. Containers, block entities, entities, explosions, and cascading physics remain closed for later explicitly journaled integrations.

## Retired architecture

The 2026 architecture cleanup permanently removed the Foundation lifecycle, command framework, settings/localization framework, menus/conversations, legacy Vault hooks, unrestricted JitPack repository use, coroutine helper, global managers, Towny/Factions adapters, mutable legacy civilization/claim/raid graph, JSON-blob datastore, and all legacy tasks/listeners/commands. A2 later introduced the deliberately narrow, compile-only Vault player-wallet adapter and group-restricted VaultAPI repository described above; it does not restore the legacy economy architecture. `CivilizationsPlugin` is a native `JavaPlugin`; `/civadmin` and `/civ` are native Paper `BasicCommand` adapters; configuration uses Bukkit's configuration API at the Paper boundary; user-facing components use Adventure.

An architecture regression test scans all production sources and the build file for retired framework imports/dependencies. Reusing an old behavior means designing it against the current domain/services and persistence ports, not copying the deleted implementation back into production.
