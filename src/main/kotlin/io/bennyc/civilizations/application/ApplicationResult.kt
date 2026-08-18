package io.bennyc.civilizations.application

/**
 * A command-ready result that keeps expected rule failures out of exceptions.
 * Adapters may translate these values into chat, console, HTTP, or test output.
 */
sealed interface ApplicationResult<out T> {
    data class Applied<T>(val value: T) : ApplicationResult<T>

    data class Unchanged<T>(val value: T) : ApplicationResult<T>

    data class Rejected(val failure: ApplicationFailure) : ApplicationResult<Nothing>
}

interface ApplicationFailure {
    val description: String
}
