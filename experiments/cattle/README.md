# Cattle Paper fixture

Destructive fixture only; never install this probe on the Desktop server. It requires
port 25581 and a `.cattle-experiment` file in the server working directory. Commands
accept console senders only. Crash commands deliberately block the fixture's SQL worker
and immediately halt its JVM after a world save.

Use a fresh isolated worktree `server/`, Paper 26.2-121, Java 25, and the production
Civilizations JAR from `./gradlew deployTestServerPlugin`. Stop Paper before replacing
any JAR. After Paper has downloaded libraries and generated a world, create the marker,
build the probe from the worktree root, and restart:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk python3 experiments/cattle/build.py
```

Set `server-ip=127.0.0.1`, `server-port=25581`, and `pause-when-empty-seconds=0`.
For this fixture use 4-second maturity and 2-second breeding cooldown before activation.
Run each command separately and wait for completion/output:

1. `cattleprobe setup` builds a small arena, keeps its chunks ticking and creates an
   unregistered cow while cattle is OFF. It prints the actual world UUID.
2. Create a SETUP season and register that world with a CATTLE habitat covering
   x/z 1..14 and y 64..70. Use the real season/world UUIDs in the manifest.
3. Create two seed slots at (8,64,8) and (10,64,8). Run `civcattle enable seeds.yml`,
   `civcattle seed`, wait, then repeat `seed`. Status must report two ALIVE rows only.
4. `cattleprobe check` verifies the old cow is contained; `cattleprobe deny` checks
   unauthorized CUSTOM spawn cancellation.
5. `cattleprobe breed`, then wait 12 seconds. Expect three registered cows; the calf
   matures according to the shortened wall-clock timer.
6. `cattleprobe birthcrash` injects a worker barrier after the authorized spawn event.
   The world is flushed and the JVM halted before SQL acknowledgment. Confirm SQLite
   has three ALIVE and one APPLYING row plus two parent reservations. Restart and wait:
   status must show four ALIVE rows, zero reservations, and four managed entities.
7. `cattleprobe deathcrash` saves the living cow, applies native lethal damage and halts
   after durable completion without saving the world again. Restart; `cattleprobe deadcheck`
   verifies the tombstoned saved entity was removed. Repeating seed must not replace it.
8. `cattleprobe unload`, wait 6 seconds, verifies live absence then the same restored IDs.
9. `cattleprobe pendingcrash` blocks the worker between death prepare and completion,
   saves and halts. Restart; `cattleprobe pendingcheck` verifies containment and prints
   its entity UUID. Run `civcattle settle-death <that UUID>`; expect no additional death
   callback/reward and the existing operation to become COMPLETED.
10. Stop the fixture. Keep `logs/latest.log` under `server/verification` between runs.

The probe uses native AI breeding and lethal damage, not synthetic Bukkit death/breed
events. The worker barrier uses reflection only inside this fixture. No NMS or testing
hooks are introduced into production code. This does not substitute for multiplayer
interaction or full third-party plugin compatibility testing.
