# Fixed Nether portal sites (S2b)

Portal restrictions default OFF. Setup is a YAML file and one enable command. There is no
zone editor, draft/revision workflow, or boundary preview. The current prototype uses free,
two-way crossings; ownership and toll mechanics are not implemented.

## Setup

1. Build and light both portals while restrictions are off. Each pair must connect a loaded
   Overworld (`NORMAL`) and Nether world. Get exact keys and UUIDs with `/civworld worlds`.
2. Give each endpoint a clear landing platform as described below. Keep both sites loaded
   while validating/enabling, and select the intended season in SETUP.
3. Create `plugins/Civilizations/portals/sites.yml` using the format below.
4. Run `/civportal validate sites.yml`, then `/civportal enable sites.yml`.
5. `/civportal check` reports the installed pairs, world identities and site problems.

All commands require `civilizations.admin`. `enable` saves the network in SQL (migration
14) and activates it after publishing the runtime snapshot. No restart is needed. It is
independent of cane activation and doesn't require inventing resource zones for the Nether.
A world already registered to another season or a different UUID cannot be used.
A byte-identical repeat is harmless. Other replacements reject. Phase/season selection and
restart do not disable the installed network. There is no reset/disable command in this
slice; no files are automatically reloaded. Do not turn it on before preparing the pairs.

```yaml
format: 1
season: 00000000-0000-0000-0000-000000000010
pairs:
  - id: western-crossing
    first:
      world: minecraft:overworld
      uuid: 00000000-0000-0000-0000-000000000001
      min-x: 0
      min-y: 64
      min-z: 0
      max-x: 1
      max-y: 66
      max-z: 0
    second:
      world: minecraft:the_nether
      uuid: 00000000-0000-0000-0000-000000000002
      min-x: 32
      min-y: 64
      min-z: 0
      max-x: 33
      max-y: 66
      max-z: 0
```

Coordinates describe the **portal interior**, excluding obsidian. Bounds are inclusive;
portals must be vertical, one block thick, 2–21 blocks wide and 3–21 high. The example uses
2×3 interiors. A constant Z means an X-axis portal; constant X means a Z-axis portal.
Obsidian frame corners are optional. Sites within one world must have a horizontal gap of
more than six blocks along at least one axis, so frames and landing spaces cannot overlap.
The file supports 1–16 uniquely named pairs. UUIDs must be canonical; unknown/duplicate keys,
malformed dimensions, inconsistent world identities, collection aliases and files over
1 MiB reject. Simple `.yml` filenames only; imports cannot escape the portal directory.

## Landing space

The plugin chooses the landing automatically:

- X-axis portal: midpoint block of its width, two blocks toward **positive Z**, at the
  interior's bottom Y.
- Z-axis portal: midpoint block of its width, two blocks toward **positive X**, same Y.

The entity lands at the horizontal center of that block. Build a safe solid **3×3 floor**
centered beneath it, with **3×3×3 air** above. For the example's first endpoint, landing is
`1.5,64,2.5`: floor x=0..2, y=63, z=1..3; clear air over it at y=64..66. The second landing
is `33.5,64,2.5`. Frames and exits must fit inside build heights and world borders.

## Gameplay and recovery

After activation, only the exact registered rectangles may be lit. Both new unregistered
portals and travel from existing unregistered portals are denied. Ordinary Nether search
and automatic destination creation are cancelled; the assigned endpoint is used directly.
End portals are unchanged. Players and entities use the same pairs. Vehicles/passenger
stacks must cross separately; entities wider than two blocks or taller than three reject.

Destination chunks load asynchronously **without generation**. Before teleporting, the
plugin rechecks entity/source identity, intact lit frames, borders and clear exits. Missing
worlds, replaced UUIDs, missing chunks, damaged frames and obstructed exits refuse travel.
Other plugins can still cancel the final teleport. At most 32 crossings wait at once;
portal-owned chunk tickets are shared/refcounted and released on completion or shutdown.

For recovery, repair and re-light the **same frame**, clear the platform, and retry. No
automatic rebuilding or item creation occurs. Physical frames retain ordinary world/claim
protection; this slice doesn't implement invulnerable infrastructure or political closure.
`check` may report unloaded chunks even though travel can load existing destination chunks.
Source chunks must already be loaded, as they are during ordinary portal contact.

Administrative teleports, Multiverse commands, FAWE/world edits and plugins that bypass
ordinary portal events are trusted server administration. Their permissions remain separate.
This is an ordinary portal-crossing policy, not a ban on every possible Nether teleport.

## Verification

The final clean build passes 182 tests. Automated checks cover geometry, negative coordinates, world identity, parser errors,
migration 13→14, SQL history, restart recovery, player destination routing, moving away
while loading, snapshot refresh, missing chunks, End travel and chunk-ticket cleanup.
Real Paper verified relighting, unregistered rejection, bidirectional cow crossings, blocked
exits, SQL-only restart recovery and crossings into unloaded existing chunks. Reloading
the destination recovered the same entity UUID at its fixed landing. Player behavior is
adapter-tested, not client-playtested. Details are recorded in the
[progress document](scarcity-world-plan.md#s2b-implementation-progress).

API references: [PlayerPortalEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/player/PlayerPortalEvent.html),
[EntityPortalEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/entity/EntityPortalEvent.html),
and [PortalCreateEvent](https://jd.papermc.io/paper/26.2/org/bukkit/event/world/PortalCreateEvent.html).
