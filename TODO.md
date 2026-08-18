# Civilizations roadmap

This document is the product and architecture backlog for turning the current plugin into the foundational gameplay plugin for a small, curated, seasonal Civilizations server.

Audit date: 2026-08-18

## Legend

- Priority: `P0` blocks a trustworthy playtest, `P1` is MVP, `P2` is after the first vertical-slice playtest, and `P3` is a later-season idea.
- Size: `XS` (hours), `S` (roughly a day), `M` (several days), `L` (roughly a week or more), `XL` (a multi-milestone system).
- “MVP” means the smallest complete loop: create/provision civilizations, claim and protect land, fight one declared war, preserve the pre-war state, resolve the war, charge/award money, and visibly reconstruct the damage.

## Architecture rework progress

- [x] Slice 1: add a Foundation-free claim domain, correct rectangle geometry, world/chunk spatial index, and randomized parity tests. The index now serves live protection events.
- [x] Slice 2: add season/civilization/membership domain records, a repository port, versioned relational schema, transactional SQLite implementation, database constraints, and integration tests. This is now the live authoritative store.
- [x] Slice 3: add command-ready application services for season setup and war gating, landless civilization provisioning, membership assignment/leadership transfer, and validated claim placement. The services return structured outcomes and are covered against the real SQLite adapter.
- [x] Slice 4: open/migrate V2 storage at startup, persist/select an active season, serialize mutations on a plugin-owned storage executor, publish copy-on-write server-thread state/indexes, quarantine legacy runtime entry points, and expose focused native Paper admin commands.
- [x] Slice 5: route Paper protection events through a centralized policy backed by the active claim index, use exact affected coordinates and cross-boundary checks, and keep all legacy claim reads off the live path.
- [x] Slice 6: add distinct persisted war and timed-battle state machines, hostile-entry/participant snapshots, deterministic expiry recovery, and an immutable conflict-eligibility read model. Destructive capabilities remain disconnected until Slice 7 can journal mutations first.
- [x] Slice 7: establish the framework-free, first-write-wins damage journal, immutable SQL records, bounded restart-safe paging, and a journal-before-world-mutation application contract. Multiple wars remain possible, but a civilization may participate in only one live battle until overlapping damage has explicit attribution.
- [ ] Slice 8: add immutable damage reports, an idempotent economy ledger, and persisted repair jobs/cursors, then connect a cancel-journal-revalidate-apply Paper mutation adapter. Foundation/legacy removal follows once no retained adapter needs it.

## Recommended direction

- [ ] **[P0][S] Adopt the vertical slice below as the first playable target.** Do not try to finish every existing Towny-style feature before testing warfare and reconstruction.
- [ ] **[P0][S] Treat legacy gameplay data as disposable unless a real dataset is identified.** This was an unfinished development plugin, so a clean versioned schema is preferable to preserving the current serialized object graph. Offer an export or one-time migration only if old data is genuinely valuable.
- [ ] **[P0][S] Keep Paper as the server platform and Kotlin as the implementation language.** A 20–30 player server does not need Folia's threading complexity.
- [ ] **[P0][S] Keep Foundation temporarily, but do not build new core domain or persistence code on Foundation types.** It currently supplies commands, config, menus, conversations, serialization, and economy hooks. Replace it behind subsystem boundaries after the core model is stable; a full removal does not need to block the first implementation milestone.
- [ ] **[P0][S] Make landless civilizations an explicit supported state.** A home and claims must remain optional. An admin-created draft may have no members or leader; an activated civilization must have a valid leader and roster but may still have no land.
- [x] **[P0][S] Separate diplomacy from warfare.** “Enemy” status is not an active war. V2 now gives wars and their timed battles durable identities, parties, rules, roster snapshots, timestamps, and results; damage/repair records follow in Slice 7.
- [x] **[P0][S] Prefer a copy-on-write pre-war journal over eagerly copying every block in a city.** V2 atomically inserts the original state for a battle/3D coordinate and returns the existing immutable row on every later mutation, without scanning untouched land.
- [ ] **[P0][S] Make all game-phase gates centralized and durable.** Use a mode such as `PEACE`, `DECLARATIONS`, `ACTIVE`, and `FINALE` rather than scattering a single boolean through event listeners.

## What exists today

The project compiles and starts on Paper 26.2. The V2 architecture is covered by domain, policy, SQLite, and runtime-restart tests; real event behavior still requires the ignored Paper fixture because mocked tests cannot prove world mutation semantics.

