package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.domain.identity.PlayerId

/** Bounds and cools durable hostile-entry attempts without delaying movement events. */
internal class BattleEntryAttemptGate(
    private val maxPending: Int = DEFAULT_MAX_PENDING,
    private val cooldownNanos: Long = DEFAULT_COOLDOWN_NANOS,
) {
    private val pending = linkedSetOf<PlayerId>()
    private val nextAttemptAt = hashMapOf<PlayerId, Long>()
    private val nextFeedbackAt = hashMapOf<PlayerId, Long>()
    private var accepted = 0L
    private var pendingRejected = 0L
    private var cooldownRejected = 0L
    private var saturatedRejected = 0L
    private var peakPending = 0

    init {
        require(maxPending > 0) { "Battle entry pending bound must be positive" }
        require(cooldownNanos >= 0) { "Battle entry cooldown cannot be negative" }
    }

    fun begin(playerId: PlayerId, nowNanos: Long): BattleEntryGateDecision = when {
        playerId in pending -> {
            pendingRejected++
            BattleEntryGateDecision.Pending
        }
        nowNanos < nextAttemptAt.getOrDefault(playerId, Long.MIN_VALUE) -> {
            cooldownRejected++
            BattleEntryGateDecision.CoolingDown
        }
        pending.size >= maxPending -> {
            saturatedRejected++
            BattleEntryGateDecision.Saturated
        }
        else -> {
            pending += playerId
            nextAttemptAt[playerId] = saturatedAdd(nowNanos, cooldownNanos)
            accepted++
            peakPending = maxOf(peakPending, pending.size)
            BattleEntryGateDecision.Accepted
        }
    }

    fun complete(playerId: PlayerId) {
        pending -= playerId
    }

    fun shouldSendFeedback(playerId: PlayerId, nowNanos: Long): Boolean {
        if (nowNanos < nextFeedbackAt.getOrDefault(playerId, Long.MIN_VALUE)) {
            return false
        }
        nextFeedbackAt[playerId] = saturatedAdd(nowNanos, cooldownNanos)
        return true
    }

    fun forget(playerId: PlayerId) {
        pending -= playerId
        nextAttemptAt -= playerId
        nextFeedbackAt -= playerId
    }

    fun metrics(): BattleEntryGateMetrics = BattleEntryGateMetrics(
        pending = pending.size,
        peakPending = peakPending,
        accepted = accepted,
        pendingRejected = pendingRejected,
        cooldownRejected = cooldownRejected,
        saturatedRejected = saturatedRejected,
    )

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    companion object {
        const val DEFAULT_MAX_PENDING = 64
        const val DEFAULT_COOLDOWN_NANOS = 1_000_000_000L
    }
}

internal enum class BattleEntryGateDecision {
    Accepted,
    Pending,
    CoolingDown,
    Saturated,
}

internal data class BattleEntryGateMetrics(
    val pending: Int,
    val peakPending: Int,
    val accepted: Long,
    val pendingRejected: Long,
    val cooldownRejected: Long,
    val saturatedRejected: Long,
)
