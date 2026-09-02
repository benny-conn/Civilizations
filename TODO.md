# Civilizations roadmap

This document is the product and architecture backlog for turning the current plugin into the foundational gameplay plugin for a small, curated, seasonal Civilizations server.

Audit date: 2026-08-31

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
- [x] A3: add persisted repair jobs, deterministic partial selection, and restart-safe cursors.
- [x] B3: connect the bounded Paper repair runner.
- [x] B4: connect bounded Paper battle resolution, immutable report sealing, surrender/admin closure, and outcome-neutral timeout recovery.
- [x] A4: add durable online combatant enrollment, snapshotted lives, idempotent life loss/elimination, and defender-at-deadline resolution without a second disconnect timer.
- [x] B5: connect targeted living-combatant PVP, same-tick Paper death batching, normal respawn/elimination messaging, and dependency-free BattleLock stand-in translation.
- [x] D1: add a repair battle picker, authoritative status/quote inventory, absolute target previews, and confirmed starts over the existing repair operations.
- [x] A5: add snapshotted casualty prices, attacker maximum-liability coverage, immutable idempotent casualty records, no-debt treasury sink charges, and battle-time withdrawal locking.
- [x] E1: add explicit connected claim groups and atomic pricing, treasury-backed land upkeep/reserve/grace, bounded journal-first exposure, and no-victor treasury restoration.

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
- [x] **[P1][S] Make geography consequential by rejecting routine player teleports.** Civilizations will not provide `/home`, `/back`, `/tpa`, random teleport, or equivalent fast travel. Administrative recovery remains available; capitals/homes may later be metadata or respawn destinations rather than teleport commands. Local markets and controlled infrastructure are preferred over a global auction house.

## What exists today

The project compiles and starts on Paper 26.2. The architecture is covered by domain, policy, SQLite, dependency-boundary, and runtime-restart tests; real event behavior still requires the ignored Paper fixture because mocked tests cannot prove world mutation semantics.

| Area | Present implementation | Readiness |
| --- | --- | --- |
| Civilizations | ID-based records with draft/active/dissolved states and landless provisioning | Live admin path; player-facing creation, descriptions, roles, and government remain to design; ordinary home teleportation is intentionally excluded |
| Membership | Relational one-civilization-per-season membership, offline UUID provisioning, leader transfer | Live admin path; invites/self-service and richer roster inspection are not yet exposed |
| Land | Immutable inclusive rectangles, explicit edge-connected claim groups, exact geometry, chunk spatial index, atomic treasury pricing, and audited group tiers | Leader rectangular player claim and admin claim paths are live; selection tools, unclaim, and settlement naming remain follow-ups |
| Protection | Central policy plus thin Paper listeners, configurable treasury upkeep/reserve/grace, and a bounded exposure lifecycle | Protected land, battle overrides, and journal-first exposure are live; exposure keeps containers, block entities, entities, and PVP protected and allows only capped simple block changes by another civilization |
| Diplomacy | Durable war declarations and lifecycle | Player declarations and focused admin operations are live; alliances, enemies, and treaties remain net-new features |
| Battles | Durable war relationship plus timed hostile-entry battle, political roster and online combatant snapshots, lives/elimination, terminal result, expiry recovery, and bounded live-world report sealing | Hostile-entry activation, targeted claimed-land PVP, Paper/BattleLock death translation, normal respawn elimination, leader surrender, safe admin force-resolution, deterministic elimination/defender-timeout outcomes, and simple battle block mutation are live |
| Damage | Immutable per-battle/3D-coordinate rows preserve the first simple block state, actor, cause, claim, and time; sealed resolution reports freeze final states, eligibility, and neutral repair-cost categories | Durable/restart-safe reporting, bounded simple break/place interception, and live repair assessment are connected; explosions and block entities remain closed |
| Reconstruction | Durable battle and land-protection repair jobs persist deterministic selections, economic snapshots, payment IDs, lifecycle, result counts, and resumable cursors | Bounded, mutually serialized Paper restoration is live; manual restoration reduces remaining work and land-protection repairs pay no victor; cosmetic animation remains a follow-up |
| Economy | Exact fixed-point civilization accounts, immutable idempotent ledger postings, opening balances, player deposit/leader withdrawal commands, durable reconciliation, atomic repair payment/victor proceeds, and no-debt battle casualties | Civilizations SQL is authoritative for civilization treasuries; Vault is an optional narrow bridge to an external plugin authoritative for player wallets. Attacker coverage and battle withdrawal locks prevent treasury evacuation after combat starts. |
| Permissions | Central leader/member/outsider/admin protection policy | Live for claims; richer ranks/plots are intentionally absent from the MVP |
| Player utilities | None beyond focused administration | Player claim UX, chat, signs, and menus are net-new only if product-prioritized; routine player teleports and warps are intentionally absent |
| Persistence | Versioned relational SQLite with prepared statements, transactions, constraints, WAL, and startup integrity checks | Live through schema 11, including claim groups, upkeep assessments/state, exposure journals, and land-protection repair jobs/items; backup tooling remains |
| Seasons/scarcity | Durable active-season selection and `SETUP/PEACE/WAR/FINALE/ARCHIVED` phase controls | Phase gate is live; reset and scarcity systems are not implemented |
| Assassination/occupation/annexation | None | Not implemented |

