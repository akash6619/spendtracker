package com.spendtracker.core.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

/**
 * Persists explicit user-approved merchant/category mappings.
 *
 * Rules are keyed by normalized merchant, observable for future settings UI,
 * and independently deletable without modifying transaction-level overrides.
 */
@Dao
interface MerchantCategoryRuleDao {
    @Query("SELECT * FROM merchant_category_rules ORDER BY normalizedMerchant ASC")
    fun observeAll(): Flow<List<MerchantCategoryRuleEntity>>

    @Query("SELECT * FROM merchant_category_rules")
    suspend fun getAll(): List<MerchantCategoryRuleEntity>

    @Query("SELECT * FROM merchant_category_rules WHERE normalizedMerchant = :merchant")
    suspend fun find(merchant: String): MerchantCategoryRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(rule: MerchantCategoryRuleEntity)

    @Query("DELETE FROM merchant_category_rules WHERE normalizedMerchant = :merchant")
    suspend fun delete(merchant: String)
}
