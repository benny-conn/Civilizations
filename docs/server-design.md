# Civilizations server design direction

Status: working product direction, updated 2026-09-01.

This document describes the intended server around the Civilizations plugin. It is not
an implementation contract. [TODO.md](../TODO.md) remains the prioritized backlog, and
[architecture.md](architecture.md) remains authoritative for code and persistence
boundaries.

## Authority boundary

Civilizations owns political facts and gameplay stakes: civilization identity,
membership, offices and capabilities, government type, claims, treasuries, taxes,
votes, wars, battles, prisoners of war, land protection, damage, and reconstruction.

External plugins may provide commodity infrastructure such as global permissions,
player wallets, combat-log stand-ins, audit/rollback, maps, and proximity voice. They
must not become a second authority for civilizations, claims, nation banks, taxes,
government, prisons, or war outcomes.

## Geography and travel

Geography is intended to matter. Season gameplay should not provide ordinary players
with `/home`, `/back`, `/tpa`, random teleport, or an equivalent fast-travel surface.
Administrative recovery teleports remain available to staff, and the protection adapter
continues to handle any teleport event safely. A future capital or home location is
metadata and possibly a respawn destination, not a general teleport destination.

The world should be finite, pregenerated, and designed so that roads, ports, borders,
local markets, and controlled Nether access have strategic value. A global auction
house would undermine that geography; local physical markets are preferred.

## Money issuance, work, and trade

The server economy has two authoritative layers:

- one external Vault-compatible player wallet per player; and
- one exact Civilizations treasury and immutable ledger per civilization.

Currency must enter a new closed server economy somewhere. The preferred faucet is not
unbounded payment for repeatable actions. The server should mint a bounded amount of
money by purchasing actual value through public orders or contracts. Examples include
rotating deliveries of food, lumber, stone, metals, exploration records, or season event
supplies. Each order has a quantity cap, price, time window, and audit identity. Delivered
items are either removed as a deliberate item sink or transferred into an explicit public
stockpile.

After that initial faucet, most circulation should be private:

- players and civilizations post escrow-backed contracts for goods, construction,
  transport, defense, or other work;
- local shops redistribute existing player money rather than minting it;
- civilizations pay wages or contracts from their own treasuries; and
- claims, upkeep, casualties, and the sink portion of repairs remove money.

A conventional jobs plugin may be useful for an early experiment, but payment must be
limited by daily/period budgets, exploit-resistant, and preferably backed by a finite
server account. Paying merely for breaking, placing, crafting, or killing forever is not
the long-term economic model because it rewards automation independently of demand.

Open questions include the initial player money supply, the first public-order basket,
whether orders consume or stockpile delivered goods, how often prices change, and how
much new money may be issued relative to measured sinks.

## Civilization government and voting

Each civilization has an explicit government type. It is selected when an admin or
authorized creator provisions the civilization. After creation, changing government type
is an audited admin-only operation; leaders cannot rewrite their constitution when a vote
becomes inconvenient.

The first proposed government types are:

- `AUTOCRACY`: no electorate is required; the ruler exercises capabilities directly and
  may delegate them through civilization roles.
- `COUNCIL`: named council roles vote on configured major decisions while designated
  officeholders handle routine operations.
- `REPUBLIC`: eligible citizens elect the leader and citizen or council votes approve the
  configured major decisions.

Exact names and rules remain configurable product decisions, but government behavior must
be implemented as policy over the same durable roles and capabilities. LuckPerms only
controls global server access and administration.

The voting primitive should support both binding proposals and non-binding polls:

- Binding proposals invoke one exact application operation after passing. Initial
  candidates include leader selection, tax changes, large treasury spending or repairs,
  war declaration, surrender, expulsion, sentencing/release, and constitutional actions.
- Non-binding polls target either council members or all eligible citizens, allow an
  arbitrary question and choices, and record a durable result without pretending the
  plugin can enforce the resulting lore or promise.

Every vote snapshots its eligible electorate, audience, quorum, threshold, close time,
and action payload. Joining or switching civilizations after opening cannot manufacture
votes. Election ballots should be secret to ordinary players; policy votes and poll
results may be public. Staff retain an audit and recovery path.

Roles and granular capabilities come before governments. The government layer decides
who can grant or exercise capabilities such as claiming, setting dues, spending, kicking,
declaring war, surrendering, sentencing, or pardoning.

## Citizen dues

Citizen dues are distinct from land upkeep. The recommended first version creates a
fixed, periodic, publicly visible invoice per eligible citizen and transfers a successful
payment from the player's Vault wallet into the civilization treasury through the durable
bridge. It never creates a negative player balance.

