package io.bennyc.civilizations.infrastructure.paper.war

import io.bennyc.civilizations.domain.identity.PlayerId
import io.bennyc.civilizations.domain.war.BattleId
import io.bennyc.civilizations.domain.war.BattleLifeEventId
import org.bukkit.NamespacedKey
import org.bukkit.entity.Entity
import org.bukkit.persistence.PersistentDataType
import java.nio.charset.StandardCharsets
import java.util.UUID

/** Dependency-free recognition of BattleLock's documented combat-log stand-in marker. */
internal object BattleLockStandIn {
    private val playerIdKey = NamespacedKey("battlelock", "combat_log_player_id")

    fun ownerId(entity: Entity): PlayerId? {
        val stored = entity.persistentDataContainer.get(playerIdKey, PersistentDataType.STRING)
            ?: return null
        return runCatching { PlayerId(UUID.fromString(stored)) }.getOrNull()
    }

    /** A stand-in entity UUID is stable across duplicate death callbacks and retries. */
    fun lifeEventId(entity: Entity): BattleLifeEventId =
        BattleLifeEventId(
            UUID.nameUUIDFromBytes(
                "civilizations:battlelock:${entity.uniqueId}"
                    .toByteArray(StandardCharsets.UTF_8),
            ),
        )
}

internal object PaperBattleDeathIdentity {
    /**
     * Paper exposes no death-event UUID. Battle/player/life/tick is stable for duplicate
     * dispatch of one death while allowing later lives and post-restart deaths to differ.
     */
    fun playerDeath(
        battleId: BattleId,
        playerId: PlayerId,
        observedLivesRemaining: Int,
        serverTick: Int,
    ): BattleLifeEventId = BattleLifeEventId(
        UUID.nameUUIDFromBytes(
            ("civilizations:player-death:$battleId:$playerId:" +
                "$observedLivesRemaining:$serverTick").toByteArray(StandardCharsets.UTF_8),
        ),
    )
}
