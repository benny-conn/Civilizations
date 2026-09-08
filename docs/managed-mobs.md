# Managed mobs — S3a durable foundation

S3a implements a shared persistence model, not live population enforcement. Cattle is the
first planned Paper integration. Sheep, chickens, villagers and other selected mobs use
the same identities and lifecycle records. A namespaced species key is not an activation
flag: accepting `minecraft:zombie` does not disable natural zombie spawning or make zombies
breed. No mobs are registered or changed automatically by this migration.

## Boundary and scope

`application/mob` contains immutable values, the lifecycle service and a pure observation
decision. `CivilizationsRepository` owns the persistence contract; JDBC implements it.
Invoke the service on the storage worker. A future Paper adapter must publish a bounded
memory index for event handling, validate live entities on the server thread, and select
explicitly enabled species/world policies. This slice adds no YAML keys or commands.

Persistence means retaining a logical identity and history across saves, unloading and
restarts. It does not mean keeping chunks loaded, forcing all mobs to tick, or making
all of their drops and renewable outputs finite.

| Species integration | Shared foundation | Separate behavior still required |
| --- | --- | --- |
| Cattle (first) | Identity, parent reservations, birth and maturity deadlines, death intent/tombstone | Seeding limits, spawn controls, feeding, milk, native event adapter and recovery |
| Sheep | Same registry | Shearing, wool regrowth, colors and reproduction integration |
| Chickens | Same registry | Egg production, thrown/dispenser eggs and hatch authorization |
| Villagers | Same registry | Food/beds and reproduction, trades, curing/conversion and raid/spawn paths |
| Other selected mobs | Namespaced species identity | Explicit resource/spawn/transform policy and mechanics tests |

The existing manifest's CATTLE zones remain the first habitat integration. Selecting
habitats for other species is future work. Ordinary breeding requires the same species;
transformations and cross-species offspring need explicit audited operations, not silent
identity adoption or a relaxed equality check. Species/entity bindings are immutable now.

## Creation and death protocol

A `MobCreation` contains a unique operation ID and logical mob ID, season, origin world
UUID, species, actor/reason, optional two parent IDs and a frozen `MobRules` snapshot.
Seeding creates adults and requires active SETUP both at prepare and begin-apply. Births
require an active non-archived season, a registered destination world, two alive same-season,
same-species mature parents, expired cooldowns and no existing parent reservations.
The adapter still must check physical proximity, habitat and live identity; SQL has no
live positions. Death facts and reconciliation may finish after season selection changes.

Creation follows `PREPARED → APPLYING → ALIVE`. Only a newly **Applied** result from
`beginSpawn` grants one world application attempt. `Unchanged`, including after a restart,
never grants another spawn. `PREPARED → CANCELLED` is the only definite cancellation;
APPLYING cannot be cancelled or automatically retried. A marked observed entity can finish
an interrupted APPLYING record. Conflicting bindings/species/worlds reject.

Both parents are reserved atomically until acknowledgment or pre-apply cancellation.
A pending birth also prevents either parent from entering a death operation. The adapter
must hold such animals unavailable and reconcile stalled births; it must not retry lethal
world mutation after a rejected prepare. Ambiguous births keep their reservations.

Maturity is measured from durable birth preparation; seeding starts mature. Both duration
values are frozen per creation and bounded to 1 millisecond through 365 days. These are
application safety bounds, not gameplay defaults. Parent cooldown starts at successful
acknowledgment and never shortens; repeated acknowledgment does not extend it again.
Rule edits affect later operations only. S3b must select and validate gameplay defaults.

Death follows a separate `PREPARED → APPLYING → COMPLETED` operation while the mob moves
`ALIVE → DEATH_PENDING → DEAD`. Only Applied from `beginDeath` grants the first completion
attempt. The second native callback must carry the same operation guard. Reconciliation
never grants payout replay. Completed tombstones and old entity bindings remain durable.

The pure `observe` decision returns MANAGED only for an ALIVE matching species/entity
binding. Unknown/conflicting/pending records are quarantined; known-dead matching-species
identities suppress resurrected world entities, including a conflicting new entity UUID.
Absence/unload is not a death transition. No automatic replacement or extinction claim is
based on a missing live UUID lookup.

## Storage and recovery

Migration **15** adds `managed_mobs`, `managed_mob_parent_reservations`, and
`managed_mob_deaths`. Creation IDs, entity UUIDs, parent reservations and one death per mob
are unique. SQL triggers retain history and prevent backward transitions or changes to
identity/rule snapshots. A failed transaction rolls back both parent reservations.

Reads support ID, creation operation, entity UUID, death operation or mob ID. Population
loading uses season-scoped keyset pages capped at 1,000 rows, ordered by UUID text; do not
replace this with a scan per event. Build an index only over needed active state and keep
old tombstones accessible through bounded reconciliation. PREPARED/APPLYING/DEATH_PENDING
are unresolved; CANCELLED is an unapplied creation, not a dead animal. ALIVE includes
unloaded animals. DEAD is durable death history. Population reporting must keep these
categories separate.

SQLite concurrent write contention can fail a worker transaction; the adapter must treat
that as no authorization, not proceed with a world mutation. It may retry the same prepare
identity. No framework, SQL or YAML types enter the application model.

## Verification and next step

Nine real-SQLite tests cover generic species, validation, idempotency/conflicts, both-parent
reservation contention, maturity/cooldown snapshots, cancellation, death/tombstone recovery,
transaction rollback and schema-14 upgrade. Recovery tests open fresh repository/connection
contexts at durable boundaries; they do not claim a full world-plus-SQL crash drill.

Full `clean build` passed all 191 tests. Isolated Paper 26.2-121 upgraded a copied
schema-14 database to 15, passed integrity checks, started with no registered mobs, and
restarted cleanly. The Desktop server was not changed; both servers remain off.

Next is **S3b: opt-in cattle Paper integration**: configuration/activation, one-time seeds,
PDC markers, event gates, bounded queues/indexes, pending-animal containment and observation,
recovery diagnostics and actual SQL/world crash injection. Broader species integrations
follow with their resource-specific tests. See the [mechanics experiment](animal-mechanics-spike.md)
and the [progress/pickup record](scarcity-world-plan.md).
