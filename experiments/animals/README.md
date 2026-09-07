# Disposable Paper animal probe

This is a destructive mechanics experiment, excluded from the production build. It
removes test-world cows/items and some commands immediately halt the server JVM. Run
only in a fresh isolated worktree's `server/`, never the desktop server symlink.
The plugin refuses to enable unless the port is 25579 and `.animal-experiment` exists
in the server working directory. Only console commands are accepted.

Use Java 25, Paper 26.2-121 and Civilizations from `./gradlew deployTestServerPlugin`.
Bootstrap Paper once to obtain `server/libraries` and `server/versions/26.2/paper-26.2.jar`.
Bind `server-ip=127.0.0.1`, `server-port=25579`; set `pause-when-empty-seconds=0`.
Create `server/.animal-experiment`, stop the server, then from the worktree root run:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk python3 experiments/animals/build.py
```

Restart Paper. Run commands separately; wait for delayed output before the next one.
Keep each run's `logs/latest.log` before restarting.

1. `animalprobe breedcancel` — wait 12 seconds for native AI breeding and summary.
2. `animalprobe breedallow` — wait 12 seconds; this resets the test animals.
3. `animalprobe food` — native feeding with an unconnected test ServerPlayer;
   cancellation must consume two wheat and refund none. Wait one tick.
4. `animalprobe death` — wait 4 seconds for delayed death completion assertions.
5. `animalprobe seed` — creates a marked cow and flushes a world checkpoint.
6. `animalprobe unload` — wait 4 seconds for identity assertions. Stop/restart,
   then `animalprobe inspect` confirms identity after clean restart.
7. `animalprobe crashremove` — removes the saved cow and immediately halts, without
   saving the removal. Restart; `animalprobe inspect` finds the old saved entity.
8. `animalprobe crashintent` — fsyncs a fixture intent file and halts before mutation.
   Restart; `animalprobe inspect` finds the saved cow and reports `pending intent=true`.
9. `animalprobe crashbirth` — creates a marked calf, records PREPARED in fixture state,
   flushes the world and halts before acknowledgment. Restart;
   `animalprobe inspectbirth` verifies the calf exists although the operation is PREPARED.
10. `stop` — leave the fixture off.

The file/YAML checkpoints model independent saves, not the future SQL registry.
Do not infer production crash safety from this probe. See the
[findings and S3 contract](../../docs/animal-mechanics-spike.md) for limitations.
