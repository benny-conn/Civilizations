# Manual Paper playtest path

This guide is the repeatable human test path for the current Civilizations MVP. It is
written for a local Paper server, two or more Minecraft clients, and one server-console
operator. Use four non-operator players (two per civilization) when possible so roster
and participation behavior can be exercised as well as the basic two-player loop.

The guide deliberately records current limitations instead of treating unfinished
features as passes. On the current build, civilization setup, claims, protection, war
declaration, hostile-entry battle activation, simple battle block mutation, persistence,
durable online combatant/life state, defender-at-timeout resolution, bounded report
sealing, treasuries, and paid repair are live. Targeted PVP and Paper death/respawn or
combat-logger translation are not implemented until B5.

## Test record

Copy this table into the playtest notes and fill it in as the run proceeds.

| Item | Value |
| --- | --- |
| Commit | |
| Paper version/build | |
| Season name/ID | |
| Civilization A name/ID | |
| Civilization B name/ID | |
| War ID | |
| Battle ID | |
| Repair job ID, when available | |
| Testers | |
| Start/end time | |

For every failure, record the exact step, player, command or action, expected behavior,
actual behavior, relevant ID, and the matching section of `server/logs/latest.log`.

## 1. Prepare the server and players

1. From the repository root, build, deploy, and run the isolated local server:

   ```bash
   ./scripts/run-test-server.sh
   ```

2. Join with every test account once. Obtain each UUID from `server/usercache.json` and
   save these placeholders in the test notes:

   - `<A_LEADER_UUID>` and optionally `<A_MEMBER_UUID>`
   - `<B_LEADER_UUID>` and optionally `<B_MEMBER_UUID>`

3. Use the server console for `/civadmin` commands. The player accounts must not be
   operators and must not have `civilizations.admin.bypass`; an operator silently bypasses
   claim protection and invalidates most of this test. Omit the leading slash when a
   command is entered directly in the console.

4. Run `/civadmin status`. Pass when Civilizations reports a ready runtime. Stop the test
   if the plugin failed startup or is still loading.

5. For a shorter session, edit `server/plugins/Civilizations/config.yml` before creating
   the season, set `gameplay.war.battle-duration-seconds` to a small value such as `300`,
   and restart. Configuration is loaded only at startup. Do not use `/reload`.

Use a unique season name for each run instead of editing or deleting the database.

## 2. Create the season and civilizations

Run these commands from the console, substituting the saved UUIDs:

```text
/civadmin season create Manual-Test-01
/civadmin season select Manual-Test-01
/civadmin civilization provision Aurelia <A_LEADER_UUID> [<A_MEMBER_UUID>]
/civadmin civilization provision Borealis <B_LEADER_UUID> [<B_MEMBER_UUID>]
/civadmin civilization list
/civadmin economy adjust Aurelia 1000.00 manual playtest funds
/civadmin economy adjust Borealis 1000.00 manual playtest funds
/civadmin economy balances
```

The square brackets mean the second player is optional; do not type the brackets. Pass
when both civilizations are active, have the intended leaders/members, and each treasury
shows exactly `1000.00`. Repeat one `provision` command and confirm it is idempotent rather
than creating a duplicate civilization or membership.

Player checks:

```text
/civ balance
/civ status
```

Each player should see their civilization treasury and no open wars.

## 3. Create land and verify peace protection

Choose two nearby, non-overlapping test areas in a loaded world. Replace the example
coordinates if they do not match safe ground in the local world.

```text
/civadmin claim Aurelia minecraft:overworld 100 100 115 115
/civadmin claim Borealis minecraft:overworld 120 100 135 115
/civadmin season phase peace
/civadmin status
```

Verify all of the following with non-operator players:

1. An Aurelia member can break/place a simple block inside Aurelia's claim.
2. The same player cannot break/place a block or open a container inside Borealis's claim.
3. The inverse is true for a Borealis member.
4. Both players can move freely through claimed land; protection does not create a wall.
5. Wilderness still behaves like ordinary Minecraft.
6. A piston, fluid, or inventory transfer that would cross an ownership boundary is
   denied. The same interaction wholly inside one ownership area is allowed.
7. An explosion may affect wilderness blocks but must leave claimed blocks untouched.

Record the exact edge and corner coordinates used. Include at least one action on a claim
edge because inclusive rectangle errors tend to appear there.

## 4. Declare war while battles are disabled

As an Aurelia member:

```text
/civ declare Borealis
/civ status
```

From the console:

```text
/civadmin war list
/civadmin war inspect <WAR_ID>
```

Pass when the declaration succeeds in `PEACE` without admin approval. Walk or teleport
the Aurelia player from outside Borealis's claim into it. The player should receive clear
feedback that war is declared but battles require the global `WAR` phase, and no battle
should be created.

Run the same `/civ declare Borealis` command again. It should not create a second open war.

## 5. Restart checkpoint before combat

1. Stop Paper cleanly with `stop`.
2. Start it again with `./scripts/run-test-server.sh`.
3. Reconnect the players and run:

   ```text
   /civadmin status
   /civadmin civilization list
   /civadmin war list
   /civadmin economy balances
   ```

Pass when the selected season, phase, civilization rosters, claims, balances, and declared
war are unchanged. Recheck one own-claim and one enemy-claim protection action.

## 6. Start and exercise a battle

From the console:

```text
/civadmin season phase war
```

