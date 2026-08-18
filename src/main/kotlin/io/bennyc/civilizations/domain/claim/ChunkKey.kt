package io.bennyc.civilizations.domain.claim

@JvmInline
value class ChunkKey(val packed: Long) {
    val x: Int
        get() = (packed shr Int.SIZE_BITS).toInt()

    val z: Int
        get() = packed.toInt()

    companion object {
        fun of(x: Int, z: Int): ChunkKey =
            ChunkKey((x.toLong() shl Int.SIZE_BITS) xor (z.toLong() and 0xffffffffL))

        fun containingBlock(x: Int, z: Int): ChunkKey =
            of(Math.floorDiv(x, CHUNK_WIDTH), Math.floorDiv(z, CHUNK_WIDTH))

        private const val CHUNK_WIDTH = 16
    }
}