| Area | Present implementation | Readiness |
| --- | --- | --- |
| Civilizations | V2 ID-based records with draft/active/dissolved states and landless provisioning | Live admin path; player-facing creation, homes, descriptions, and economy remain to design/port |
| Membership | Relational one-civilization-per-season membership, offline UUID provisioning, leader transfer | Live admin path; invites/self-service and richer roster inspection are not yet exposed |
| Land | Immutable inclusive rectangles, exact geometry, chunk spatial index, relational rows | Live admin claim/protection path; player selection, unclaim, settlements/colonies, and costs remain |
| Protection | Central policy plus thin V2 listeners for blocks, containers, entities, PVP, fire, explosions, fluids, pistons, and automation boundaries | Live peacetime protection; conflict eligibility and journaling exist, but no destructive war capability is connected before the two-phase Paper adapter |
| Diplomacy | Allies, enemies, outlaws; mutual enemy status implies “warring” | Prototype; no durable declaration or treaty lifecycle |
| Battles | V2 durable war relationship plus timed hostile-entry battle, roster snapshot, terminal result, and expiry recovery; legacy raid/TNT prototype remains quarantined | Architecture core is restart-safe; no Paper entry trigger or destructive capability is live before journaling |
| Damage | V2 immutable per-battle/3D-coordinate rows preserve the first simple block state, actor, cause, claim, and time; legacy `Location` map is quarantined | Durable/restart-safe architecture core; Paper mutation interception, block entities, final-state reports, and repair status remain |
| Reconstruction | Paid, chunked, bottom-up block replacement | Proof of concept; partial percentages, costing, containers, conflicts, and restart recovery are broken or undefined |
| Economy | Vault/Foundation balance hooks, civilization bank, deposits, withdrawals, taxes, upkeep | Partial; persistence and task behavior are unsafe |
| Permissions | Default/outsider/ally/enemy ranks plus custom ranks and plots | Partial; configuration loading has a group-assignment bug and the policy is spread across listeners |
| Utilities | Home, warps, colony teleports, flight, chat, signs, menus, info/list/top | Broad but not playtest-critical; several features should be quarantined until the core loop is stable |
| Persistence | Versioned relational SQLite with prepared statements, transactions, constraints, WAL, and startup integrity checks | Live for seasons/civilizations/memberships/claims/wars/battles/participants/block changes; repair, ledger, and backup tooling remain |
| Seasons/scarcity | Durable active-season selection and `SETUP/PEACE/WAR/FINALE/ARCHIVED` phase controls | Phase gate is live; reset and scarcity systems are not implemented |
| Assassination/occupation/annexation | None | Not implemented |

## MVP acceptance scenario

The MVP is ready for a real 12-player Saturday test when this complete scenario works:

1. An admin provisions three named civilizations, each with a preselected leader and four players, before any civilization owns land. Self-service civilization creation can be disabled.
2. The server begins in `PEACE`. Players claim connected rectangular regions, and protection is correct on edges, negative coordinates, overlapping selections, explosions, fluids, pistons, containers, and cross-border interactions.
3. A restart preserves rosters, leaders, land, homes, balances, the global phase, and all indexes.
4. An admin enables war. One civilization declares war, and a timed battle starts when a member enters the opponent's claimed land. Only snapshotted participants acting in the opposing civilization's claims receive eventual war overrides.
5. Attackers break blocks and use war TNT. The visual falling-block effect is cosmetic; every real world mutation is authorized and journaled before it happens. Repeated changes retain the original pre-war state, and attacker-placed blocks are also reversible.
6. The battle ends deterministically, even if nobody is online. A result and damage summary are saved, destruction stops immediately, and an idempotent ledger records reparations/spoils.
7. The losing civilization pays to repair some or all eligible damage. The correct fraction is charged, restoration is visibly paced, and the task resumes safely after a restart.
8. Admins can inspect, pause, resume, cancel, or force-resolve the battle/repair and can recover from a bad state without editing a database.
9. Automated geometry, policy, persistence, state-machine, restart, and repair tests pass; a scripted local Paper playtest covers the real event integrations.

## Milestone 0 — lock the MVP rules

- [ ] **[P0][S] Write the Season One rules that the code must enforce.** Decide who can create civilizations, whether rosters are locked, who may claim, the number/size of claims, and whether the main settlement must remain connected.
- [ ] **[P0][S] Define claim adjacency.** Recommended MVP rule: rectangles are inclusive, may not overlap, and a new non-colony rectangle must share at least one block-length edge with the existing settlement; corner-only contact does not count.
- [ ] **[P0][S] Decide whether disconnected colonies are in MVP.** Recommendation: omit them from the first playtest. If retained, model settlements/claim groups explicitly instead of bypassing connectivity with a boolean.
- [ ] **[P0][S] Define who can declare and start wars.** The architecture currently accepts leader declaration during `WAR`, prevents duplicate open wars for the same pair, supports multiple political fronts, permits only one live battle per civilization, and models hostile claim entry as the battle trigger. Decide whether Season One adds admin approval or a preparation countdown before exposing commands/listeners.
- [x] **[P0][S] Define the battle land scope.** There is no separate battlefield object: during an active battle, each side's eligible area is the ordinary claimed land of the opposing civilization. The exact destruction policy remains inert until it is coupled to the damage journal.
- [ ] **[P0][S] Define the first victory calculation.** Keep it legible: time limit plus a small set of metrics such as blocks damaged, attacker/defender deaths, surrender, or an admin-set result. Avoid power formulas until the playtest produces evidence for them.
- [ ] **[P0][S] Define repair economics.** Specify cost per eligible block, what fraction becomes victor spoils, whether the balance may go into debt, who may initiate repair, and whether admins can waive costs.
- [ ] **[P0][S] Protect inventory-bearing blocks during MVP wars.** Chests, shulkers, furnaces, and other containers should not be destructible until inventory snapshot/loot/duplication rules exist. Preserve signs/banners and other important block-entity data if they are allowed to break.
- [ ] **[P0][S] Define behavior in unresolved damaged areas.** Recommended MVP rule: lock affected coordinates against normal building until repaired or explicitly abandoned, preventing reconstruction from overwriting new work.
- [ ] **[P1][S] Decide whether raid lives remain in MVP.** Recommendation: use normal death plus inventory drops in the first vertical slice and defer finite lives/respawn delays until the war/repair loop is trustworthy.

