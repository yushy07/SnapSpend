package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.util.MoneyUtils

enum class TransactionType {
    EXPENSE,
    EARNING,
    TRANSFER;

    companion object {
        fun fromString(value: String): TransactionType = when (value.trim().lowercase()) {
            "earning", "income", "credit" -> EARNING
            "transfer", "self" -> TRANSFER
            else -> EXPENSE
        }
    }
}

enum class TransactionStatus {
    SUCCESS,
    FAILED,
    PENDING;

    companion object {
        fun fromString(value: String): TransactionStatus = when (value.trim().lowercase()) {
            "pending", "processing" -> PENDING
            "failed", "declined", "cancelled" -> FAILED
            else -> SUCCESS
        }
    }
}

enum class TransactionSource {
    SCREENSHOT,
    PDF,
    MANUAL,
    IMPORT
}

enum class CategoryType {
    EXPENSE,
    EARNING;

    companion object {
        fun fromString(value: String): CategoryType = when (value.trim().lowercase()) {
            "earning", "income" -> EARNING
            else -> EXPENSE
        }
    }
}

enum class RepeatingState {
    SUGGESTED,
    CONFIRMED,
    DISMISSED
}

/**
 * Room Entity for Transactions (`transactions` table).
 * Stores all monetary amounts as whole `Long` in paise (`amountPaise` / `amountInPaise`, e.g., 15050L = ₹150.50), never Double.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["duplicateKey"]),
        Index(value = ["timestamp"]),
        Index(value = ["category"]),
        Index(value = ["categoryId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long,
    val hasExplicitTime: Boolean = true,
    val amountPaise: Long, // Always stored in whole paise (15050L = ₹150.50)
    val type: TransactionType,
    val merchant: String,
    val category: String = "Other",
    val categoryId: Long = 0L,
    val paymentApp: String = "Unknown",
    val referenceNumber: String? = null,
    val status: TransactionStatus = TransactionStatus.SUCCESS,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val source: TransactionSource = TransactionSource.MANUAL,
    val duplicateKey: String = MoneyUtils.buildDuplicateKey(
        referenceNumber = referenceNumber,
        timestampMillis = timestamp,
        hasExplicitTime = hasExplicitTime,
        amountPaise = amountPaise,
        merchant = merchant
    ),
    val deletedAtMillis: Long? = null
) {
    /**
     * Secondary constructor supporting string-typed `type` ("expense"/"earning"/"transfer"),
     * `status` ("pending"/"success"/"failed"), `amountInPaise` (Long), `date` (Long), and `categoryId` (Long).
     */
    constructor(
        id: Long = 0L,
        date: Long,
        amountInPaise: Long,
        type: String,
        merchant: String,
        categoryId: Long,
        paymentApp: String = "Unknown",
        referenceNumber: String? = null,
        status: String = "success",
        note: String = "",
        createdAt: Long = System.currentTimeMillis(),
        category: String = DefaultCategories.categoryNameForId(categoryId)
    ) : this(
        id = id,
        timestamp = date,
        hasExplicitTime = true,
        amountPaise = amountInPaise,
        type = TransactionType.fromString(type),
        merchant = merchant,
        category = category,
        categoryId = categoryId,
        paymentApp = paymentApp,
        referenceNumber = referenceNumber,
        status = TransactionStatus.fromString(status),
        note = note,
        createdAt = createdAt,
        source = TransactionSource.MANUAL,
        duplicateKey = MoneyUtils.buildDuplicateKey(
            referenceNumber = referenceNumber,
            timestampMillis = date,
            hasExplicitTime = true,
            amountPaise = amountInPaise,
            merchant = merchant
        ),
        deletedAtMillis = null
    )

    /** Secondary constructor supporting `date` and `amount` in paise as `Long` with enum types. */
    constructor(
        id: Long = 0L,
        date: Long,
        amount: Long,
        type: TransactionType,
        merchant: String,
        category: String,
        status: TransactionStatus = TransactionStatus.SUCCESS,
        categoryId: Long = 0L,
        paymentApp: String = "Unknown",
        referenceNumber: String? = null,
        note: String = "",
        createdAt: Long = System.currentTimeMillis(),
        source: TransactionSource = TransactionSource.MANUAL,
        duplicateKey: String = MoneyUtils.buildDuplicateKey(
            referenceNumber = referenceNumber,
            timestampMillis = date,
            hasExplicitTime = true,
            amountPaise = amount,
            merchant = merchant
        )
    ) : this(
        id = id,
        timestamp = date,
        hasExplicitTime = true,
        amountPaise = amount,
        type = type,
        merchant = merchant,
        category = category,
        categoryId = categoryId,
        paymentApp = paymentApp,
        referenceNumber = referenceNumber,
        status = status,
        note = note,
        createdAt = createdAt,
        source = source,
        duplicateKey = duplicateKey,
        deletedAtMillis = null
    )

    /** Alias for timestamp (epoch millis). */
    val date: Long
        get() = timestamp

    /** Exact Long amount in paise (e.g. 15050L = ₹150.50). */
    val amountInPaise: Long
        get() = amountPaise

    /** Alias for amountPaise (exact Long in paise, e.g. 15050L = ₹150.50). */
    val amount: Long
        get() = amountPaise

    /** Lowercase string representation of type ("expense", "earning", "transfer"). */
    val typeString: String
        get() = type.name.lowercase()

    /** Lowercase string representation of status ("pending", "success", "failed"). */
    val statusString: String
        get() = status.name.lowercase()

    /** Formatted Rupee display string converted from `Long` paise (e.g., "₹150.50"). */
    val formattedAmountRupees: String
        get() = MoneyUtils.formatPaise(amountPaise)

    /** Signed formatted Rupee display string (e.g., "-₹150.50" or "+₹150.50"). */
    val signedFormattedAmountRupees: String
        get() = when (type) {
            TransactionType.EARNING -> MoneyUtils.formatPaise(amountPaise, showPlusForPositive = true)
            TransactionType.EXPENSE -> "-" + MoneyUtils.formatPaise(amountPaise)
            TransactionType.TRANSFER -> MoneyUtils.formatPaise(amountPaise)
        }

    /**
     * Converts this database entity (storing `Long` paise) into a UI presentation model
     * with formatted Rupee strings and resolved category visual metadata.
     */
    fun toUiPresentation(categoryEntity: CategoryEntity? = null): TransactionUiPresentation {
        val resolvedCategoryName = categoryEntity?.name ?: category
        val resolvedColor = categoryEntity?.colorHex
            ?: DefaultCategories.ALL_DEFAULTS.firstOrNull { it.name.equals(resolvedCategoryName, ignoreCase = true) }?.colorHex
            ?: "#94A3B8"
        val resolvedIcon = categoryEntity?.iconName
            ?: DefaultCategories.ALL_DEFAULTS.firstOrNull { it.name.equals(resolvedCategoryName, ignoreCase = true) }?.iconName
            ?: "category"

        return TransactionUiPresentation(
            id = id,
            date = timestamp,
            formattedDate = MoneyUtils.formatIsoDate(timestamp),
            formattedTime = MoneyUtils.formatTime24h(timestamp),
            amountInPaise = amountPaise,
            formattedAmountRupees = formattedAmountRupees,
            signedFormattedAmountRupees = signedFormattedAmountRupees,
            type = type.name.lowercase(),
            transactionType = type,
            merchant = merchant,
            categoryId = categoryEntity?.id ?: categoryId,
            categoryName = resolvedCategoryName,
            categoryColorHex = resolvedColor,
            categoryIconName = resolvedIcon,
            paymentApp = paymentApp,
            referenceNumber = referenceNumber,
            status = status.name.lowercase(),
            transactionStatus = status,
            note = note,
            createdAt = createdAt,
            deletedAtMillis = deletedAtMillis
        )
    }
}

