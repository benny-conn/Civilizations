# Civilizations

Civilizations is an in-progress Paper plugin for civilization, territory, economy, and warfare gameplay.

The architecture rework is complete. See [TODO.md](TODO.md) for the prioritized product roadmap, [docs/architecture.md](docs/architecture.md) for the stable dependency and persistence boundaries, and [docs/worktree-roadmap.md](docs/worktree-roadmap.md) for the dependency-aware multi-worktree queue for net-new MVP work.

The live core includes pure claim geometry and indexing, versioned relational persistence, durable season selection/phases, preselected landless civilization rosters, leadership, validated claim placement, centralized land protection, a durable war/timed-battle lifecycle, a first-write-wins battle damage journal, and immutable resolution-time damage reports.

The incomplete pre-rework commands, mutable model graph, menus, scheduled tasks, adapters, and JSON-blob datastores have been deleted. Paper listeners protect claims through the application policy. Active battles publish an immutable eligibility read model and the application can durably prepare simple block mutations, but there is intentionally no live war override until a Paper adapter can cancel an event, commit its journal record off-thread, revalidate the world state, and only then apply the mutation on the server thread.

## Current platform

- Minecraft/Paper 26.2, compiled against Paper build 112
- Java 25
- Kotlin 2.4.10
- Gradle 9.5 via the checked-in wrapper

The runtime has only Kotlin stdlib and the packaged SQLite JDBC driver as implementation dependencies. Plugin lifecycle, commands, configuration, messages, and events use Paper/Adventure directly; Foundation, Vault, JitPack, and the legacy coroutine helper are absent from the build.

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

The current build has been smoke-tested on Paper 26.2 build 112 through season creation, offline-UUID roster provisioning, claim creation, phase changes, clean shutdown, restart/index recovery, claimed-versus-wilderness explosion behavior, and incremental schema migrations followed by clean restarts. The Foundation-free native Paper build also passed consecutive boots with Civilizations as the only installed plugin while loading the previous `v2:` configuration layout and existing schema-v4 database. War/battle persistence, timer recovery, and damage-journal durability are covered against real SQLite and runtime restarts. Protection decisions use only the published in-memory snapshot and claim index; event handlers never query SQLite.

## Administration

The native Paper `/civadmin` command requires `civilizations.admin`, which defaults to operators. Run `/civadmin` for help. The current commands support:

- runtime/active-season status, including open-war and active-battle counts;
- season creation, selection, and phase changes;
- landless drafts and idempotent offline-UUID provisioning;
- membership assignment, leadership transfer, and activation;
- civilization listing and rectangular admin claim creation.

Claim size/count/connectivity rules, safe gameplay phase gates, and the database filename are in `config.yml`; see [docs/configuration.md](docs/configuration.md) for the key reference and configuration contract. Configuration is validated and installed at startup, so changes currently require a server restart. Mutations are serialized on a plugin-owned storage thread, then a refreshed snapshot and claim index are installed on the Paper thread before completion is reported.

Operators have the explicit `civilizations.admin.bypass` permission. Ordinary members may mutate their own claims; outsiders cannot build, break, use containers/switches, move fluids or pistons across a border, damage protected entities, or PVP inside claimed land. Movement and teleportation are not restricted by land ownership.

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
