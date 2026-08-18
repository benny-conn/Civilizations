package io.bennyc.civilizations.domain.claim

import java.util.UUID

@JvmInline
value class ClaimId(val value: UUID) {
    override fun toString(): String = value.toString()
}
