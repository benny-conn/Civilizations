# Civilizations worktree roadmap

This is the handoff map for completing the MVP architecture with multiple Codex worktrees. [TODO.md](../TODO.md) remains the product backlog and acceptance criteria; this file defines safe implementation boundaries and merge order.

## Starting a slice

1. Update local `main` from `origin/main`, then create one Codex worktree and one named branch for exactly one item below.
2. Read [AGENTS.md](../AGENTS.md), [README.md](../README.md), [TODO.md](../TODO.md), and [architecture.md](architecture.md) before editing.
3. Keep the slice independently buildable. Do not add a second durable store, query SQL from Paper event paths, or reconnect quarantined legacy entry points.
4. Rebase the branch onto the latest `main` before handoff, resolve its own conflicts, and run `./gradlew clean build`.
5. Update the relevant checklist/status row in this file and `TODO.md` in the same branch. The person integrating branches owns final conflict resolution and the real-Paper checkpoint.

Codex runs `.codex/environments/environment.toml` when it creates a worktree. The setup compiles production and test sources and provisions the Gradle Java toolchain if necessary. The ignored `server/` directory is intentionally not copied: a worktree that reaches a Paper integration boundary should use the **Set up Paper** action to create its own isolated fixture.

## Concurrency rules

Two areas are intentional serialization points:

- **Durable-core lane:** only one active branch may edit `CivilizationsSchema.kt`, `CivilizationsRepository.kt`, or `JdbcCivilizationsRepository.kt`. Schema migration numbers and repository contracts must land in order.
- **Paper-runtime lane:** only one active branch may edit `CivilizationsPlugin.kt`, `CivilizationsRuntime.kt`, `CivilizationsProtectionListener.kt`, or command registration. This avoids merging two independently correct server-thread/storage-thread lifecycles into an unsafe one.

Branches in different lanes may proceed together when their port contract already exists on `main`. Operations-only work may run alongside either lane. Agents must not make speculative changes to another lane's files merely to make a future integration easier.

## Merge queue

| ID | Branch suggestion | Lane | Depends on | Deliverable and boundary | Status |
| --- | --- | --- | --- | --- | --- |
| A1 | `architecture/damage-reports` | Durable core | Slice 7 | Immutable per-battle damage report and deterministic eligible-change/cost basis. Accept final world observations through application-owned values; do not call Paper from the service. | Ready |
| B1 | `architecture/paper-war-mutations` | Paper runtime | Slice 7 | First live cancel → journal off-thread → revalidate → apply-on-server-thread path for simple block break/place. Containers and cascading physics stay denied. No schema changes. | Ready |
| C1 | `operations/ci-build` | Operations | Current `main` | GitHub Actions clean build/test using the wrapper and Java toolchain; no gameplay files. | Ready |
| C2 | `operations/paper-smoke-fixture` | Operations | B1 | Explicit test-fixture reset/checkpoint scripts and an MVP Paper checklist. Destructive scripts must only target the resolved worktree `server/` directory. | Blocked by B1 |
| A2 | `architecture/economy-ledger` | Durable core | A1 plus repair-economics decision | Civilization accounts and immutable idempotency-keyed ledger transfers for resolution, spoils, and repair payment. Vault, if retained, is an adapter—not the source of truth. | Blocked by A1/rules |
| A3 | `architecture/repair-jobs` | Durable core | A1, A2 | Persisted repair jobs, deterministic partial selection, cursors, lifecycle, and restart/idempotency tests. No Paper world mutation. | Blocked by A1/A2 |
| B2 | `architecture/battle-entry-adapter` | Paper runtime | B1 | Hostile-claim-entry trigger, throttled movement lookup, boundary feedback, and admin recovery/inspection commands over existing `WarService` operations. | Blocked by B1 |
| B3 | `architecture/paper-repair-runner` | Paper runtime | A3, B1 | Bounded server-thread repair batches, world-state conflict checks, pause/resume, and real-Paper restart verification. | Blocked by A3/B1 |
| F1 | `architecture/foundation-removal` | Paper runtime/build | A1–B3 | Delete quarantined legacy source or port only retained adapters, replace settings/messages/lifecycle, remove Foundation/shading, and verify a clean Paper boot. | Final architecture slice |

### Work that can start now

The safest three-worktree batch is A1, B1, and C1. They own separate file surfaces. Merge A1 and C1 in either order. Rebase B1 onto the resulting `main`, run the real Paper check, then merge it. After that, keep A2 → A3 sequential in the durable-core lane while B2 and C2 proceed in their separate lanes.

Do not start A2 until the Season One repair-economics decision is written down. Do not start B3 until repair jobs have a durable cursor. Do not remove Foundation before the live war and repair paths prove which remaining adapters are actually worth retaining.

## Product decisions that still block code

These are deliberately decisions, not invitations for an implementation agent to invent game design:

- declaration approval/countdown and whether rosters remain locked during a war;
- the first victory calculation and surrender/admin-resolution behavior;
- per-block repair pricing, victor share, debt policy, and who may initiate repair;
- behavior for coordinates with unresolved damage (the recommended MVP rule is to lock them);
- which non-container block entities, if any, are safe in the first playtest.

An agent may model a neutral mechanism behind a port, but must not choose these policies implicitly in database defaults or event listeners.

## Later parallel tracks

After the MVP loop is complete, separate worktrees can take player-facing roster/claim UX, season reset/history, scarcity experiments, assassination, or occupation. Assassination must be its own persisted conflict context with targeted PVP eligibility and atomic succession; it is not a special case hidden inside ordinary claim protection.
