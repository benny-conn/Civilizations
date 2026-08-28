# Civilizations roadmap

This document is the product and architecture backlog for turning the current plugin into the foundational gameplay plugin for a small, curated, seasonal Civilizations server.

Audit date: 2026-08-18

Product decision update: 2026-08-28

## Legend

- Priority: `P0` blocks a trustworthy playtest, `P1` is MVP, `P2` is after the first vertical-slice playtest, and `P3` is a later-season idea.
- Size: `XS` (hours), `S` (roughly a day), `M` (several days), `L` (roughly a week or more), `XL` (a multi-milestone system).
- “MVP” means the smallest complete loop: create/provision civilizations, claim and protect land, fight one declared war, preserve the pre-war state, resolve the war, charge/award money, and visibly reconstruct the damage.

## Architecture rework — complete

- [x] Slice 1: add a Foundation-free claim domain, correct rectangle geometry, world/chunk spatial index, and randomized parity tests. The index now serves live protection events.
- [x] Slice 2: add season/civilization/membership domain records, a repository port, versioned relational schema, transactional SQLite implementation, database constraints, and integration tests. This is now the live authoritative store.
- [x] Slice 3: add command-ready application services for season setup and war gating, landless civilization provisioning, membership assignment/leadership transfer, and validated claim placement. The services return structured outcomes and are covered against the real SQLite adapter.
- [x] Slice 4: open/migrate V2 storage at startup, persist/select an active season, serialize mutations on a plugin-owned storage executor, publish copy-on-write server-thread state/indexes, quarantine legacy runtime entry points, and expose focused native Paper admin commands.
- [x] Slice 5: route Paper protection events through a centralized policy backed by the active claim index, use exact affected coordinates and cross-boundary checks, and keep all legacy claim reads off the live path.
- [x] Slice 6: add distinct persisted war and timed-battle state machines, hostile-entry/participant snapshots, deterministic expiry recovery, and an immutable conflict-eligibility read model. Destructive capabilities remain disconnected until Slice 7 can journal mutations first.
- [x] Slice 7: establish the framework-free, first-write-wins damage journal, immutable SQL records, bounded restart-safe paging, and a journal-before-world-mutation application contract. Multiple wars remain possible, but a civilization may participate in only one live battle until overlapping damage has explicit attribution.
- [x] Slice 8: replace the remaining Foundation lifecycle/command hook with native Paper, delete the entire quarantined legacy source/resources, remove Foundation/Vault/JitPack/coroutines from the build, add a regression guard, and verify a clean build and Paper restart.

There are no remaining architecture-rework slices. Everything below is net-new gameplay, product, testing, or operations work on the completed architecture.

## MVP delivery sequence

- [x] A1: add immutable damage reports and a deterministic final-state/cost basis.
- [x] B1: connect the performant cancel → journal → revalidate → apply Paper mutation path for simple battle block changes, with in-memory authorization, bounded backpressure, and no event-thread SQL or chunk loading.
- [x] B2: connect hostile-claim-entry activation, player declaration/surrender, safely mutable political-war rosters, and focused admin war/battle recovery operations.
- [x] A2: add exact civilization accounts, an immutable idempotency-keyed ledger, validated repair-economics YAML, and a durable Vault player-wallet bridge whose ambiguous operations require reconciliation instead of retry.
- [ ] A3: add persisted repair jobs, deterministic partial selection, and restart-safe cursors.
- [ ] B3: connect the bounded Paper repair runner.

Parallel agents should use [docs/worktree-roadmap.md](docs/worktree-roadmap.md). It defines the two serialized feature lanes, work that can begin immediately, dependencies, suggested branches, and merge order.

## Recommended direction