## Milestone 1 — trustworthy core and storage

### Domain model

- [ ] **[P0][L] Replace the cyclic mutable object graph with ID-based domain records and services.** At minimum model `Season`, `Civilization`, `Membership`, `Claim`, `War`, `WarParticipant`, `BlockChange`, and `LedgerEntry` separately.
- [ ] **[P0][M] Add explicit lifecycle states.** Suggested examples: civilization `DRAFT/ACTIVE/DISSOLVED`; war `DECLARED/PREPARING/ACTIVE/RESOLVING/REPAIRABLE/REPAIRING/CLOSED/CANCELLED`; season `SETUP/PEACE/WAR/FINALE/ARCHIVED`.
- [ ] **[P0][M] Enforce invariants in one service layer.** A player has at most one civilization; an active leader is a member; names are uniquely normalized; claims have an owner; and one civilization pair cannot have duplicate open wars.
- [ ] **[P0][M] Stop commands and listeners from directly mutating model collections.** Route changes through application services that validate, persist, update indexes, and emit messages/events as one operation.
- [ ] **[P0][S] Use stable world identifiers and integer block coordinates.** Store a Paper world key (with a documented recovery policy), `minX/maxX/minZ/maxZ`, and inclusive bounds rather than serializing Bukkit `Location` objects as domain keys.

### Persistence and recovery

- [ ] **[P0][XL] Create a versioned relational schema and migration runner.** Suggested tables: schema versions, seasons, civilizations, memberships, claims, wars, participants, block changes/repair status, and economy ledger entries.
- [ ] **[P0][M] Replace constructed SQL with prepared statements.** Current JSON, names, and descriptions containing apostrophes can break statements; result sets/statements are also not consistently closed.
- [ ] **[P0][L] Make multi-record mutations transactional.** Membership moves, war start/end, balance transfers, claim changes, and repair progress must not leave half-applied state.
- [ ] **[P0][M] Remove inactivity-based automatic deletion.** `Database.Delete_After: 30` conflicts with persistent history and can delete referenced players/civilizations. Archive explicitly through the season lifecycle instead.
- [ ] **[P0][M] Add idempotent startup recovery.** Rebuild indexes, validate invariants, resume active timers/repairs from persisted timestamps and cursors, and quarantine invalid records with actionable logs.
- [x] **[P0][M] Add orderly shutdown.** V2 stops accepting mutations, drains its single storage executor with a bounded wait, and owns no anonymous/global scheduler cancellation.
- [x] **[P0][M] Establish a thread-ownership rule.** Bukkit/Paper world and entity APIs run on the server thread; database work runs off-thread; refreshed state returns to the server thread before becoming visible.
- [ ] **[P0][S] Replace the unstructured `CoroutineScope(Dispatchers.Default)` calls with a plugin-owned structured scope/executor.** Cancel and join it on shutdown and surface failures.
- [x] **[P0][S] Choose the initial database target.** V2 uses packaged SQLite with WAL, foreign keys, a busy timeout, and serialized writes. The legacy MySQL/SQLite datastore is quarantined rather than extended.
- [ ] **[P0][M] Add backup/export tooling before destructive season or migration operations.** Include dry-run, manifest, database backup, and recovery instructions.

### Current persistence defects to cover with regression tests

- [ ] **[P0][S] Player power is stored in the table but never read/assigned during load.**
- [ ] **[P0][S] Saving a player with no civilization omits the column instead of writing `NULL`, so a departed player can rejoin the old civilization after restart.**
- [ ] **[P0][S] `Claims` writes `Total_Blocks` and reads `Total_blocks`; area totals do not reliably round-trip.**
- [ ] **[P0][S] Civilization taxes are not serialized.**
- [ ] **[P0][S] Active raids, timers, participants, lives, and cooldowns are not serialized.**
- [ ] **[P0][S] A civilization with zero citizens is deleted during load, and leader fallback calls `citizens.iterator().next()`.** This blocks safe draft/empty provisioning.
- [ ] **[P0][S] Async saves can race mutable collections, complete out of order, or still be running when the datastore is closed.**
- [ ] **[P0][S] The current connection/result-set ownership and reconnect path need replacement, not incremental patching.**
- [ ] **[P0][S] Money/power underflow calculations zero the balance/count before calculating the amount to remove, producing incorrect power.**

