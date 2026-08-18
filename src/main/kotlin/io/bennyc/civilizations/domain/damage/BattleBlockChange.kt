package io.bennyc.civilizations.domain.damage

import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.identity.SeasonId
import io.bennyc.civilizations.domain.war.BattleId
import java.time.Instant

/**
 * The framework-neutral state needed to reconstruct an ordinary block.
 * Inventory-bearing and richer block-entity snapshots are intentionally not
 * represented until their duplication and serialization rules are defined.
 */
data class SimpleBlockSnapshot(
    val blockData: String,
) {
    init {
        require(blockData.isNotBlank()) { "Block data cannot be blank" }
        require(blockData == blockData.trim()) {
            "Block data cannot have surrounding whitespace"
        }
        require(blockData.length <= MAX_BLOCK_DATA_LENGTH) {
            "Block data exceeds $MAX_BLOCK_DATA_LENGTH characters"
        }
    }

    private companion object {
        const val MAX_BLOCK_DATA_LENGTH = 32_768
    }
}

enum class BlockMutationCause {
    PLAYER_BREAK,
    PLAYER_PLACE,
    EXPLOSION,
    FIRE,
    FLUID,
    PISTON,
    ENTITY_CHANGE,
}

/** One immutable first-write-wins row in a battle's damage journal. */
data class BattleBlockChange(
    val id: BlockChangeId,
    val seasonId: SeasonId,
    val battleId: BattleId,
    val claimId: ClaimId,
    val position: BlockPosition3D,
    val originalState: SimpleBlockSnapshot,
    val firstMutationCause: BlockMutationCause,
    val firstActorId: PlayerId,
    val recordedAt: Instant,
) {
    val cursor: BlockChangeCursor
        get() = BlockChangeCursor(recordedAt, id)
}

data class BlockChangeCursor(
    val recordedAt: Instant,
    val id: BlockChangeId,
)
