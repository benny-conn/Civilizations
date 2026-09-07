# Sugar-cane experiment (S2a)

Default: **OFF**. Registering a world never enables gameplay restrictions. Schema 13 adds
an explicit, durable cane policy; it does not activate diamond, cattle or portal rules.

## Activation

Prepare all intended worlds and SUGAR_CANE zones through [world manifests](world-manifests.md).
Select the intended season in SETUP, with all its registered worlds loaded under their
recorded UUIDs and build heights. Then an operator with `civilizations.admin` can run:

```
/civworld enable-cane 3 regional cane experiment
/civworld cane-status
```

The height argument is required (1–3 blocks; 3 retains vanilla's usual column cap).
The remaining text is a required audit reason of 1–256 characters. The command records
season, height, actor, reason and timestamp in SQL. There are no new YAML settings and no
restart requirement. Success is reported after the policy reaches the published runtime
snapshot. Same-season/same-height retries preserve the original audit record; conflicting
parameters reject.

This is **one server-wide experiment**, not a toggle. Activation freezes imports for that
season and persists across phase changes, active-season selection, archival and restarts.
There is no deactivation, rebalancing or replacement command: those require a future
explicit audited reset/release lifecycle. Prepare the zones before enabling. In particular,
this prevents switching seasons or entering another dimension to gain an unrestricted farm.
The Desktop test server has not been activated or updated by this coding slice.

## Gameplay contract

- A proposed cane column must fit wholly inside one SUGAR_CANE zone of the activated season,
  match its world key and UUID, and stay within the snapshotted height limit. Bounds are
  inclusive XYZ blocks. A column cannot bridge two adjacent vertical zones.
- Outside those zones, including unregistered worlds/dimensions and replacement UUIDs,
  ordinary planting and growth are denied. Operators have no planting bypass. Placement
  receives a short explanation; `/civworld here`, `inspect`, and `cane-status` show geography
  and current enforcement. Claims still apply independently; the listener never grants
  access to somebody else's farm.
- Existing cane may be harvested and transported. Replanting is checked like any placement.
  Vanilla piston harvesting remains allowed: cane breaks instead of moving to a new growing
  location. Moving soil/water cannot bypass the next growth check. Cane is already excluded
  from the simple solid-block battle/exposure journal, so this slice does not add a crop
  restoration route.
- Natural growth, player placement (including multi-place), fertilization changes, spread,
  formation and entity block placement have cancellation adapters. A fertilizer batch is
  checked against all proposed block states, and one invalid cane target cancels the batch.
  Activated batches exceeding 4096 changed blocks fail closed. Vanilla Java bonemeal did
  not grow cane in the Paper 26.2 fixture; the batch hook also covers plugins that emit it.
- No world scan runs. A cane check reads only the already-loaded target chunk, up to six
  vertical neighbors, and an immutable spatial index. It never accesses SQL or loads a
  chunk. While Civilizations is loading or unavailable, cane creation fails closed through
  the listener; unrelated crops are unchanged.

This enforces ordinary farm locations, **not an audited total item supply**. Existing
world-generated cane, container loot, trades and inventory imports are not removed.
WorldPainter generation, privileged `/setblock`/FAWE edits, rollback tools and plugins that
write blocks without cancellable events are trusted administration, not covered mutation
routes. A later supply audit and controlled world/administration release are still needed.
Removing or disabling the plugin necessarily removes its event enforcement.

## Evidence

The full clean build passes 173 tests, including schema-12 upgrade preservation with policy
OFF, activation validation/history immutability, frozen manifest imports, exact column and
identity checks, phase/season selection behavior, runtime restart recovery, and Paper
placement/fertilization adapters.

An isolated Paper 26.2 build-121 fixture tested actual vanilla growth with `Block.randomTick()`
before/after activation, the configured height-2 cap, native bonemeal behavior, powered piston
harvesting with root retention, and ordinary breaking. Fertilizer and alternate-dimension
checks additionally dispatch events through Paper's actual plugin manager. The same policy
and checks survived restart. Player placement is covered by adapter tests, not a client
playtest. Local evidence and the next step are in the [progress record](scarcity-world-plan.md#s2a-implementation-progress).

API references: [BlockGrowEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/block/BlockGrowEvent.html),
[BlockFertilizeEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/block/BlockFertilizeEvent.html),
and [Block](https://jd.papermc.io/paper/26.2/org/bukkit/block/Block.html).