## MVP acceptance scenario

The MVP is ready for a real 12-player Saturday test when this complete scenario works:

1. An admin provisions three named civilizations, each with a preselected leader and four players, before any civilization owns land. Self-service civilization creation can be disabled.
2. The server begins in `PEACE`. Players claim connected rectangular regions, and protection is correct on edges, negative coordinates, overlapping selections, explosions, fluids, pistons, containers, and cross-border interactions.
3. A restart preserves rosters, leaders, land, homes, balances, the global phase, and all indexes.
4. An admin enables war. One civilization declares war, and a timed battle starts when a member enters the opponent's claimed land. Only snapshotted participants acting in the opposing civilization's claims receive eventual war overrides.
5. Attackers break blocks and use war TNT. The visual falling-block effect is cosmetic; every real world mutation is authorized and journaled before it happens. Repeated changes retain the original pre-war state, and attacker-placed blocks are also reversible.
6. The battle ends deterministically, even if nobody is online. A result and damage summary are saved, destruction stops immediately, casualty sinks remain idempotent, and only unused attacker coverage is released.
7. The losing civilization pays to repair some or all eligible damage. The correct fraction is charged, restoration is visibly paced, and the task resumes safely after a restart.
8. Admins can inspect, pause, resume, cancel, or force-resolve the battle/repair and can recover from a bad state without editing a database.
9. Automated geometry, policy, persistence, state-machine, restart, and repair tests pass; a scripted local Paper playtest covers the real event integrations.

## Milestone 0 — lock the MVP rules

