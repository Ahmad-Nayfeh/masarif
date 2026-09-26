package com.masarif.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.masarif.core.Channel
import com.masarif.core.Direction
import com.masarif.core.MatchType
import com.masarif.core.RuleSource

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["smsHash"], unique = true),
        Index(value = ["dateTime"]),
        Index(value = ["categoryId"]),
        Index(value = ["merchantClean"]),
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,
    /** موجب دائماً، بالهللات. */
    val amountHalalas: Long,
    val direction: Direction,
    val rawSms: String,
    val merchantRaw: String,
    val merchantClean: String,
    val categoryId: Long,
    val channel: Channel,
    val country: String? = null,
    val cardLast4: String? = null,
    val smsHash: String,
    val isManual: Boolean = false,
    /** true إن غيّر المستخدم تصنيف هذه العملية وحدها؛ القواعد لا تمسّها بعدها. */
    val categoryOverridden: Boolean = false,
    val createdAt: Long,
)

@Entity(tableName = "merchant_rules", indices = [Index(value = ["categoryId"])])
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val matchType: MatchType,
    val categoryId: Long,
    /** اسم معياري للعرض يوحّد العربي/الإنجليزي (بنده). */
    val canonicalName: String? = null,
    val createdBy: RuleSource,
    val priority: Int,
    val createdAt: Long,
)

@Entity(tableName = "categories", indices = [Index(value = ["catKey"], unique = true)])
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** مفتاح ثابت للتصنيفات الافتراضية، null لتصنيفات المستخدم. */
    val catKey: String? = null,
    val nameAr: String,
    val icon: String,
    val color: Long,
    val isIncome: Boolean,
    val sortOrder: Int,
    val isProtected: Boolean = false,
)

@Entity(tableName = "unparsed_sms", indices = [Index(value = ["smsHash"], unique = true)])
data class UnparsedSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val smsHash: String,
    val reason: String = "",
)

/** بطاقة في قائمة الانتظار: تاجر غير مصنّف مع عدد عملياته ومجموعها. */
data class PendingMerchant(
    val merchant: String,
    val count: Int,
    val total: Long,
    val lastAt: Long,
)

class Converters {
    @TypeConverter fun directionToString(v: Direction): String = v.name
    @TypeConverter fun stringToDirection(v: String): Direction = Direction.valueOf(v)
    @TypeConverter fun channelToString(v: Channel): String = v.name
    @TypeConverter fun stringToChannel(v: String): Channel = Channel.valueOf(v)
    @TypeConverter fun matchTypeToString(v: MatchType): String = v.name
    @TypeConverter fun stringToMatchType(v: String): MatchType = MatchType.valueOf(v)
    @TypeConverter fun ruleSourceToString(v: RuleSource): String = v.name
    @TypeConverter fun stringToRuleSource(v: String): RuleSource = RuleSource.valueOf(v)
}