typealias Transaction = TransactionEntity

/**
 * UI Presentation model for a Transaction, converting the database's `Long` paise values
 * (`amountInPaise`) into formatted Rupee strings ready for UI display.
 */
data class TransactionUiPresentation(
    val id: Long,
    val date: Long,
    val formattedDate: String,
    val formattedTime: String,
    val amountInPaise: Long,
    val formattedAmountRupees: String,
    val signedFormattedAmountRupees: String,
    val type: String,
    val transactionType: TransactionType,
    val merchant: String,
    val categoryId: Long,
    val categoryName: String,
    val categoryColorHex: String,
    val categoryIconName: String,
    val paymentApp: String,
    val referenceNumber: String?,
    val status: String,
    val transactionStatus: TransactionStatus,
    val note: String,
    val createdAt: Long,
    val deletedAtMillis: Long? = null
)

/**
 * Room Entity for Categories (`categories` table).
 * Includes id (Long), name (String), type (CategoryType / String), colorHex (String), and iconName (String).
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val type: CategoryType,
    val colorHex: String,
    val iconName: String,
    val isDefault: Boolean = false
) {
    /** Secondary constructor accepting `type` as a String ("expense" / "earning"). */
    constructor(
        id: Long = 0L,
        name: String,
        type: String,
        colorHex: String,
        iconName: String,
        isDefault: Boolean = false
    ) : this(
        id = id,
        name = name,
        type = CategoryType.fromString(type),
        colorHex = colorHex,
        iconName = iconName,
        isDefault = isDefault
    )

    val typeString: String
        get() = type.name.lowercase()

    val color: String
        get() = colorHex

    val icon: String
        get() = iconName
}

typealias Category = CategoryEntity

