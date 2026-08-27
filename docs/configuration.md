# Civilizations configuration

Civilizations writes the default `config.yml` into the plugin data directory on first
startup. It validates the effective configuration before constructing the runtime. A
malformed value or an unsafe phase gate stops plugin startup and reports the relevant
YAML path instead of silently substituting zero or another fallback.

Configuration is loaded once at startup. Restart the server after editing it; there is
currently no `/reload` integration or partial live-reload behavior.

## Current keys

| Path | Default | Meaning |
| --- | --- | --- |
| `storage.database-file` | `civilizations-v2.db` | SQLite filename directly inside the plugin data directory. Paths outside that directory are rejected. |
| `claims.max-area` | `65536` | Maximum inclusive X/Z block area for one rectangular claim. Must be a positive integer. |
| `claims.max-count` | `32` | Maximum claim rectangles owned by one civilization. Must be a positive integer. |
| `claims.require-edge-connection` | `true` | Whether every additional rectangle must share a non-zero block edge with the civilization's existing land. |
| `gameplay.phase-gates.roster-changes` | `[SETUP, PEACE, WAR]` | Phases in which drafts, provisioning, membership, leadership, and activation may change. May contain only `SETUP`, `PEACE`, and `WAR`; active battle participant/side snapshots remain immutable. `[]` disables roster changes. |
| `gameplay.phase-gates.claim-creation` | `[SETUP, PEACE]` | Phases in which new claims may be created. May contain only `SETUP` and `PEACE`; `[]` disables claiming. |
| `gameplay.phase-gates.member-land-actions` | `[SETUP, PEACE, WAR]` | Phases in which members may ordinarily interact with their own claimed land. May contain only `SETUP`, `PEACE`, and `WAR`; `[]` freezes ordinary member actions in every phase. Explicit conflict capabilities and admin bypass remain separately authorized. |
| `gameplay.war.battle-duration-seconds` | `1800` | Duration snapshotted into a new war and used for each hostile-entry battle in that war. Must be from `1` through `31536000`; changes require restart and affect only later declarations. |

Phase names are case-insensitive when loaded, but the shipped file uses uppercase names
to match the durable season statuses. Duplicate or unknown phase names are rejected.

## Planned repair and economy keys

The economy ledger and repair-job slices will add validated settings for the following
settled Season One policy. These keys are documented here as planned behavior and are not
accepted by the current build yet.

| Setting | Planned default | Validation and meaning |
| --- | --- | --- |
| Initial civilization balance | `0` | Non-negative integral credits assigned through an idempotent opening ledger entry. |
| Restore-original unit price | `1` | Non-negative integral credits for each eligible `RESTORE_ORIGINAL_BLOCK` report entry. |
| Remove-placement unit price | `1` | Non-negative integral credits for each eligible `REMOVE_PLACED_BLOCK` report entry. |
| Victor share | `25%` | Percentage from `0%` through `100%` of an ordinary repair payment transferred to the victor. |
| Allow debt | `false` | When false, an ordinary repair cannot charge beyond the civilization's available balance. |
| Ordinary initiator roles | leader only | Valid civilization roles allowed to request a paid repair. Admin authorization remains separate. |

The implementation slice will choose and document the final YAML paths, parse them into
an immutable application-owned rules value, and snapshot the effective values into each
durable repair job. Changes will require a restart and will apply only to future jobs.

There will be no `admin-waives-cost` setting. A privileged admin repair command will name
the target civilization and execute the same repair operation with an audited
admin-sponsored funding context. It charges no civilization account and therefore pays
no victor share.

## Configuration boundary

YAML is an input adapter, not gameplay state. Infrastructure translates keys into plain
Kotlin rule values before application services are created. Application and domain code
must not depend on Bukkit configuration objects or look up strings such as
`claims.max-area` during an operation.

Settings may tune accepted policy inside code-enforced safety limits. They do not replace
durable season phases, conflict authorization, persisted rosters, claims, balances,
journals, or jobs. Values that define a long-running operation must be snapshotted into
its SQL record when that operation starts; a later config edit applies only to future
operations.

When adding a key:

1. Define an immutable application-owned rule value and inject it into the service that
   enforces it.
2. Parse and validate the YAML at the Paper boundary with an error that names the path.
3. Preserve hard lifecycle and data-integrity bounds in application code.
4. Add the key and its default to `config.yml` and this reference.
5. Test both an accepted override and malformed or unsafe input.