- [x] **[P0][S] Adopt the vertical slice below as the first playable target.** Architecture slices and acceptance criteria now prioritize warfare and reconstruction over legacy Towny-style breadth.
- [x] **[P0][S] Treat pre-rework gameplay data as disposable unless a real dataset is identified.** The live system uses a versioned relational database; no speculative object-graph migration is on the MVP path.
- [x] **[P0][S] Keep Paper as the server platform and Kotlin as the implementation language.** The modernized build and local fixture target Paper directly.
- [x] **[P0][S] Remove Foundation and the retired implementation.** Lifecycle and commands are native Paper; legacy source/resources and obsolete dependencies are deleted rather than stubbed or ported.
- [x] **[P0][S] Make landless civilizations an explicit supported state.** Drafts may be empty; activation requires a leader and roster but no home or claim.
- [x] **[P0][S] Separate diplomacy from warfare.** “Enemy” status is not an active war. Wars and timed battles have durable identities, parties, rules, roster snapshots, timestamps, and results.
- [x] **[P0][S] Prefer a copy-on-write pre-war journal over eagerly copying every block in a city.** The journal atomically inserts the original state for a battle/3D coordinate and returns the existing immutable row on later mutation attempts without scanning untouched land.
- [x] **[P0][S] Make all game-phase gates centralized and durable.** Civilizations persists `SETUP/PEACE/WAR/FINALE/ARCHIVED`; services and protection policy consume that single state.

## What exists today

The project compiles and starts on Paper 26.2. The architecture is covered by domain, policy, SQLite, dependency-boundary, and runtime-restart tests; real event behavior still requires the ignored Paper fixture because mocked tests cannot prove world mutation semantics.

| Area | Present implementation | Readiness |
| --- | --- | --- |
| Civilizations | ID-based records with draft/active/dissolved states and landless provisioning | Live admin path; player-facing creation, homes, descriptions, and economy remain to design |
| Membership | Relational one-civilization-per-season membership, offline UUID provisioning, leader transfer | Live admin path; invites/self-service and richer roster inspection are not yet exposed |
| Land | Immutable inclusive rectangles, exact geometry, chunk spatial index, relational rows | Live admin claim/protection path; player selection, unclaim, settlements/colonies, and costs remain |
| Protection | Central policy plus thin Paper listeners for blocks, containers, entities, PVP, fire, explosions, fluids, pistons, and automation boundaries | Live protection plus journal-first simple block break/place for active battle participants; PVP, entities, block entities, explosions, and cascading destruction remain closed |
| Diplomacy | Durable war declarations and lifecycle | Player declarations and focused admin operations are live; alliances, enemies, and treaties remain net-new features |
| Battles | Durable war relationship plus timed hostile-entry battle, roster snapshot, terminal result, and expiry recovery | Hostile-entry activation, leader surrender, admin force-resolution, and simple battle block mutation are live; PVP, deaths, elimination, and ordinary victory/timeout outcomes remain |
| Damage | Immutable per-battle/3D-coordinate rows preserve the first simple block state, actor, cause, claim, and time; sealed resolution reports freeze final states, eligibility, and neutral repair-cost categories | Durable/restart-safe reporting core plus bounded simple break/place interception; explosions, block entities, repair jobs, and repair status remain |
| Reconstruction | None | Must be implemented as persisted repair jobs and a bounded Paper runner |
| Economy | Exact fixed-point civilization accounts, immutable idempotent ledger postings, opening balances, player deposit/leader withdrawal commands, and durable reconciliation state | Civilizations SQL is authoritative for civilization treasuries; Vault is an optional narrow bridge to an external plugin authoritative for player wallets. Repair-job charging and casualty economics remain |
| Permissions | Central leader/member/outsider/admin protection policy | Live for claims; richer ranks/plots are intentionally absent from the MVP |
| Player utilities | None beyond focused administration | Homes, player claim UX, chat, signs, warps, and menus are net-new only if product-prioritized |
| Persistence | Versioned relational SQLite with prepared statements, transactions, constraints, WAL, and startup integrity checks | Live through schema 7 for seasons/civilizations/memberships/claims/wars/battles/participants/block changes/damage reports/accounts/ledger/player-wallet bridge; repair jobs and backup tooling remain |
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

