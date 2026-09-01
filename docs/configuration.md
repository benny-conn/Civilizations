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
| `claims.require-edge-connection` | `true` | Compatibility switch for a single-tier configuration. Explicit claim-group tiers are authoritative: a disconnected rectangle founds the next group when a tier exists. |
| `claims.base-price` | `100.00` | Non-negative base treasury price for every ordinary rectangular claim. |
| `claims.price-per-block` | `1.00` | Non-negative treasury price multiplied by the inclusive rectangle area for every ordinary claim. |
| `claims.ordinary-initiator-roles` | `[LEADER]` | Non-empty built-in civilization roles allowed to purchase ordinary claims. |
| `claims.groups.tiers[0]` | group `1`: `1` member, `0.00` balance, `0.00` establishment | The first disconnected connected component. Tier `max-groups` values must be exactly contiguous from `1`; thresholds authorize creation but do not later revoke land. |
| `claims.groups.tiers[1]` | group `2`: `4` members, `50000.00` balance, `25000.00` establishment | Requirements and one-time sink payment for the second connected component. Every rectangle's ordinary land price is additional. |
| `claims.groups.tiers[2]` | group `3`: `8` members, `150000.00` balance, `75000.00` establishment | Requirements and one-time sink payment for the third and final shipped connected component. Add another contiguous entry to allow more. |
| `gameplay.phase-gates.roster-changes` | `[SETUP, PEACE, WAR]` | Phases in which drafts, provisioning, membership, leadership, and activation may change. May contain only `SETUP`, `PEACE`, and `WAR`; active battle participant/side snapshots remain immutable. `[]` disables roster changes. |
| `gameplay.phase-gates.claim-creation` | `[SETUP, PEACE]` | Phases in which new claims may be created. May contain only `SETUP` and `PEACE`; `[]` disables claiming. |
| `gameplay.phase-gates.member-land-actions` | `[SETUP, PEACE, WAR]` | Phases in which members may ordinarily interact with their own claimed land. May contain only `SETUP`, `PEACE`, and `WAR`; `[]` freezes ordinary member actions in every phase. Explicit conflict capabilities and admin bypass remain separately authorized. |
| `gameplay.war.battle-duration-seconds` | `1800` | Duration snapshotted into a new war and used for each hostile-entry battle in that war. Must be from `1` through `31536000`; changes require restart and affect only later declarations. |
| `gameplay.war.lives-per-combatant` | `1` | Lives granted to each eligible online combatant enrolled when a battle starts. Must be from `1` through `10`; the effective value is snapshotted into the battle. Disconnecting does not consume a life by itself. |
| `gameplay.war.resolution-observations-per-tick` | `200` | Maximum journaled block coordinates observed on the Paper thread in one tick while sealing a battle's damage report. Must be from `1` through `4000`. This is a performance budget, not snapshotted gameplay meaning. |
| `gameplay.land-protection.enabled` | `true` | Enables recurring treasury-backed land protection. When disabled, protection remains on, no upkeep/reserve is charged, and existing grace/exposure state returns to protected at the next synchronization. |
| `gameplay.land-protection.interval-seconds` | `604800` | Time between due upkeep assessments for a civilization with land, from `60` through one year plus one day. Default: seven days. |
| `gameplay.land-protection.grace-seconds` | `259200` | Time after an unpaid assessment before land becomes exposed, from `60` through 90 days. Default: three days. |
| `gameplay.land-protection.assessment-interval-seconds` | `60` | Operational scheduler polling interval, from `10` through `3600`. It does not change the durable due time. |
| `gameplay.land-protection.base-charge` | `1000.00` | Non-negative recurring upkeep sink charged for a civilization with claimed land. |
| `gameplay.land-protection.per-block-charge` | `0.10` | Non-negative recurring upkeep sink multiplied by total inclusive claimed area. |
| `gameplay.land-protection.base-reserve` | `5000.00` | Non-spendable treasury floor required while land is protected. It is not debited; withdrawals that would cross it are rejected. |
| `gameplay.land-protection.per-block-reserve` | `0.25` | Additional required treasury floor multiplied by total inclusive claimed area. |
| `gameplay.land-protection.damage-limit-per-exposure` | `500` | Snapshotted maximum distinct journaled coordinates that other civilizations may damage in one exposure, from `1` through `100000`. Repeat mutations at one coordinate retain one site and its original state. |
| `economy.currency-scale` | `2` | Decimal places used by exact Civilizations money, from `0` through `6`. It is snapshotted per season and cannot change for an initialized season. |
| `economy.opening-civilization-balance` | `0.00` | Non-negative opening treasury balance assigned through one idempotent ledger transaction when a civilization account is initialized. |
| `economy.repair.restore-original-unit-price` | `1.00` | Non-negative price for each selected `RESTORE_ORIGINAL_BLOCK` repair unit. |
| `economy.repair.remove-placement-unit-price` | `1.00` | Non-negative price for each selected `REMOVE_PLACED_BLOCK` repair unit. |
| `economy.repair.victor-share-percent` | `25.00` | Percentage from `0` through `100`, with at most two decimal places, of an ordinary repair payment assigned to the battle victor. |
| `economy.repair.ordinary-initiator-roles` | `[LEADER]` | Non-empty civilization roles allowed to initiate an ordinary paid repair. |
| `economy.battle-casualties.attacker-death-cost` | `2500.00` | Non-negative treasury cost of one attacker life loss, snapshotted into a new battle. |
| `economy.battle-casualties.defender-death-cost` | `1000.00` | Non-negative treasury cost of one defender life loss, snapshotted into a new battle. |
| `economy.battle-casualties.require-attacker-coverage` | `true` | Whether battle activation must pre-fund every possible attacker life loss. The reserve is unavailable for other spending until the battle ends. |
| `economy.battle-casualties.lock-withdrawals-during-battle` | `true` | Whether both battle parties are barred from starting player-wallet withdrawals while the battle is `ACTIVE` or `RESOLVING`. |
| `repair.runner.blocks-per-tick` | `20` | Global maximum authoritative repair mutations in one server tick, from `1` through `1000`. At the normal 20 ticks/second, the default ceiling is 400 blocks/second; storage and chunk transitions can make actual throughput lower. |
| `repair.assessment.blocks-per-tick` | `200` | Global maximum live block observations in one server tick while calculating status or a fresh quote, from `1` through `4000`. |