Move the Aurelia player from outside Borealis land across the boundary. Pass when the war
becomes active, one battle starts, both sides receive the battle ID and absolute end time,
and repeated boundary crossings do not create another battle. At least one permitted
player from each side must be online when the boundary is crossed; offline members remain
in the historical participant roster but are not combatants in this battle.

Record and inspect the battle:

```text
/civadmin battle list
/civadmin battle inspect <BATTLE_ID>
```

The inspection should show the full participant count, smaller/equal combatant count,
living count, snapshotted lives, and no combat resolution yet.

While the battle is active, verify:

1. A snapshotted Aurelia participant can break and place simple, independent blocks in
   Borealis's claims.
2. The same player cannot mutate Aurelia's own land through the enemy-side battle override
   unless normal owner policy independently permits it.
3. A non-participant cannot use the battle to mutate either civilization's land.
4. Containers and other block entities remain protected. Include at least a chest and a
   sign or banner.
5. Beds, multi-block placement, gravity/cascading blocks, fire, fluids, and explosions do
   not gain an unjournaled battle override.
6. Repeatedly changing one simple coordinate works in the world but retains one immutable
   pre-battle baseline for later reconstruction.
7. A block placed over air in enemy land is accepted as a simple placement and is later
   eligible for removal during repair.
8. PVP is still denied in claimed land. This is an expected current limitation, not a
   failed test; targeted battle PVP belongs to the upcoming combat adapter.
9. Disconnecting a combatant does not change the living count by itself. If testing an
   external combat logger, use [combat-logging.md](combat-logging.md); its real player/NPC
   death translation is a B5 checkpoint, not part of A4.

Optional four-player roster check: after the battle starts, move a non-leader member to
the opposing civilization with `/civadmin civilization move-member <PLAYER_UUID> <civ>`.
The political roster may change, but this battle's already-snapshotted side and
capabilities must not change. A later battle should use the new membership.

## 7. Stop destructive eligibility and test recovery

Use one of these paths:

- Preferred surrender path: the current leader of either side runs `/civ surrender`.
  The battle should enter `RESOLVING` immediately, stop destructive eligibility, seal its
  damage report in bounded batches, and close with the opposing side as winner.
- Explicit admin path: run
  `/civadmin battle force-resolve <BATTLE_ID> <attacker|defender|draw> <audit reason>`.
  It must use the same resolving, observation, and report-sealing path; it may not skip
  directly to a report-less closed battle.
- Emergency-only path: run `/civadmin battle cancel <BATTLE_ID> <audit reason>` and verify
  the battle becomes `CANCELLED`.

After surrender or force-resolution, verify that battle block mutation stops immediately.
Pass when `/civadmin battle inspect <BATTLE_ID>` eventually reports `CLOSED` with the
requested outcome and repair status becomes available.

For the restart checkpoint, create enough damage that observation cannot finish instantly,
then stop Paper while the battle is `RESOLVING`. After restart, a surrendered battle should
reuse its durable requested outcome, resume report sealing, and close without re-enabling
destruction or duplicating report rows.

To test ordinary expiry, let a battle reach its configured deadline with nobody issuing a
command. It should enter `RESOLVING`, remove destructive eligibility, and seal its damage
report even with zero players online. It should deliberately remain `RESOLVING` without a
winner only if it is a legacy battle created before schema 9. A new battle should show
`combatResolution=TIMEOUT`, request `DEFENDER_VICTORY`, seal the report, and close with the
defending civilization as winner without an admin command.

## 8. Repair path for a battle that already has a sealed report

Continue here after surrender or admin force-resolution has sealed the damage report and
closed the battle. A report-less or still-resolving battle should be rejected clearly
rather than charged.

As a player in the damaged civilization:

```text
/civ repair status <BATTLE_ID>
/civ repair start <BATTLE_ID> 50
/civ repair status <BATTLE_ID>
```

Pass when status shows actual restored, repairable, and conflicted counts; the first job
charges only enough to reach an absolute 50%; and the job advances visibly in bounded
batches. Record the repair job ID and compare both civilization balances and the victor's
configured share.

Next, manually restore several still-damaged blocks exactly to their original states and
run status again. The completion percentage should increase. A later 100% request must
charge only for the blocks still needed to reach 100%:

```text
/civ repair start <BATTLE_ID> 100
/civ repair status <BATTLE_ID>
```

Alter one remaining damaged block to a third state before the final request. It should be
reported as a conflict, excluded from the price, and never overwritten by the runner.

Admin inspection and recovery commands:

```text
/civadmin repair list <BATTLE_ID>
/civadmin repair inspect <REPAIR_JOB_ID>
/civadmin repair pause <REPAIR_JOB_ID> manual pause checkpoint
/civadmin repair resume <REPAIR_JOB_ID> manual resume checkpoint
/civadmin repair cancel <REPAIR_JOB_ID> manual cancellation checkpoint
```

For restart recovery, stop Paper while a sufficiently large repair is `RUNNING`. After
restart it should be `PAUSED` at the same durable cursor. Resume it explicitly and verify
already completed items are not charged or applied twice.

## 9. Finish the run

Capture:

- `server/logs/latest.log`;
- the completed test-record table;
- screenshots or coordinates for incorrect protection/repair behavior;
- IDs from every affected season, civilization, war, battle, ledger transfer, and repair
  job;
- whether the problem reproduced after a clean restart.

Do not manually edit `civilizations-v2.db` to make a failed path pass. Preserve the server
directory until the issue is understood so world state and SQL state can be inspected
together.
