# World manifests (S1)

S1 registers immutable resource geography. It does **not** alter mining, spawning, breeding,
crop growth, portals, borders, world generation, or claims. Enforcement is always OFF;
there is no activation command or configuration switch in this slice.

## Operator workflow

1. Load the intended world through Paper or your world manager. Run `/civworld worlds`
   for its exact namespaced key, UUID, and inclusive build-height range.
2. Create/select a season in SETUP with `/civadmin season`. Record the season UUID.
3. Create `plugins/Civilizations/manifests/` and place a UTF-8 `.yml` file there using
   the example below. Replace the example IDs, bounds, and zones with the intended values.
4. Run `/civworld validate sample.yml`. This checks the loaded identity, geometry,
   existing registrations, and season without saving anything.
5. Run `/civworld import sample.yml`. Success means SQL committed and the in-memory
   index was published. `/civworld list`, `/civworld inspect <manifest-uuid>`, and
   player-only `/civworld here` inspect it; `here` uses the active season and exact live
   world identity. All commands require `civilizations.admin` (operators by default).
6. Restart and inspect again. YAML is never read at startup or automatically reloaded;
   accepted SQL records recover even if the authoring file is moved or deleted.

New registration requires SETUP. A byte-identical repeat returns the original record,
including actor/time, even after a phase change. Changed bytes under the same manifest ID
are rejected (including comments/formatting, because the source SHA256 changes). A world
key or UUID cannot be rebound under another manifest/season. Revision is a positive
provenance label, not an update mechanism. Replacement, season reuse, and activation need
an explicit audited lifecycle in a later slice. Do not edit SQL to bypass this boundary.
An unloaded/replaced world is reported by inspection; index lookup also requires its UUID.
Loaded identity is captured on the server thread when an import is submitted.

## Format 1

```yaml
format: 1
id: 00000000-0000-0000-0000-000000000020
season: 00000000-0000-0000-0000-000000000001
revision: 1
world:
  key: minecraft:scarcity_prototype_2048
  uuid: 00000000-0000-0000-0000-000000000030
bounds: {min-x: 0, min-y: -64, min-z: 0, max-x: 2047, max-y: 319, max-z: 2047}
zones:
  - id: western-diamond
    resource: DIAMOND
    bounds: {min-x: 100, min-y: -60, min-z: 100, max-x: 300, max-y: 0, max-z: 300}
  - id: western-cattle
    resource: CATTLE
    bounds: {min-x: 100, min-y: 63, min-z: 100, max-x: 300, max-y: 150, max-z: 300}
  - id: river-cane
    resource: SUGAR_CANE
    bounds: {min-x: 400, min-y: 62, min-z: 100, max-x: 450, max-y: 90, max-z: 500}
```

All fields are required; no defaults. Unknown/duplicate keys, noncanonical UUIDs,
noninteger coordinates/revisions, unsupported resource types, and YAML collection aliases
are rejected. Paths use simple 1–64 character ASCII alphanumeric/underscore/hyphen basenames
plus `.yml`; traversal and symlinks outside the manifest directory are rejected. Files are
limited to 1 MiB, nesting to 32 levels. Reading/parsing and all SQL use the storage worker.
This is an explicit authoring import, not a new `config.yml` section; it needs no restart.

Geometry uses inclusive integer block coordinates in all three axes. Same-resource zones
may not intersect, including shared boundary blocks. Different resources may overlap, and
same-resource zones with disjoint Y ranges are allowed. Zones must fit inside world bounds;
world Y bounds must fit inside the loaded world's build height. Namespaced world keys and
lowercase zone IDs (1–64 letters/digits/hyphens/underscores) are validated. Safety limits:
1–256 uniquely named zones per manifest, 32 manifests total, horizontal world span at most
8192 blocks per axis, x/z within ±29,999,984, Y within -2048..2047, and at most 262,144
zone/chunk entries globally. These are bounded execution limits, not balancing settings.

Schema 12 stores the season/world binding, bounds, revision, original SHA256, importing
actor/time and resource zones. SQL rejects updates/deletes of history and invalid zone
containment/overlap. The derived chunk index includes season, world key and UUID, then
filters exact XYZ bounds; political claim ownership never changes resource geography.
No animal registry, supply budget, crop policy, or portal policy is implied by registration.

The Desktop prototype's `resource-layout.proposed.json` remains a conceptual authoring
proposal, not this format. Converting it requires choosing exact rectangular 3D bounds and
loading the export to obtain its real UUID. The prototype has not been registered by S1.

## Verification

Automated coverage includes randomized index/full-scan comparisons, negative chunks and
inclusive edges, immutable copies, invalid geometry, strict parsing/file confinement,
read-only validation, transaction rollback, SETUP/identity gates, duplicate and reassignment
rejection, SQL history guards, schema-11 upgrade preservation, and runtime restart recovery.
The dated Paper verification and remaining scope are recorded in
[scarcity-world-plan.md](scarcity-world-plan.md#s1-implementation-progress).
