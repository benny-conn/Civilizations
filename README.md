# Civilizations

Civilizations is an in-progress Paper plugin for civilization, territory, economy, and warfare gameplay.

The plugin is undergoing an incremental architecture rework. See [TODO.md](TODO.md) for the prioritized roadmap and [docs/architecture.md](docs/architecture.md) for the dependency and persistence boundaries followed by new code.

The V2 core is now the live runtime. It includes pure claim geometry and indexing, versioned relational persistence, durable season selection/phases, preselected landless civilization rosters, leadership, and validated claim placement.

Legacy commands, listeners, scheduled tasks, and JSON-blob datastores are temporarily quarantined rather than running beside V2 as a second source of truth. Until the next protection slice lands, this build is an administration/testing milestone rather than a playable server plugin.

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

## Run

Run the plugin on Paper 26.2 with Java 25. Copy the built JAR into the server's `plugins` directory and restart the server. V2 packages its selected SQLite driver and stores its authoritative data in `plugins/Civilizations/civilizations-v2.db` by default.

The current build has been smoke-tested on Paper 26.2 build 112 through season creation, offline-UUID roster provisioning, claim creation, phase changes, clean shutdown, and restart/index recovery.

## V2 administration

The native `/civadmin` command requires `civilizations.admin`, which defaults to operators. Run `/civadmin` for help. The focused cutover commands currently support:

- runtime/active-season status;
- season creation, selection, and phase changes;
- landless drafts and idempotent offline-UUID provisioning;
- membership assignment, leadership transfer, and activation;
- civilization listing and rectangular admin claim creation.

Claim size/count/connectivity rules and the V2 database filename are in `config.yml`. Mutations are serialized on a plugin-owned storage thread, then a refreshed snapshot and claim index are installed on the Paper thread before completion is reported.

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
