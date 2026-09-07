package io.bennyc.civilizations.infrastructure.paper.scarcity

import io.bennyc.civilizations.application.scarcity.*
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.SeasonId
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.bukkit.event.block.BlockFertilizeEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.mockito.Mockito.*
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class PaperCaneListenerTest {
    private class Fixture(height: Int = 3) {
        val uuid = UUID(0, 10)
        val world = mock(World::class.java)
        val season = SeasonId(UUID(0, 1))
        val materials = mutableMapOf<Triple<Int, Int, Int>, Material>()
        val blocks = mutableMapOf<Triple<Int, Int, Int>, Block>()
        val zone = ResourceZone("cane", ResourceKind.SUGAR_CANE, ResourceBounds(0, 60, 0, 4, 62, 4))
        val manifest = WorldManifest(UUID(0, 20), season, WorldId("minecraft:cane"), uuid, 1, "a".repeat(64), zone.bounds, listOf(zone))
        val index = ResourceZoneIndex(listOf(RegisteredWorldManifest(manifest, Instant.EPOCH, "console")))
        var policy: CaneGrowthPolicy? = CaneGrowthPolicy(CaneActivation(season, height, "console", "test", Instant.EPOCH), index)
        val listener = PaperCaneListener { policy }
        init {
            `when`(world.uid).thenReturn(uuid)
            `when`(world.key).thenReturn(NamespacedKey.minecraft("cane"))
            `when`(world.minHeight).thenReturn(-64)
            `when`(world.maxHeight).thenReturn(320)
            `when`(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true)
            `when`(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer { block(it.getArgument(0), it.getArgument(1), it.getArgument(2)) }
        }
        fun block(x: Int, y: Int, z: Int): Block = blocks.getOrPut(Triple(x, y, z)) {
            val b = mock(Block::class.java)
            `when`(b.world).thenReturn(world)
            `when`(b.x).thenReturn(x); `when`(b.y).thenReturn(y); `when`(b.z).thenReturn(z)
            `when`(b.type).thenAnswer { materials[Triple(x, y, z)] ?: Material.AIR }
            b
        }
        fun state(x: Int, y: Int, z: Int, material: Material = Material.SUGAR_CANE): BlockState {
            val state = mock(BlockState::class.java)
            val b = block(x, y, z)
            `when`(state.block).thenReturn(b)
            `when`(state.type).thenReturn(material)
            return state
        }
        fun grow(x: Int, y: Int, z: Int, material: Material = Material.SUGAR_CANE): BlockGrowEvent {
            val e = BlockGrowEvent(block(x, y, z), state(x, y, z, material))
            listener.onGrow(e)
            return e
        }
    }
    @Test fun `growth checks target entire column height and loaded identity`() {
        val f = Fixture()
        f.materials[Triple(0, 60, 0)] = Material.SUGAR_CANE
        assertFalse(f.grow(0, 61, 0).isCancelled)
        f.materials[Triple(0, 61, 0)] = Material.SUGAR_CANE
        assertFalse(f.grow(0, 62, 0).isCancelled)
        f.materials[Triple(0, 62, 0)] = Material.SUGAR_CANE
        assertTrue(f.grow(0, 63, 0).isCancelled)
        assertTrue(f.grow(5, 60, 0).isCancelled)
        `when`(f.world.uid).thenReturn(UUID(0, 99))
        assertTrue(f.grow(0, 60, 0).isCancelled)
    }
    @Test fun `fertilizer checks the complete proposed column and cancels mixed batch`() {
        val f = Fixture()
        f.materials[Triple(0, 60, 0)] = Material.SUGAR_CANE
        val valid = BlockFertilizeEvent(f.block(0, 60, 0), null, listOf(f.state(0, 61, 0), f.state(0, 62, 0)))
        f.listener.onFertilize(valid)
        assertFalse(valid.isCancelled)
        val invalid = BlockFertilizeEvent(f.block(0, 60, 0), null, listOf(f.state(0, 61, 0), f.state(5, 60, 0)))
        f.listener.onFertilize(invalid)
        assertTrue(invalid.isCancelled)
        val tall = BlockFertilizeEvent(f.block(0, 60, 0), null, listOf(f.state(0, 61, 0), f.state(0, 62, 0), f.state(0, 63, 0)))
        f.listener.onFertilize(tall)
        assertTrue(tall.isCancelled)
    }
    @Test fun `planting denies invalid location including operators and reports reason`() {
        val f = Fixture()
        f.materials[Triple(5, 60, 0)] = Material.SUGAR_CANE
        val e = mock(BlockPlaceEvent::class.java)
        val player = mock(Player::class.java)
        val placed = f.block(5, 60, 0)
        `when`(e.blockPlaced).thenReturn(placed)
        `when`(e.player).thenReturn(player)
        `when`(player.isOp).thenReturn(true)
        f.listener.onPlace(e)
        verify(e).isCancelled = true
        verify(player).sendMessage(any(net.kyori.adventure.text.Component::class.java))
    }
    @Test fun `disabled policy leaves cane alone but unavailable runtime fails closed without affecting wheat`() {
        val f = Fixture()
        f.policy = CaneGrowthPolicy(null, f.index)
        assertFalse(f.grow(100, 60, 100).isCancelled)
        f.policy = null
        assertTrue(f.grow(0, 60, 0).isCancelled)
        assertFalse(f.grow(100, 60, 100, Material.WHEAT).isCancelled)
        f.policy = CaneGrowthPolicy(CaneActivation(f.season, 3, "console", "test", Instant.EPOCH), f.index)
        `when`(f.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false)
        assertTrue(f.grow(0, 60, 0).isCancelled)
        verify(f.world, never()).getChunkAt(anyInt(), anyInt())
    }
}