- [ ] **[P0][S] Write the remaining Season One rules that the code must enforce.** Global command permissions may be granted through LuckPerms, but civilization-scoped authority and lifecycle safety remain plugin-owned. Rosters may change during `WAR`; a battle's participant/side snapshot remains immutable, so a side switch affects only later battles. Claim-group limits, progression tiers, and the initial player-facing command grants still need exact defaults.
- [ ] **[P0][S] Define claim adjacency.** Recommended MVP rule: rectangles are inclusive, may not overlap, and a new non-colony rectangle must share at least one block-length edge with the existing settlement; corner-only contact does not count.
- [x] **[P0][S] Decide whether disconnected colonies are part of the product model.** A civilization may own a configurable number of disconnected claim groups. Rectangles within one group must remain connected; founding another group requires a configurable, substantial treasury payment and may require configurable membership and/or balance thresholds. Model groups explicitly rather than bypassing connectivity with a boolean. Exact Season One defaults may initially restrict civilizations to one group.
- [x] **[P0][S] Define who can declare and start wars.** A civilization member with access to the declaration command may declare without admin approval or a preparation countdown during `SETUP`, `PEACE`, or `WAR`. Declarations are durable but cannot produce a battle outside the global `WAR` phase. Changing the global phase to enable or stop battles requires a dedicated permission that defaults to operators/admins. The service and Paper adapters enforce this rule.
- [x] **[P0][S] Define the battle land scope.** There is no separate battlefield object: during an active battle, each side's eligible area is the ordinary claimed land of the opposing civilization. B1 couples supported simple block changes to the damage journal; owner changes in either side's land use the same journal path so manual rebuilding cannot escape the pre-war history.
- [ ] **[P0][S] Define the first battle victory calculation.** Wars have no winner or loser; each battle may. A civilization's current leader may surrender its side, and force-resolution is an audited admin recovery operation. Decide the ordinary death/elimination and timeout rules plus participant/disconnect behavior. Keep physical damage out of the victory score so destruction is a cost to manage rather than the objective.
- [x] **[P0][S] Define repair economics.** Starting civilization balance, restore-original/remove-placement unit prices, victor share, debt policy, and ordinary initiator roles are validated YAML settings with defaults of `0`, `1`, `1`, `25%`, `false`, and leader-only. Effective values are snapshotted into each durable repair job. An admin repair is not a configurable waiver: the admin command targets a civilization and invokes the same application operation as an audited, payment-free admin-sponsored repair, which produces no victor proceeds.
- [x] **[P0][S] Protect block entities and entities during MVP wars.** B1 permits only simple, non-block-entity building blocks whose complete relevant state fits `SimpleBlockSnapshot`. Containers, signs, banners, lecterns, spawners, beds, and every other block entity remain protected; conflict capabilities do not authorize entity damage other than separately modeled participant PVP. Unsafe cascading or multi-block changes remain suppressed until every affected coordinate can be journaled first.
- [x] **[P0][S] Define behavior in unresolved damaged areas.** Manual rebuilding remains allowed. A repair runner may mutate a coordinate only when its current state still exactly matches the final damaged state sealed in the battle report; a later player edit becomes a reported conflict/skip and is never overwritten.
- [ ] **[P1][M] Define Season One battle lives and post-death behavior.** Evaluate one-life elimination with normal respawn outside the battle, loss of that battle's PVP/destruction capability, a short reconnect grace period, and optional teammate-locked spectating. Do not grant unrestricted spectator free-camera access. Make the state durable and restart-safe if it enters MVP.

### Proposed Season One battle outcome — awaiting approval

