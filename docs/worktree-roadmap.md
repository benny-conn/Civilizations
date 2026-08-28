# Civilizations MVP worktree roadmap

The architecture rework is complete. This is the handoff map for implementing net-new MVP gameplay with multiple Codex worktrees. [TODO.md](../TODO.md) remains the product backlog and acceptance criteria; this file defines safe implementation boundaries and merge order.

## Starting a slice

1. Update local `main` from `origin/main`, then create one Codex worktree and one named branch for exactly one item below.
2. Read [AGENTS.md](../AGENTS.md), [README.md](../README.md), [TODO.md](../TODO.md), and [architecture.md](architecture.md) before editing.
3. Keep the slice independently buildable. Do not add a second durable store, query SQL from Paper event paths, or restore retired implementation paths.
4. Rebase the branch onto the latest `main` before handoff, resolve its own conflicts, and run `./gradlew clean build`.
5. Update the relevant checklist/status row in this file and `TODO.md` in the same branch. The person integrating branches owns final conflict resolution and the real-Paper checkpoint.

Codex runs `.codex/environments/environment.toml` when it creates a worktree. The setup compiles production and test sources and provisions the Gradle Java toolchain if necessary. The ignored `server/` directory is intentionally not copied: a worktree that reaches a Paper integration boundary should use the **Set up Paper** action to create its own isolated fixture.

## Concurrency rules

Two areas are intentional serialization points:

- **Durable-feature lane:** only one active branch may edit `CivilizationsSchema.kt`, `CivilizationsRepository.kt`, or `JdbcCivilizationsRepository.kt`. Schema migration numbers and repository contracts must land in order.
- **Paper-integration lane:** only one active branch may edit `CivilizationsPlugin.kt`, `CivilizationsRuntime.kt`, `PaperProtectionListener.kt`, or command registration. This avoids merging two independently correct server-thread/storage-thread lifecycles into an unsafe one.

Branches in different lanes may proceed together when their port contract already exists on `main`. Operations-only work may run alongside either lane. Agents must not make speculative changes to another lane's files merely to make a future integration easier.

## Merge queue

| ID | Branch suggestion | Lane | Depends on | Deliverable and boundary | Status |
| --- | --- | --- | --- | --- | --- |
| A1 | `feature/damage-reports` | Durable feature | Damage journal | Immutable per-battle damage report and deterministic eligible-change/cost basis. Accept final world observations through application-owned values; do not call Paper from the service. | Complete |
| B0 | `benny/configurable-phase-rules` | Paper integration | Current `main` | Typed, validated YAML phase gates for roster changes, claim creation, and ordinary member land actions; document the configuration boundary and keep unsafe lifecycle combinations code-enforced. No schema changes or live reload. | Complete |
| B1 | `benny/paper-war-mutations` | Paper integration | Damage journal | First live cancel → journal off-thread → revalidate → apply-on-server-thread path for simple block break/place. Use only published in-memory authorization on the event thread, bounded/coalesced pending work, fail-closed backpressure, and no chunk loading. All block entities, entities, and cascading physics stay denied. No schema changes. | Complete |
| C1 | `operations/ci-build` | Operations | Current `main` | GitHub Actions clean build/test using the wrapper and Java toolchain; no gameplay files. | Ready |
| C2 | `operations/paper-smoke-fixture` | Operations | B1 | Explicit test-fixture reset/checkpoint scripts and an MVP Paper checklist. The manual path is live in `docs/manual-playtest.md`; seed/reset automation remains. Destructive scripts must only target the resolved worktree `server/` directory. | In progress |
| A2 | `benny/economy-ledger` | Durable feature + narrow Paper bridge | A1, B2 migration 6 | Exact civilization accounts, immutable idempotency-keyed ledger transfers, typed repair-economics YAML, and a durable optional Vault player-wallet bridge. Civilizations SQL remains treasury-authoritative; ambiguous external results require explicit reconciliation. | Complete |
| A3 | `benny/repair-jobs` | Durable feature | A1, A2 | Persisted repair jobs, fresh-world assessment inputs, deterministic absolute-target selection, atomic payment/victor proceeds, cursors, lifecycle, and restart/idempotency tests. No Paper world mutation. | Complete |
| B2 | `benny/battle-entry-adapter` | Paper integration | B1 | Hostile-claim-entry trigger, throttled movement lookup, immediate boundary feedback, declaration during `SETUP`/`PEACE`/`WAR`, leader surrender, `WAR`-gated battle activation, a safely widened `WAR` roster-mutation gate, and admin recovery/inspection commands over `WarService`. Preserve immutable active-battle sides when political-war rosters change. Migration 6 widens durable declaration authorization from leader to member. | Complete |
| B3 | `benny/paper-repair-runner` | Paper integration | A3, B1 | Repair status/start and admin lifecycle commands; bounded server-thread repair batches; compare-before-repair conflict skips that preserve manual rebuilding; pause/resume; optional cosmetic hooks; real-Paper restart verification. | Complete |
| B4 | `benny/battle-resolution-coordinator` | Paper integration | B3 | Bounded Paper observation of a resolving battle's journal, immutable damage-report sealing, then closure with an already-explicit surrender/admin outcome. Preserve restart-safe `RESOLVING` state and do not invent the ordinary timeout winner. | Complete |
| A4 | `benny/battle-combat-state` | Durable feature | B4, approved Season One combat rules | Durable combatant enrollment, lives/elimination, disconnect deadlines, and deterministic ordinary victory/timeout transitions. Snapshot effective rules per battle. | Decision blocked |
| B5 | `benny/paper-battle-combat` | Paper integration | A4 | Targeted participant PVP, Paper death/respawn/reconnect enforcement, capability removal after elimination, and bounded teammate-view UX if approved. | Blocked by A4 |
| A5 | `benny/battle-casualty-economics` | Durable feature | A2, A4, approved casualty prices | Immutable idempotent casualty records and civilization treasury sink charges with attacker/defender prices snapshotted per battle. | Decision blocked |
| D1 | `feature/repair-inventory-gui` | Paper integration | B3 | Inventory GUI over the same repair status/start services, showing actual completion, remaining repairable work, conflicts, price, and victor share. No menu-owned policy. | Later UX follow-up |

