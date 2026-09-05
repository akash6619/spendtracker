package com.spendtracker.app.data

import android.content.Context
import com.spendtracker.core.model.SourceType
import com.spendtracker.core.model.TransactionCandidate
import com.spendtracker.core.parser.FinancialMessageParser

/**
 * Result of one inbox scan.
 * It reports the number of rows inspected and carries only accepted,
 * privacy-safe candidates forward to repository persistence.
 */
data class MessageScanResult(
    val scannedMessages: Int,
    val transactions: List<TransactionCandidate>,
)

/**
 * App-facing boundary for initiating a recent-message scan.
 * The abstraction lets the ViewModel use production SMS scanning or a
 * deterministic fake without depending on Android provider APIs.
 */
fun interface MessageScanner {
    suspend fun scanRecentMessages(): MessageScanResult
}

/**
 * Coordinates the current three-month SMS scan pipeline.
 *
 * Each inbox row is parsed locally; accepted rows receive a keyed source
 * fingerprint and become [TransactionCandidate] values. Unsupported messages
 * are counted as scanned but are not returned or persisted.
 */
class SmsMessageScanner(
    context: Context,
    private val fingerprinter: AndroidSourceFingerprinter,
) : MessageScanner {
    private val inboxReader = SmsInboxReader(context)
    private val parser = FinancialMessageParser()

    override suspend fun scanRecentMessages(): MessageScanResult {
        val transactions = mutableListOf<TransactionCandidate>()
        val scannedMessages = inboxReader.readRecentMessages { message ->
            parser.parse(message)?.let { parsed ->
                // Fingerprint while the ephemeral body is available; persist parsed data only.
                transactions += TransactionCandidate(
                    sourceType = SourceType.ANDROID_SMS,
                    sourceProviderId = message.sourceId,
                    sourceFingerprint = fingerprinter.fingerprint(message),
                    transaction = parsed,
                )
            }
        }
        return MessageScanResult(
            scannedMessages = scannedMessages,
            transactions = transactions,
        )
    }
}