- [x] **[P0][S] Write the remaining Season One rules that the code must enforce.** Global command permissions default to players and may be narrowed through LuckPerms; civilization-scoped authority and lifecycle safety remain plugin-owned. Rosters may change during `WAR`; a battle's participant/side snapshot remains immutable. Claim-group tiers and player command defaults are explicit, validated configuration.
- [x] **[P0][S] Define claim adjacency.** Rectangles are inclusive, may not overlap, and rectangles in one group share an edge-connected path; corner-only contact does not count. A disconnected rectangle founds another explicit priced group when its tier permits it.
- [x] **[P0][S] Decide whether disconnected colonies are part of the product model.** A civilization may own a configurable number of disconnected claim groups. Rectangles within one group must remain connected; founding another group requires a configurable, substantial treasury payment and may require configurable membership and/or balance thresholds. Model groups explicitly rather than bypassing connectivity with a boolean. Exact Season One defaults may initially restrict civilizations to one group.
- [x] **[P0][S] Define who can declare and start wars.** A civilization member with access to the declaration command may declare without admin approval or a preparation countdown during `SETUP`, `PEACE`, or `WAR`. Declarations are durable but cannot produce a battle outside the global `WAR` phase. Changing the global phase to enable or stop battles requires a dedicated permission that defaults to operators/admins. The service and Paper adapters enforce this rule.
- [x] **[P0][S] Define the battle land scope.** There is no separate battlefield object: during an active battle, each side's eligible area is the ordinary claimed land of the opposing civilization. B1 couples supported simple block changes to the damage journal; owner changes in either side's land use the same journal path so manual rebuilding cannot escape the pre-war history.
- [x] **[P0][S] Define the first battle victory calculation.** Wars have no winner or loser; each battle may. Eliminating the opposing enrolled combatants wins, simultaneous final elimination draws, and defenders win by holding until the absolute deadline. A current leader may surrender and force-resolution remains an audited admin recovery operation. Physical damage is not victory score.
- [x] **[P0][S] Define repair economics.** Starting civilization balance, restore-original/remove-placement unit prices, victor share, and ordinary initiator roles are validated YAML settings with defaults of `0`, `1`, `1`, `25%`, and leader-only. A civilization can pay only from its available treasury balance. Effective values are snapshotted into each durable repair job. An admin repair is not a configurable waiver: the admin command targets a civilization and invokes the same application operation as an audited, payment-free admin-sponsored repair, which produces no victor proceeds.
- [x] **[P0][S] Protect block entities and entities during MVP wars.** B1 permits only simple, non-block-entity building blocks whose complete relevant state fits `SimpleBlockSnapshot`. Containers, signs, banners, lecterns, spawners, beds, and every other block entity remain protected; conflict capabilities do not authorize entity damage other than separately modeled participant PVP. Unsafe cascading or multi-block changes remain suppressed until every affected coordinate can be journaled first.
- [x] **[P0][S] Define behavior in unresolved damaged areas.** Manual rebuilding remains allowed. A repair runner may mutate a coordinate only when its current state still exactly matches the final damaged state sealed in the battle report; a later player edit becomes a reported conflict/skip and is never overwritten.
- [x] **[P1][M] Define Season One battle lives and post-death behavior.** New battles snapshot eligible online members and configurable lives (default one). Losing the final life removes that battle's published capability. Disconnect alone retains the life so a dedicated combat logger can supply a real player/NPC death; Civilizations does not run a competing grace timer. B5 preserves normal vanilla respawn and adds clear elimination/lives messaging. Teammate-locked spectating remains deferred UX.

### Season One battle outcome — approved and durable in A4

- Snapshot the durable political-war rosters for history, plus a smaller combatant set from eligible online members when the battle starts. Require at least one combatant on each side so an offline civilization cannot be auto-defeated.
- Give each combatant one life for that battle. On death, respawn normally at a safe home/spawn, remove that battle's entry/PVP/destruction capability, and optionally offer a teammate-locked camera only after it can be enforced without free-camera information leaks.
- A disconnect does not alter Civilizations combat state. The external combat-logging plugin keeps the player vulnerable or produces a death, and B5 translates that authoritative consequence into the idempotent life-loss path. Without such a consequence, the player remains alive until return or battle timeout.
- The attacker wins by eliminating all defending combatants before the deadline. The defender wins by eliminating all attackers or holding until the deadline. Simultaneous elimination is a draw. Block damage is never victory score.
- A current civilization leader may surrender, immediately producing a loss for that side. Admin force-resolution must name an explicit `ATTACKER_WIN`, `DEFENDER_WIN`, `DRAW`, or `CANCELLED` outcome and an audit reason; it does not run a hidden score formula.
- Casualty economics are live in A5: every death creates one idempotent charge against the dead player's snapshotted civilization, with configurable attacker and defender prices defaulting to `2500.00` and `1000.00`. Attackers pre-fund their maximum possible liability by default; both parties' withdrawals lock while the battle is active/resolving. Direct charges stop at zero and record unpaid amounts without debt. Death costs are a currency sink rather than opponent payment and remain separate from repair prices and victor repair share.

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
- [x] **[P0][M] Add idempotent startup recovery for implemented state.** Startup migrates, validates, rebuilds indexes, advances expired battles, reconciles ambiguous player-wallet transfers, and pauses interrupted repair jobs without guessing at world completion.
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
- [x] **[P1][S] Keep routine player teleports out of the product.** A future capital/home may be stored for map, history, or respawn policy, but Civilizations will not turn it into `/home`, `/back`, `/tpa`, random teleport, or an equivalent travel bypass. Staff recovery teleports remain administrative tools.
- [ ] **[P1][S] Add roster inspection and validation commands.** Show UUID, last known name, role, leader, online state, and any invariant violations.
- [ ] **[P2][L] Add civilization-owned custom roles and granular capabilities.** Leaders may create named roles such as `Knight` and grant plugin-owned actions such as claiming or managing settlement PVP. LuckPerms gates global command access/admin authority; it does not replace durable civilization-scoped role assignments. Start with commands and add an inventory GUI over the same application service rather than putting policy in menu handlers.