## Milestone 2 — civilizations, rosters, and land

### Provisioning and membership

- [x] **[P1][M] Add an admin command to create a draft civilization without land, members, or a home.** Names are normalized and IDs do not depend on names.
- [x] **[P1][M] Add offline-safe membership commands using UUID/profile resolution.** The first adapter accepts explicit player UUIDs, so accepted players can be provisioned before their first login.
- [ ] **[P1][M] Add bulk, idempotent season provisioning.** A reviewed YAML/JSON roster manifest or import command should create civilizations, assign members, set leaders, report conflicts, and be safe to rerun.
- [ ] **[P1][S] Add configuration for self-service creation and joining.** Season One can disable `/civ create`, open joining, invites, and leaving while retaining admin roster control.
- [x] **[P1][M] Make moving a player atomic.** Membership is one relational row per player/season; explicit moves update it transactionally and leaders must transfer first.
- [x] **[P1][M] Define leader vacancy behavior.** Drafts may have no leader, active civilizations must have exactly one, and the admin adapter exposes deterministic leadership transfer.
- [ ] **[P1][S] Keep homes optional and fail clearly when absent.** Do not invent a home until a claim exists and a leader/admin sets one.
- [ ] **[P1][S] Add roster inspection and validation commands.** Show UUID, last known name, role, leader, online state, and any invariant violations.

### Claim representation and spatial index

- [ ] **[P0][L] Replace `Region` with an immutable 2D rectangle value.** Normalize inclusive integer bounds once; calculate area as `(maxX - minX + 1) * (maxZ - minZ + 1)` using `Long`; do not hardcode Y `0..256`.
- [ ] **[P0][L] Add a world/chunk spatial index.** Map each overlapped chunk key to candidate claim IDs. A point lookup checks only the point's chunk and then performs exact rectangle containment. Expected cost is `O(k)` for the small number of claims touching that chunk, independent of total server claims.
- [ ] **[P0][M] Keep the authoritative claim repository separate from the derived index.** Rebuild and verify the index at startup; update it atomically after claim add/remove; provide an admin rebuild/check command.
- [ ] **[P0][M] Use exact rectangle intersection for overlap checks.** Query candidates from every chunk touched by the proposed rectangle, then test interval overlap. This catches the cross-shaped overlap that endpoint-only checks miss.
- [ ] **[P0][M] Use analytic adjacency rather than materializing a bounding box.** For edge-only adjacency, one X edge is exactly one block beyond another and the Z intervals overlap, or vice versa.
- [ ] **[P1][M] Represent connected settlements explicitly.** On add, attach the claim to a settlement/claim group; on remove, run a small adjacency-graph traversal and reject or intentionally split disconnected land according to the rules.
- [ ] **[P1][S] Bound claim size/chunk coverage and reject coordinate/area overflow.** The chunk index is appropriate for this server scale when individual rectangles have sane limits.
- [ ] **[P1][M] Make claim creation one atomic operation.** Validate world, selection, overlap, adjacency, limits, cost, player authority, and current phase; then debit, insert, index, and publish the result.
- [ ] **[P1][S] Decide claim mutation rules during war and repair.** Recommended: freeze affected settlements from claim/unclaim until the war and damage ledger close.
- [ ] **[P1][M] Add geometry/property tests.** Cover reversed corners, inclusive edges, single-block rectangles, negative fractional coordinates, different worlds, containment, cross overlaps, edge/corner adjacency, index add/remove/rebuild, and parity with a brute-force reference.
- [ ] **[P1][M] Add a benchmark with thousands of claims and representative point/explosion queries.** Establish a latency/allocation budget before optimizing beyond the chunk index.

### Protection policy

- [x] **[P0][L] Centralize protection in one `ProtectionService`.** Inputs include actor, action, exact target, season phase, conflict capability, and admin bypass and return a reasoned allow/deny value.
- [x] **[P0][M] Make event listeners thin adapters.** V2 listeners translate Paper events into policy actions and apply the decision without claim scans, database reads, or business rules.
- [x] **[P0][M] Define and test an event coverage matrix.** `docs/architecture.md` records the live coverage and intentional movement/teleport pass-through; pure tests cover the policy matrix and boundary cases.
- [x] **[P0][M] Use the affected block/entity location, not the player's feet, for authorization.** Block, entity, projectile target, and inventory endpoints use exact stable-world X/Z coordinates.
- [x] **[P0][M] Make war permissions an explicit override in the same policy.** Capabilities are actor/action/phase/eligible-claim/PVP-target scoped. The runtime publishes battle eligibility and exposes journaling, but deliberately supplies no destructive capability until the two-phase Paper adapter is available.
- [x] **[P1][M] Simplify MVP roles to leader/member/outsider/admin unless playtesting proves custom ranks are necessary.** Protection treats leader/member as owners, all others as outsiders, and uses an explicit operator-default bypass.
- [x] **[P1][S] Default outsiders/enemies to no build, break, switch, or container access.** The centralized live policy is default-deny on claimed land.

