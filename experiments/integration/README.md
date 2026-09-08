# S5 integrated scarcity fixture

Technical integration on Paper 26.2-121 / Java 25, using the accepted 2048-square
WorldPainter sample prepared by S4. These plugins are disposable verification tools,
not part of the production JAR. They require loopback fixture port 25583 and the
`S5-FIXTURE` marker. No Minecraft client or human barter session is claimed.

## Reproduce in a fresh worktree

Never use the root repository's Desktop `server` symlink. Stop Paper before replacing
any JAR or modifying world files. World scans acquire the server's world session lock.
Keep private deposit specifications and complete world/SQL audits out of Git.

1. Supply the pristine `../civilizations-s4/server/verification/prepared-2048` artifact,
   its `prepared-audit.json`, private `prototype-deposits.json`, and S4 Paper runtime.
   `fixture.py` checks every prepared file against the S4 audit before copying. It refuses
   an existing server directory. Paths assume sibling worktrees; transfer these private
   artifacts together when moving machines. Install `tools/scarcity/requirements.txt`
   in a local Python virtual environment for NBT scans.
2. Run `python3 experiments/integration/fixture.py`, then
   `./gradlew clean build deployTestServerPlugin`, then
   `JAVA_HOME=/opt/homebrew/opt/openjdk python3 experiments/integration/build.py`.
   Start Paper from `server/` with Java 25 and `-Xms512M -Xmx2G -jar paper.jar --nogui`.
   The empty whitelist and online mode prevent casual player entry during setup.
3. Run `s5probe prepare` once. Wait for the 576-chunk Nether pregeneration completion.
   The probe creates three fenced cattle habitats, three cane basins, public food plots,
   three paired lit portals, and a fenced Nether transit walkway. It captures actual world
   UUIDs, heights and positions in `verification/layout.json`. End is disabled. The Nether
   is a **resource-free transit fixture**, not a finished survival dimension.
4. Create a SETUP season with `civadmin season create IntegratedS5`. Run
   `python3 experiments/integration/author_files.py` from the worktree root. Import both
   `overworld.yml` and `nether.yml` with `civworld import`. There are 12 Overworld zones
   (six diamond, three cane, three cattle); Nether explicitly has `zones: []`.
5. Use `civadmin civilization provision` to create Westhaven, Eastwatch and Southmeadow
   with leader UUIDs `00000000-0000-0000-0000-000000000061`, `...062`, `...063`.
   These are fixture roster identities, not connected accounts. Create their admin claims:

   | Civilization | x1,z1 | x2,z2 |
   | --- | --- | --- |
   | Westhaven | 425,505 | 455,535 |
   | Eastwatch | 1555,615 | 1585,645 |
   | Southmeadow | 1095,1515 | 1125,1545 |

   All public farms are outside claims. Stop Paper and retain the preparation log.
6. Run `clear_authoring_kit.py` with the NBT virtual environment to remove the single
   leftover WorldPainter creative test-kit chest inventory from this copy. Run `audit.py`.
   Review its `assembled-audit.json`: 1,536 diamond ores in six deposits, no other diamond
   supply, no lazy loot/merchant offers, no cattle yet. The only initial items are public
   wheat seeds, carrots and potatoes. This audit is an attestation for this fixture only.
7. Start Paper. Enable all policies using the actual new audit hash:

   ```text
   civworld enable-diamonds equipment <assembled-audit-sha256> S5 assembled stopped-world inventory audit
   civworld enable-cane 3 S5 regional basins
   civportal enable sites.yml
   civcattle enable seeds.yml
   civcattle seed
   civportal check
   ```

   Wait for each asynchronous operation to finish. Cattle rules snapshot the fixture's
   **8-second maturity / 4-second cooldown** into SQL. Production defaults remain
   12 hours / 6 hours. The fixture treasury grant is also test-only.
8. Run `s5probe cane`, `s5probe food`, then `s5probe relocate`. Wait for the relocation
   completion log before `s5probe breed`. The probe staff-teleports two cows to the first
   portal, then uses native portal contacts and pathfinding across the Nether relay.
   It positions the relocated adults together and sets native love mode. Wait for one birth.
   Run `s5probe exchange <x> <y> <z>` with a known ore from the private deposit mask.
   It harvests actual blocks and makes two **scripted inventory exchanges**. No human
   negotiated trade, lead journey or travel-time balance is asserted. Then enter PEACE.
9. Stop, save the integration log and run `audit.py --expected-mined 1`. It verifies
   1,535 remaining ores plus one harvested diamond and exact world-PDC/SQL correspondence
   for 25 ALIVE cows, with no unresolved parent reservations. It scans all generated
   Overworld/Nether chunks, including neighbors outside the playable border.
10. Remove the accelerated cattle overrides from config while stopped. Restart and run
    `s5probe restart`, `s5probe access`, `s5probe unregistered`, all policy status commands,
    `civportal check`, and repeat `civcattle seed`. Wait for the delayed unregistered-portal
    check. The access test calls the actual runtime protection policy for all nine
    leader/farm and leader/town combinations; it is not a client block-event test.
    Verify SQL still stores 8000/4000 milliseconds and 25 ALIVE records. Stop again, save
    the restart log, and run `audit.py --expected-mined 1 --report restart-audit.json`.

Reports refuse overwrites. Copy logs after shutdown, before starting another run. The
mutated test world is **not** a pristine season release artifact. No release activation,
reset, additional species adapter, or SQL migration was added in S5.

## Remaining acceptance evidence

The 90–120-minute human trading session, 48-hour real-time population trial, two-account
battle/repair interactions, player-load/p99 cost and full third-party Desktop plugin stack
are unverified. Automated inventory transfers cannot prove meaningful trade incentives,
and current public-farm permission checks cannot prove permanent food access under future
player claims. Portal sovereignty/tolls and a full survival Nether remain later decisions.
See [progress and pickup](../../docs/scarcity-world-plan.md) and `observations.txt`.