### Claim representation and spatial index

- [x] **[P0][L] Replace live `Region` use with an immutable 2D rectangle value.** Current geometry normalizes inclusive integer bounds, uses `Long` area, and has no hardcoded Y range.
- [x] **[P0][L] Add a world/chunk spatial index.** Point lookup checks only chunk candidates and exact containment, independent of total claim count.
- [x] **[P0][M] Keep the authoritative claim repository separate from the derived index.** Startup and every successful mutation rebuild and atomically publish derived runtime state; a dedicated admin rebuild command remains optional operations work.
- [x] **[P0][M] Use exact rectangle intersection for overlap checks.** Candidate filtering and interval intersection cover cross-shaped overlaps.
- [x] **[P0][M] Use analytic adjacency rather than materializing a bounding box.** Edge-only adjacency uses interval math and excludes corner contact.
- [x] **[P1][L] Represent connected claim groups explicitly.** Claims reference durable groups; a bridge claim merges adjacent groups. Founding a disconnected group atomically validates its contiguous configured tier, membership/balance thresholds, authority, and establishment price before debit and insert. Existing land is not deleted if a civilization later falls below a threshold.
- [x] **[P1][S] Bound claim size/chunk coverage and reject coordinate/area overflow.** Claim services validate configurable area/count limits and checked geometry before index insertion.
- [x] **[P1][M] Make claim creation one atomic operation.** The player adapter supplies its current stable world and normalized rectangle; the service validates geometry, overlap, group adjacency, limits, price, leader authority, treasury, and phase in one transaction, then debit/group/claim commit before runtime publication.
- [x] **[P2][M] Design claim-group progression as validated tiers.** Contiguous tiers define member-count and treasury thresholds plus establishment cost. Each durable group audits the effective thresholds/cost used at creation; later configuration edits do not reinterpret that purchase.
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
- [x] **[P2][XL] Add treasury-backed land protection upkeep and bounded exposure.** A distinct configurable lifecycle charges by protected area, preserves a purpose-specific withdrawal reserve, enters grace without debt/dissolution, and exposes capped simple building-block damage only to another civilization after grace. Every permitted change commits its exposure journal first. Containers, block entities, entities, and PVP remain protected; battles suspend exposure and defer new upkeep shortfalls. Treasury restoration is deterministic, manual restoration lowers its remaining count/price, and it has no victor proceeds.
- [ ] **[P2][L] Add configurable citizen dues over the civilization treasury.** The civilization's government approves a prospective, capped schedule; each period creates a public durable invoice and a successful payment enters the treasury through the Vault bridge without a negative player balance. The first version records notification/delinquency rather than compounding debt, automatic disenfranchisement, removal, or imprisonment. Any later consequence is an explicit government action, not a scheduler side effect.
- [ ] **[P2][M] Add inventory UX for claims, claim-group progression, and land protection.** Present tier requirements, exact purchase/upkeep/reserve values, grace deadlines, exposure cap, manual restoration progress, and repair confirmation over the existing application services. Menus must not own pricing or authorization policy.

The former `Region`, plot, visualization, all-civilization scan, and raid-ratio implementations were deleted. Current claim geometry, indexing, and protection behavior are covered by property/policy tests; new unclaim, settlement, and war-placement behavior remains explicitly listed as feature work above.

## Milestone 3 — durable war lifecycle

