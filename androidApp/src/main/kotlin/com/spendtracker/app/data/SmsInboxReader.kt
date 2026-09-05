package com.spendtracker.app.data

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.spendtracker.core.importing.MessageSource
import com.spendtracker.core.model.SourceMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android [MessageSource] backed by the system SMS inbox content provider.
 *
 * It accepts the coordinator's exact history boundary, queries only required
 * columns on an IO dispatcher, and immediately passes each row to the consumer.
 * A provider that cannot return a cursor fails the import rather than being
 * mistaken for an empty inbox; no message bodies are retained or persisted.
 */
class SmsInboxReader internal constructor(
    private val queryInbox: (cutoffEpochMillis: Long) -> Cursor?,
) : MessageSource {
    constructor(context: Context) : this(
        queryInbox = { cutoffEpochMillis ->
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                PROJECTION,
                "${Telephony.Sms.DATE} >= ?",
                arrayOf(cutoffEpochMillis.toString()),
                "${Telephony.Sms.DATE} ASC",
            )
        },
    )

    override suspend fun readMessagesSince(
        cutoffEpochMillis: Long,
        consume: suspend (SourceMessage) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        var scanned = 0
        val cursor = checkNotNull(queryInbox(cutoffEpochMillis)) {
            "SMS provider did not return a cursor"
        }
        cursor.use {
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

    private companion object {
        // Project only the fields needed for parsing and source identity.
        val PROJECTION = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )
    }
}
