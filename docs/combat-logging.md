# Combat logging boundary

Civilizations deliberately does not implement a second disconnect timer. A battle
combatant who disconnects keeps the same durable remaining-life count. A server-provided
combat-logging plugin may keep the player vulnerable, spawn a killable stand-in, or turn
the logout into a death. B5 should translate that plugin's authoritative death consequence
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
