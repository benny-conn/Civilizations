# Scarcity and season-world implementation proposal

Research date: 2026-09-07. Baseline: `9a43165`, Paper 26.2 build 121.

Status: **map authoring complete for the prototype; scarcity code not started**.
This develops [server-design.md](server-design.md#regional-scarcity-and-strategic-infrastructure)
and the [scarcity backlog](../TODO.md#scarcity-and-specialization). Gameplay numbers below
remain proposals. See the dated handoff below for actual progress and user decisions.

## Progress and pickup instructions — 2026-09-07

This section records completed work, not merely the intended delivery sequence.
User agreement: update this record after every completed step, including verification,
artifacts and next work; do not wait until the entire slice is finished.

| Work | Status | Evidence / remaining boundary |
| --- | --- | --- |
| Desktop Paper environment | Complete, currently stopped by user request | Paper 26.2 build 121 / Java 25; 18 plugins enabled on last boot. Do not restart merely to inspect progress. |
| WorldPainter installation | Complete | Official 2.27.1 portable Mac app installed at `/Applications/WorldPainter.app`; publisher archive SHA256 verified. Gatekeeper remains enabled. |
| S0 map compatibility sample | Map portion complete | WorldPainter exported all 1,024 chunks of a 512-square world. Multiverse imported it on Paper; spawn, mountain, water and cave block checks passed before/after restart; UUID and 512-block border persisted. User subsequently joined and proceeded to the larger map. |
| S0 managed-animal mechanics experiment | Not started | Birth/death event ordering, cancellation effects and crash windows remain unverified. Do not mark all of S0 complete. |
| 2,048-square authored prototype | Export complete; user accepted overview | All 16,384 chunks verified offline; three settlement spawn columns have solid grass and two air blocks. User explicitly declined a further playtest. Not imported or verified on Paper; acceptance of appearance is not runtime verification. |
| S1 world manifest / resource zones | Recommended next code slice; not started | No scarcity schema, services, commands or activation controls exist yet. Latest user discussion identifies this as the next recommendation, not a completed implementation or instruction to start coding. |
| S2–S6 | Not started | No crop/portal enforcement, registered herds, finite deposits, supply audit or season release. |
| Proximity text chat | Explicitly deferred | Recorded in worktree roadmap; ordinary text chat retains existing behavior. |

### Local artifacts and running environment

These are local desktop artifacts, not committed to Git. A different machine needs the
artifacts transferred or regenerated before continuing map work.

- Server: `/Users/benjaminconn/Desktop/Civilizations Server`.
  Repository `server` is a symlink to it; it appears untracked and must not be committed.
  Use `start-playtest.command` for the pinned build 121 rather than silently replacing it
  with the repository build-112 fixture. `SERVER-READY.md`, `ACTIVE-PLUGINS.json`,
  `STARTUP-VERIFICATION.log`, and current `logs/latest.log` record operations and versions.
- Editable small map and script:
  `/Users/benjaminconn/Desktop/Civilizations WorldPainter Sample/Scarcity-Sample.world`
  and `create-sample.js`. `manifest.json` and `paper-restart-verification.log` record checks.
  Live world key `minecraft:scarcity_sample`; UUID
  `413d14c0-e8f1-47f0-af81-59fe8e77e7f6`; spawn `110.5,75,110.5`.
  Paper migrated the data to the server's `world/dimensions/minecraft/scarcity_sample`.
  `/mvtp scarcity_sample` enters it; `/mvtp world` returns to the mechanics world.
- Larger prototype folder:
  `/Users/benjaminconn/Desktop/Civilizations 2048 Prototype`.
  `Civilizations-2048.world` is editable; `create-prototype.js` reproduces it;
  `heightmap.png`, `Map-Overview.png`, `manifest.json`, and `READ-ME.md` accompany it.
  `exports/scarcity_prototype_2048` is the completed Minecraft export (about 82 MiB of
  region data), not a registered live world. WorldPainter's official `wpscript` was used
  because desktop control could not attach to its Java window.
- Prototype coordinates: x/z `0..2047`, seed `202609072048`, water level 62,
  build range `-64..319`. Sites: Westhaven `440.5,79,520.5`, Eastwatch
  `1570.5,81,630.5`, Southmeadow `1110.5,77,1530.5`.
- `resource-layout.proposed.json` contains six candidate diamond regions, three cattle
  habitats and three cane basins. It is an authoring proposal, **not a supported runtime
  import format**. Marker locations do not mean deposits/animals were placed; ordinary
  resources remain in the export. No exact supply budget has been enforced.

The server plugin stack includes LuckPerms, Staff++, AxGraves, FAWE/WorldGuard, Multiverse,
BlueMap, Plan, voice chat, ProtocolLib, ViaVersion and Grim, plus the existing Civilizations,
BattleLock and economy plugins. ViaBackwards was removed at user request. CoreProtect was
built from unchanged official source commit `b95fb65dde7cb2947acc418267c1d270f9fe83e4`
with Java 25 and `-Dproject.branch=development` because its published 24.0 JAR rejects 26.2;
the source build enabled and initialized FAWE logging through a restart. See server notes
for development-build warnings and exact hashes. Graves never expire; frozen disconnects
do not auto-ban; Grim uses testing permissions; dashboards bind to loopback. These are
startup checks, not comprehensive multiplayer integration tests.

User instructions: keep the server off for now; no backups needed for this disposable
playground; larger-map playtest declined; defer proximity chat. The later season-release
backup/recovery proposal below is not a requirement to back up this current test work.

### Next agent's coding starting point

Recommend a bounded S1 slice: season/world binding, validated application-owned resource
zones and rule revision, durable import through the repository port, immutable spatial
index, and admin inspection/validation. Leave scarcity off until explicitly activated.
Choose the actual import contract rather than treating the proposed JSON as established.
Reconcile the original S1-after-S0 dependency by keeping animal lifecycle implementation
blocked on its unfinished experiment; foundational world/zone work can proceed separately
once assigned. Sugar-cane growth is the recommended first enforcement feature after S1.

Read AGENTS.md and the architecture/roadmap before coding. Use one slice per worktree,
serialize SQL migrations and Paper lifecycle changes, allocate migrations from current
main, and keep hot paths free of SQL. No scarcity code or new schema migration was made
in this task. The documentation was developed on `benny/scarcity-world-plan`. The user requested
merging this handoff into main and pushing it on 2026-09-07; inspect Git history for the
latest progress entry when resuming.

## Recommendation

Build a finite, deliberately authored map with WorldPainter, then enforce a small set of
resource rules inside Civilizations. Use ordinary Minecraft blocks, items and clients.
World generation creates the starting supply; Civilizations governs what may reproduce,
regrow or enter the season afterward. A beautiful terrain pack alone cannot do both.

The first prototype should make three different trades possible: buying diamonds from a
mining region, transporting breeding cattle from an established herd, and importing a
crop that cannot be farmed locally. Basic food, wood, stone and iron remain accessible.
The experiment should create dependencies without making survival depend on another
leader's permission.

Start with the repository's 12-player, three-civilization test assumption. Proposed numbers
below are initial measurements to test, not final season balancing. Keep the current
mechanics world intact and build the resource experiment in a separate staging directory.

## What the research supports

| Option | Evidence and limitations | Proposed use |
| --- | --- | --- |
| WorldPainter | Designed for painting terrain and exporting Java worlds. Its 2.27.0 changelog adds the Minecraft 26.1+ map format, while explicitly distinguishing that from support for every new block. | First choice for a finite continent with intentional rivers, mountain passes and deposits. Validate a small export on our exact Paper build first. |
| Terra | Image samplers can drive geography; configurable feature stages can distribute ores. However, the project's published Modrinth versions currently top out at 1.21.8, including Bukkit 6.6.6-BETA. | Keep as an alternative if a maintained 26.2-compatible artifact is established. Do not assume an old Bukkit JAR works. |
| A versioned generation datapack | Paper exposes initial enabled/disabled datapacks and world-generation settings. | Fallback: a selected vanilla seed plus a narrow datapack and audited preparation pass. More suitable for repeatable procedural regions than exact hand-authored geography. |
| A new custom Kotlin terrain generator | Would put noise, terrain, caves, features and version maintenance on this project. | Defer. Our custom code should enforce the season rules, not recreate a terrain engine. |

Sources: [WorldPainter](https://www.worldpainter.net/),
[WorldPainter changelog](https://www.pepsoft.org/worldpainter/CHANGELOG),
[Terra image configuration](https://terra.polydev.org/config/development/image/index.html),
[Terra ore features](https://terra.polydev.org/config/development/pack-from-scratch/ores.html),
[Terra published versions](https://modrinth.com/plugin/terra/versions), and
[Paper world settings](https://docs.papermc.io/paper/reference/server-properties/).

Compatibility caveats: WorldPainter's main page currently describes the Mac installer as
older than its current portable archive. The changelog also contains an inconsistent future
date on its 2.27.1 entry. Select an actual downloadable artifact, record its checksum, and
prove export/import/spawn behavior rather than treating either page as a tested build.
The initial research did not test a generator; subsequent WorldPainter installation and
512-sample verification are recorded in the progress section above.

## Proposed first resource rules

| Resource | Starting experiment | What creates trade | Important boundaries |
| --- | --- | --- | --- |
| Raw diamonds | Six finite deposits distributed across at least three regions; no automatic replenishment. Select total ore count after a timed mining test with the allowed enchantments. | Unequal mining access and gradual depletion. | Remove unintended ore and loot supply; decide diamond-equipment trades explicitly; repair must not regenerate deposits. |
| Cattle | 24 registered cows, eight in each of three habitats. No new natural cows after activation. Proposed parent cooldown: six real hours; juvenile maturity: twelve real hours. | Physical transport of breeding stock and long-term herd management. | Breeding may succeed outside origin habitats under the proposed policy; cooldowns and maturity are durable. No spontaneous births while nobody plays. |
| Sugar cane | Three river-basin growing zones. Existing cane can be transported, planted and consumed anywhere, but additional growth requires an approved zone. | Renewable regional production of paper and sugar. | Audit natural generation, growth, placement of already-tall plants, piston harvesting and other output sources. This restricts cane production, not every possible paper item unless that stronger rule is chosen. |
| Nether access | Three registered pairs of portals with fixed approved endpoints. | Ports, roads and crossings become strategic infrastructure. | Prototype crossings remain usable and do not charge tolls. Ownership, tolls and closure are later policy decisions. |

Keep basic crops and non-selected animals normal initially. Finite villagers, chickens,
bees, fish and other species follow only after the cattle lifecycle works: containers,
eggs, curing and transformations require different accounting. The initial cow policy must
still address mooshroom conversion and cow imports from every accessible dimension.

The map should offer advantages, not assign each civilization an exclusive resource forever.
Place some sources near starting regions and some in contestable areas; do not put all
sources behind one pass or inside one civilization's easy opening claim. Use physical
travel and current claim rules. Do not add livestock theft or killing exceptions to war:
the existing ordinary-entity protection remains authoritative until a separate raid design.

## Build the map as a versioned artifact

1. **Compatibility sample:** export roughly 512×512 blocks using ordinary materials and
   build height. Load it into a separate Paper 26.2 build-121 fixture, visit caves and edges,
   save, restart and verify the spawn and dimensions. Check that the server genuinely uses
   the exported chunks. If this fails, use the selected-seed/datapack fallback before
   investing in a larger map.
2. **Playable prototype:** author a 2,048×2,048 world with three viable settlement areas,
   navigable water, two or more routes between important regions, and visible geographic
   clues to deposits. That is 16,384 chunks before a buffer. Treat 4,096×4,096 (65,536 chunks)
   as a later season candidate, not a size commitment. Measure travel with real players.
3. **Single source for zones:** version the terrain source, annotated masks and a machine
   readable resource manifest together. Define an explicit block origin, mask resolution,
   inclusive/exclusive edge convention, dimension and optional Y range. Export the same
   geometry into the validator and runtime, rather than redrawing it independently.
4. **Prepare before opening:** generate/export all playable chunks and a measured loading
   buffer beyond the border. Inspect their final resource distribution, including caves,
   structures and containers. A one-time bounded preparation job may replace unintended
   diamond ore or plant deposits, but it must record seed, selection algorithm, count and
   checksum and run only against the explicit unopened staging world.
5. **Seed and audit:** perform a checkpointed initial animal census; then register approved
   seed animals and validate the exact population. Do not simply start cancelling natural
   spawns and assume the exported world contains no animals.
6. **Seal the release:** save the generator version/checksum, seed, masks, datapacks,
   plugin versions, dimension keys/UUIDs, borders, resource counts and initial SQL snapshot.
   Back up the entire stopped server and practice restoration into a separate directory.
   Pin the release for the season. No map re-export over played chunks or automatic world
   upgrades during the season.

WorldPainter exports may already supply the required chunks. Where additional generation
is needed, use a verified compatible pregenerator rather than writing one. Chunky's
[documented commands](https://github.com/pop4959/Chunky/wiki/Commands) support bounded
selection and pause/continuation; its exact 26.2 artifact still needs a staging check.
No claim is made here that a world border by itself prevents background chunk generation.

All accessible dimensions need explicit borders and a supply audit. For the prototype,
keep the End closed; pregenerate the accessible Nether and register both ends of each
crossing. Merely reducing overworld diamonds would leave other dimensions and loot sources
outside the supply model. Retain required generation packs and registries with the world;
pregeneration does not prove they can safely be removed.

## Civilizations implementation

### Zones, rules and activation

Add application-owned resource-zone and season-policy values, persisted through the
existing repository port. Keep world/entity access in Paper adapters. A compiled immutable
chunk index answers location queries; movement, block and spawn events never query SQL.
Keep geographic resource zones separate from civilization claims. Derive political control
from the current claim index rather than persisting a second ownership record.

Draft YAML/JSON is authoring input at the infrastructure boundary. Activation imports a
validated, immutable manifest and rule revision into SQL with its content hash and a world
binding. Reject overlapping same-resource zones unless their semantics are explicitly
defined, invalid coordinates, absent dimensions and conflicting active bindings. An active
world cannot silently acquire another season's policy. Changes require a restart and an
explicit audited revision for future operations; existing births and jobs retain their
snapshots. Ship scarcity disabled for existing worlds, with activation unavailable until
the world-validation checkpoint passes.

Proposed records are a season-world manifest, resource zones, registered animals, animal
life events, pending world operations and portal-site pairs. Do not reserve migration
numbers now; each schema change follows the durable-feature lane on then-current main.

### Cattle: births, deaths and recovery

Give each animal a durable logical ID and a versioned PDC marker linking its current entity
UUID to SQL. PDC is an identity aid, not the authoritative population ledger. Paper supports
entity PDC, but entity persistence and database commits are separate saves; this is a
recovery protocol, not an atomic transaction across Minecraft and SQLite.
[Paper PDC documentation](https://docs.papermc.io/paper/dev/pdc/).

Implement seeding and births as prepare → apply → reconcile operations. Cancel ordinary
birth creation, reserve both parents and the birth identity durably, then revalidate the
parents on the Paper thread and create only the authorized child. Set a durable maturity
deadline and prevent ordinary age acceleration for managed juveniles. Apply maturity when
an animal loads or its bounded deadline task runs. Define and test food, XP and parent
cooldown effects when a birth is rejected; cancelling an event does not prove those effects
are rolled back. The cancellable [EntityBreedEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/EntityBreedEvent.html)
exposes child and parent entities, providing an adapter entry point.

The first technical spike must test delayed death handling as well. Paper 26.2 exposes a
cancellable [EntityDeathEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/EntityDeathEvent.html).
Evaluate temporarily suppressing a managed death, recording it durably, and completing it
on the server thread without duplicating drops, XP or death callbacks. Do not claim strict
finite-population recovery from a fire-and-forget asynchronous death log. Unhandled command
removal, another plugin's deletion or a partial backup restore becomes an explicit anomaly.

On recovery, reconcile identity markers, pending operations and death tombstones as
entities load. An absent entity in an unloaded chunk is not dead; never recreate it merely
because it is not currently visible. An ambiguous applied birth must not retry by spawning
a second child. Quarantine ambiguous animals from reproduction/harvest until a bounded
reconciliation or audited admin resolution completes. Report confirmed alive, known dead
and unresolved counts separately; do not claim extinction while unresolved identities remain.

This is needed because [Paper's spawn reasons](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/CreatureSpawnEvent.SpawnReason.html)
explicitly say `CHUNK_GEN` is no longer called: generated entities can already exist.
Use [EntitiesLoadEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/world/EntitiesLoadEvent.html)
for bounded validation and [EntitiesUnloadEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/world/EntitiesUnloadEvent.html)
only to update presence. After activation, unregistered managed animals are not silently
adopted into the population. Account for plugin/custom spawns, commands, transformations
and cross-dimension transfers; a generic `CUSTOM` spawn reason is not authorization.

### Crops, portals and resource supply

Enforce crop growth from immutable zone policy. Test each actual growth route rather than
assuming one listener covers every plant: natural growth, relevant fertilizer changes,
spread and automation. [BlockFertilizeEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/block/BlockFertilizeEvent.html)
provides a cancellable collection of changed blocks, so checks must consider every target.
Harvesting and transporting existing output should remain legal under ordinary protection.

Portal policy checks both creation and travel. Validate the complete portal shape, origin,
destination, dimensions and linked site, including existing unregistered portals and entity
travel. [PortalCreateEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/world/PortalCreateEvent.html)
and [PlayerPortalEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerPortalEvent.html)
are starting hooks, not proof that every portal/teleport route is covered. Integration tests
must establish the event sequence for existing portals as well. Reject a missing unsafe
endpoint rather than generating an arbitrary new exit.

For diamonds, first validate the physical ore inventory of the unopened world. Ordinary
extraction can then use world persistence; avoid a SQL row for every stone block or every
inventory movement. Keep bounded extraction telemetry separate from an asserted exact
global item balance. Fortune, Silk Touch, placement and existing items complicate such a
balance. Include every resource introduction path in a release checklist:

| Path | Proposed treatment or required test |
| --- | --- |
| Structures, loot tables, trial rewards and replenished containers | Explicitly allow a bounded supply or remove the selected outputs. Audit both already-filled and lazily generated loot. |
| Villager diamond equipment | Proposed restriction in the scarce season if equipment scarcity is the objective. Finite raw diamonds alone do not establish scarce diamond equipment. Keep the distinction visible to players. |
| Wandering traders, species transformations and imported entities | Resource-specific allowlist; retain logical identity for approved transformations. |
| Piston, explosion, fire, plugin edits and rollback tools | Test prevention or accounting at the affected resource, including inside claims and wilderness. Staff rollback must coordinate with the population ledger. |
| Repairs | Resource-bearing blocks must not produce harvested value and later be restored as cheap building damage. Verify both battle and exposure reconstruction. |
| Later chunks, dimension access and border changes | No newly accessible unvalidated supply. Expansion requires an audited new manifest and resource budget. |

The repair concern comes from code inspection, not a reproduced exploit:
`SimpleBattleBlockPolicy` currently identifies physically simple solid blocks rather than
a resource allowlist, and the battle adapter calls `player.breakBlock`. Before enabling
scarce ores, add and test an explicit resource restriction on journaled destruction and
restoration. The initial proposal denies scarce-resource blocks on the battle/exposure
mutation path while leaving ordinary authorized mining outside those paths available.
The rule must also prevent valuable player-placed blocks becoming repair supply and must
be checked against saved reports/jobs. Never silently rewrite historical jobs; unresolved
old jobs need explicit handling before activating scarcity in their world.

Configure and measure Paper's built-in anti-xray for the selected ore palette, and keep
exact deposit masks and seeds in staff-only artifacts. This reduces one information leak;
it is not a guarantee that deposits remain secret. Provide prospecting clues rather than
forcing blind digging. [Paper anti-xray guide](https://docs.papermc.io/paper/anti-xray/).

## Delivery sequence and acceptance gates

These are proposed future slices. Follow one worktree per slice, serialize SQL migrations
and Paper runtime/listener changes, and rebase/build before each handoff. World authoring
can be independent work, but this plan does not assign parallel agents or change the
existing government/economy product sequence.

| Slice | Scope and dependencies | Required result |
| --- | --- | --- |
| S0 — compatibility and mechanics spike | Operations; no production policy. Test WorldPainter export and the managed birth/death event sequence on a separate 26.2 fixture. | Small world survives restart; documented event ordering, cancellation side effects and crash windows; go/no-go for chosen tools. |
| S1 — world manifest and zones | Durable lane, after S0. Application rules, SQL import/activation, spatial index and admin validation/status. | Invalid/overlapping zones and mismatched worlds reject; snapshot recovery and randomized geometry tests pass. |
| S2 — crop and portal enforcement | Paper lane, after S1. Read-only hot-path policy and clear denial messages. | Allowed/denied growth and paired travel work with non-operators; existing portals, automation and restarts cannot bypass rules. |
| S3 — managed cattle lifecycle | Durable lane then Paper lane, after S0/S1. Seeding, birth reservations, maturity, deaths, reconciliation and staff diagnostics. | Duplicate events and crashes at each boundary cannot create a second authorized animal; unload is never mistaken for death; ambiguity is visible and contained. |
| S4 — extraction and repair boundary | Serialized durable/Paper changes as necessary, after S1. Ore preparation, loot/trade decisions, resource exclusions and diagnostics. | No unauthorized new supply in the release audit; adversarial harvest → battle/exposure repair → harvest fails to multiply selected resources. |
| S5 — integrated resource playtest | After S2–S4. 2,048-square map, three civilizations, three resource types and registered portals. | At least two meaningful resource exchanges, successful herd relocation and reproduction, visible depletion, and no permanent basic-food lockout. |
| S6 — season release | After tuning and the existing first-season governance/economy prerequisites. Full map and supply manifest, backups, recovery drill, player guide. | Staff can restore world and SQL together, explain every restriction, and show all acceptance evidence. |

S0 is the next concrete deliverable I recommend. It avoids spending weeks on a map or an
animal registry before proving the two riskiest external boundaries. No production runtime
dependency or world replacement is necessary to complete that spike.

## Playtest measurements and stop conditions

Run an initial 90–120 minute session for geography, crop restrictions and trading, with
separately snapshotted accelerated breeding rules. Then run a 48-hour trial with the
proposed real-time breeding/maturity values. Accelerated results do not validate seasonal
population growth. Use at least two real online accounts for battle/repair interactions.

Measure travel time between settlement areas and sources, time to first useful harvest,
ore depletion, herd births/deaths, reproduction outside origin zones, denial reasons,
pending queue depth and tick cost. Record player exchanges through observed tests or a
future trade/contract system; existing Vault balances cannot reveal all physical trades.

Proposed technical budgets are under 1 ms p99 added scarcity work per tick in the target
load test, bounded queues and reconciliation batches, and zero event-thread SQL. These are
targets to verify on the host, not measured performance claims. Add synthetic tests for
thousands of zones and animal identities, plus real chunk unload/reload and hard-crash
tests in disposable fixtures. Check full-server tick times as well as plugin averages.

Stop expansion if players can replenish diamonds through repairs, births duplicate after
a crash, ordinary chunk loading changes confirmed population totals without an event, or
one group can deny everybody basic survival. Rework geography or rules if most playtime
becomes compulsory hauling with no meaningful decisions. Recovery never secretly respawns
an extinct herd: any introduction is an audited event with a new supply record.

## Decisions to settle before implementing gameplay

The recommended experiment is concrete enough to build a spike, but the following remain
product choices: target population/season length and world size; raw-diamond versus
equipment scarcity; cattle count and real-time breeding rates; whether habitat restricts
birth or just origin; crop choice; End opening; portal ownership/tolls; and extinction
recovery events. The proposed values above are my starting choices for staging, not consent
to enforce them on players. Livestock raids and finite villagers are later expansions.
