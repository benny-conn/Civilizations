package io.bennyc.civilizations.domain.identity

import java.util.UUID

@JvmInline
value class SeasonId(val value: UUID) {
    override fun toString(): String = value.toString()
}
