package io.bennyc.civilizations.domain.claim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaimBoundsTest {
    private val overworld = WorldId("minecraft:overworld")

    @Test
    fun `normalizes selection corners and counts inclusive area`() {
        val bounds = ClaimBounds.between(overworld, 12, 8, 10, 5)

        assertEquals(10, bounds.minX)
        assertEquals(12, bounds.maxX)
        assertEquals(5, bounds.minZ)
        assertEquals(8, bounds.maxZ)
        assertEquals(3, bounds.width)
        assertEquals(4, bounds.depth)
        assertEquals(12, bounds.area)
    }

    @Test
    fun `single block claim has one block of area`() {
        val bounds = ClaimBounds.between(overworld, -4, -9, -4, -9)

        assertEquals(1, bounds.area)
        assertTrue(bounds.contains(-4, -9))
    }

    @Test
    fun `contains uses block coordinates correctly across negative chunk boundaries`() {
        val bounds = ClaimBounds.between(overworld, -17, -17, -1, -1)

        assertTrue(bounds.contains(BlockPosition2D(overworld, -17, -17)))
        assertTrue(bounds.contains(BlockPosition2D(overworld, -1, -1)))
        assertFalse(bounds.contains(BlockPosition2D(overworld, 0, -1)))
        assertFalse(bounds.contains(BlockPosition2D(WorldId("minecraft:the_nether"), -1, -1)))
    }

    @Test
    fun `crossing rectangles overlap even when no corner is contained`() {
        val horizontal = ClaimBounds.between(overworld, -10, -2, 10, 2)
        val vertical = ClaimBounds.between(overworld, -2, -10, 2, 10)

        assertTrue(horizontal.overlaps(vertical))
        assertTrue(vertical.overlaps(horizontal))
    }

    @Test
    fun `rectangles in different worlds never overlap or connect`() {
        val first = ClaimBounds.between(overworld, 0, 0, 4, 4)
        val second = ClaimBounds.between(WorldId("minecraft:the_end"), 0, 0, 4, 4)

        assertFalse(first.overlaps(second))
        assertFalse(first.isEdgeAdjacentTo(second))
    }

    @Test
    fun `edge adjacency accepts shared edges but rejects corners gaps and overlap`() {
        val center = ClaimBounds.between(overworld, 0, 0, 4, 4)
        val east = ClaimBounds.between(overworld, 5, 1, 8, 3)
        val north = ClaimBounds.between(overworld, 1, -3, 3, -1)
        val corner = ClaimBounds.between(overworld, 5, 5, 8, 8)
        val gap = ClaimBounds.between(overworld, 6, 1, 8, 3)
        val overlapping = ClaimBounds.between(overworld, 4, 1, 8, 3)

        assertTrue(center.isEdgeAdjacentTo(east))
        assertTrue(east.isEdgeAdjacentTo(center))
        assertTrue(center.isEdgeAdjacentTo(north))
        assertFalse(center.isEdgeAdjacentTo(corner))
        assertFalse(center.isEdgeAdjacentTo(gap))
        assertFalse(center.isEdgeAdjacentTo(overlapping))
    }

    @Test
    fun `rejects areas that cannot be represented by a long`() {
        assertFailsWith<IllegalArgumentException> {
            ClaimBounds.between(
                overworld,
                Int.MIN_VALUE,
                Int.MIN_VALUE,
                Int.MAX_VALUE,
                Int.MAX_VALUE,
            )
        }
    }
}
