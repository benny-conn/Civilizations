package io.bennyc.civilizations.domain.damage

import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.WorldId

data class BlockPosition3D(
    val worldId: WorldId,
    val x: Int,
    val y: Int,
    val z: Int,
) {
    fun horizontal(): BlockPosition2D = BlockPosition2D(worldId, x, z)
}
