package com.spendtracker.app.data

import android.content.Context
import android.provider.Telephony
import com.spendtracker.core.importing.ImportPolicy
import com.spendtracker.core.importing.MessageSource
import com.spendtracker.core.model.SourceMessage
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [MessageSource] backed by the system SMS inbox content provider.
 *
 * It calculates the configured calendar-month cutoff, queries only required
 * columns on an IO dispatcher, and immediately passes each row to the consumer.
 * It does not retain a list of source messages or persist their bodies.
 */
class SmsInboxReader(private val context: Context) : MessageSource {
    suspend fun readRecentMessages(
        policy: ImportPolicy = ImportPolicy(),
        consume: (SourceMessage) -> Unit,
    ): Int {
        val cutoff = ZonedDateTime.now()
            .minusMonths(policy.historyMonths.toLong())
            .toInstant()
            .toEpochMilli()

        return readMessagesSince(cutoff, consume)
    }

    override suspend fun readMessagesSince(
        cutoffEpochMillis: Long,
        consume: (SourceMessage) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        // Project only the fields needed for parsing and source identity.
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
        var scanned = 0

        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(cutoffEpochMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val senderColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateColumn = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

            while (cursor.moveToNext()) {
                scanned += 1
                // Consume each row immediately; this reader never accumulates SMS bodies.
                consume(
                    SourceMessage(
                        sourceId = cursor.getLong(idColumn).toString(),
                        sender = cursor.getString(senderColumn).orEmpty(),
                        body = cursor.getString(bodyColumn).orEmpty(),
                        receivedAtEpochMillis = cursor.getLong(dateColumn),
                    ),
                )
            }
        }

        scanned
    }
}
