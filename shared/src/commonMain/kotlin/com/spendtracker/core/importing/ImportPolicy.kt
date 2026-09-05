package com.spendtracker.core.importing

/**
 * Defines the privacy and history limits applied to an inbox import.
 *
 * The MVP reads three calendar months and explicitly forbids retaining raw SMS
 * bodies. Keeping these values together makes the policy visible to import code
 * and prevents platform adapters from silently choosing broader behavior.
 */
data class ImportPolicy(
    val historyMonths: Int = DEFAULT_HISTORY_MONTHS,
    // Kept explicit so future import modes cannot silently retain message text.
    val retainRawMessageBody: Boolean = false,
) {
    init {
        require(historyMonths > 0) { "History window must be positive" }
    }

    companion object {
        const val DEFAULT_HISTORY_MONTHS = 3
    }
}