### Work that can start now

B4 now bridges explicit surrender/admin outcomes to immutable reports and repair, while expired battles seal safely without inventing a winner. C1 and the remaining C2 automation are independent operations items. A4 is the next durable gameplay feature, followed by B5 and A5, but their policy-bearing parts remain blocked until the proposed Season One combat and casualty rules are approved. D1 is the later command-to-inventory repair UX follow-up.

A3 snapshots A2's validated repair-economics values into each durable job and uses one idempotent ledger transaction for payment and proceeds. B3 must preserve that application boundary: it supplies bounded current-world observations and executes persisted items, but does not recalculate price or select work in Paper code. The removed legacy frameworks and object graph are not available as implementation shortcuts.

## Settled product decisions

- War declarations need no admin approval or preparation countdown and are allowed during `SETUP`, `PEACE`, and `WAR`; only the global `WAR` phase permits battles. Changing that global gate uses a dedicated permission that defaults to operators/admins.
- Political-war rosters remain mutable. Active battle participant/side snapshots remain immutable, so switching civilizations affects only future battles.
- Wars have no winner or loser. A battle may have an outcome, a current civilization leader may surrender its side, and admin force-resolution is an audited recovery action.
- Manual rebuilding is allowed before repair. The repair runner compares the live block with the damage report's sealed final state and skips rather than overwrites any later change.
- Repair percentages are absolute completion targets. Exact manual restoration counts toward completion, so 50% paid plus 3% rebuilt manually leaves 47% selectable for a 100% target. A civilization can pay only from its available treasury. The configurable victor share defaults to 25% and may be 0%.
- MVP battle destruction is limited to simple, independently mutable, non-block-entity building blocks. Containers, all other block entities, non-player entities, and cascading changes remain protected.
- Disconnected land is modeled as explicit claim groups with configurable limits, establishment costs, and progression thresholds rather than a connectivity bypass.
- External economy plugins remain authoritative for player wallets through Vault; Civilizations SQL is authoritative for civilization treasuries. Deposits withdraw a player only after a durable prepare record, withdrawals reserve the treasury before player credit, and any indeterminate result is reconciled rather than automatically retried.

## Product decisions that still block code

These are deliberately decisions, not invitations for an implementation agent to invent game design:

- the first ordinary battle victory calculation, timeout result, and force-resolution inputs;
- lives/elimination, reconnect grace, participant enrollment, and teammate-locked spectating behavior;
- the exact economic relationship among casualty charges, battle outcome, and any future spoils outside the settled repair-payment share;
- Season One defaults for claim-group limits, founding costs, and progression thresholds;
- the initial set of global LuckPerms-gated player commands; civilization-scoped custom roles remain a post-MVP plugin-owned feature.

An agent may model a neutral mechanism behind a port, but must not choose these policies implicitly in database defaults or event listeners.

## Later parallel tracks

After the MVP loop is complete, separate worktrees can take the D1 repair inventory GUI, player-facing roster/claim UX, season reset/history, scarcity experiments, assassination, or occupation. Assassination must be its own persisted conflict context with targeted PVP eligibility and atomic succession; it is not a special case hidden inside ordinary claim protection.