- Snapshot the durable political-war rosters for history, plus a smaller combatant set from eligible online members when the battle starts. Require at least one combatant on each side so an offline civilization cannot be auto-defeated.
- Give each combatant one life for that battle. On death, respawn normally at a safe home/spawn, remove that battle's entry/PVP/destruction capability, and optionally offer a teammate-locked camera only after it can be enforced without free-camera information leaks.
- Give disconnects a short, configurable reconnect grace period, then count the player as eliminated. Persist the deadline so restart or reconnect cannot reset it.
- The attacker wins by eliminating all defending combatants before the deadline. The defender wins by eliminating all attackers or holding until the deadline. Simultaneous elimination is a draw. Block damage is never victory score.
- A current civilization leader may surrender, immediately producing a loss for that side. Admin force-resolution must name an explicit `ATTACKER_WIN`, `DEFENDER_WIN`, `DRAW`, or `CANCELLED` outcome and an audit reason; it does not run a hidden score formula.
- Recommended economic baseline: every death creates one idempotent charge against the dead player's civilization, with a higher configurable attacker price and lower configurable defender price. Treat death costs as a currency sink rather than a direct opponent payment to reduce kill-farming incentives; keep repair prices and victor repair share separate.

## Milestone 1 — trustworthy core and storage

### Domain model

- [x] **[P0][L] Replace the cyclic mutable object graph with ID-based domain records and services.** Implemented concepts are separate records; `LedgerEntry` and repair records will be added as net-new feature models rather than fields on a graph.
- [x] **[P0][M] Add explicit lifecycle states to implemented aggregates.** Civilization, war, battle, and season transitions are explicit; repair lifecycle arrives with the net-new repair-job feature.
- [x] **[P0][M] Enforce invariants in one service layer.** Membership, leadership, normalized names, claim ownership, and duplicate-open-war constraints are centralized and backed by SQL constraints where possible.
- [x] **[P0][M] Stop live commands and listeners from directly mutating model collections.** Paper adapters route mutations through services and publish a replacement runtime snapshot/index after durable success.
- [x] **[P0][S] Use stable world identifiers and integer block coordinates.** Claims and damage rows use stable world keys and inclusive integer coordinates rather than Bukkit `Location` domain keys.

### Persistence and recovery

- [x] **[P0][XL] Create a versioned relational schema and migration runner.** Current aggregates have normalized tables and ordered migrations; ledger and repair slices will extend the same mechanism.
- [x] **[P0][M] Replace constructed SQL with prepared statements.** All repository access is prepared and scoped; the retired datastore is deleted.
- [x] **[P0][L] Make multi-record mutations transactional.** Current membership, claim, war, battle, and journal operations are transactional; future ledger/repair features must use the same transaction boundary.
- [x] **[P0][M] Remove inactivity-based automatic deletion from the live system.** Civilizations archives through lifecycle state; the auto-deleting datastore is deleted.
- [x] **[P0][M] Add idempotent startup recovery for implemented state.** Startup migrates, validates, rebuilds indexes, and advances expired battles; persisted repair recovery belongs to the repair-job feature.
- [x] **[P0][M] Add orderly shutdown.** The runtime stops accepting mutations, drains its single storage executor with a bounded wait, and owns no anonymous/global scheduler cancellation.
- [x] **[P0][M] Establish a thread-ownership rule.** Bukkit/Paper world and entity APIs run on the server thread; database work runs off-thread; refreshed state returns to the server thread before becoming visible.
- [x] **[P0][S] Replace unstructured background work with a plugin-owned executor.** The runtime serializes storage work, surfaces failure, stops submissions, and drains on shutdown; the legacy coroutine implementation is deleted.
- [x] **[P0][S] Choose the initial database target.** Civilizations uses packaged SQLite with WAL, foreign keys, a busy timeout, and serialized writes. The former MySQL/SQLite datastore was deleted.
- [ ] **[P0][M] Add backup/export tooling before destructive season or migration operations.** Include dry-run, manifest, database backup, and recovery instructions.

