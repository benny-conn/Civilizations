# Civilizations

Civilizations is an in-progress Paper plugin for civilization, territory, economy, and warfare gameplay.

The plugin is undergoing an incremental architecture rework. See [TODO.md](TODO.md) for the prioritized product roadmap, [docs/architecture.md](docs/architecture.md) for dependency and persistence boundaries, and [docs/worktree-roadmap.md](docs/worktree-roadmap.md) for the dependency-aware multi-worktree merge queue.

The V2 core is now the live runtime. It includes pure claim geometry and indexing, versioned relational persistence, durable season selection/phases, preselected landless civilization rosters, leadership, validated claim placement, centralized land protection, a durable war/timed-battle lifecycle, and a first-write-wins battle damage journal.

Legacy commands, scheduled tasks, and JSON-blob datastores are temporarily quarantined rather than running beside V2 as a second source of truth. V2 Paper listeners now protect claims. Active battles publish an immutable eligibility read model and the application can durably prepare simple block mutations, but there is intentionally no live war override until a Paper adapter can cancel an event, commit its journal record off-thread, revalidate the world state, and only then apply the mutation on the server thread.

## Current platform

- Minecraft/Paper 26.2, compiled against Paper build 112
- Java 25
- Kotlin 2.4.10
- Gradle 9.5 via the checked-in wrapper
- MineAcademy Foundation 6.10.1

Foundation is retained temporarily as the plugin lifecycle/settings host and because legacy source still imports it. The live V2 admin command uses Paper's command API directly, and no V2 domain, application, persistence, or runtime code depends on Foundation.

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

Run the plugin on Paper 26.2 with Java 25. Copy the built JAR into the server's `plugins` directory and restart the server. V2 packages its selected SQLite driver and stores its authoritative data in `plugins/Civilizations/civilizations-v2.db` by default.

The current build has been smoke-tested on Paper 26.2 build 112 through season creation, offline-UUID roster provisioning, claim creation, phase changes, clean shutdown, restart/index recovery, claimed-versus-wilderness explosion behavior, and incremental schema migrations followed by clean restarts. War/battle persistence, timer recovery, and damage-journal durability are covered against real SQLite and runtime restarts. Protection decisions use only the published in-memory snapshot and claim index; event handlers never query SQLite.

## V2 administration

The native `/civadmin` command requires `civilizations.admin`, which defaults to operators. Run `/civadmin` for help. The focused cutover commands currently support:

- runtime/active-season status, including open-war and active-battle counts;
- season creation, selection, and phase changes;
- landless drafts and idempotent offline-UUID provisioning;
- membership assignment, leadership transfer, and activation;
- civilization listing and rectangular admin claim creation.

Claim size/count/connectivity rules and the V2 database filename are in `config.yml`. Mutations are serialized on a plugin-owned storage thread, then a refreshed snapshot and claim index are installed on the Paper thread before completion is reported.

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

## Follow-up modernization

The platform migration is complete, but several gameplay systems still use compatibility APIs supplied by Foundation, including legacy chat formatting and Bukkit conversations. Removing Foundation should be a dedicated follow-up migration so each subsystem can be replaced and gameplay-tested rather than stubbed.
