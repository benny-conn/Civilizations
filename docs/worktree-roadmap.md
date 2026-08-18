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
| B1 | `feature/paper-war-mutations` | Paper integration | Damage journal | First live cancel → journal off-thread → revalidate → apply-on-server-thread path for simple block break/place. Containers and cascading physics stay denied. No schema changes. | Ready |
| C1 | `operations/ci-build` | Operations | Current `main` | GitHub Actions clean build/test using the wrapper and Java toolchain; no gameplay files. | Ready |
| C2 | `operations/paper-smoke-fixture` | Operations | B1 | Explicit test-fixture reset/checkpoint scripts and an MVP Paper checklist. Destructive scripts must only target the resolved worktree `server/` directory. | Blocked by B1 |
| A2 | `feature/economy-ledger` | Durable feature | A1 | Civilization accounts and immutable idempotency-keyed ledger transfers for resolution, spoils, and repair payment. Add typed validated YAML rules for initial balance, repair-unit prices, victor share, debt, and ordinary initiator roles; the plugin ledger remains authoritative. | Ready |
| A3 | `feature/repair-jobs` | Durable feature | A1, A2 | Persisted repair jobs, deterministic partial selection, cursors, lifecycle, and restart/idempotency tests. No Paper world mutation. | Blocked by A2 |
| B2 | `feature/battle-entry-adapter` | Paper integration | B1 | Hostile-claim-entry trigger, throttled movement lookup, boundary feedback, and admin recovery/inspection commands over existing `WarService` operations. | Blocked by B1 |
| B3 | `feature/paper-repair-runner` | Paper integration | A3, B1 | Bounded server-thread repair batches, world-state conflict checks, pause/resume, and real-Paper restart verification. | Blocked by A3/B1 |

### Work that can start now

After A1 merges, A2, B1, and C1 are the safe ready items in separate durable-feature, Paper-integration, and operations lanes. Merge C1 independently; rebase B1 onto the resulting `main`, run the real Paper check, then merge it. Keep A2 → A3 sequential in the durable-core lane while B2 and C2 proceed in their separate lanes.

A2 must implement the settled repair-economics settings through the validated configuration boundary and preserve their effective values for later repair-job snapshots. Do not start B3 until repair jobs have a durable cursor. The removed legacy frameworks and object graph are not available as implementation shortcuts.

## Product decisions that still block code

These are deliberately decisions, not invitations for an implementation agent to invent game design:

- declaration approval/countdown and whether rosters remain locked during a war;
- the first victory calculation and surrender/admin-resolution behavior;
- behavior for coordinates with unresolved damage (the recommended MVP rule is to lock them);
- which non-container block entities, if any, are safe in the first playtest.

An agent may model a neutral mechanism behind a port, but must not choose these policies implicitly in database defaults or event listeners.

## Later parallel tracks

After the MVP loop is complete, separate worktrees can take player-facing roster/claim UX, season reset/history, scarcity experiments, assassination, or occupation. Assassination must be its own persisted conflict context with targeted PVP eligibility and atomic succession; it is not a special case hidden inside ordinary claim protection.