The former JSON/SQL hybrid datastore and all of its known persistence defects were removed with the retired object graph. New persistence regressions belong beside the relational repository feature that introduces them.

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
- [ ] **[P2][L] Add civilization-owned custom roles and granular capabilities.** Leaders may create named roles such as `Knight` and grant plugin-owned actions such as claiming or managing settlement PVP. LuckPerms gates global command access/admin authority; it does not replace durable civilization-scoped role assignments. Start with commands and add an inventory GUI over the same application service rather than putting policy in menu handlers.

### Claim representation and spatial index

- [x] **[P0][L] Replace live `Region` use with an immutable 2D rectangle value.** Current geometry normalizes inclusive integer bounds, uses `Long` area, and has no hardcoded Y range.
- [x] **[P0][L] Add a world/chunk spatial index.** Point lookup checks only chunk candidates and exact containment, independent of total claim count.
- [x] **[P0][M] Keep the authoritative claim repository separate from the derived index.** Startup and every successful mutation rebuild and atomically publish derived runtime state; a dedicated admin rebuild command remains optional operations work.
- [x] **[P0][M] Use exact rectangle intersection for overlap checks.** Candidate filtering and interval intersection cover cross-shaped overlaps.
- [x] **[P0][M] Use analytic adjacency rather than materializing a bounding box.** Edge-only adjacency uses interval math and excludes corner contact.
- [ ] **[P1][L] Represent connected claim groups explicitly.** On add, attach the rectangle to a claim group; rectangles within a group must connect by an edge. Founding a disconnected group atomically validates the configured group limit, membership/balance tier, authority, and establishment price before debiting the civilization and inserting the group and first rectangle. Falling below a later threshold must not silently delete existing land.
- [x] **[P1][S] Bound claim size/chunk coverage and reject coordinate/area overflow.** Claim services validate configurable area/count limits and checked geometry before index insertion.
- [ ] **[P1][M] Make claim creation one atomic operation.** Validate world, selection, overlap, adjacency, limits, cost, player authority, and current phase; then debit, insert, index, and publish the result.
- [ ] **[P2][M] Design civilization progression as validated tiers.** Prefer explicit, inspectable member-count and treasury thresholds for additional claim groups and other benefits over arbitrary YAML expressions. Snapshot or audit the effective rule used for every durable purchase so later configuration edits do not reinterpret it.
- [ ] **[P1][S] Decide claim mutation rules during war and repair.** Recommended: freeze affected settlements from claim/unclaim until the war and damage ledger close.
- [x] **[P1][M] Add geometry/property tests.** Deterministic and randomized parity tests cover normalized/inclusive geometry, negative coordinates, overlap, adjacency, and index add/remove/rebuild behavior.
- [ ] **[P1][M] Add a benchmark with thousands of claims and representative point/explosion queries.** Establish a latency/allocation budget before optimizing beyond the chunk index.

### Protection policy

- [x] **[P0][L] Centralize protection in one `ProtectionService`.** Inputs include actor, action, exact target, season phase, conflict capability, and admin bypass and return a reasoned allow/deny value.
- [x] **[P0][M] Make event listeners thin adapters.** Paper listeners translate events into policy actions and apply the decision without claim scans, database reads, or business rules.
- [x] **[P0][M] Define and test an event coverage matrix.** `docs/architecture.md` records the live coverage, including unrestricted movement with hostile-entry detection; pure tests cover the policy matrix and boundary cases.
- [x] **[P0][M] Use the affected block/entity location, not the player's feet, for authorization.** Block, entity, projectile target, and inventory endpoints use exact stable-world X/Z coordinates.
- [x] **[P0][M] Make war permissions an explicit override in the same policy.** Capabilities are actor/action/phase/eligible-claim/PVP-target scoped. The runtime converts active battle eligibility into only the break/place capability consumed by the journal-first Paper adapter; unrelated destructive actions remain closed.
- [x] **[P1][M] Simplify MVP roles to leader/member/outsider/admin unless playtesting proves custom ranks are necessary.** Protection treats leader/member as owners, all others as outsiders, and uses an explicit operator-default bypass.
- [x] **[P1][S] Default outsiders/enemies to no build, break, switch, or container access.** The centralized live policy is default-deny on claimed land.

