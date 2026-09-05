package com.spendtracker.core.importing

import com.spendtracker.core.model.SourceMessage

/**
 * Platform boundary for reading source messages into shared import logic.
 * Implementations stream one message at a time from the requested cutoff and
 * return only an aggregate count, preventing raw inbox bodies from accumulating.
 */
interface MessageSource {
    suspend fun readMessagesSince(
        cutoffEpochMillis: Long,
        consume: (SourceMessage) -> Unit,
    ): Int
}
