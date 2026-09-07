# Managed cattle mechanics experiment — 2026-09-07

Decision: proceed to S3 using durable reservations, identity reconciliation and an explicit
pending-death state. Ordinary asynchronous event logging is insufficient. This experiment
adds no production animal rules, population registry, configuration settings or migration.

## Fixture and evidence

Paper **26.2-121 / a2a42c5**, Java 25, Civilizations 0.0.16-BETA; isolated worktree
`civilizations-s0-animals/server`, bound to `127.0.0.1:25579`. Desktop server was not used.
The source and build helper are in `experiments/animals`. The probe requires the fixture
marker and port, accepts console commands only, and must never be deployed to a real world.
Its synchronous saves and file writes intentionally expose test boundaries; they are not
production persistence patterns. The helper compiles against Paper's downloaded server
implementation for native feeding and the explicit checkpoint flush only; no NMS dependency enters Civilizations.

Evidence logs are under that fixture's ignored `server/verification/`. Checked-in
`experiments/animals/observations.txt` preserves the significant observation lines.
No Minecraft client participated. Feeding used an unconnected native survival ServerPlayer
and real `mobInteract`; AI breeding used ticking live cows. No synthetic Bukkit breeding
or death events were dispatched.

## Results

| Experiment | Observation | S3 consequence |
| --- | --- | --- |
| Native AI breeding, cancelled | Event saw an invalid/unspawned child, adult ages 0, active love timers. Next tick: love 0/0, ages 0/0, no calf or XP. | Cancel at the breed event before reserving. Set our own durable cooldown after authorization; cancellation itself does not apply one. |
| Native AI breeding, accepted | Breed event preceded XP and `BREEDING` spawn. Next tick child was valid and parent ages were 5999/5999 (vanilla 6000-tick cooldown). | Cancelling only the subsequent spawn is too late to roll back earlier parent/XP effects. |
| Native feeding then cancelled birth | Four wheat became two after feeding two parents; remained two after native birth cancellation. | Food is consumed before the birth event. Do not promise refunds. Reject known-ineligible feeding at the interaction boundary; reservation races may still consume food. |
| Cancel lethal damage | Death callback saw health 0. Cancellation with revive health 4 returned a living, valid cow at health 4. No reward entities appeared. | Cancel while recording the death intent, and freeze/protect the pending animal from repeated interactions and damage. |
| Complete delayed death | Setting health to zero later fired a **second** death callback. Exactly one controlled diamond and seven XP were emitted. | Use the operation ID and an explicit completing state. Do not record a second logical death or reroll/replay rewards on recovery. |
| Chunk unload/reload | Live UUID lookup became null; entity load restored the same UUID and logical PDC. | Unload changes presence, never the durable life state. |
| Clean restart | Saved UUID and logical PDC recovered after loading the entity section. | Marker-based reconciliation is feasible. |
| Unsaved removal then hard halt | Removal bypassed the death callback; restart recovered the previously saved animal. | Tombstones must suppress resurrected world snapshots. Other-plugin deletion cannot be treated as a reliable death callback. |
| Durable intent then hard halt before removal | Fsynced intent survived while the animal remained alive in its saved chunk. | Pending operations must be reconciled; do not assume database intent means Minecraft mutation completed. |
| Saved child then hard halt before acknowledgment | Child survived with its operation still PREPARED. | Never retry an ambiguous birth by blindly spawning another animal. |

An initial birth checkpoint used a nested console `save-all flush` dispatch immediately
before halting. That queued command had not run, and the child was absent on restart.
The final probe invokes the native synchronous flush directly before halting; this
distinguishes a world mutation from a completed world save.

The reward test replaces randomized loot with one diamond and sets XP to seven to make
counts deterministic. The initial counter observed duplicate notifications for an orb;
counting unique UUIDs fixed the measurement and the rerun passed. This is not evidence
of two physical XP rewards. The probe does not validate player kill credit, looting,
advancements, sounds, or compatibility with every third-party death listener.

The crash intent uses an fsynced fixture file, and the birth checkpoint uses fixture YAML;
these deliberately model the independent world/operation persistence boundary. They do
**not** test the future SQLite transaction implementation. S3 must add its own transaction,
duplicate-operation and crash-injection tests once that implementation exists. Hard halts
are JVM termination tests, not simulations of disk failure or partial backup restoration.

## S3 implementation contract

1. SQL owns animal IDs, parent reservations, birth/death operation IDs, maturity and
   cooldown deadlines. PDC links entities to those records; UUID visibility is only presence.
2. Cancel native managed births and suppress repeated attempts while a bounded worker
   durably reserves both parents. Revalidate on the server thread before applying one child.
   A failed/ambiguous apply is unresolved, not permission to respawn. Snapshot effective rules.
3. Cancel initial death and hold the animal inert and unavailable. Commit a durable intent
   off-thread, then complete on-thread with a completion guard. Capture needed damage/loot
   context; do not assume a later `setHealth(0)` retains the original killer or loot semantics.
4. A restart reconciles pending records and tombstones as entities load. Known-dead entities
   reappearing from an older save must not breed or pay rewards. Unknown or conflicting
   identities remain quarantined; absence alone never authorizes a replacement.
5. Reward delivery cannot be made atomic with SQL and Minecraft saves. Never replay an
   ambiguous payout automatically. Keep unresolved outcomes visible; choose a conservative
   loss/repair policy instead of claiming exactly-once drops. Other listeners cancelling
   completion also require explicit reconciliation.
6. Start S3 with the durable cattle lifecycle and tests, then the Paper adapter. Keep scarcity
   opt-in, settings validated, population counts split into alive/dead/unresolved, and no
   irregular zones, editing workflow or boundary previews. Proposed population numbers
   and long breeding/maturity durations remain proposals until S3 defines defaults.

Paper documents cancellation and revive-health behavior in
[EntityDeathEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/EntityDeathEvent.html).
The local 26.2-121 `Animal` bytecode additionally confirms food consumption precedes love
mode, cancelled breeding resets love, and successful finalization applies cooldown/XP
before adding the child. Runtime observations above are the acceptance evidence.