### Current claim/protection defects to cover or remove

- [x] **[P0][S] Every live point query uses the world/chunk claim index; legacy all-civilization scans are quarantined.**
- [ ] **[P0][S] `Region.isWithin` uses `Double.toInt()`, which truncates toward zero and is wrong for negative fractional coordinates.**
- [ ] **[P0][S] Area math omits inclusive rows/columns and gives a one-block-wide rectangle zero area.**
- [ ] **[P0][S] Claim overlap checks only endpoints/containment and miss crossing rectangles with no corner inside the other.**
- [ ] **[P0][S] Connectivity and visualization materialize 3D bounding boxes; `Region.blocks` can materialize the entire full-height volume.**
- [ ] **[P0][S] Entity counting loads every chunk touched by a claim and is used in raid ratio logic.**
- [ ] **[P0][S] The global `getPlotFromLocation` overload discards found plots and always returns `null`.**
- [x] **[P0][S] Live interaction protection uses the clicked block, affected entity, or inventory endpoint rather than `player.location`.**
- [ ] **[P0][S] A raider allowed to attack can place arbitrary blocks, while only TNT was intended; placed blocks are not added to damage recovery.**
- [x] **[P0][S] Piston handling checks the head and every moved block from source to destination, including movement out of a claim.**
- [x] **[P0][S] Hanging-entity protection safely resolves players, projectiles, and non-player/environmental removal.**
- [ ] **[P0][S] Unclaiming can orphan homes, plots, colonies, damage records, and disconnected claims.**
- [ ] **[P0][S] Distance helpers do not handle empty sets or different worlds safely.**
- [x] **[P0][S] The live policy no longer loads legacy permission groups; membership and explicit conflict/admin capabilities determine access.**

## Milestone 3 — durable war lifecycle

- [x] **[P1][M] Add the persisted global gameplay phase and admin controls.** `SETUP/PEACE/WAR/FINALE/ARCHIVED` and emergency `WAR -> PEACE` are durable and exposed through `/civadmin`; scheduling, broadcasts, and audit logs remain follow-ups.
- [x] **[P1][XL] Implement persisted war and battle state machines.** A durable war moves through `DECLARED/ACTIVE/CLOSED/CANCELLED`; each timed battle moves through `ACTIVE/RESOLVING/CLOSED/CANCELLED`. Central transitions are timestamp-based and idempotent.
- [x] **[P1][M] Store war parties, declarer, trigger claim/player, rules snapshot, participant snapshot, start/end/resolution timestamps, and terminal result.** Eligible land is derived from the ordinary opposing claims rather than stored as a separate battlefield.
- [x] **[P1][M] Separate declaration from battle activation.** Diplomacy flags no longer substitute for a V2 war record; declaration, war activation, and hostile-entry battle start are separate operations.
- [ ] **[P1][M] Add preparation and clear boundary feedback.** Warn both rosters, show exact start/end time, prevent damage before activation, and make eligible opposing land visible.
- [ ] **[P1][M] Define participation robustly.** Membership, alliances, joining/leaving the zone, disconnects, deaths, and spectators must have explicit behavior; do not infer all participation from whichever claim-enter event happens to fire.
- [ ] **[P1][M] End wars outside player loops.** Runtime startup/refresh now advances expired battles to `RESOLVING` with zero players online and removes active eligibility. Slice 8 still must calculate/persist damage reports, ledger effects, and repair eligibility exactly once.
- [ ] **[P1][M] Add admin recovery commands.** Inspect state and participants, start, pause, resume, force-resolve, cancel/rollback, and clear a stuck war with an audit reason.
- [ ] **[P1][M] Add war restart tests at every transition.** Restart in declaration, preparation, active combat, resolution, and repairable states and assert the same eventual result.
- [ ] **[P1][S] Make balance/power rewards idempotent ledger entries.** A restarted resolution must not pay twice.
- [ ] **[P1][S] Quarantine old war settings until implemented.** `Raid.Buy_In` and `Attacker_Teleport` are loaded but unused; do not expose inert controls.

### Current raid defects to cover or remove

- [ ] **[P0][S] `Raid.onEnd` clears the raid and cancels effects inside the online-player loop; with nobody online, the raid never clears.**
- [ ] **[P0][S] Online/in-raid ratios use integer division.** The configured 3-defender/5-attacker ratio becomes zero and is effectively always valid.
- [ ] **[P0][S] Player counting returns one when the count is zero, masking empty cases and distorting ratios.**
- [ ] **[P0][S] The raid command applies an already-converted seconds value as minutes, producing a much longer command cooldown than configured.**
- [ ] **[P0][S] No deterministic winner, score, damage summary, reparations, or automatic repair eligibility is created at raid end.**
- [ ] **[P0][S] Damage from all wars is mixed into one civilization-level `Damages` object with no war, attacker, season, or status identity.**
- [ ] **[P0][S] Raid lives and death rewards are not transactionally persisted; killer-null and balance edge cases need explicit handling.**

