package io.bennyc.civilizations.domain.claim

import kotlin.test.Test
import kotlin.test.assertEquals

class ChunkKeyTest {
    @Test
    fun `packs and unpacks signed chunk coordinates`() {
        val key = ChunkKey.of(-42, 97)

        assertEquals(-42, key.x)
        assertEquals(97, key.z)
    }

    @Test
    fun `maps negative block coordinates using floor division`() {
        assertEquals(ChunkKey.of(-1, -1), ChunkKey.containingBlock(-1, -1))
        assertEquals(ChunkKey.of(-1, -1), ChunkKey.containingBlock(-16, -16))
        assertEquals(ChunkKey.of(-2, -2), ChunkKey.containingBlock(-17, -17))
        assertEquals(ChunkKey.of(0, 0), ChunkKey.containingBlock(15, 15))
        assertEquals(ChunkKey.of(1, 1), ChunkKey.containingBlock(16, 16))
    }
}
