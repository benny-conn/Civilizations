# Opt-in cattle policy — S3b

Cattle now uses the shared managed-mob registry on Paper. Enforcement defaults OFF until
an admin installs an activation. This is the first species adapter; sheep, chickens,
villagers, mooshrooms and other resource producers still need their own adapters.
This does not establish finite beef/leather supply through every loot/trade/repair route.

## Setup

1. Register the season's worlds and CATTLE habitat boxes through `/civworld import` in
   active SETUP. Include every world intended to receive managed cattle. World registration
   is frozen after cattle activation; UUID **and** world key must match at runtime.
2. Prepare loaded seed locations: solid ground and two clear air blocks, entirely within
   a CATTLE habitat. The plugin never generates or force-loads a chunk for these operations.
3. Put a seed file under `plugins/Civilizations/cattle/`, using the real world UUID:

```yaml
seeds:
  - {id: west-one, world-uuid: '00000000-0000-0000-0000-000000000030', x: 120, y: 64, z: 120}
  - {id: west-two, world-uuid: '00000000-0000-0000-0000-000000000030', x: 122, y: 64, z: 120}
```

4. Run `/civcattle enable seeds.yml`, then `/civcattle seed`. Every command requires
   `civilizations.admin`. The file allows 2–128 uniquely named slots at distinct positions,
   integer coordinates and UUIDs. It is limited to 64 KiB and a simple `.yml` basename;
   paths outside the cattle directory reject. Invalid activation is rejected without
   changing durable state. Seed chunks must already be loaded when applying a slot.
5. Run `/civcattle status`. Repeating `seed` is safe: the same slot uses the same logical
   and operation IDs. ALIVE, APPLYING, CANCELLED and DEAD slots never create replacements.
   With more than 32 simultaneous requests, repeat the command after the worker drains.

Activation and slots are stored in SQL (migration **16**) and cannot be replaced, disabled,
or expanded through config edits. A later reset/rebalancing workflow is separate work.
Deleting the authoring file after activation is harmless. There is no draft/revision or
boundary-preview workflow.

## Settings and behavior

| Config path | Default | Accepted values |
| --- | --- | --- |
| `scarcity.cattle.maturity-seconds` | 43200 (12 hours) | Integer 1–31536000 |
| `scarcity.cattle.breeding-cooldown-seconds` | 21600 (6 hours) | Integer 1–31536000 |

These paths are validated at startup, including existing configs without the section.
Change them and restart **before activation**. Activation freezes them into SQL; existing
activation and operation snapshots remain authoritative after later config edits.
Maturity is measured from durable birth preparation, including offline elapsed time;
parent cooldown begins when the birth is acknowledged. No offline births are simulated.

After activation, ordinary cow spawning is denied server-wide (including spawn eggs,
commands, CUSTOM and natural spawn reasons). Only the coordinator's single live authorized
spawn may pass; PDC alone does not authorize a new spawn. Preexisting/unregistered cows
are contained rather than adopted or killed. A cow's normal AI, gravity, invulnerability,
silence and collision flags are restored after its identity is confirmed.

Two registered mature cows can reproduce anywhere in a registered season world, including
outside their original habitat, when both cooldowns have expired. Habitat boxes constrain
initial seeding. Both parents must remain loaded, in the same world and within four blocks
when applying. Native breeding is cancelled first; the worker reserves both parents and
begins the one permitted spawn attempt. The authorized calf carries logical ID, creation
ID and marker version 1 before its spawn event.

Juvenile feeding cannot accelerate maturity. Pending/unknown cows cannot be fed, milked,
damaged, leashed/unleashed or teleported through the handled events. Healthy adult milk
and transport remain available under ordinary claim protection. Managed birth currently
awards no breeding XP; cancelled vanilla feeding is not refunded. Full player advancement
and killer-credit parity is not claimed. Cow transformations are denied; other species'
resource/transformation routes remain outside this adapter.

## Death and recovery

The initial death is cancelled and the cow held while SQL prepares a death operation.
The guarded completion callback uses the original captured drops/XP instead of rerolling
loot. Only a fresh begin-death result permits that payout attempt. A completed SQL tombstone
suppresses an old saved cow on load without drops. World and SQL saves are independent;
recovery never replays rewards, so an interrupted payout can be lost.

On loading a marked APPLYING birth, the coordinator reconciles that observed entity and
acknowledges its identity. It does not spawn another calf. Parent locks release only after
that acknowledgment. An APPLYING birth with no observable child remains unresolved;
missing unloaded entities are never declared dead. There is no automatic replacement or
admin force-respawn escape hatch in this slice.

Pending deaths stay contained across restart or third-party completion cancellation.
`/civcattle status` lists separate durable states, unresolved logical IDs and contained
loaded entity UUIDs (first 20). Use `/civcattle reconcile <entity UUID>` to refresh an already
loaded entity, then `/civcattle settle-death <entity UUID>` for a confirmed pending death.
Settlement removes that observed entity and completes its existing SQL death **without
rewards**. It never acts on an absent/unloaded UUID. Unknown identities or conflicting
bindings stay contained for inspection; they are not silently adopted into the population.

The coordinator caps outstanding worker calls at 32, tracked loaded entities at 4096 and
startup loaded chunks at 8192 (two chunks per tick). It maintains up to 16 tracked cows per
tick. Overflow entities remain held; load/reconcile them after capacity becomes available.
A staff status request reads durable history in 1000-row pages off-thread; no gameplay
event reads SQL. EntitiesUnloadEvent drops only the live cache entry. The plugin creates
no chunk tickets. This is a bounded prototype, not a measured large-server performance
claim; 4096 tracked cows is a safety cap, not a recommended population.

## Verification

`./gradlew clean build deployTestServerPlugin` passed 194 tests. Isolated Paper 26.2-121
at loopback port 25581 used two seeds and shortened 4-second maturity / 2-second cooldown.
Live native checks verified activation, seed idempotency, unauthorized CUSTOM denial,
unregistered containment, reproduction and wall-clock maturity. A forced halt blocked the
SQL worker after a fourth calf spawned: its world save survived while SQL was APPLYING
and both parents were reserved. Restart reconciled four ALIVE entities/rows and released
both reservations with no second spawn. A second halt after durable death recovered the
older saved cow; the tombstone suppressed it and reseeding did not replace the dead slot.

Chunk unload/reload preserved the same live identities. A PREPARED death survived a
third forced halt; the loaded cow stayed contained, and explicit settlement completed the
same operation without another death callback. One intermediate fixture shutdown failed
when the JAR was overwritten while that JVM was running; the final verification uses
stop-before-deploy and a fresh boot of the final artifact. That final boot/shutdown was
clean, SQL integrity passed, and the two ALIVE/two DEAD records persisted with no unresolved
operations. Restoring config defaults did not change the saved 4s/2s activation rules.

The disposable probe is in `experiments/cattle`; local logs are in
`civilizations-s3b/server/verification`. No player-client playtest or full third-party plugin
compatibility claim is made. Player interaction gates and broader species integration still
need multiplayer coverage during the later integrated playtest. Desktop server was not used.