- [x] **[P1][M] Add the persisted global gameplay phase and admin controls.** `SETUP/PEACE/WAR/FINALE/ARCHIVED` and emergency `WAR -> PEACE` are durable and exposed through `/civadmin`; scheduling, broadcasts, and audit logs remain follow-ups.
- [x] **[P1][XL] Implement persisted war and battle state machines.** A durable war moves through `DECLARED/ACTIVE/CLOSED/CANCELLED`; each timed battle moves through `ACTIVE/RESOLVING/CLOSED/CANCELLED`. Central transitions are timestamp-based and idempotent.
- [x] **[P1][M] Store war parties, declarer, trigger claim/player, rules snapshot, participant snapshot, start/end/resolution timestamps, and terminal result.** Eligible land is derived from the ordinary opposing claims rather than stored as a separate battlefield.
- [x] **[P1][M] Separate declaration from battle activation.** Diplomacy flags do not substitute for a war record; declaration, war activation, and hostile-entry battle start are separate operations.
- [x] **[P1][M] Add immediate activation and clear boundary feedback.** There is no admin approval or preparation countdown. Eligible hostile entry atomically activates a declared war and starts its battle, both snapshotted rosters receive the exact end time, entry outside active `WAR` receives immediate feedback, and damage remains closed without active eligibility. A richer visual border remains optional UX work.
- [x] **[P1][M] Define participation robustly.** A4 durably separates the immutable political roster from eligible online combatants, preserves sides through later roster moves, retains lives across disconnects, and removes eliminated capabilities. B5 grants exact opposing-living-combatant PVP in either side's claimed land, batches same-tick Paper deaths, preserves normal respawn, and translates BattleLock stand-in deaths without a second logout timer. Joining/leaving claim land does not change enrollment; teammate-locked viewing is a later optional UX item.
- [x] **[P1][M] End battles outside player loops.** Runtime startup/refresh and the live Paper coordinator advance expired battles to `RESOLVING` with zero players online, remove active eligibility, and seal damage through bounded non-generating world observation. New A4 battles durably request defender victory at timeout; surrender, elimination, timeout, and audited admin outcomes close after sealing. Older outcome-neutral battles remain safe for explicit recovery. The containing war remains winnerless.
- [x] **[P1][M] Persist battle casualties and apply idempotent economic consequences.** Schema 10 snapshots separately configurable attacker and defender death costs, attacker coverage, and the withdrawal lock. Stable life-event identities key immutable casualty rows; direct charges stop at zero, record uncollectible amounts without debt, and remain a currency sink separate from repair and battle victory.
- [ ] **[P1][M] Complete admin recovery commands.** B2 provides war/battle list and inspection, explicit war activation/closure/cancellation, battle force-resolution/cancellation, and required logged audit reasons. Pause/resume, participant detail, rollback, durable structured audit records, repair controls, and a complete stuck-state workflow remain.
- [ ] **[P1][M] Add war restart tests at every transition.** Restart in declaration, preparation, active combat, resolution, and repairable states and assert the same eventual result.
- [x] **[P1][S] Provide idempotent ledger primitives for balance/reward effects.** Exact immutable postings and caller-owned idempotency keys are live; repair creation atomically records payment/proceeds, and A5 records attacker reserves, direct casualty charges, and unused-reserve releases through the same ledger.
- [x] **[P1][S] Retire old war settings.** The unused `Raid.Buy_In`, `Attacker_Teleport`, and their legacy settings framework were deleted; new controls require implemented behavior and validation.

The former in-memory raid, ratio, countdown, lives, and civilization-level damage implementations were deleted. Current outcome, casualty, resolution, and Paper repair behavior is implemented only through the durable war/damage/economy model above.

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