## Milestone 4 — reversible destruction and reconstruction

### Damage journal

- [x] **[P1][XL] Implement a per-battle copy-on-write block-change journal.** Immutable rows are unique by battle/world/X/Y/Z; the first accepted preparation stores the original simple block state and later preparations retain it.
- [ ] **[P1][L] Track the expected damaged state and change source.** Record break/place/explosion/fire/fluid/physics, actor/civilization when available, timestamps, and repair status for auditing and conflict handling.
- [x] **[P1][M] Model attacker placements before placement.** The journal accepts air or an existing simple block as the original state, so reconstruction can remove attacker-placed blocks or restore what they replaced. Live Paper placement interception remains in Slice 8.
- [ ] **[P1][M] Preserve full block state for allowed block entities.** Material/block data alone does not preserve sign text, banner data, inventories, lecterns, spawners, or other tile state. Keep containers protected until safe inventory semantics exist.
- [ ] **[P1][M] Capture before mutating.** Persist or durably queue the original record before allowing destructive world changes so a crash cannot destroy a block without a recovery record.
- [ ] **[P1][M] Make TNT physics visual and bounded.** The authoritative mutation remains in the journal; falling blocks must not place permanent untracked blocks, damage unrelated claims, duplicate drops, or load uncontrolled chunks.
- [ ] **[P1][M] Handle cascading changes.** Explosions, attached blocks, gravity, fluids, and fire can alter blocks outside the initial list; either intercept and journal them or suppress them during MVP wars.
- [ ] **[P1][M] Produce a stable damage report at resolution.** Count eligible blocks by category/cost, exclude no-op/restored-during-war entries, and save the immutable price/reparations basis.

### Repair engine

- [ ] **[P1][XL] Implement a persisted, resumable repair job.** Store requested fraction, eligible change IDs, price, payment/ledger IDs, cursor, state, and completion/error details.
- [ ] **[P1][M] Calculate exact partial repair deterministically.** Select the requested percentage of eligible changes, charge only that selection, and report skipped/conflicted blocks separately.
- [ ] **[P1][M] Restore in a safe visible order with per-tick budgets.** Prefer bottom-up/dependency-aware batches, keep chunks bounded, avoid suffocating players, and expose speed as blocks per tick/second accurately.
- [ ] **[P1][M] Define current-world conflict behavior.** Recommended MVP behavior is to lock damaged coordinates; otherwise only overwrite when the current state matches the journaled damaged state and require admin review for conflicts.
- [ ] **[P1][M] Make payment and victor proceeds transactional and idempotent.** A repair may not charge twice or pay twice after a crash. Support an admin-free repair path for recovery/testing.
- [ ] **[P1][M] Pause/resume cleanly on shutdown, unloaded worlds, or errors.** Do not silently discard handled or unhandled locations.
- [ ] **[P1][M] Add repair tests.** Cover 1%, 50%, 100%, zero funds, repeated damage, attacker placements, preexisting air, protected containers, conflict skips, restart at every cursor, and double-resolution/payment attempts.

### Current damage/repair defects to cover or remove

- [x] **[P0][S] Replace assignment with first-write-wins.** SQL uniqueness plus `ON CONFLICT DO NOTHING` preserves the first V2 snapshot, and update/delete triggers keep journal evidence immutable.
- [ ] **[P0][S] Only destroyed block-data strings are captured; placed blocks, inventories, block entities, entities, fluids, fire, and other changes are not recoverable.**
- [ ] **[P0][S] `percentage / 100` uses integer division, so 1–99% currently repairs zero blocks.**
- [ ] **[P0][S] Repair charges the cost of the full damage list regardless of the requested percentage or skipped blocks.**
- [ ] **[P0][S] Admin repair parses the civilization name as the percentage argument and is effectively broken.**
- [ ] **[P0][S] Repairs are in-memory tasks with no durable cursor, locking, conflict policy, or restart recovery.**
- [ ] **[P0][S] The current falling-block helper can mutate world blocks outside a durable change transaction.**

## Milestone 5 — playtest quality and operations

