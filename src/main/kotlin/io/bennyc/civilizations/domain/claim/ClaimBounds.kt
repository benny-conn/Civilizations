package io.bennyc.civilizations.domain.claim

/**
 * An immutable, inclusive rectangle on Minecraft's X/Z plane.
 *
 * Construction is private so all instances have normalized coordinates and a
 * representable area. Y is intentionally absent: civilization ownership spans
 * the world's vertical build range.
 */
@ConsistentCopyVisibility
data class ClaimBounds private constructor(
    val worldId: WorldId,
    val minX: Int,
    val maxX: Int,
    val minZ: Int,
    val maxZ: Int,
) {
    val width: Long = maxX.toLong() - minX.toLong() + 1L
    val depth: Long = maxZ.toLong() - minZ.toLong() + 1L
    val area: Long = Math.multiplyExact(width, depth)

    val minChunkX: Int = Math.floorDiv(minX, CHUNK_WIDTH)
    val maxChunkX: Int = Math.floorDiv(maxX, CHUNK_WIDTH)
    val minChunkZ: Int = Math.floorDiv(minZ, CHUNK_WIDTH)
    val maxChunkZ: Int = Math.floorDiv(maxZ, CHUNK_WIDTH)

    fun contains(position: BlockPosition2D): Boolean =
        position.worldId == worldId && contains(position.x, position.z)

    fun contains(x: Int, z: Int): Boolean =
        x in minX..maxX && z in minZ..maxZ

    fun overlaps(other: ClaimBounds): Boolean =
        worldId == other.worldId &&
            intervalsOverlap(minX, maxX, other.minX, other.maxX) &&
            intervalsOverlap(minZ, maxZ, other.minZ, other.maxZ)

    /**
     * Returns true only when the rectangles share a non-zero-length block edge.
     * Overlap and corner-only contact are not considered edge adjacency.
     */
    fun isEdgeAdjacentTo(other: ClaimBounds): Boolean {
        if (worldId != other.worldId || overlaps(other)) {
            return false
        }

        val touchesOnX =
            areConsecutive(maxX, other.minX) || areConsecutive(other.maxX, minX)
        if (touchesOnX && intervalsOverlap(minZ, maxZ, other.minZ, other.maxZ)) {
            return true
        }

        val touchesOnZ =
            areConsecutive(maxZ, other.minZ) || areConsecutive(other.maxZ, minZ)
        return touchesOnZ && intervalsOverlap(minX, maxX, other.minX, other.maxX)
    }

    inline fun forEachChunk(action: (ChunkKey) -> Unit) {
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                action(ChunkKey.of(chunkX, chunkZ))
            }
        }
    }

    companion object {
        private const val CHUNK_WIDTH = 16

        fun between(
            worldId: WorldId,
            firstX: Int,
            firstZ: Int,
            secondX: Int,
            secondZ: Int,
        ): ClaimBounds {
            val minX = minOf(firstX, secondX)
            val maxX = maxOf(firstX, secondX)
            val minZ = minOf(firstZ, secondZ)
            val maxZ = maxOf(firstZ, secondZ)

            val width = maxX.toLong() - minX.toLong() + 1L
            val depth = maxZ.toLong() - minZ.toLong() + 1L
            require(width <= Long.MAX_VALUE / depth) {
                "Claim area exceeds the supported 64-bit range"
            }

            return ClaimBounds(worldId, minX, maxX, minZ, maxZ)
        }

        private fun intervalsOverlap(
            firstMin: Int,
            firstMax: Int,
            secondMin: Int,
            secondMax: Int,
        ): Boolean = firstMin <= secondMax && firstMax >= secondMin

        private fun areConsecutive(lower: Int, upper: Int): Boolean =
            lower.toLong() + 1L == upper.toLong()
    }
}