The former `Region`, plot, visualization, all-civilization scan, and raid-ratio implementations were deleted. Current claim geometry, indexing, and protection behavior are covered by property/policy tests; new unclaim, settlement, and war-placement behavior remains explicitly listed as feature work above.

## Milestone 3 — durable war lifecycle

- [x] **[P1][M] Add the persisted global gameplay phase and admin controls.** `SETUP/PEACE/WAR/FINALE/ARCHIVED` and emergency `WAR -> PEACE` are durable and exposed through `/civadmin`; scheduling, broadcasts, and audit logs remain follow-ups.
- [x] **[P1][XL] Implement persisted war and battle state machines.** A durable war moves through `DECLARED/ACTIVE/CLOSED/CANCELLED`; each timed battle moves through `ACTIVE/RESOLVING/CLOSED/CANCELLED`. Central transitions are timestamp-based and idempotent.
- [x] **[P1][M] Store war parties, declarer, trigger claim/player, rules snapshot, participant snapshot, start/end/resolution timestamps, and terminal result.** Eligible land is derived from the ordinary opposing claims rather than stored as a separate battlefield.
- [x] **[P1][M] Separate declaration from battle activation.** Diplomacy flags do not substitute for a war record; declaration, war activation, and hostile-entry battle start are separate operations.
- [x] **[P1][M] Add immediate activation and clear boundary feedback.** There is no admin approval or preparation countdown. Eligible hostile entry atomically activates a declared war and starts its battle, both snapshotted rosters receive the exact end time, entry outside active `WAR` receives immediate feedback, and damage remains closed without active eligibility. A richer visual border remains optional UX work.
- [ ] **[P1][M] Define participation robustly.** Membership may change during a political war, but an active battle's participant and side snapshots never change. Joining/leaving the zone, side switches, disconnect grace, deaths, elimination, and teammate-locked spectating need explicit behavior; do not infer all participation from whichever claim-entry event happens to fire.
- [ ] **[P1][M] End battles outside player loops.** Runtime startup/refresh advances expired battles to `RESOLVING` with zero players online and removes active eligibility. The containing war remains a winnerless political relationship until separately closed or cancelled. A1 now seals damage reports exactly once; A2–A3 still must persist ledger effects and repair eligibility before resolution is fully orchestrated.
- [ ] **[P1][M] Persist battle casualties and apply idempotent economic consequences.** Death records need stable identities so restart/retry cannot charge twice. If the proposed Season One model is approved, use separately configurable attacker and defender death costs, charge the dead player's civilization, and treat the charge as a currency sink. Keep casualty economics separate from physical repair pricing and battle victory.
- [ ] **[P1][M] Complete admin recovery commands.** B2 provides war/battle list and inspection, explicit war activation/closure/cancellation, battle force-resolution/cancellation, and required logged audit reasons. Pause/resume, participant detail, rollback, durable structured audit records, repair controls, and a complete stuck-state workflow remain.
- [ ] **[P1][M] Add war restart tests at every transition.** Restart in declaration, preparation, active combat, resolution, and repairable states and assert the same eventual result.
- [x] **[P1][S] Provide idempotent ledger primitives for balance/reward effects.** Exact immutable postings and caller-owned idempotency keys are live; A3/battle orchestration still must invoke them for each concrete repair or reward policy.
- [x] **[P1][S] Retire old war settings.** The unused `Raid.Buy_In`, `Attacker_Teleport`, and their legacy settings framework were deleted; new controls require implemented behavior and validation.

The former in-memory raid, ratio, countdown, lives, and civilization-level damage implementations were deleted. Missing result, report, ledger, and repair behavior is net-new work in the current war/damage model.

