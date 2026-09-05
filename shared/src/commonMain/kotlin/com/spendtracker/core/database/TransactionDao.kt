package com.spendtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import kotlinx.coroutines.flow.Flow

@Dao
/**
 * Defines Room queries and atomic write rules for the transaction ledger.
 *
 * Besides list, detail, reporting, review, and count queries, this DAO resolves
 * fingerprint/provider-ID collisions transactionally. Re-imports update detected
 * fields while preserving stable IDs, creation time, and explicit user overrides.
 */
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY sourceReceivedAtEpochMillis DESC, id ASC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE sourceType = :sourceType AND sourceFingerprint = :fingerprint")
    suspend fun findByFingerprint(sourceType: String, fingerprint: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE sourceType = :sourceType AND sourceProviderId = :providerId")
    suspend fun findByProviderId(sourceType: String, providerId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("UPDATE transactions SET sourceProviderId = NULL, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun clearProviderId(id: String, updatedAt: Long)

    @Query("UPDATE transactions SET userCategory = :category, userIncludedInSpend = :included, updatedAtEpochMillis = :updatedAt WHERE id = :id")
    suspend fun updateOverrides(id: String, category: String?, included: Boolean?, updatedAt: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT * FROM transactions WHERE sourceReceivedAtEpochMillis >= :fromInclusive AND sourceReceivedAtEpochMillis < :toExclusive ORDER BY sourceReceivedAtEpochMillis DESC")
    suspend fun inPeriod(fromInclusive: Long, toExclusive: Long): List<TransactionEntity>

    @Query("""
        SELECT COALESCE(SUM(amountMinor), 0) AS totalMinor, COUNT(*) AS transactionCount
        FROM transactions
        WHERE currency = 'INR'
          AND COALESCE(userIncludedInSpend, detectedIncludedInSpend) = 1
          AND sourceReceivedAtEpochMillis >= :fromInclusive
          AND sourceReceivedAtEpochMillis < :toExclusive
    """)
    suspend fun periodTotal(fromInclusive: Long, toExclusive: Long): PeriodTotal

    @Query("SELECT * FROM transactions WHERE confidence < :threshold OR kind = 'UNKNOWN' ORDER BY sourceReceivedAtEpochMillis DESC")
    suspend fun needingReview(threshold: Double): List<TransactionEntity>

    @Query("""
        SELECT COALESCE(userCategory, detectedCategory) AS category,
               SUM(amountMinor) AS totalMinor,
               COUNT(*) AS transactionCount
        FROM transactions
        WHERE currency = 'INR'
          AND COALESCE(userIncludedInSpend, detectedIncludedInSpend) = 1
          AND sourceReceivedAtEpochMillis >= :fromInclusive
          AND sourceReceivedAtEpochMillis < :toExclusive
        GROUP BY COALESCE(userCategory, detectedCategory)
        ORDER BY totalMinor DESC
    """)
    suspend fun categoryTotals(fromInclusive: Long, toExclusive: Long): List<CategoryTotal>

    @Transaction
    suspend fun upsertAll(incoming: List<TransactionEntity>) {
        incoming.forEach { candidate ->
            // Fingerprint is authoritative because Android may reuse provider row IDs.
            val fingerprintMatch = findByFingerprint(candidate.sourceType, candidate.sourceFingerprint)
            if (fingerprintMatch != null) {
                // Free a reused provider ID before attaching it to the fingerprint match.
                candidate.sourceProviderId?.let { providerId ->
                    findByProviderId(candidate.sourceType, providerId)
                        ?.takeIf { it.id != fingerprintMatch.id }
                        ?.let { clearProviderId(it.id, candidate.updatedAtEpochMillis) }
                }
                update(candidate.copy(
                    id = fingerprintMatch.id,
                    // Re-import detected fields, but never overwrite user corrections.
                    userCategory = fingerprintMatch.userCategory,
                    userIncludedInSpend = fingerprintMatch.userIncludedInSpend,
                    createdAtEpochMillis = fingerprintMatch.createdAtEpochMillis,
                ))
            } else {
                // A provider-ID collision represents a new fact, not an update.
                candidate.sourceProviderId?.let { providerId ->
                    findByProviderId(candidate.sourceType, providerId)?.let { previous ->
                        clearProviderId(previous.id, candidate.updatedAtEpochMillis)
                    }
                }
                insert(candidate)
            }
        }
    }
}
