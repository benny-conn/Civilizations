# Civilizations

Civilizations is an in-progress Paper plugin for civilization, territory, economy, and warfare gameplay.

The architecture rework is complete. See [TODO.md](TODO.md) for the prioritized product roadmap, [docs/architecture.md](docs/architecture.md) for the stable dependency and persistence boundaries, and [docs/worktree-roadmap.md](docs/worktree-roadmap.md) for the dependency-aware multi-worktree queue for net-new MVP work.

The live core includes pure claim geometry and indexing, versioned relational persistence, durable season selection/phases, preselected landless civilization rosters, leadership, validated claim placement, centralized land protection, player war declaration and surrender commands, hostile-claim-entry battle activation, a durable war/timed-battle lifecycle, a first-write-wins battle damage journal, immutable resolution-time damage reports, and exact civilization treasury accounts backed by an immutable idempotent ledger.

The incomplete pre-rework commands, mutable model graph, menus, scheduled tasks, adapters, and JSON-blob datastores have been deleted. Paper listeners protect claims through the application policy. During an active battle in the global `WAR` phase, snapshotted participants may break or place simple single blocks in either side's claimed land. The listener cancels the original event, commits its first-write-wins journal record off-thread, then revalidates and applies the mutation on the server thread. Block entities, multi-place operations, entities, explosions, and unsafe cascading blocks remain protected.

## Current platform

- Minecraft/Paper 26.2, compiled against Paper build 112
- Java 25
- Kotlin 2.4.10
- Gradle 9.5 via the checked-in wrapper

The bundled runtime has only Kotlin stdlib and the packaged SQLite JDBC driver as implementation dependencies. Plugin lifecycle, commands, configuration, messages, and events use Paper/Adventure directly. VaultAPI is compile-only and its JitPack repository is group-restricted: when a server supplies Vault and a player-economy provider, one narrow Paper adapter bridges player wallets to Civilizations treasuries. Foundation and the legacy coroutine helper remain absent.

## Build

No global Gradle or Kotlin installation is required. The wrapper also uses a Java 25 toolchain and can provision one when necessary.

```bash
./gradlew clean build
```

The deployable plugin is written to `build/libs/Civilizations-0.0.16-BETA.jar`.

## Codex worktrees

The checked-in Codex local environment at `.codex/environments/environment.toml` makes new worktrees self-preparing. Its idempotent setup runs `./gradlew --no-daemon testClasses`, which downloads the checked-in Gradle distribution and provisions the configured Java 25 toolchain when the host does not already have it.

The environment also exposes **Clean build**, **Run tests**, **Set up Paper**, and **Run Paper** actions. Each worktree gets its own ignored `server/` directory on demand; the root server fixture, its worlds, and its SQLite data are intentionally not copied through `.worktreeinclude`.

Before assigning parallel tasks, use the lanes and merge order in [docs/worktree-roadmap.md](docs/worktree-roadmap.md). In particular, schema/repository changes are sequential, as are Paper runtime/lifecycle changes.

## Run

Run the plugin on Paper 26.2 with Java 25. Copy the built JAR into the server's `plugins` directory and restart the server. Civilizations packages its selected SQLite driver and stores its authoritative data in `plugins/Civilizations/civilizations-v2.db` by default.

The current build has been smoke-tested on Paper 26.2 build 112 through season creation, offline-UUID roster provisioning, claim creation, phase changes, clean shutdown, restart/index recovery, claimed-versus-wilderness explosion behavior, battle-mutation and hostile-entry listener registration, native war/admin command registration, and incremental schema migrations followed by clean restarts. The Foundation-free native Paper build also passed consecutive boots with Civilizations as the only installed plugin while loading the previous `v2:` configuration layout and existing databases. War/battle persistence, timer recovery, hostile-entry activation, bounded entry backpressure, damage-journal durability, and the cancel/journal/revalidate/replay event flow are covered by real-SQLite runtime tests and focused Paper-adapter tests. Protection and entry-candidate decisions use only the published in-memory snapshot and claim index; event handlers never query SQLite.

A2 additionally passed a real-Paper schema-6-to-7 upgrade with no Vault installed, opening-account creation, exact admin adjustment, ledger inspection, clean shutdown, and a second boot preserving the treasury and ledger. Focused tests cover the Vault adapter itself; a live Vault-plus-economy-provider checkpoint remains for the server playtest environment that supplies those plugins.

## Administration

The native Paper `/civadmin` command requires `civilizations.admin`, which defaults to operators. Run `/civadmin` for help. The current commands support:

- runtime/active-season status, including open-war and active-battle counts;
- season creation, selection, and phase changes;
- landless drafts and idempotent offline-UUID provisioning;
- membership assignment, leadership transfer, and activation;
- civilization listing and rectangular admin claim creation;
- war/battle listing and inspection, war activation/closure/cancellation, and battle force-resolution/cancellation with required audit reasons;
- explicit roster moves that preserve immutable participant sides in already-started battles.
- civilization treasury balance inspection, audited adjustments, and explicit reconciliation of ambiguous player-wallet transfers.

The player-facing `/civ` command exposes war status/declaration/surrender plus `balance`, `deposit <amount>`, and leader-only `withdraw <amount>`. Player wallet operations require server-provided Vault plus an economy plugin; Civilizations never creates a Vault bank account. Its player permissions default to players so LuckPerms can narrow access. Declaration is allowed in `SETUP`, `PEACE`, and `WAR`; a battle can start only during `WAR`. Entering or leaving the global `WAR` phase additionally requires the operator-default `civilizations.admin.phase.war` permission.

Claim size/count/connectivity rules, safe gameplay phase gates, and the database filename are in `config.yml`; see [docs/configuration.md](docs/configuration.md) for the key reference and configuration contract. Configuration is validated and installed at startup, so changes currently require a server restart. Mutations are serialized on a plugin-owned storage thread, then a refreshed snapshot and claim index are installed on the Paper thread before completion is reported.

Operators have the explicit `civilizations.admin.bypass` permission. Ordinary members may mutate their own claims; outsiders cannot build, break, use containers/switches, move fluids or pistons across a border, damage protected entities, or PVP inside claimed land. Movement and teleportation are not blocked by land ownership, but a horizontal block transition or teleport into a hostile claim can start an eligible declared battle. Entry candidates are resolved from published memory and coalesced behind a bounded queue before durable work runs off-thread.

## Local test server

The repository includes a reproducible development server workflow. Server binaries, worlds, logs, plugin data, and configuration live in the ignored `server/` directory at the repository root.

Set it up without starting it:

```bash
./scripts/setup-test-server.sh
```

Build and deploy the current plugin, then start Paper:

```bash
./scripts/run-test-server.sh
```

The server defaults to 1–2 GB of memory. Override that when needed:

```bash
SERVER_MIN_MEMORY=2G SERVER_MAX_MEMORY=4G ./scripts/run-test-server.sh
```

Paper is checksum-pinned through `gradle.properties`. Running the setup script creates `server/eula.txt` with acceptance of the [Minecraft EULA](https://aka.ms/MinecraftEULA).

## Architecture status

The modernization and architecture cleanup are complete. There is one live domain/application model, one relational store, one runtime owner, one protection policy, and thin native Paper adapters. Removed unfinished systems remain available in Git history if a future feature needs product ideas, but their architecture should not be restored. Remaining roadmap items are net-new gameplay and operational work on these boundaries.