Rates apply prospectively after a notice period, are capped by server configuration, and
are subject to the civilization's government approval policy. Missed payments initially
produce notifications and a delinquency count rather than compounding debt or automatic
imprisonment. Any removal or loss of office should require an explicit government action,
not a hidden scheduler side effect.

## Prisoners of war

Ordinary civilization leaders do not receive unilateral civil-jail authority. Prisoners
of war are a separate, explicit conflict context available only to eligible combatants in
an active war or battle.

The preferred capture loop is a later extension of final-life combat:

1. A combatant who would lose their final life may enter a short, durable `DOWNED` window.
2. Allies can rescue them; an opponent must remain exposed and complete a visible capture
   interaction to take custody. If neither happens, ordinary elimination/respawn wins.
3. A captured player enters a durable custody record tied to captor, war, battle, terms,
   start, maximum duration, and release state. Capture does not silently alter citizenship
   or claim ownership.
4. Custody may end through rescue, escape, exchange, ransom accepted by the prisoner's
   government, parole, war closure, timeout, or audited admin release.

Custody must create gameplay for the prisoner rather than a disguised temporary ban:

- sentences have a short online-time target and a hard wall-clock maximum;
- chat and proximity voice remain available for negotiation and intrigue;
- physical camps may contain work, trade, contraband, or designed escape opportunities;
- holding prisoners should cost the captor upkeep or attention rather than generating
  passive profit;
- prisoners keep a meaningful escape or parole path; and
- logout cannot erase custody, but also cannot extend it indefinitely.

An escape should be physical. Reaching an escape condition changes custody to `ESCAPED`
instead of teleporting the player home. A later bounded `CUSTODY_ESCAPE` conflict context
may let eligible guards pursue or recapture the escaped player without enabling general
PVP. Invisible permanent confinement, unrestricted guard damage, inventory confiscation,
and automatic tax-debtor imprisonment are out of scope.

Still-open POW decisions include whether final-life capture is mandatory or opt-in, the
rescue/capture channel duration, transport versus relocation to a camp, custody caps,
ransom approval rules, inventory treatment, and the exact escape/pursuit boundary.

## Regional scarcity and strategic infrastructure

Regional resource asymmetry is part of the intended season design, not merely a future
novelty. The first real season should use a finite authored or configured world in which a
small number of strategically meaningful resources have geographically restricted
sources. Candidate experiments include livestock habitats, diamond or metal deposits,
special crops, villager access, and a limited set of Nether portal sites.

World generation establishes the initial geography; a Civilizations-owned scarcity
policy preserves the rules after generation. Generic world generators do not understand
claims, political control, seasons, wars, or bypasses. Runtime policy may need to control
natural spawning, breeding, portal creation, replenishment, loot, and admin rebalancing.
It must use indexed zones and event-driven rules rather than scanning every loaded entity
or block.

Season One should start with only a few resources and multiple sources for each. A single
source can create total lockout rather than negotiation. Each resource needs an explicit
answer to:

- Is it finite, slowly replenished, or renewable only inside its habitat?
- Can it be transported and reproduced elsewhere, or does geography remain authoritative?
- Which vanilla alternatives, structures, loot, villagers, dimensions, breeding, or farms
  bypass the intended scarcity?
- What telemetry tells staff whether the resource causes trade rather than grind or
  permanent monopoly?

A useful first experiment deliberately mixes different kinds of scarcity instead of
giving every resource the same rule. Livestock may have origin scarcity—cows naturally
appear only in a few regions, but stolen or traded breeding stock can spread, making the
initial monopoly temporary. A special crop may have habitat scarcity and reproduce only
inside its climate zone. Diamonds or another ore may use several finite regional deposits.
Nether portals may be permanent strategic infrastructure. That mix creates trade,
industrial change, smuggling, and durable geography without requiring every monopoly to
last forever.

Limited Nether access should use registered portal sites and deny ordinary portal creation
elsewhere. The design must decide whether a controlling civilization may close access,
charge a toll, or must preserve a protected right of passage. At least several portal
sites and an admin recovery path are required.

WorldPainter, a versioned world-generation data pack, or a configurable generator such as
Terra can author the initial map. The exact tool is operational; the durable resource-zone
identities and live enforcement belong to purpose-built code that can integrate with
Civilizations.

## Proposed implementation order

1. Civilization roles and granular capabilities.
2. Durable proposal, electorate snapshot, vote, poll, and action-approval primitives.
3. Government types and leader-selection policies.
4. Fixed citizen dues and public treasury visibility.
5. Bounded public purchase orders and player/civilization escrow contracts.
6. A small regional-resource prototype plus the real-season world design.
7. Final-life downed/rescue/capture and bounded POW custody.

The first mechanics playtest does not require all of these systems. The actual first
season world should not launch until its travel, money faucet, government, and initial
regional-resource rules are intentionally selected and exercised in a staging playtest.
