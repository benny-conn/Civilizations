# S4 isolated Paper probe

This is a disposable verification plugin, never part of the production JAR. It refuses
ports other than 25582 and requires a `S4-FIXTURE` marker in the server working directory.
Only console commands run it. Build from a worktree with Paper 26.2-121 and Java 25:

```sh
./gradlew clean build deployTestServerPlugin
JAVA_HOME=/opt/homebrew/opt/openjdk python3 experiments/scarcity/build.py
```

**Stop Paper before either command copies plugin JARs.** Use a fresh isolated `server/`,
never the root repository's Desktop symlink. Copy a pristine prepared world into the
fixture before its first boot. This fixture may generate test Nether/End chunks and is
not a release world. Capture logs and stop it when finished.

- `s4probe repair`: mutates a disposable position at 8,200,8; checks production simple-block
  admission, native `breakNaturally`, legacy battle runner refusal, and no second harvest.
  Stone still restores. An in-memory historical exposure item is injected at the production
  runner boundary; it takes the pause path before touching the world. There are no forged
  SQL repair records and no paid-job or client battle claim.
- `s4probe loot-off`: 32 seeded native end-city loot table inventory fills before activation.
- Register the six test DIAMOND boxes with `/civworld import`, then enable equipment mode
  using the reviewed prepared-audit hash. The activation is permanent in this disposable DB.
- `s4probe loot-on`: same fills, now filtered; unrelated rewards must remain.
- `s4probe spawn-on`: native custom spawn with initial diamond helmet must be cancelled.
- `s4probe vault-on`: dispatches a **synthetic** vault event with diamond/emerald rewards.
- `s4probe world`: print the loaded world key/UUID for a fixture manifest.
- `s4probe block x y z`: check a known private prepared-deposit coordinate on real Paper.
- Restart and repeat policy status, loot and coordinate checks. Changed mode must be rejected
  and an identical activation retry must be accepted.

The ordinary-harvest probe creates real dropped items at its disposable coordinate; do
not promote its mutated world as the prepared artifact. Source and prepared-world audits
are separate. `observations.txt` summarizes results; full local logs and private deposit
specification live under `civilizations-s4/server/verification`.