Phase names are case-insensitive when loaded, but the shipped file uses uppercase names
to match the durable season statuses. Duplicate or unknown phase names are rejected.

## Economy ownership and snapshots

Money is stored as signed integer minor units, never floating point. For example, scale
`2` stores `12.34` as `1234`. Values that require rounding are rejected. A season's
currency scale and opening balance are written to SQL when that season first initializes;
changing the configured scale afterward fails startup instead of reinterpreting balances.

Civilizations SQL owns civilization treasury balances and their immutable ledger history.
An external economy plugin owns player balances. If Vault and an economy provider are
installed, `/civ deposit` withdraws from that player wallet and credits the SQL treasury;
leader-only `/civ withdraw` reserves/debits SQL before crediting the player. No Vault bank
or organization account is used.

Every player-wallet operation has a durable prepare record. A clean provider rejection
is recorded and compensated where necessary. A server stop, thrown provider call, or
unknown result is never retried automatically; `/civadmin economy pending` exposes it and
`/civadmin economy reconcile <id> <succeeded|failed> <reason>` records the admin decision.

Every repair job snapshots these effective values. A later configuration change affects
only new jobs. An ordinary job is created atomically with its ledger payment and is
rejected when the paying civilization lacks the full cost; treasury balances never go
below zero. The configured victor percentage may be `0`. Any amount not credited to the
battle victor is removed from circulation as a currency sink.

Repair percentages are absolute completion targets rather than percentages of whatever
remains. Before quoting or creating a job, current blocks are reassessed. Blocks already
restored exactly to their pre-battle state count toward completion and are not charged.
For example, completing 50% through a job and then 3% manually makes the current status
53%, so a request to reach 100% selects and charges 47%. Later alterations that match
neither the original nor sealed damaged state are shown as conflicts and are not selected.

There is no `admin-waives-cost` setting. The privileged admin repair command names
the target civilization and executes the same repair operation with an audited
admin-sponsored funding context. It charges no civilization account and therefore pays
no victor share.

An ordinary claim purchase is also one SQL transaction: authority, phase, geometry,
overlap, group adjacency/tier, membership and balance requirements, land price, and
establishment price are checked before a single treasury debit and claim/group insert.
The group stores the effective founding cost and thresholds. A bridge rectangle may merge
two or more groups but does not refund old establishment payments.

Land upkeep is a treasury sink with no debt. A due charge is paid only when the account can
both cover the charge and retain its current area-based reserve. Otherwise the state enters
grace. After grace and outside battle it becomes `EXPOSED`; this does not dissolve the
civilization or remove claims. Deposits and admin adjustments may fund recovery, while a
leader withdrawal may not reduce the treasury below the reserve. A battle takes precedence
over exposure, and a due assessment is deferred while that civilization is in active or
resolving combat.

Exposure permits only journal-first simple break/place by a member of another civilization.
Containers, all block entities, entities, and PVP remain protected. `/civ protection status`
shows upkeep state, reserve/deadline, cap use, actual manual restoration, remaining
repairable/conflicted coordinates, and the current 100% repair price. `/civ protection pay`
rechecks recovery; `/civ protection repair <target-percent>` creates an absolute-target
treasury repair with no victor posting. Changes require restart; scheduled assessments and
created repair jobs retain their durable effective values/cursors.

Every new combat-enabled battle also snapshots its attacker/defender death prices,
coverage requirement, and withdrawal-lock policy. With the default coverage rule, battle
activation atomically debits the attacking treasury for its maximum possible casualty
liability: attacker price multiplied by the sum of enrolled attacker lives. If that money
is unavailable, the battle does not start. Each attacker death consumes part of the
already-funded reserve; when the battle closes or is cancelled, only the unused portion
returns to the attacker. That release is not a refund of a charged death.

Defender deaths, and attacker deaths when coverage is disabled, charge the treasury at the
time of the durable life loss. The charge takes at most the current balance, stops at zero,
and records any uncollectible remainder for history without creating debt. Casualty money
is a pure currency sink: it is not paid to the opponent and is independent of repair
pricing and the repair victor share. Deposits remain allowed during battle, while new
withdrawals are locked for both parties by default through `ACTIVE` and `RESOLVING`.

The two Paper repair budgets control pace rather than durable meaning, so they are not
snapshotted into jobs. The runner processes one job and holds at most one plugin chunk
ticket at a time. It releases that lease when moving to another chunk/job, uses
non-generating asynchronous chunk loads, defers solid restoration while a player
intersects the block, and pauses at the unchanged cursor if a world or existing chunk is
unavailable.

The battle-resolution observation budget is also operational rather than historical.
Battle resolution, battle repair, and land-protection restoration serialize Paper world
work, so scanners/runners never race plugin-owned chunk leases. SQL work remains on the
runtime worker and no missing chunk is generated.

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