@Entity(tableName = "merchant_memory")
data class MerchantMemoryEntity(
    @PrimaryKey
    val normalizedMerchant: String,
    val displayMerchant: String,
    val categoryName: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val categoryName: String,
    val monthlyLimitPaise: Long, // Stored in whole paise
    val lastNotifiedThreshold: Int = 0, // 0, 80, or 100
    val notifiedYearMonth: String = "" // e.g., "2026-10"
)

@Entity(tableName = "repeating_payments")
data class RepeatingPaymentEntity(
    @PrimaryKey
    val id: String, // normalized merchant key
    val merchant: String,
    val category: String,
    val usualAmountPaise: Long, // Stored in whole paise
    val usualGapDays: Int,
    val nextExpectedTimestamp: Long,
    val state: RepeatingState = RepeatingState.SUGGESTED
)

object DefaultCategories {
    val LEGACY_FACTORY_COLOR_HEXES = setOf(
        "#FF9C9C",
        "#A3F01F",
        "#3DD1E0",
        "#E8DFC8"
    )

    val EXPENSE_CATEGORIES = listOf(
        CategoryEntity(id = 1L, name = "Food & Dining", type = CategoryType.EXPENSE, colorHex = "#FF5A36", iconName = "restaurant", isDefault = true),
        CategoryEntity(id = 2L, name = "Groceries", type = CategoryType.EXPENSE, colorHex = "#65D46E", iconName = "shopping_basket", isDefault = true),
        CategoryEntity(id = 3L, name = "Transport & Fuel", type = CategoryType.EXPENSE, colorHex = "#2563EB", iconName = "directions_car", isDefault = true),
        CategoryEntity(id = 4L, name = "Shopping", type = CategoryType.EXPENSE, colorHex = "#FF85B3", iconName = "shopping_bag", isDefault = true),
        CategoryEntity(id = 5L, name = "Bills & Utilities", type = CategoryType.EXPENSE, colorHex = "#FFD23F", iconName = "bolt", isDefault = true),
        CategoryEntity(id = 6L, name = "Rent", type = CategoryType.EXPENSE, colorHex = "#8B5CF6", iconName = "home", isDefault = true),
        CategoryEntity(id = 7L, name = "Entertainment", type = CategoryType.EXPENSE, colorHex = "#F59E0B", iconName = "movie", isDefault = true),
        CategoryEntity(id = 8L, name = "Health", type = CategoryType.EXPENSE, colorHex = "#10B981", iconName = "medical_services", isDefault = true),
        CategoryEntity(id = 9L, name = "Education", type = CategoryType.EXPENSE, colorHex = "#06B6D4", iconName = "school", isDefault = true),
        CategoryEntity(id = 10L, name = "Travel", type = CategoryType.EXPENSE, colorHex = "#D946EF", iconName = "flight", isDefault = true),
        CategoryEntity(id = 11L, name = "Subscriptions", type = CategoryType.EXPENSE, colorHex = "#E11D48", iconName = "autorenew", isDefault = true),
        CategoryEntity(id = 12L, name = "Sent to People", type = CategoryType.EXPENSE, colorHex = "#6366F1", iconName = "person", isDefault = true),
        CategoryEntity(id = 13L, name = "Other", type = CategoryType.EXPENSE, colorHex = "#94A3B8", iconName = "category", isDefault = true)
    )

    val EARNING_CATEGORIES = listOf(
        CategoryEntity(id = 14L, name = "Salary", type = CategoryType.EARNING, colorHex = "#22C55E", iconName = "payments", isDefault = true),
        CategoryEntity(id = 15L, name = "Freelance/Side Income", type = CategoryType.EARNING, colorHex = "#0EA5E9", iconName = "work", isDefault = true),
        CategoryEntity(id = 16L, name = "Pocket Money/Gifts", type = CategoryType.EARNING, colorHex = "#F472B6", iconName = "card_giftcard", isDefault = true),
        CategoryEntity(id = 17L, name = "Refunds & Cashback", type = CategoryType.EARNING, colorHex = "#A3E635", iconName = "replay", isDefault = true),
        CategoryEntity(id = 18L, name = "Other Income", type = CategoryType.EARNING, colorHex = "#EAB308", iconName = "savings", isDefault = true)
    )

    val ALL_DEFAULTS: List<CategoryEntity> = EXPENSE_CATEGORIES + EARNING_CATEGORIES

    fun categoryNameForId(categoryId: Long): String =
        ALL_DEFAULTS.firstOrNull { it.id == categoryId }?.name ?: "Other"

    fun categoryIdForName(categoryName: String): Long =
        ALL_DEFAULTS.firstOrNull { it.name.equals(categoryName.trim(), ignoreCase = true) }?.id ?: 0L

    fun defaultColorForName(categoryName: String): String =
        ALL_DEFAULTS.firstOrNull { it.name.equals(categoryName.trim(), ignoreCase = true) }?.colorHex ?: "#94A3B8"
}