- [x] **[P1][XL] Implement a persisted, resumable repair job.** Schema 8 stores the absolute completion target, observed counts, selected eligible change IDs/order, snapshotted prices/share, payment ID, cursor, lifecycle, and completion/error details.
- [x] **[P1][M] Calculate exact partial repair deterministically.** Fresh observations count exact manual restoration toward the absolute target. Selection is deterministic and charges only still-repairable blocks needed to reach that target; altered/conflicted blocks remain separate. Thus 50% paid plus 3% manual restoration leaves 47% to buy for a 100% target.
- [x] **[P1][M] Restore in a safe visible order with per-tick budgets.** Persisted items are bottom-up, the Paper runner processes one job and holds at most one non-generating chunk ticket, solid blocks defer while intersecting a player, and validated settings expose the blocks-per-tick/second ceiling.
- [x] **[P1][M] Define current-world conflict behavior.** Do not lock damaged coordinates. Repair only when the live state exactly matches the report's sealed final damaged state; otherwise record a conflict/skip for inspection and preserve the player's later manual change.
- [x] **[P1][M] Make payment and victor proceeds transactional and idempotent.** Job/items and one ledger transaction commit atomically. The payer cannot spend more than its treasury; the configured share (including 0%) credits the other party only when it won. Admin-sponsored jobs record their actor/target but cost and pay zero.
- [x] **[P1][M] Pause/resume cleanly on shutdown, unloaded worlds, or errors.** Startup pauses interrupted `RUNNING` jobs at their exact cursor; the Paper runner also pauses unavailable world/chunk work without advancing it, and audited admin commands expose resume/cancel.
- [ ] **[P1][M] Add repair tests.** Cover 1%, 50%, 100%, zero funds, repeated damage, attacker placements, preexisting air, protected containers, conflict skips, restart at every cursor, and double-resolution/payment attempts.
- [x] **[P2][M] Add inventory GUI repair UX over the command/service workflow.** `/civ repair` now lists the player's closed battles and uses bounded live scans to render actual completion, remaining repairable work, conflicts, treasury balance, authoritative price/victor proceeds, latest-job state, and absolute 25/50/75/100% targets. A separate preview screen confirms the application-service quote before the ordinary start operation performs its final world/economic recheck; menu handlers contain no pricing or authorization policy.
- [ ] **[P2][M] Add cosmetic repair animation.** Consider bounded falling-block or block-display effects so reconstruction appears visibly assembled. The durable repair cursor and authoritative server-thread block mutation remain the source of truth; cosmetic entities may not place blocks, duplicate drops, or survive as orphaned state after restart.

The unsafe legacy repair command/task and falling-block helper were deleted. The current first-write-wins journal is the only shipped damage mechanism; repair behavior is introduced only through the persisted engine specified above.

## Milestone 5 — playtest quality and operations

- [ ] **[P1][L] Add pure unit tests for domain rules and state transitions.** Geometry, memberships, permissions, war eligibility, state transitions, pricing, ledger idempotency, and repair selection should not require a running server.
- [x] **[P1][L] Add repository integration tests against the selected database.** Real SQLite tests cover ordered migrations, constraints, rollback, restart recovery, and idempotent current operations, including ledger, player-wallet bridge, and repair-job cases.
- [ ] **[P1][M] Select a maintained Paper-compatible event test approach.** Use it for basic listener/policy wiring, but keep a real local Paper server suite for behaviors mocks cannot represent.
- [ ] **[P1][M] Turn the ignored root `server/` into a repeatable gameplay test fixture.** The manual operator/player path, restart checkpoints, expected limitations, evidence checklist, and sealed-report repair path are documented in `docs/manual-playtest.md`. Seed/reset automation that only targets the explicit server test directory remains.
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

The working product direction for the following systems is consolidated in
[docs/server-design.md](docs/server-design.md). Items remain unchecked until their
application, persistence, and Paper boundaries are implemented and tested.

### Season system and persistent history

- [ ] **[P2][XL] Implement the season lifecycle.** Setup, opening peace, war phase, finale, freeze, archive, and next-season provisioning should be explicit, persisted transitions rather than a calendar cron job.
- [ ] **[P2][XL] Build an idempotent reset plan with dry-run and backup gates.** Wipe player inventories, Ender Chests, configured containers, economy, memberships/civilizations, transient entities, and seasonal state while preserving buildings/roads/world geography.
- [ ] **[P2][L] Decide which historical artifacts survive.** Books, signs, banners, maps, named items, and hidden treasure need deliberate rules to prevent accidental storage loopholes.
- [ ] **[P2][M] Archive civilization, war, ownership, and map metadata rather than deleting it.** Future seasons should be able to show who built/occupied ruins without giving the old organization current power.
- [ ] **[P2][L] Add an archaeological map/history integration.** Consider BlueMap/Dynmap-style layers or a separate read model after the core records ownership and war history cleanly.
- [ ] **[P2][M] Add finale and season analytics.** Population, trade proxies, wars, deaths, damage, reconstruction, wealth, and territorial history should inform the next ruleset.

