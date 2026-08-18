package io.bennyc.civilizations.application.claim

import io.bennyc.civilizations.domain.claim.BlockPosition2D
import io.bennyc.civilizations.domain.claim.ChunkKey
import io.bennyc.civilizations.domain.claim.Claim
import io.bennyc.civilizations.domain.claim.ClaimBounds
import io.bennyc.civilizations.domain.claim.ClaimId
import io.bennyc.civilizations.domain.claim.WorldId
import io.bennyc.civilizations.domain.identity.CivilizationId
import io.bennyc.civilizations.domain.identity.SeasonId

/**
 * A derived, thread-confined index for hot claim queries.
 *
 * Authoritative claims live in persistence. This index is rebuilt at startup
 * and then mutated on the Paper server thread alongside accepted claim changes.
 */
class ClaimSpatialIndex(
    val seasonId: SeasonId,
    initialClaims: Iterable<Claim> = emptyList(),
) {
    private val claimsById = linkedMapOf<ClaimId, Claim>()
    private val claimIdsByWorldAndChunk =
        mutableMapOf<WorldId, MutableMap<ChunkKey, MutableSet<ClaimId>>>()

    val size: Int
        get() = claimsById.size

    init {
        rebuild(initialClaims)
    }

    fun get(claimId: ClaimId): Claim? = claimsById[claimId]

    fun add(claim: Claim) {
        require(claim.seasonId == seasonId) {
            "Claim ${claim.id} belongs to season ${claim.seasonId}, not indexed season $seasonId"
        }
        require(claim.id !in claimsById) { "Claim ${claim.id} is already indexed" }

        claimsById[claim.id] = claim
        index(claim)
    }

    fun remove(claimId: ClaimId): Claim? {
        val removed = claimsById.remove(claimId) ?: return null
        val chunks = claimIdsByWorldAndChunk[removed.bounds.worldId] ?: return removed

        removed.bounds.forEachChunk { chunkKey ->
            chunks[chunkKey]?.let { claimIds ->
                claimIds.remove(claimId)
                if (claimIds.isEmpty()) {
                    chunks.remove(chunkKey)
                }
            }
        }

        if (chunks.isEmpty()) {
            claimIdsByWorldAndChunk.remove(removed.bounds.worldId)
        }

        return removed
    }

    fun rebuild(claims: Iterable<Claim>) {
        val replacementClaims = linkedMapOf<ClaimId, Claim>()
        val replacementChunks =
            mutableMapOf<WorldId, MutableMap<ChunkKey, MutableSet<ClaimId>>>()

        for (claim in claims) {
            require(claim.seasonId == seasonId) {
                "Claim ${claim.id} belongs to season ${claim.seasonId}, not indexed season $seasonId"
            }
            require(replacementClaims.putIfAbsent(claim.id, claim) == null) {
                "Claim ${claim.id} occurs more than once during index rebuild"
            }
            index(claim, replacementChunks)
        }

        claimsById.clear()
        claimsById.putAll(replacementClaims)
        claimIdsByWorldAndChunk.clear()
        claimIdsByWorldAndChunk.putAll(replacementChunks)
    }

    /**
     * Returns the containing claim when the ownership invariant holds.
     * Throws if corrupt authoritative data has allowed overlapping claims.
     */
    fun claimAt(position: BlockPosition2D): Claim? {
        var found: Claim? = null
        forEachCandidate(position.worldId, position.chunkKey) { claim ->
            if (claim.bounds.contains(position)) {
                found?.let { previous ->
                    error("Overlapping claims ${previous.id} and ${claim.id} contain $position")
                }
                found = claim
            }
        }
        return found
    }

    /** Returns every match so startup validation can diagnose corrupt overlaps. */
    fun claimsAt(position: BlockPosition2D): List<Claim> = buildList {
        forEachCandidate(position.worldId, position.chunkKey) { claim ->
            if (claim.bounds.contains(position)) {
                add(claim)
            }
        }
    }

    fun findIntersecting(bounds: ClaimBounds): List<Claim> {
        val candidates = candidateIds(bounds)
        return candidates.mapNotNull { claimId ->
            claimsById[claimId]?.takeIf { it.bounds.overlaps(bounds) }
        }
    }

    fun findEdgeAdjacent(
        bounds: ClaimBounds,
        civilizationId: CivilizationId? = null,
    ): List<Claim> {
        val candidates = candidateIdsAround(bounds)
        return candidates.mapNotNull { claimId ->
            claimsById[claimId]?.takeIf { claim ->
                (civilizationId == null || claim.civilizationId == civilizationId) &&
                    claim.bounds.isEdgeAdjacentTo(bounds)
            }
        }
    }

    private fun index(
        claim: Claim,
        chunksByWorld: MutableMap<WorldId, MutableMap<ChunkKey, MutableSet<ClaimId>>> =
            claimIdsByWorldAndChunk,
    ) {
        val chunks = chunksByWorld.getOrPut(claim.bounds.worldId) { mutableMapOf() }
        claim.bounds.forEachChunk { chunkKey ->
            chunks.getOrPut(chunkKey) { linkedSetOf() }.add(claim.id)
        }
    }

    private inline fun forEachCandidate(
        worldId: WorldId,
        chunkKey: ChunkKey,
        action: (Claim) -> Unit,
    ) {
        val claimIds = claimIdsByWorldAndChunk[worldId]?.get(chunkKey) ?: return
        for (claimId in claimIds) {
            claimsById[claimId]?.let(action)
        }
    }

    private fun candidateIds(bounds: ClaimBounds): Set<ClaimId> {
        val candidates = linkedSetOf<ClaimId>()
        val chunks = claimIdsByWorldAndChunk[bounds.worldId] ?: return candidates
        bounds.forEachChunk { chunkKey ->
            chunks[chunkKey]?.let(candidates::addAll)
        }
        return candidates
    }

    private fun candidateIdsAround(bounds: ClaimBounds): Set<ClaimId> {
        val candidates = linkedSetOf<ClaimId>()
        val chunks = claimIdsByWorldAndChunk[bounds.worldId] ?: return candidates

        val minChunkX = chunkCoordinate(bounds.minX.toLong() - 1L)
        val maxChunkX = chunkCoordinate(bounds.maxX.toLong() + 1L)
        val minChunkZ = chunkCoordinate(bounds.minZ.toLong() - 1L)
        val maxChunkZ = chunkCoordinate(bounds.maxZ.toLong() + 1L)

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                chunks[ChunkKey.of(chunkX, chunkZ)]?.let(candidates::addAll)
            }
        }
        return candidates
    }

    private fun chunkCoordinate(blockCoordinate: Long): Int =
        Math.floorDiv(blockCoordinate, CHUNK_WIDTH).toInt()

    private companion object {
        const val CHUNK_WIDTH = 16L
    }
}