- [ ] **[P1][L] Add pure unit tests for domain rules and state transitions.** Geometry, memberships, permissions, war eligibility, state transitions, pricing, ledger idempotency, and repair selection should not require a running server.
- [ ] **[P1][L] Add repository integration tests against the selected database.** Include migrations, constraints, rollback, restart recovery, and concurrent/idempotent commands.
- [ ] **[P1][M] Select a maintained Paper-compatible event test approach.** Use it for basic listener/policy wiring, but keep a real local Paper server suite for behaviors mocks cannot represent.
- [ ] **[P1][M] Turn the ignored root `server/` into a repeatable gameplay test fixture.** Add documented seed/setup steps, test operators/players, reset scripts that only target the explicit server test directory, and a checklist for the MVP scenario.
- [ ] **[P1][M] Add scripted smoke/restart checkpoints.** Build/deploy, start, provision, claim, begin war, stop/restart, resolve, repair, restart, and verify database/world state.
- [ ] **[P1][M] Add structured audit logs.** Record admin actions, civilization/membership changes, claim changes, war transitions, damage counts, money transfers, repair progress, and recovery decisions with IDs.
- [ ] **[P1][M] Add timings/metrics around spatial queries, event-policy decisions, explosion recording, database queues, and repair batches.** Avoid logging every block at normal verbosity.
- [ ] **[P1][S] Add a CI workflow for clean build and tests on every push to `main`.** Keep the full Paper gameplay suite optional/nightly if it is too slow for every commit.
- [ ] **[P1][S] Add configuration validation and a generated reference.** Fail startup clearly on unsafe or nonsensical settings instead of silently calculating zero/invalid ratios.
- [ ] **[P1][S] Require an economy provider only when economy-backed gameplay is enabled.** Provide a clear startup error or a built-in ledger-backed economy mode rather than failing in a battle/repair command.
- [ ] **[P1][S] Add a pre-playtest backup/restore drill.** Verify both database and world restoration, not merely backup creation.

## Existing systems to quarantine during the MVP

These features can remain in the repository temporarily, but should be hidden/disabled until they are routed through the new services and tested. They should not expand the first vertical slice.

- [ ] **[P0][M] Add feature flags/default-off registration for plots, colonies, custom ranks, fly, warp signs, upkeep/taxes, maps, public homes, and legacy menus.**
- [ ] **[P0][S] Remove the production `/civ test` command.**
- [ ] **[P0][S] Remove or disable the obsolete Towny/Factions adapter code unless a real migration use case appears.**
- [ ] **[P0][S] Audit every admin subcommand before exposing `/civadmin`.** Several commands have incorrect argument indexes or do not repair both sides of relationships/membership.
- [ ] **[P0][S] Ensure the admin command tree is default-deny and uses an explicit permission.** Bypass permissions must also be explicit and auditable.
- [ ] **[P0][S] Stop cancelling every Bukkit scheduler task during plugin shutdown.** The current loop cancels pending tasks belonging to the whole server, not only Civilizations.
- [ ] **[P0][S] Remove or rewrite the current upkeep/tax task before enabling it.** It runs asynchronously while calling Bukkit/economy APIs, mutates citizen sets during iteration, assumes players are online, and can delete civilizations automatically.
- [ ] **[P0][S] Remove or rewrite the current mob-removal task before using it for scarcity.** A non-monster/nonlocal return can end the entire run early, and its removal set is never cleared.

### Known command/configuration issues

- [ ] **[P2][S] Fix or replace admin claim.** It treats the civilization argument as the action and normally performs no claim.
- [ ] **[P2][S] Fix or replace admin leader and admin `set leader`.** They look up the civilization argument as the player and have confirmation/display problems.
- [ ] **[P2][S] Fix or replace admin permissions.** Its argument counts/indexes do not match its usage.
- [ ] **[P2][S] Make admin delete detach/save every citizen and clear related state transactionally.**
- [ ] **[P2][S] Prevent admin add from placing one player in multiple civilization citizen sets.**
- [ ] **[P2][S] Save all direct mutations consistently.** Leader, home, toggles, permissions, taxes, and other fields currently rely on incidental later saves.
- [ ] **[P2][S] Either implement or remove `inviteOnly`.** It is toggled and displayed but does not govern a public join path.
- [ ] **[P2][S] Clarify `public`.** It currently mainly controls public home teleport, not civilization membership or land access.
- [ ] **[P2][S] Replace deprecated/legacy chat, conversation, menu, and formatting paths as their containing systems are touched.**

## Foundation removal plan

Foundation is widespread enough that stubbing it out now would destroy useful behavior without advancing the game. Remove it incrementally:

- [ ] **[P1][M] Keep all new domain, geometry, policy, war, ledger, and repository modules free of Foundation imports.** This is the critical boundary.
- [ ] **[P1][L] Replace `SerializedMap`/`ConfigSerializable` persistence first.** The cyclic deserialization through global managers is the highest-risk dependency.
- [ ] **[P2][L] Replace settings/localization with typed validated configuration and Adventure components.**
- [ ] **[P2][L] Replace Foundation commands with a modern Paper command layer backed by the same application services.** Commands should contain no business logic.
- [ ] **[P2][M] Use Vault directly behind an `EconomyGateway`, or use the plugin's own ledger if that better fits the server economy.**
- [ ] **[P2][L] Replace menus/conversations only for features that survive playtesting.** Do not port the unfinished 949-line civilization menu wholesale.
- [ ] **[P2][S] Remove shaded Foundation and obsolete adapters once no imports remain; then simplify the shadow JAR and dependency exclusions.**