### Scarcity and specialization

- [ ] **[P2][L] Design regional scarcity as a policy system, not a world-wide entity scan.** Choose a small number of strategically meaningful resources with multiple geographically restricted sources for the first experiment. Initial candidates include livestock habitats, ore deposits, special crops, villager access, and registered Nether portal sites.
- [ ] **[P2][XL] Add finite strategic-species registries and spawning rules.** Seed selected passive species once, deny later ordinary natural spawns, persist births/deaths and wall-clock juvenile maturity, enforce slow habitat-aware breeding cooldowns, optionally disable villager breeding, handle chunks/restarts, and expose population/extinction diagnostics without a world-wide scan.
- [ ] **[P2][L] Add anti-bypass rules for eggs, spawn eggs, breeding, curing, transformations, wandering traders, alternate dimensions, loot tables, generated structures, and later-generated chunks only as each scarce resource requires.**
- [ ] **[P2][M] Add admin seeding/rebalancing tools and telemetry.** Scarcity must create interdependence rather than unknowable grind.
- [ ] **[P2][L] Author a finite asymmetric season world.** Use WorldPainter, a versioned world-generation data pack, or a configurable generator for initial geography, but keep durable resource-zone identities and live enforcement in purpose-built policy that understands Civilizations.
- [ ] **[P2][M] Add registered Nether portal sites and deny ordinary portal creation elsewhere.** Define several sites, stable linking, admin recovery, and whether controllers may close access, charge tolls, or must preserve a right of passage.
- [ ] **[P2][L] Design a narrow livestock-raid context.** Current battles continue to protect ordinary entities. A later explicit event may permit eligible participants to attach leads and physically steal registered livestock; killing, affected-headcount limits, costs, warnings, and political consequences remain provisional because finite animal death is not repairable.

### Government, civic decisions, and economic exchange

- [ ] **[P2][L] Persist an explicit government type for each civilization.** The creating admin or authorized creator selects it at provisioning; later changes are audited admin-only operations. Initial candidates are autocracy, council government, and a citizen republic.
- [ ] **[P2][XL] Add durable binding proposals and votes.** Snapshot the council/citizen electorate, quorum, threshold, closing time, and exact action payload. Candidate actions include leader selection, dues, large spending or repair, war declaration, surrender, expulsion, and custody decisions. A passing proposal invokes one idempotent application operation.
- [ ] **[P2][M] Add durable non-binding polls.** An authorized officeholder can ask an arbitrary question of the snapshotted council or citizen electorate and record the result without pretending the plugin can enforce the lore outcome.
- [ ] **[P2][L] Add bounded public purchase orders as the primary money faucet.** Mint currency against capped, time-limited deliveries or public work rather than indefinitely paying for repeatable actions. Record issuance and either consume delivered goods or transfer them to an explicit stockpile.
- [ ] **[P2][L] Add escrow-backed player and civilization contracts.** Existing money pays for goods, construction, transport, defense, or other measurable work; local physical trade remains preferable to a global auction house.

### Prisoners of war

- [ ] **[P2][L] Complete a dedicated POW rules and abuse review before implementation.** No capture or custody mechanics are approved. Decide capture/consent, death and lives interaction, transport, inventory, duration/logout, prisoner activity, escape/pursuit/recapture, ransom/exchange/parole, war closure, and admin recovery, then test the proposed loop in staging. [Exploratory notes](docs/pow-design-notes.md) preserve ideas but are not acceptance criteria.

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
- Complete custom world generation or broad resource scarcity; a small regional-resource prototype remains pre-season work
- A web application or public server listing
- Folia, Velocity, or a multi-server network
- Full custom-rank/plot/colony/menu parity
- Monetization features

The first playtest succeeds if the civilization → claim → declared battle → reversible destruction → paid reconstruction loop is understandable, safe after restarts, and fun enough that players want another round.
