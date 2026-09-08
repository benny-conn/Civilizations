# Finite diamond supply (S4)

S4 supplies a preparation tool and runtime safeguards. S5 still owns the integrated
world/dimension release audit. Neither a manifest import nor an activation hash certifies
that a live server is ready for players.

## Prepare the unopened world

Use `tools/scarcity/prepare_world.py` with Python 3.12+, `nbtlib==2.0.4` and NumPy.
Install the pinned tool dependencies with `pip install -r tools/scarcity/requirements.txt`.
This first tool accepts WorldPainter's 26.x Overworld export layout, inline zlib region
chunks, full chunk-aligned bounds at Y -64..319, and at most a 2048-square map. It refuses
missing/non-full/inhabited chunks, player data, additional dimension/entity/POI region
files, and existing output directories. It does not generate chunks or edit a running
world. An 8-byte WorldPainter timestamp lock is permitted under a shared OS file lock;
a Paper lock is rejected. Keep WorldPainter and Paper closed for preparation.

The JSON specification requires `format: 1`, integer `seed`, six inclusive `bounds`
values `[minX,minY,minZ,maxX,maxY,maxZ]`, and `deposits`. Each deposit has a unique `id`,
nonoverlapping six-coordinate `bounds`, and an exact `ore-count` from 1..65536.
Optional `container-policy` defaults to `reject`; `strip-selected` explicitly removes
raw diamond/ore/block and diamond-equipment item stacks, including nested item contents.
Unrelated inventory contents remain. Lazy loot tables and merchant offers always block
preparation and require a separate supply decision. No configuration is hot-reloaded.

```sh
python tools/scarcity/prepare_world.py /path/to/unopened-export deposits.json \
  --output /path/to/new-prepared-world --unopened-export --report preparation.json
python tools/scarcity/prepare_world.py /path/to/new-prepared-world deposits.json \
  --report prepared-audit.json
python -m unittest discover -s tools/scarcity -v
```

The first pass inventories the entire declared world. Preparation replaces all original
diamond ores with stone/deepslate, then selects the exact budget from stone/deepslate
inside each box using SHA256-ranked coordinates and the supplied seed. It never replaces
air, buildings, water or other ores to make a deposit. A second independent read-only run
must confirm exact per-box counts, zero ore outside boxes, and zero selected item stacks,
lazy tables and offers. Preserve both reports, specification, source/output file hashes,
and the code commit privately. A failed preparation leaves `PREPARATION-INCOMPLETE`;
discard that disposable output and start from the untouched source. Do not resume it or
load it on Paper. The source export is never rewritten.

The audit counts physical ore and item stacks, not an exact global diamond balance.
Fortune, Silk Touch, crafting, placement, ordinary transport, picked-up gear and smelting
remain legitimate transformations of existing supply. These are not regenerated from SQL.

## Activate runtime restrictions

Register all world manifests in the active SETUP season, including at least one DIAMOND
zone, before enabling policy:

```text
/civworld enable-diamonds equipment <prepared-audit-sha256> <audit reason>
/civworld diamond-status
```

`equipment` removes raw diamond supply and diamond equipment from generated container
loot and vault rewards, rejects new merchant offers, and blocks purchases from existing
villagers or standalone merchant GUIs. New creatures carrying selected equipment/items
are denied at spawn, including plugin/custom spawns; existing animals are not wiped on
chunk load. `raw-only` permits diamond equipment outputs while denying diamonds, ore
items and storage blocks. Equipment scarcity must not be claimed in that mode. These
choices are explicit command arguments, not YAML defaults. Default before activation is
OFF; registration alone leaves gameplay unchanged.

Migration **17** stores the mode, season, audit SHA256, actor, reason and time permanently.
The hash is a staff attestation to the reviewed artifact, not a parser for the audit file
or an automatic release gate. Activation requires loaded identity/height matches and an
unopened experiment with no battle history or unresolved exposure damage in **any season**.
It freezes new manifests/zones and cannot be changed by restart, phase or season selection.
Retrying the same mode/hash is idempotent. A different setup requires a future explicit
reset/release lifecycle. Runtime restrictions apply server-wide after activation, including
other dimensions; they fail closed for selected outputs while the runtime is loading.

## Reconstruction invariant and historical work

Resource-bearing blocks cannot enter battle/exposure destruction or placement replay.
The shared application policy covers diamond ores/blocks, other vanilla ores and principal
resource storage blocks; see `ReconstructionResourcePolicy` for the exact set. This is an
always-on reconstruction invariant, independent of scarcity activation. Normal authorized
mining and building outside the journaled paths retain existing protection rules.

Both application journals reject selected resource states. New repair assessments reject
historical resource reports rather than taking payment for prohibited restoration. Both
Paper repair runners check saved original **and** changed states before applying work;
unsafe historical work pauses without advancing its cursor, changing its target, or
silently discarding its payment. Ordinary stone restoration remains available. This does
not reclassify history or automatically refund/cancel old jobs. Existing repair cancel
commands and an explicit operator settlement are needed for a historical server; S4
activation deliberately refuses that conversion. Do not edit SQL to work around it.

## Release boundary and verification

S4's isolated fixture loaded a copy of the prepared 2048 world on Paper 26.2-121. Six
sampled ore coordinates survived import. Server-thread probes verified native mining,
resource admission denial, historical battle execution refusal and exposure pause
admission, no second harvest, and ordinary stone restoration. The old repair items were
injected into runner boundaries by the disposable probe; this was not a multiplayer battle
or a persisted paid-job crash test. Unit tests cover journal rejection and historical
assessment; existing durable cursor/payment tests remain in the suite.

Native `LootTable.fillInventory` calls exercised actual loot generation: identical seeds
produced 76 selected diamond outputs before activation and zero after, preserving 220 other
outputs. Vault dispatch and purchase filtering are adapter tests, not a client-operated
vault/trade session. [Paper container-loot contract](https://jd.papermc.io/paper/26.2/org/bukkit/event/world/LootGenerateEvent.html)
and [purchase event](https://jd.papermc.io/paper/26.2/io/papermc/paper/event/player/PlayerPurchaseEvent.html)
explain why purchase-time checks are needed in addition to offer acquisition.

Before S5 can accept a release, establish every accessible dimension/border, prevent
access to unvalidated chunks, register portal/cane/cattle policy, and inspect world items,
entities, prefilled and lazy containers, trials, trades and plugin reward/import paths.
Commands, external rollback tools, custom nested loot and other plugins that directly
edit inventories/worlds remain trusted administrative supply paths; they need an explicit
operating policy. CoreProtect rollback must never recreate harvested ore or ledger animals.
Mob drops from legitimately picked-up items must not be deleted as newly generated supply.

The isolated fixture enables Paper anti-xray mode 1 for both diamond ores to Y64; retain
this world-specific setting when assembling S5. Observed idle/probe tick times are a smoke
measurement, not a multiplayer load benchmark or proof against client x-ray. Packet-level
obfuscation and gameplay discoverability still need S5 verification. Keep exact deposit
coordinates/seed artifacts staff-only and provide intended prospecting clues.
[Paper anti-xray guidance](https://docs.papermc.io/paper/anti-xray/).
