# Civilizations

Civilizations is an in-progress Paper plugin for civilization, territory, economy, and warfare gameplay.

The plugin is undergoing an incremental architecture rework. See [TODO.md](TODO.md) for the prioritized roadmap and [docs/architecture.md](docs/architecture.md) for the dependency and persistence boundaries followed by new code.

## Current platform

- Minecraft/Paper 26.2, compiled against Paper build 112
- Java 25
- Kotlin 2.4.10
- Gradle 9.5 via the checked-in wrapper
- MineAcademy Foundation 6.10.1

Foundation is retained as a transitional dependency because commands, menus, configuration, serialization, conversations, and economy hooks currently depend on it throughout the plugin. It is shaded and isolated inside the plugin JAR, and its optional integration dependencies are not bundled.

## Build

No global Gradle or Kotlin installation is required. The wrapper also uses a Java 25 toolchain and can provision one when necessary.

```bash
./gradlew clean build
```

The deployable plugin is written to `build/libs/Civilizations-0.0.16-BETA.jar`.

## Run

Run the plugin on Paper 26.2 with Java 25. Copy the built JAR into the server's `plugins` directory and restart the server. Vault is optional at startup but required for economy-backed features.

The current build has been smoke-tested through a complete Paper 26.2 build 112 startup and clean shutdown using the default SQLite configuration.

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
