package io.bennyc.civilizations.domain.war

import java.util.UUID

@JvmInline
value class WarId(val value: UUID) {
    override fun toString(): String = value.toString()
}