## Milestone 4 — reversible destruction and reconstruction

### Damage journal

- [x] **[P1][XL] Implement a per-battle copy-on-write block-change journal.** Immutable rows are unique by battle/world/X/Y/Z; the first accepted preparation stores the original simple block state and later preparations retain it.
- [ ] **[P1][L] Track the expected damaged state and change source.** Record break/place/explosion/fire/fluid/physics, actor/civilization when available, timestamps, and repair status for auditing and conflict handling.
- [x] **[P1][M] Model attacker placements before placement.** The journal accepts air or an existing simple block as the original state, so reconstruction can remove attacker-placed blocks or restore what they replaced. The live Paper adapter captures that replaced state before replaying an accepted placement.
- [ ] **[P2][L] Revisit block-entity destruction only if playtesting demands it.** Season One protects every block entity. Any later exception must preserve its full payload and define inventory, loot, text/NBT, duplication, and repair semantics before receiving a conflict capability.
- [x] **[P1][M] Capture before mutating.** Supported break/place events are cancelled until the immutable original record commits; only then may a revalidated action mutate the world.
- [x] **[P1][M] Keep the B1 mutation bridge bounded and observable.** Authorization uses the published in-memory battle/claim snapshot, immutable inputs are captured on the server thread, duplicate battle/coordinate attempts are coalesced, and a code-enforced queue bound fails closed. Completion returns to the server thread without loading chunks, revalidates state and capability, and applies at most once. Aggregate metrics expose queue depth, latency, stale attempts, and backpressure without per-block normal-verbosity logs.
- [ ] **[P1][M] Make TNT physics visual and bounded.** The authoritative mutation remains in the journal; falling blocks must not place permanent untracked blocks, damage unrelated claims, duplicate drops, or load uncontrolled chunks.
- [ ] **[P1][M] Handle cascading changes.** Explosions, attached blocks, gravity, fluids, and fire can alter blocks outside the initial list; either intercept and journal them or suppress them during MVP wars.
- [x] **[P1][M] Produce a stable damage report at resolution.** Resolving battles accept one complete set of framework-neutral final observations, exclude restored/no-op entries, classify eligible coordinates as restore-original or remove-placement repair units, and seal the immutable basis with idempotent conflict detection. Monetary rates remain a Season One repair-economics decision.

### Repair engine

- [ ] **[P1][XL] Implement a persisted, resumable repair job.** Store requested fraction, eligible change IDs, price, payment/ledger IDs, cursor, state, and completion/error details.
- [ ] **[P1][M] Calculate exact partial repair deterministically.** Select the requested percentage of eligible changes, charge only that selection, and report skipped/conflicted blocks separately.
- [ ] **[P1][M] Restore in a safe visible order with per-tick budgets.** Prefer bottom-up/dependency-aware batches, keep chunks bounded, avoid suffocating players, and expose speed as blocks per tick/second accurately.
- [x] **[P1][M] Define current-world conflict behavior.** Do not lock damaged coordinates. Repair only when the live state exactly matches the report's sealed final damaged state; otherwise record a conflict/skip for inspection and preserve the player's later manual change.
- [ ] **[P1][M] Make payment and victor proceeds transactional and idempotent.** A repair may not charge twice or pay twice after a crash. Admin commands execute the same repair operation for an explicit target civilization under an audited admin-sponsored context; that path charges no account and creates no victor proceeds.
- [ ] **[P1][M] Pause/resume cleanly on shutdown, unloaded worlds, or errors.** Do not silently discard handled or unhandled locations.
- [ ] **[P1][M] Add repair tests.** Cover 1%, 50%, 100%, zero funds, repeated damage, attacker placements, preexisting air, protected containers, conflict skips, restart at every cursor, and double-resolution/payment attempts.
- [ ] **[P2][M] Add cosmetic repair animation.** Consider bounded falling-block or block-display effects so reconstruction appears visibly assembled. The durable repair cursor and authoritative server-thread block mutation remain the source of truth; cosmetic entities may not place blocks, duplicate drops, or survive as orphaned state after restart.

