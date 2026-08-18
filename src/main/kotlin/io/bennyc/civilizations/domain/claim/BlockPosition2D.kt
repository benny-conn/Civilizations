package io.bennyc.civilizations.domain.claim

data class BlockPosition2D(
    val worldId: WorldId,
    val x: Int,
    val z: Int,
) {
    val chunkKey: ChunkKey
        get() = ChunkKey.containingBlock(x, z)
}
