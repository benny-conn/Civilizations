# Civilizations architecture

Civilizations is being migrated incrementally from a Foundation-centered plugin into a domain-centered Paper plugin. Each migration slice should remain independently buildable and mergeable.

## Dependency direction

Dependencies point inward:

```text
Paper commands, listeners, and UI
                 |
                 v
        Application services
                 |
                 v
       Plain Kotlin domain model

Persistence, Paper world access, economy, configuration, and messaging implement ports owned by the application layer. They do not leak framework types into the domain.
```

The domain and application layers must not import Bukkit/Paper, Foundation, Vault, JDBC, configuration, command, or menu types. They should be testable with the ordinary JVM test task.

Foundation remains a temporary adapter for the legacy command, menu, settings, and localization code. New architectural code must not depend on it.

## Runtime ownership

- Paper world/entity access and live-state mutation belong to the server thread.
- Persistence and other blocking I/O belong to plugin-owned background execution.
- Results cross that boundary as immutable domain values.
- The database is durable state; purpose-built in-memory indexes serve hot event queries.
- Long-running work such as war resolution and reconstruction is represented as persisted jobs rather than anonymous scheduler tasks.

## Claim model

The first migrated subsystem is the claim geometry and spatial index under `domain.claim` and `application.claim`.

- Claims are inclusive, immutable 2D rectangles identified by integer X/Z coordinates and a stable world identifier.
- Rectangle construction normalizes reversed selection corners once.
- Area, containment, overlap, and edge adjacency are pure operations.
- Corner-only contact is not edge adjacency.
- The spatial index maps world/chunk pairs to candidate claim IDs and performs an exact rectangle check after candidate lookup.
- Arbitrarily shaped territory remains a collection of rectangles. It is not converted into a materialized polygon or block set.
- The index is derived state and is rebuilt from authoritative claims during startup.

The legacy `Region` and `ClaimUtil` remain in place for this first slice. A later slice will load authoritative claim rows, build this index, and route Paper protection events through it before deleting the legacy implementation.