The unsafe legacy repair command/task and falling-block helper were deleted. The current first-write-wins journal is the only shipped damage mechanism; repair behavior is introduced only through the persisted engine specified above.

## Milestone 5 — playtest quality and operations

- [ ] **[P1][L] Add pure unit tests for domain rules and state transitions.** Geometry, memberships, permissions, war eligibility, state transitions, pricing, ledger idempotency, and repair selection should not require a running server.
- [x] **[P1][L] Add repository integration tests against the selected database.** Real SQLite tests cover ordered migrations, constraints, rollback, restart recovery, and idempotent current operations, including ledger/bridge cases; repair-job cases belong to A3.
- [ ] **[P1][M] Select a maintained Paper-compatible event test approach.** Use it for basic listener/policy wiring, but keep a real local Paper server suite for behaviors mocks cannot represent.
- [ ] **[P1][M] Turn the ignored root `server/` into a repeatable gameplay test fixture.** Add documented seed/setup steps, test operators/players, reset scripts that only target the explicit server test directory, and a checklist for the MVP scenario.
- [ ] **[P1][M] Add scripted smoke/restart checkpoints.** Build/deploy, start, provision, claim, begin war, stop/restart, resolve, repair, restart, and verify database/world state.
- [ ] **[P1][M] Add structured audit logs.** Record admin actions, civilization/membership changes, claim changes, war transitions, damage counts, money transfers, repair progress, and recovery decisions with IDs.
- [ ] **[P1][M] Add timings/metrics around spatial queries, event-policy decisions, explosion recording, database queues, and repair batches.** Avoid logging every block at normal verbosity.
- [ ] **[P1][S] Add a CI workflow for clean build and tests on every push to `main`.** Keep the full Paper gameplay suite optional/nightly if it is too slow for every commit.
- [ ] **[P1][S] Generate the configuration reference from declared settings metadata.** Current storage, claim, and phase-gate keys have typed, path-specific startup validation and a maintained reference; future pricing/economy settings must receive the same validation before this item is complete.
- [x] **[P1][S] Enable economy-backed gameplay only after the built-in ledger exists and its configuration validates.** Civilizations SQL is authoritative for civilization treasuries. The optional Vault adapter touches only player wallets and uses durable prepare/result/reconciliation records; it does not create or trust an external organization bank.
- [ ] **[P1][S] Add a pre-playtest backup/restore drill.** Verify both database and world restoration, not merely backup creation.

## Retired implementation — complete

- [x] Delete the mutable legacy civilization/player/claim/raid object graph and global managers.
- [x] Delete the legacy datastore, serializers, settings/localization framework, commands, menus, conversations, listeners, tasks, and custom events.
- [x] Delete Towny/Factions adapters, unsafe repair/TNT helpers, upkeep/tax logic, mob-removal logic, and production test commands.
- [x] Replace the remaining plugin lifecycle and command registration with native Paper APIs.
- [x] Remove Foundation, the legacy Vault hooks, unrestricted JitPack use, and the unstructured coroutine helper/dependency. A2 later reintroduced only compile-only VaultAPI and a group-restricted JitPack source under the explicit player-wallet bridge decision.
- [x] Ship focused native `/civadmin` and `/civ` surfaces with explicit admin, war, phase-control, participation, and bypass permissions.
- [x] Add an architecture regression test that rejects retired source imports and build dependencies.

Plots, colonies, custom ranks, player chat, fly, warps/signs, homes, menus, and similar breadth are no longer half-enabled compatibility features. If playtesting prioritizes one, implement it as net-new behavior through the current domain, application, persistence, and Paper boundaries.

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
- Monetization features

The first playtest succeeds if the civilization → claim → declared battle → reversible destruction → paid reconstruction loop is understandable, safe after restarts, and fun enough that players want another round.
