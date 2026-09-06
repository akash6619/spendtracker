package com.spendtracker.app.data

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.spendtracker.core.importing.SourceFingerprinter
import com.spendtracker.core.model.LedgerTransaction
import com.spendtracker.core.model.SourceMessage
import com.spendtracker.core.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coarse reasons the original SMS cannot be shown.
 * Values are privacy-safe; no provider or message details accompany them.
 */
enum class SourceUnavailableReason {
    NOT_ANDROID_SMS,
    NO_PROVIDER_ID,
    PERMISSION_REVOKED,
    MESSAGE_NOT_FOUND,
    LOOKUP_FAILED,
}

/**
 * Result of one on-demand source lookup. [Found] carries the raw message only
 * long enough to display it; callers must discard it when the view closes.
 */
sealed interface SourceLookupResult {
    data class Found(
        val sender: String,
        val body: String,
        val receivedAtEpochMillis: Long,
    ) : SourceLookupResult

    data class Unavailable(val reason: SourceUnavailableReason) : SourceLookupResult
}

/**
 * Platform boundary for reading one original SMS on explicit user action.
 * Implementations must never persist, cache, or log the returned body.
 */
fun interface SourceMessageLookup {
    suspend fun lookup(transaction: LedgerTransaction): SourceLookupResult
}

/**
 * Android SMS-provider implementation of [SourceMessageLookup].
 *
 * It resolves the persisted provider row ID against the system inbox at display
 * time, then recomputes the installation-local fingerprint of the fetched row
 * and compares it with the stored fingerprint before showing anything. Android
 * can reuse provider row IDs after deletions, so the ID alone may point at an
 * unrelated message; only a fingerprint match proves it is the original source.
 * The body exists only in the returned value; nothing is written to the app
 * database and no content is logged.
 */
class SmsSourceLookup internal constructor(
    private val queryRow: (providerId: String) -> Cursor?,
    private val fingerprinter: SourceFingerprinter,
) : SourceMessageLookup {
    constructor(context: Context, fingerprinter: SourceFingerprinter) : this(
        queryRow = { providerId ->
            context.applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                PROJECTION,
                "${Telephony.Sms._ID} = ?",
                arrayOf(providerId),
                null,
            )
        },
        fingerprinter = fingerprinter,
    )

    override suspend fun lookup(transaction: LedgerTransaction): SourceLookupResult = withContext(Dispatchers.IO) {
        if (transaction.sourceType != SourceType.ANDROID_SMS) {
            return@withContext SourceLookupResult.Unavailable(SourceUnavailableReason.NOT_ANDROID_SMS)
        }
        val providerId = transaction.sourceProviderId
        if (providerId == null) {
            return@withContext SourceLookupResult.Unavailable(SourceUnavailableReason.NO_PROVIDER_ID)
        }
        try {
            val row = queryRow(providerId)
                ?: return@withContext SourceLookupResult.Unavailable(SourceUnavailableReason.MESSAGE_NOT_FOUND)
            row.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@withContext SourceLookupResult.Unavailable(SourceUnavailableReason.MESSAGE_NOT_FOUND)
                }
                val sender = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)).orEmpty()
                val body = cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty()
                val receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                val fetchedFingerprint = fingerprinter.fingerprint(
                    SourceMessage(
                        sourceId = null,
                        sender = sender,
                        body = body,
                        receivedAtEpochMillis = receivedAt,
                    ),
                )
                if (fetchedFingerprint != transaction.sourceFingerprint) {
                    // The provider ID was reused; this is not the original message.
                    return@withContext SourceLookupResult.Unavailable(SourceUnavailableReason.MESSAGE_NOT_FOUND)
                }
                SourceLookupResult.Found(sender, body, receivedAt)
            }
        } catch (_: SecurityException) {
            SourceLookupResult.Unavailable(SourceUnavailableReason.PERMISSION_REVOKED)
        } catch (_: Exception) {
            SourceLookupResult.Unavailable(SourceUnavailableReason.LOOKUP_FAILED)
        }
    }

    private companion object {
        val PROJECTION = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
    }
}