## After the MVP

### Season system and persistent history

- [ ] **[P2][XL] Implement the season lifecycle.** Setup, opening peace, war phase, finale, freeze, archive, and next-season provisioning should be explicit, persisted transitions rather than a calendar cron job.
- [ ] **[P2][XL] Build an idempotent reset plan with dry-run and backup gates.** Wipe player inventories, Ender Chests, configured containers, economy, memberships/civilizations, transient entities, and seasonal state while preserving buildings/roads/world geography.
- [ ] **[P2][L] Decide which historical artifacts survive.** Books, signs, banners, maps, named items, and hidden treasure need deliberate rules to prevent accidental storage loopholes.
- [ ] **[P2][M] Archive civilization, war, ownership, and map metadata rather than deleting it.** Future seasons should be able to show who built/occupied ruins without giving the old organization current power.
- [ ] **[P2][L] Add an archaeological map/history integration.** Consider BlueMap/Dynmap-style layers or a separate read model after the core records ownership and war history cleanly.
- [ ] **[P2][M] Add finale and season analytics.** Population, trade proxies, wars, deaths, damage, reconstruction, wealth, and territorial history should inform the next ruleset.

### Scarcity and specialization

- [ ] **[P2][L] Design scarcity as a policy system, not a world-wide entity scan.** Choose a small number of strategically meaningful scarce resources for the first experiment.
- [ ] **[P2][XL] Add controlled villager/animal/resource registries and spawning rules.** Preserve intentional sources, enforce caps/regions, handle chunks/restarts, and expose admin diagnostics.
- [ ] **[P2][L] Add anti-bypass rules for farms, breeding, curing, wandering traders, alternate dimensions, loot tables, and generated structures only as each scarce resource requires.**
- [ ] **[P2][M] Add admin seeding/rebalancing tools and telemetry.** Scarcity must create interdependence rather than unknowable grind.
- [ ] **[P3][XL] Consider geographically asymmetric custom resource generation after manual Season One experiments prove which resources create good politics.**

### Death and semi-hardcore play

- [ ] **[P2][L] Add a persisted respawn queue/death state.** Support configurable delays, reconnects, spectators, server restarts, and safe release locations.
- [ ] **[P2][M] Define different death behavior for peace, active war, assassination, and finale phases.**
- [ ] **[P2][M] Add incapacitation/recovery only after ordinary delayed respawn is playtested.**

### Assassination and succession

- [ ] **[P2][L] Design assassination as an explicit scheduled/active event, never as “any kill in claimed land changes the leader.”** Define eligible attacker/target, consent/phase, warning, location/window, equipment, guards, cooldown, offline behavior, and success/failure.
- [ ] **[P2][M] Reuse the central protection/war override mechanism with a distinct `ASSASSINATION` conflict context.** Only the event's valid participants should receive PVP permission.
- [ ] **[P2][M] Make throne transfer atomic and auditable.** Validate the successor's membership, update leadership once, handle disconnect/restart/death races, and provide admin reversal.
- [ ] **[P2][M] Decide political consequences.** Possible outcomes include direct succession, an election window, temporary regency, loss of treasury access, or civil-war eligibility; test the least destructive version first.

### Occupation, taxation, and annexation

- [ ] **[P3][XL] Add occupation before permanent annexation.** A decisive victory can create a timed tax/resource obligation or occupier permissions while the original civilization retains ownership.
- [ ] **[P3][L] Model original owner, current controller, occupation terms, protected capital, and expiration separately.** Do not overwrite a claim's history with a single owner field.
- [ ] **[P3][L] Allow annexation only under explicit season rules, repeated/decisive victories, or inactivity/abandonment.** Include limits that prevent one loss from erasing a community.
- [ ] **[P3][M] Add restoration/rebellion/treaty paths so occupation creates gameplay rather than a terminal failure state.**

### Community and integrations

- [ ] **[P2][M] Treat Simple Voice Chat as an external required client/server mod for the community, with only an optional Civilizations integration.** Possible hooks include war radio restrictions, diplomatic zones, or status display; voice transport does not belong in this plugin.
- [ ] **[P2][M] Add whitelist/roster import from the accepted-player workflow if manual provisioning becomes burdensome.** The application/waitlist website itself should remain a separate system.
- [ ] **[P3][L] Add treasure/finale events as season-specific modules using the global phase and audit infrastructure.**
- [ ] **[P3][L] Add treaties, trade contracts, vassalage, and richer diplomacy only after the base war incentives are understood.**

## Explicitly not required for the first playtest

- Automated three-month resets
- Permanent annexation
- Assassination/throne combat
- Custom world generation or broad resource scarcity
- A web application or public server listing
- Folia, Velocity, or a multi-server network
- Full custom-rank/plot/colony/menu parity
- A full Foundation removal
- Monetization features

The first playtest succeeds if the civilization → claim → declared battle → reversible destruction → paid reconstruction loop is understandable, safe after restarts, and fun enough that players want another round.
