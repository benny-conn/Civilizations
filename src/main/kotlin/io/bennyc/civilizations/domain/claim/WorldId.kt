package io.bennyc.civilizations.domain.claim

@JvmInline
value class WorldId(val value: String) {
    init {
        require(value.isNotBlank()) { "World ID cannot be blank" }
        require(value == value.trim()) { "World ID cannot have surrounding whitespace" }
    }

    override fun toString(): String = value
}
