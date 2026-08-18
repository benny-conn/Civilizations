# Civilizations agent guide

Read these documents before making architectural or gameplay changes:

- [README.md](README.md) — supported toolchain, build, and local Paper server workflow.
- [TODO.md](TODO.md) — prioritized product and engineering backlog, MVP boundary, and known legacy defects.
- [docs/architecture.md](docs/architecture.md) — dependency direction and the architecture-rework rules.

## Working agreements

- The architecture rework is incremental. Each branch should be independently buildable, testable, and mergeable.
- New domain and application code belongs under `src/main/kotlin` and must not import Paper/Bukkit, Foundation, Vault, JDBC, command, menu, or configuration types.
- Framework and database implementations are adapters around application-owned ports.
- Do not add new features to the legacy serialized object graph or legacy datastore.
- Paper world/entity access and live-state mutation stay on the server thread. Blocking I/O uses plugin-owned background execution.
- SQL is durable state; purpose-built memory indexes serve hot gameplay paths. Never query SQL from block/movement event hot paths.
- Update the roadmap or architecture documentation when a slice establishes or changes a project-wide decision.
- Run `./gradlew clean build` before handing off a completed slice. Use the ignored root `server/` workflow for real Paper behavior when a slice reaches an integration boundary.
- Preserve unrelated user changes and keep commits scoped to the active slice.
