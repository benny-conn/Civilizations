# Combat logging boundary

Civilizations deliberately does not implement a second disconnect timer. A battle
combatant who disconnects keeps the same durable remaining-life count. A server-provided
combat-logging plugin may keep the player vulnerable, spawn a killable stand-in, or turn
the logout into a death. B5 translates that plugin's authoritative death consequence
into the same idempotent Civilizations life-loss operation used for an ordinary
`PlayerDeathEvent`.

This boundary prevents restart timing, network loss, and two competing grace periods from
silently producing different battle outcomes. If the external plugin does nothing, the
disconnected combatant remains alive until returning or until the battle deadline; the
defender-holds timeout rule still ends the battle deterministically.

## Initial Paper 26.2 recommendation

[BattleLock](https://hangar.papermc.io/Jelly-Pudding/BattleLock) is the first integration
candidate for the test server. Its current release explicitly targets Paper 26.2, spawns a
killable persistent combat-log NPC, and exposes the original player's UUID on that NPC via
the `battlelock:combat_log_player_id` persistent-data key. That gives B5 a dependency-free
way to recognize an NPC death and attribute the battle life loss to the enrolled player.
Civilizations has no BattleLock binary dependency: it reads that one documented marker and
uses the stand-in entity UUID to derive a stable life-event ID. In either party's claimed
battle land, a living owner's proxy can be damaged only by a living opponent from the same
battle. Outside that land, the server's ordinary wilderness/claim entity policy remains in
force. This is not a generic claimed-land villager/entity damage grant.
BattleLock 1.8 removes the proxy from its own lethal-damage callback, so Civilizations
marks the hit after exact protection authorization, captures it only if still uncancelled
at `MONITOR`, and retains `EntityDeathEvent` as an idempotent fallback for environmental or
alternate removal paths.

[PvPManager](https://github.com/ChanceSD/PvPManager) is the more established fallback and
publishes a developer API plus configurable logout-kill behavior. Its current public
compatibility listing is still on the Minecraft 1.21 line, so it should be tried only after
an isolated Paper 26.2 load/death/restart test.

The selection is operational, not durable gameplay policy. Civilizations stores neither a
plugin name nor that plugin's tag timer in battle records. Swapping combat-log plugins must
not reinterpret combatants, lives, eliminations, or already-requested outcomes.

## Required B5 verification

- Ordinary player death consumes exactly one life.
- Killing a BattleLock stand-in consumes exactly one life for its UUID.
- Reconnect, plugin retry, and duplicate entity events cannot consume the same life twice.
- A surviving stand-in/reconnecting player retains the prior life count.
- Eliminated players lose battle PVP and destruction capability after the refreshed runtime
  snapshot publishes.
- Plugin/server restart does not discard a stand-in penalty or a Civilizations elimination.

The implementation also preserves vanilla death, drops, and respawn selection. It adds no
Civilizations respawn countdown and no spectator camera. A final-life death suppresses that
player's battle capabilities while storage is still pending; after publication, the
durable eliminated state continues to deny PVP and journaled block mutation.

The B5 isolated server checkpoint co-loaded Civilizations with reviewed BattleLock `1.8`
(`a67cd459fbee85f6e26072f282340499735547b2b9930fa41cdb2ad805dec505`) on Paper 26.2
build 112, reached ready state, shut down cleanly, and restarted with the same database.
The upcoming human playtest still owns the multi-client hit, logout, stand-in kill,
inventory, and reconnect checks listed above.
