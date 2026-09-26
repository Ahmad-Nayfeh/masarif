package com.masarif.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.masarif.core.MatchType
import com.masarif.core.RuleSource
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(t: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<TransactionEntity>)

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /** كل العمليات ضمن الفترة المختارة (start = 0 يعني الكل). */
    @Query("SELECT * FROM transactions WHERE dateTime >= :start ORDER BY dateTime DESC, id DESC")
    fun observeSince(start: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE smsHash = :hash")
    suspend fun countByHash(hash: String): Int

    /** نفس النص بنفس وقت العملية (±نافذة): يلتقط تكرار الاستلام الحي ثم إعادة المسح. */
    @Query("SELECT COUNT(*) FROM transactions WHERE rawSms = :body AND dateTime BETWEEN :from AND :to")
    suspend fun countSimilar(body: String, from: Long, to: Long): Int

    @Query(
        """
        SELECT merchantClean AS merchant, COUNT(*) AS count, SUM(amountHalalas) AS total, MAX(dateTime) AS lastAt
        FROM transactions
        WHERE categoryId = :uncategorizedId AND categoryOverridden = 0 AND dateTime >= :start
        GROUP BY LOWER(merchantClean)
        ORDER BY lastAt DESC
        """
    )
    fun observePending(uncategorizedId: Long, start: Long): Flow<List<PendingMerchant>>

    @Query("SELECT COUNT(DISTINCT LOWER(merchantClean)) FROM transactions WHERE categoryId = :uncategorizedId AND categoryOverridden = 0 AND dateTime >= :start")
    suspend fun countPendingMerchants(uncategorizedId: Long, start: Long): Int

    @Query("SELECT * FROM transactions WHERE LOWER(merchantClean) = LOWER(:merchant) AND dateTime >= :start ORDER BY dateTime DESC")
    fun observeByMerchant(merchant: String, start: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND dateTime >= :start ORDER BY dateTime DESC")
    fun observeByCategory(categoryId: Long, start: Long): Flow<List<TransactionEntity>>

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE LOWER(merchantClean) = LOWER(:merchant) AND categoryOverridden = 0")
    suspend fun setCategoryForMerchant(merchant: String, categoryId: Long): Int

    @Query("UPDATE transactions SET categoryId = :categoryId, categoryOverridden = :overridden WHERE id = :id")
    suspend fun setCategory(id: Long, categoryId: Long, overridden: Boolean)

    @Query("UPDATE transactions SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Query("SELECT * FROM transactions WHERE categoryOverridden = 0")
    suspend fun getRuleDriven(): List<TransactionEntity>

    @Query("SELECT DISTINCT cardLast4 FROM transactions WHERE cardLast4 IS NOT NULL ORDER BY cardLast4")
    fun observeCards(): Flow<List<String>>
}

@Dao
interface MerchantRuleDao {
    @Insert
    suspend fun insert(r: MerchantRuleEntity): Long

    @Insert
    suspend fun insertAll(list: List<MerchantRuleEntity>)

    @Update
    suspend fun update(r: MerchantRuleEntity)

    @Query("DELETE FROM merchant_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM merchant_rules")
    suspend fun deleteAll()

    @Query("DELETE FROM merchant_rules WHERE createdBy = :source")
    suspend fun deleteBySource(source: RuleSource)

    @Query("SELECT * FROM merchant_rules ORDER BY priority DESC, pattern ASC")
    fun observeAll(): Flow<List<MerchantRuleEntity>>

    @Query("SELECT * FROM merchant_rules")
    suspend fun getAll(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE id = :id")
    suspend fun getById(id: Long): MerchantRuleEntity?

    @Query("SELECT * FROM merchant_rules WHERE pattern = :pattern AND matchType = :matchType AND createdBy = :source LIMIT 1")
    suspend fun find(pattern: String, matchType: MatchType, source: RuleSource): MerchantRuleEntity?

    @Query("SELECT COUNT(*) FROM merchant_rules WHERE createdBy = :source")
    suspend fun countBySource(source: RuleSource): Int

    @Query("UPDATE merchant_rules SET categoryId = :to WHERE categoryId = :from")
    suspend fun moveCategory(from: Long, to: Long): Int

    @Query("UPDATE merchant_rules SET categoryId = :categoryId WHERE canonicalName = :canonical AND createdBy = :source")
    suspend fun setCategoryForCanonical(canonical: String, categoryId: Long, source: RuleSource): Int

    @Query("SELECT COUNT(*) FROM merchant_rules WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(c: CategoryEntity): Long

    @Insert
    suspend fun insertAll(list: List<CategoryEntity>)

    @Update
    suspend fun update(c: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()

    @Query("SELECT * FROM categories ORDER BY isIncome ASC, sortOrder ASC, id ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isIncome ASC, sortOrder ASC, id ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE catKey = :key LIMIT 1")
    suspend fun getByKey(key: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT MAX(sortOrder) FROM categories")
    suspend fun maxSortOrder(): Int?
}

@Dao
interface UnparsedSmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(u: UnparsedSmsEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(list: List<UnparsedSmsEntity>)

    @Query("DELETE FROM unparsed_sms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM unparsed_sms")
    suspend fun deleteAll()

    @Query("SELECT * FROM unparsed_sms ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<UnparsedSmsEntity>>

    @Query("SELECT * FROM unparsed_sms ORDER BY receivedAt DESC")
    suspend fun getAll(): List<UnparsedSmsEntity>

    @Query("SELECT COUNT(*) FROM unparsed_sms")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM unparsed_sms")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM unparsed_sms WHERE smsHash = :hash")
    suspend fun countByHash(hash: String): Int
}
