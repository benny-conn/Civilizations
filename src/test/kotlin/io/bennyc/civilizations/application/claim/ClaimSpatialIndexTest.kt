package io.bennyc.civilizations.application.claim

import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class ClaimSpatialIndexTest {
    private val overworld = WorldId("minecraft:overworld")
    private val nether = WorldId("minecraft:the_nether")
    private val firstCivilization = civilizationId(1)
    private val secondCivilization = civilizationId(2)

    @Test
    fun `finds claims across chunks and negative coordinates`() {
        val negative = claim(1, firstCivilization, overworld, -33, -20, -1, -1)
        val positive = claim(2, secondCivilization, overworld, 16, 16, 48, 48)
        val index = ClaimSpatialIndex(listOf(negative, positive))

        assertSame(negative, index.claimAt(BlockPosition2D(overworld, -17, -8)))
        assertSame(positive, index.claimAt(BlockPosition2D(overworld, 48, 48)))
        assertNull(index.claimAt(BlockPosition2D(overworld, 15, 15)))
        assertNull(index.claimAt(BlockPosition2D(nether, -17, -8)))
    }

    @Test
    fun `filters same-chunk candidates with exact rectangle containment`() {
        val first = claim(1, firstCivilization, overworld, 0, 0, 2, 2)
        val second = claim(2, secondCivilization, overworld, 12, 12, 15, 15)
        val index = ClaimSpatialIndex(listOf(first, second))

        assertSame(first, index.claimAt(BlockPosition2D(overworld, 1, 1)))
        assertNull(index.claimAt(BlockPosition2D(overworld, 8, 8)))
        assertSame(second, index.claimAt(BlockPosition2D(overworld, 14, 14)))
    }

    @Test
    fun `finds exact intersections including crossing rectangles`() {
        val horizontal = claim(1, firstCivilization, overworld, -10, -2, 10, 2)
        val unrelatedSameChunk = claim(2, firstCivilization, overworld, 11, 11, 12, 12)
        val otherWorld = claim(3, firstCivilization, nether, -10, -2, 10, 2)
        val index = ClaimSpatialIndex(listOf(horizontal, unrelatedSameChunk, otherWorld))
        val vertical = ClaimBounds.between(overworld, -2, -10, 2, 10)

        assertEquals(setOf(horizontal.id), index.findIntersecting(vertical).map { it.id }.toSet())
    }

    @Test
    fun `finds edge adjacency and can filter by owner`() {
        val ownedNeighbor = claim(1, firstCivilization, overworld, 0, 1, 4, 3)
        val foreignNeighbor = claim(2, secondCivilization, overworld, 6, 5, 8, 8)
        val cornerOnly = claim(3, firstCivilization, overworld, 10, 5, 12, 7)
        val index = ClaimSpatialIndex(listOf(ownedNeighbor, foreignNeighbor, cornerOnly))
        val proposed = ClaimBounds.between(overworld, 5, 0, 9, 4)

        assertEquals(
            setOf(ownedNeighbor.id),
            index.findEdgeAdjacent(proposed, firstCivilization).map { it.id }.toSet(),
        )
        assertEquals(
            setOf(ownedNeighbor.id, foreignNeighbor.id),
            index.findEdgeAdjacent(proposed).map { it.id }.toSet(),
        )
    }

    @Test
    fun `remove and rebuild replace all derived index state`() {
        val first = claim(1, firstCivilization, overworld, 0, 0, 20, 20)
        val second = claim(2, secondCivilization, nether, -20, -20, 0, 0)
        val index = ClaimSpatialIndex(listOf(first))

        assertSame(first, index.remove(first.id))
        assertNull(index.claimAt(BlockPosition2D(overworld, 10, 10)))
        assertEquals(0, index.size)

        index.rebuild(listOf(second))

        assertEquals(1, index.size)
        assertNull(index.get(first.id))
        assertSame(second, index.claimAt(BlockPosition2D(nether, -10, -10)))
    }

    @Test
    fun `duplicate add and failed rebuild do not corrupt existing state`() {
        val first = claim(1, firstCivilization, overworld, 0, 0, 4, 4)
        val duplicate = first.copy(bounds = ClaimBounds.between(overworld, 10, 10, 12, 12))
        val index = ClaimSpatialIndex(listOf(first))

        assertFailsWith<IllegalArgumentException> { index.add(duplicate) }
        assertFailsWith<IllegalArgumentException> { index.rebuild(listOf(first, duplicate)) }

        assertEquals(1, index.size)
        assertSame(first, index.claimAt(BlockPosition2D(overworld, 2, 2)))
        assertNull(index.claimAt(BlockPosition2D(overworld, 11, 11)))
    }

    @Test
    fun `strict point lookup exposes corrupt overlapping ownership`() {
        val first = claim(1, firstCivilization, overworld, 0, 0, 10, 10)
        val second = claim(2, secondCivilization, overworld, 5, 5, 15, 15)
        val index = ClaimSpatialIndex(listOf(first, second))
        val overlap = BlockPosition2D(overworld, 7, 7)

        assertEquals(setOf(first.id, second.id), index.claimsAt(overlap).map { it.id }.toSet())
        assertFailsWith<IllegalStateException> { index.claimAt(overlap) }
    }

    @Test
    fun `randomized point and intersection queries match brute force`() {
        val random = Random(0xC1_71_2A_710L)
        val worlds = listOf(overworld, nether, WorldId("civilizations:test_world"))
        val claims = List(750) { index ->
            val world = worlds[random.nextInt(worlds.size)]
            val minX = random.nextInt(-2_000, 2_001)
            val minZ = random.nextInt(-2_000, 2_001)
            val width = random.nextInt(1, 96)
            val depth = random.nextInt(1, 96)
            claim(
                index + 1,
                if (index % 2 == 0) firstCivilization else secondCivilization,
                world,
                minX,
                minZ,
                minX + width - 1,
                minZ + depth - 1,
            )
        }
        val index = ClaimSpatialIndex(claims)

        repeat(5_000) {
            val point = BlockPosition2D(
                worlds[random.nextInt(worlds.size)],
                random.nextInt(-2_200, 2_201),
                random.nextInt(-2_200, 2_201),
            )
            val expected = claims.filter { it.bounds.contains(point) }.map { it.id }.toSet()
            val actual = index.claimsAt(point).map { it.id }.toSet()

            assertEquals(expected, actual, "Point lookup diverged for $point")
        }

        repeat(1_000) {
            val world = worlds[random.nextInt(worlds.size)]
            val firstX = random.nextInt(-2_200, 2_201)
            val firstZ = random.nextInt(-2_200, 2_201)
            val query = ClaimBounds.between(
                world,
                firstX,
                firstZ,
                firstX + random.nextInt(0, 128),
                firstZ + random.nextInt(0, 128),
            )
            val expected = claims.filter { it.bounds.overlaps(query) }.map { it.id }.toSet()
            val actual = index.findIntersecting(query).map { it.id }.toSet()

            assertEquals(expected, actual, "Intersection lookup diverged for $query")
        }
    }

    private fun claim(
        id: Int,
        civilizationId: CivilizationId,
        worldId: WorldId,
        firstX: Int,
        firstZ: Int,
        secondX: Int,
        secondZ: Int,
    ): Claim = Claim(
        ClaimId(UUID(0L, id.toLong())),
        civilizationId,
        ClaimBounds.between(worldId, firstX, firstZ, secondX, secondZ),
    )

    private fun civilizationId(id: Int): CivilizationId =
        CivilizationId(UUID(1L, id.toLong()))
}
