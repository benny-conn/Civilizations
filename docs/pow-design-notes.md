# Prisoner-of-war design notes

Status: **exploratory notes only; no mechanics in this file are approved requirements.**

This file preserves ideas that may be useful during a future POW design pass. Do not
implement directly from it. The current game continues to use ordinary final-life
elimination, normal respawn, protected entities, and no custody state.

## Desired experience

A future POW system could create negotiation, rescue, prisoner exchange, escape, ransom,
and political drama without becoming a temporary ban or giving civilization leaders
unilateral civil-jail power.

Whatever design is eventually approved should:

- be available only through an explicit war/battle conflict context;
- preserve a meaningful game for the prisoner;
- have short, enforceable online and wall-clock limits;
- survive disconnects and restarts without extending custody indefinitely;
- have escape, release, and admin recovery paths;
- avoid silently changing citizenship, claims, inventory ownership, or war outcomes; and
- use narrow custody capabilities rather than enabling general PVP.

## Capture concept to explore

One possible extension of final-life combat is a short durable `DOWNED` state. Allies
could attempt a visible rescue interaction while opponents attempt a visible capture
interaction. If neither completes, ordinary elimination and respawn would remain the
fallback.

This is not settled. Alternatives include voluntary surrender, post-battle capture,
physical incapacitation without replacing death, or no player custody at all.

Questions include:

- Is capture mandatory, opt-in, or conditioned on a special battle objective?
- Does a downed state replace the final death or occur after it?
- How long are rescue and capture channels, and what interrupts them?
- Can one battle create many prisoners, or is custody deliberately scarce?
- What happens to inventory, equipment, experience, and casualty charges?
- Must captors escort someone physically, or does a completed capture relocate them to a
  registered camp as a custody transition?

## Custody concept to explore

A possible durable custody record could name the prisoner, captor, source battle and war,
camp, terms, online-time target, absolute release time, and release state. Possible release
paths include rescue, escape, exchange, consensual ransom, parole, war closure, timeout,
or audited admin release.

Ideas for keeping custody playable include communication and proximity voice, negotiation,
camp work or trade, visitors, contraband, escape opportunities, and a cost or attention
burden for the captor. Invisible permanent confinement, unrestricted guard damage, and
automatic tax-debtor imprisonment are specifically unwanted.

None of these lifecycle states or release paths is approved yet.

## Physical escape concept to explore

A registered camp might offer a bounded physical escape rather than an invisible barrier.
Prisoners could obtain contraband, tools, information, or outside assistance and reach a
defined escape boundary. Success could change custody to `ESCAPED`, grant a short head
start, and begin a narrow `CUSTODY_ESCAPE` guard-pursuit window. Reaching a safe condition
or surviving the pursuit could close custody. Recapture should not reset the absolute
release deadline.

Open questions include what prisoners may interact with, how camps remain escapable without
making custody meaningless, what qualifies as safety, who may pursue, how far pursuit may
extend, how outside rescuers participate, and what happens on logout.

## In-person exchange concept to explore

Governments might create an exchange proposal naming prisoners, representatives, a neutral
location, and a time window. Both parties would physically bring the prisoners to the
meeting. Once all required people are present, authorized representatives could confirm an
atomic all-or-none release so one side cannot recover its citizen and refuse the other.

Open questions include whether the location has a ceasefire, whether ambush is legitimate,
whether ransom or items are escrowed, how a missing participant affects the exchange, and
whether released players must still travel home physically.

## Decision gate

Before any POW implementation branch starts, approve a concise rules document covering:

1. capture trigger and consent;
2. death, lives, casualty-economy, and BattleLock interaction;
3. transport and camp validation;
4. prisoner activity and inventory;
5. duration and logout behavior;
6. escape, rescue, pursuit, and recapture;
7. ransom, exchange, parole, and government approval;
8. war closure and admin recovery; and
9. multiplayer abuse and staging-playtest cases.
