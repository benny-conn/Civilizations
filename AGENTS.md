# Civilizations agent guide

Read these documents before making architectural or gameplay changes:

- [README.md](README.md) — supported toolchain, build, and local Paper server workflow.
- [TODO.md](TODO.md) — prioritized product and engineering backlog, MVP boundary, and retired-system decisions.
- [docs/architecture.md](docs/architecture.md) — stable dependency direction, runtime ownership, and persistence rules.
- [docs/worktree-roadmap.md](docs/worktree-roadmap.md) — parallel lanes, file ownership, dependencies, and merge order for multi-worktree delivery.

## Working agreements

- The architecture rework is complete. Net-new work remains incremental; each branch should be independently buildable, testable, and mergeable.
- One worktree owns one roadmap item. Respect the durable-feature and Paper-integration serialization lanes; do not make overlapping schema migration or runtime-lifecycle changes in parallel.
- New domain and application code belongs under `src/main/kotlin` and must not import Paper/Bukkit, Foundation, Vault, JDBC, command, menu, or configuration types.
- Framework and database implementations are adapters around application-owned ports.
- Parse YAML only at the Paper/infrastructure boundary and pass validated, immutable,
  application-owned rule values inward. Domain and application services must never read
  configuration paths or Bukkit configuration types directly.
- Prefer making settled balancing and policy choices configurable, but keep lifecycle
  invariants as code-enforced safety bounds. A configuration option may narrow an allowed
  operation; it must not silently enable an unsafe phase or bypass durable authorization.
- Keep durable facts in SQL, not YAML. When configuration affects a long-lived operation
  such as a war, battle, ledger transfer, or repair job, snapshot the effective rules into
  that durable record so a restart or later config edit cannot reinterpret history.
- Every new configuration key requires a documented default, path-specific startup
  validation, and tests for accepted and rejected values. Unless a feature explicitly
  implements atomic reload semantics, document that its settings require a restart.
- The legacy serialized graph, datastore, commands, menus, and tasks were deleted. Do not restore them; use Git history only as product reference and implement accepted behavior through the current architecture.
- Foundation, the legacy Vault hooks, unrestricted JitPack use, and the legacy coroutine helper are retired. The explicit A2 decision permits only the compile-only VaultAPI dependency, its group-restricted JitPack source, and Vault imports inside `infrastructure/paper/economy`; do not widen that exception without another project-level decision.
- Paper world/entity access and live-state mutation stay on the server thread. Blocking I/O uses plugin-owned background execution.
- SQL is durable state; purpose-built memory indexes serve hot gameplay paths. Never query SQL from block/movement event hot paths.
- Update the roadmap or architecture documentation when a slice establishes or changes a project-wide decision.
- Run `./gradlew clean build` before handing off a completed slice. Use the ignored root `server/` workflow for real Paper behavior when a slice reaches an integration boundary.
- Preserve unrelated user changes and keep commits scoped to the active slice.
- Rebase onto the latest `main` before handoff, then report the branch name, commit, tests, migrations, and any real-Paper verification. Do not merge another agent's branch unless explicitly assigned integration ownership.
