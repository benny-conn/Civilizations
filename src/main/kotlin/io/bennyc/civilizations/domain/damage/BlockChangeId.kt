package io.bennyc.civilizations.domain.damage

import java.util.UUID

@JvmInline
value class BlockChangeId(val value: UUID) {
    override fun toString(): String = value.toString()
}
