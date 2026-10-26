package com.example.data.repository

import androidx.room.withTransaction
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.RepeatingPaymentEntity
import com.example.data.local.RepeatingState
import com.example.data.local.SnapSpendDatabase
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.data.rules.ResolutionMode
import com.example.util.MoneyUtils
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ImportSummary(
    val insertedCount: Int,
    val updatedPendingCount: Int,
    val skippedDuplicateCount: Int
)

class SnapSpendRepository(private val database: SnapSpendDatabase) {
    private val dao = database.dao()

    val transactionsFlow: Flow<List<TransactionEntity>> = dao.observeAllTransactions()
    val categoriesFlow: Flow<List<CategoryEntity>> = dao.observeAllCategories()
    val merchantMemoriesFlow: Flow<List<MerchantMemoryEntity>> = dao.observeAllMerchantMemories()
    val budgetsFlow: Flow<List<BudgetEntity>> = dao.observeAllBudgets()
    val repeatingPaymentsFlow: Flow<List<RepeatingPaymentEntity>> = dao.observeAllRepeatingPayments()

    suspend fun ensureDefaultCategoriesSeeded() {
        val current = dao.getAllCategoriesOnce()
        if (current.isEmpty()) {
            dao.insertCategoriesIgnore(DefaultCategories.ALL_DEFAULTS)
        } else {
            current.forEach { cat ->
                if (cat.isDefault && cat.colorHex.uppercase() in DefaultCategories.LEGACY_FACTORY_COLOR_HEXES) {
                    val updatedHex = DefaultCategories.defaultColorForName(cat.name)
                    if (!cat.colorHex.equals(updatedHex, ignoreCase = true)) {
                        dao.updateCategory(cat.copy(colorHex = updatedHex))
                    }
                }
            }
        }
    }

    suspend fun getAllTransactionsOnce(): List<TransactionEntity> = dao.getAllTransactionsOnce()
    suspend fun getAllCategoriesOnce(): List<CategoryEntity> = dao.getAllCategoriesOnce()
    suspend fun getAllMerchantMemoriesOnce(): List<MerchantMemoryEntity> = dao.getAllMerchantMemoriesOnce()
    suspend fun getAllBudgetsOnce(): List<BudgetEntity> = dao.getAllBudgetsOnce()
    suspend fun getAllRepeatingPaymentsOnce(): List<RepeatingPaymentEntity> = dao.getAllRepeatingPaymentsOnce()

    fun observeTransactionsByDateRange(startDate: Long, endDate: Long) =
        dao.observeTransactionsByDateRange(startDate, endDate)

    fun observeTransactionsByDateRangeUi(startDate: Long, endDate: Long) =
        dao.observeTransactionsByDateRangeUi(startDate, endDate)

    fun observeFilteredTransactionsUi(
        searchQuery: String = "",
        type: TransactionType? = null,
        category: String? = null,
        categoryId: Long? = null,
        status: TransactionStatus? = null,
        paymentApp: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        minAmountInPaise: Long? = null,
        maxAmountInPaise: Long? = null
    ) = dao.observeFilteredTransactionsUi(
        searchQuery = searchQuery,
        type = type,
        category = category,
        categoryId = categoryId,
        status = status,
        paymentApp = paymentApp,
        startDate = startDate,
        endDate = endDate,
        minAmountInPaise = minAmountInPaise,
        maxAmountInPaise = maxAmountInPaise
    )

    /**
     * Saves a single manual or edited transaction and records Merchant Memory if the user
     * specified a merchant and category.
     */
    suspend fun saveTransaction(
        transaction: TransactionEntity,
        learnMerchantCategory: Boolean = false
    ): Long {
        val resolvedCategoryId = if (transaction.categoryId != 0L) {
            transaction.categoryId
        } else {
            dao.getCategoryByName(transaction.category)?.id
                ?: DefaultCategories.categoryIdForName(transaction.category)
        }
        val resolvedCategoryName = if (transaction.category.isNotBlank() && transaction.category != "Other") {
            transaction.category
        } else if (resolvedCategoryId != 0L) {
            dao.getCategoryById(resolvedCategoryId)?.name
                ?: DefaultCategories.categoryNameForId(resolvedCategoryId)
        } else {
            transaction.category
        }

        val recomputedKey = MoneyUtils.buildDuplicateKey(
            referenceNumber = transaction.referenceNumber,
            timestampMillis = transaction.timestamp,
            hasExplicitTime = transaction.hasExplicitTime,
            amountPaise = transaction.amountPaise,
            merchant = transaction.merchant
        )
        val cleanTx = transaction.copy(
            category = resolvedCategoryName,
            categoryId = resolvedCategoryId,
            duplicateKey = recomputedKey
        )
        val id = if (cleanTx.id == 0L) {
            dao.insertTransaction(cleanTx)
        } else {
            dao.updateTransaction(cleanTx)
            cleanTx.id
        }

        if (learnMerchantCategory && cleanTx.type != TransactionType.TRANSFER) {
            saveMerchantMemoryRule(cleanTx.merchant, cleanTx.category)
        }
        refreshRepeatingPayments()
        return id
    }

    suspend fun deleteTransaction(id: Long) {
        dao.deleteTransactionById(id)
        refreshRepeatingPayments()
    }

    /**
     * Commits confirmed AI review drafts in an atomic database transaction:
     * - Drafts with ResolutionMode.UPDATE_PENDING_TO_FINAL update the existing PENDING transaction in place.
     * - Non-skipped new drafts are inserted.
     * - Any draft whose category was changed on the review screen saves a Merchant Memory rule.
     */
    suspend fun commitConfirmedDrafts(drafts: List<DraftTransaction>): ImportSummary {
        var inserted = 0
        var updatedPending = 0
        var skipped = 0

        database.withTransaction {
            for (draft in drafts) {
                if (draft.categoryWasManuallyEdited && draft.type != TransactionType.TRANSFER) {
                    saveMerchantMemoryRuleInternal(draft.merchant, draft.category)
                }

                if (draft.isSkipped) {
                    skipped++
                    continue
                }

                val key = draft.duplicateKey
                if (draft.resolutionMode == ResolutionMode.UPDATE_PENDING_TO_FINAL && draft.existingMatchId != null) {
                    val updatedEntity = TransactionEntity(
                        id = draft.existingMatchId,
                        timestamp = draft.timestamp,
                        hasExplicitTime = draft.hasExplicitTime,
                        amountPaise = draft.amountPaise,
                        type = draft.type,
                        merchant = draft.merchant,
                        category = draft.category,
                        paymentApp = draft.paymentApp,
                        referenceNumber = draft.referenceNumber,
                        status = draft.status,
                        note = draft.note,
                        source = draft.source,
                        duplicateKey = key
                    )
                    dao.updateTransaction(updatedEntity)
                    updatedPending++
                } else {
                    val entity = TransactionEntity(
                        timestamp = draft.timestamp,
                        hasExplicitTime = draft.hasExplicitTime,
                        amountPaise = draft.amountPaise,
                        type = draft.type,
                        merchant = draft.merchant,
                        category = draft.category,
                        paymentApp = draft.paymentApp,
                        referenceNumber = draft.referenceNumber,
                        status = draft.status,
                        note = draft.note,
                        source = draft.source,
                        duplicateKey = key
                    )
                    dao.insertTransaction(entity)
                    inserted++
                }
            }
        }
        refreshRepeatingPayments()
        return ImportSummary(
            insertedCount = inserted,
            updatedPendingCount = updatedPending,
            skippedDuplicateCount = skipped
        )
    }

    suspend fun saveMerchantMemoryRule(merchant: String, categoryName: String) {
        saveMerchantMemoryRuleInternal(merchant, categoryName)
    }

    private suspend fun saveMerchantMemoryRuleInternal(merchant: String, categoryName: String) {
        val norm = MoneyUtils.normalizeMerchant(merchant)
        if (norm == "unknown" || categoryName.isBlank()) return
        dao.upsertMerchantMemory(
            MerchantMemoryEntity(
                normalizedMerchant = norm,
                displayMerchant = merchant.trim(),
                categoryName = categoryName,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMerchantMemory(normalizedMerchant: String) {
        dao.deleteMerchantMemory(normalizedMerchant)
    }

    // --- Categories ---
    suspend fun addOrUpdateCategory(
        id: Long = 0L,
        oldName: String? = null,
        newName: String,
        type: CategoryType,
        colorHex: String,
        iconName: String,
        isDefault: Boolean = false
    ) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            dao.upsertCategory(
                CategoryEntity(
                    id = id,
                    name = trimmed,
                    type = type,
                    colorHex = colorHex,
                    iconName = iconName,
                    isDefault = isDefault
                )
            )
            if (!oldName.isNullOrBlank() && oldName != trimmed) {
                dao.updateTransactionsCategoryName(oldName, trimmed)
                dao.updateBudgetCategoryName(oldName, trimmed)
                dao.updateMerchantMemoryCategoryName(oldName, trimmed)
            }
        }
    }

    suspend fun deleteCustomCategory(category: CategoryEntity) {
        if (category.isDefault) return
        database.withTransaction {
            val fallback = if (category.type == CategoryType.EARNING) "Other Income" else "Other"
            dao.updateTransactionsCategoryName(category.name, fallback)
            dao.deleteCustomCategory(category.id)
        }
    }

    // --- Budgets ---
    suspend fun setCategoryBudget(categoryName: String, monthlyLimitPaise: Long) {
        if (monthlyLimitPaise <= 0L) {
            dao.deleteBudget(categoryName)
        } else {
            val existing = dao.getAllBudgetsOnce().firstOrNull { it.categoryName == categoryName }
            dao.upsertBudget(
                BudgetEntity(
                    categoryName = categoryName,
                    monthlyLimitPaise = monthlyLimitPaise,
                    lastNotifiedThreshold = existing?.lastNotifiedThreshold ?: 0,
                    notifiedYearMonth = existing?.notifiedYearMonth ?: ""
                )
            )
        }
    }

    suspend fun updateBudgetNotificationState(categoryName: String, threshold: Int, yearMonth: String) {
        val existing = dao.getAllBudgetsOnce().firstOrNull { it.categoryName == categoryName } ?: return
        dao.upsertBudget(
            existing.copy(
                lastNotifiedThreshold = threshold,
                notifiedYearMonth = yearMonth
            )
        )
    }

    // --- Repeating Payments ---
    suspend fun refreshRepeatingPayments() {
        val allTxs = dao.getAllTransactionsOnce()
        val existing = dao.getAllRepeatingPaymentsOnce()
        val detected = LocalInsightsAndRulesEngine.detectRepeatingPayments(allTxs, existing)
        dao.insertRepeatingPayments(detected)
    }

    suspend fun updateRepeatingPaymentState(id: String, newState: RepeatingState) {
        val existing = dao.getAllRepeatingPaymentsOnce().firstOrNull { it.id == id } ?: return
        dao.upsertRepeatingPayment(existing.copy(state = newState))
    }

    // --- Full Backup Serialization (JSON & CSV) ---
    suspend fun exportFullBackupJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAtMillis", System.currentTimeMillis())
        root.put("appName", "SnapSpend")

        val txArray = JSONArray()
        for (tx in dao.getAllTransactionsOnce()) {
            val obj = JSONObject().apply {
                put("id", tx.id)
                put("timestamp", tx.timestamp)
                put("hasExplicitTime", tx.hasExplicitTime)
                put("amountPaise", tx.amountPaise)
                put("type", tx.type.name)
                put("merchant", tx.merchant)
                put("category", tx.category)
                put("paymentApp", tx.paymentApp)
                put("referenceNumber", tx.referenceNumber ?: JSONObject.NULL)
                put("status", tx.status.name)
                put("note", tx.note)
                put("createdAt", tx.createdAt)
                put("source", tx.source.name)
                put("duplicateKey", tx.duplicateKey)
            }
            txArray.put(obj)
        }
        root.put("transactions", txArray)

        val catArray = JSONArray()
        for (cat in dao.getAllCategoriesOnce()) {
            val obj = JSONObject().apply {
                put("name", cat.name)
                put("type", cat.type.name)
                put("colorHex", cat.colorHex)
                put("iconName", cat.iconName)
                put("isDefault", cat.isDefault)
            }
            catArray.put(obj)
        }
        root.put("categories", catArray)

        val memArray = JSONArray()
        for (mem in dao.getAllMerchantMemoriesOnce()) {
            val obj = JSONObject().apply {
                put("normalizedMerchant", mem.normalizedMerchant)
                put("displayMerchant", mem.displayMerchant)
                put("categoryName", mem.categoryName)
                put("updatedAt", mem.updatedAt)
            }
            memArray.put(obj)
        }
        root.put("merchantMemories", memArray)

        val budgetArray = JSONArray()
        for (b in dao.getAllBudgetsOnce()) {
            val obj = JSONObject().apply {
                put("categoryName", b.categoryName)
                put("monthlyLimitPaise", b.monthlyLimitPaise)
            }
            budgetArray.put(obj)
        }
        root.put("budgets", budgetArray)

        val repArray = JSONArray()
        for (r in dao.getAllRepeatingPaymentsOnce()) {
            val obj = JSONObject().apply {
                put("id", r.id)
                put("merchant", r.merchant)
                put("category", r.category)
                put("usualAmountPaise", r.usualAmountPaise)
                put("usualGapDays", r.usualGapDays)
                put("nextExpectedTimestamp", r.nextExpectedTimestamp)
                put("state", r.state.name)
            }
            repArray.put(obj)
        }
        root.put("repeatingPayments", repArray)

        return root.toString(2)
    }

    suspend fun exportTransactionsCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("Date,Time,Merchant,Category,Type,Amount (Rupees),Amount (Paise),Status,Payment App,Reference Number,Source,Note")
        val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.US)

        for (tx in dao.getAllTransactionsOnce()) {
            val dStr = dateFmt.format(Date(tx.timestamp))
            val tStr = if (tx.hasExplicitTime) timeFmt.format(Date(tx.timestamp)) else ""
            val isNeg = tx.amountPaise < 0L
            val absPaise = kotlin.math.abs(tx.amountPaise)
            val sign = if (isNeg) "-" else ""
            val rupeesExact = String.format(Locale.US, "%s%d.%02d", sign, absPaise / 100L, absPaise % 100L)
            sb.append(csvEscape(dStr)).append(',')
            sb.append(csvEscape(tStr)).append(',')
            sb.append(csvEscape(tx.merchant)).append(',')
            sb.append(csvEscape(tx.category)).append(',')
            sb.append(csvEscape(tx.type.name)).append(',')
            sb.append(rupeesExact).append(',')
            sb.append(tx.amountPaise).append(',')
            sb.append(csvEscape(tx.status.name)).append(',')
            sb.append(csvEscape(tx.paymentApp)).append(',')
            sb.append(csvEscape(tx.referenceNumber.orEmpty())).append(',')
            sb.append(csvEscape(tx.source.name)).append(',')
            sb.appendLine(csvEscape(tx.note))
        }
        return sb.toString()
    }

    private fun csvEscape(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    /**
     * RFC 4180 compliant CSV parser supporting multi-line quoted fields, escaped quotes, and commas inside fields.
     */
    fun parseCsv(csvContent: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var insideQuotes = false
        var i = 0
        val len = csvContent.length

        while (i < len) {
            val c = csvContent[i]
            when {
                c == '"' -> {
                    if (insideQuotes && i + 1 < len && csvContent[i + 1] == '"') {
                        currentField.append('"')
                        i++
                    } else {
                        insideQuotes = !insideQuotes
                    }
                }
                c == ',' && !insideQuotes -> {
                    currentRow.add(currentField.toString())
                    currentField.clear()
                }
                (c == '\r' || c == '\n') && !insideQuotes -> {
                    if (c == '\r' && i + 1 < len && csvContent[i + 1] == '\n') {
                        i++
                    }
                    currentRow.add(currentField.toString())
                    currentField.clear()
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> {
                    currentField.append(c)
                }
            }
            i++
        }
        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentField.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    /**
     * Imports transactions from an RFC 4180 CSV export in Merge or Replace Everything mode.
     */
    suspend fun importTransactionsCsv(csvContent: String, replaceEverything: Boolean): ImportSummary {
        val rows = parseCsv(csvContent)
        if (rows.isEmpty()) {
            return ImportSummary(0, 0, 0)
        }
        val dataRows = if (rows.first().firstOrNull()?.trim()?.equals("Date", ignoreCase = true) == true) {
            rows.drop(1)
        } else {
            rows
        }

        val parsedTransactions = mutableListOf<TransactionEntity>()
        for (cols in dataRows) {
            if (cols.size < 4) continue
            val dateStr = cols.getOrNull(0)?.trim()
            val timeStr = cols.getOrNull(1)?.trim()
            val (timestamp, hasExplicitTime) = MoneyUtils.parseDateAndTime(dateStr, timeStr)
            val merchant = cols.getOrNull(2)?.trim()?.ifBlank { "Unknown" } ?: "Unknown"
            val category = cols.getOrNull(3)?.trim()?.ifBlank { "Other" } ?: "Other"
            val typeStr = cols.getOrNull(4)?.trim().orEmpty()
            val type = runCatching { TransactionType.valueOf(typeStr.uppercase(Locale.US)) }.getOrDefault(TransactionType.EXPENSE)

            val rupeesCol = cols.getOrNull(5)?.trim()
            val paiseCol = cols.getOrNull(6)?.trim()?.toLongOrNull()
            val amountPaise = when {
                paiseCol != null -> paiseCol
                !rupeesCol.isNullOrBlank() -> MoneyUtils.parseRupeesToPaise(rupeesCol) ?: 0L
                else -> 0L
            }

            val statusStr = cols.getOrNull(7)?.trim().orEmpty()
            val status = runCatching { TransactionStatus.valueOf(statusStr.uppercase(Locale.US)) }.getOrDefault(TransactionStatus.SUCCESS)
            val paymentApp = cols.getOrNull(8)?.trim()?.ifBlank { "Unknown" } ?: "Unknown"
            val refRaw = cols.getOrNull(9)?.trim()?.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) && it != "null" }
            val sourceStr = cols.getOrNull(10)?.trim().orEmpty()
            val source = runCatching { TransactionSource.valueOf(sourceStr.uppercase(Locale.US)) }.getOrDefault(TransactionSource.IMPORT)
            val note = cols.getOrNull(11)?.trim().orEmpty()

            val dupKey = MoneyUtils.buildDuplicateKey(refRaw, timestamp, hasExplicitTime, amountPaise, merchant)
            parsedTransactions.add(
                TransactionEntity(
                    id = 0L,
                    timestamp = timestamp,
                    hasExplicitTime = hasExplicitTime,
                    amountPaise = amountPaise,
                    type = type,
                    merchant = merchant,
                    category = category,
                    paymentApp = paymentApp,
                    referenceNumber = refRaw,
                    status = status,
                    note = note,
                    createdAt = System.currentTimeMillis(),
                    source = source,
                    duplicateKey = dupKey
                )
            )
        }

        var inserted = 0
        var updatedPending = 0
        var skipped = 0

        database.withTransaction {
            if (replaceEverything) {
                dao.clearAllTransactions()
                dao.insertTransactions(parsedTransactions)
                inserted = parsedTransactions.size
            } else {
                val existingList = dao.getAllTransactionsOnce().toMutableList()
                for (incoming in parsedTransactions) {
                    val normRef = MoneyUtils.normalizeReference(incoming.referenceNumber)
                    val matched = existingList.firstOrNull { ex ->
                        val exRef = MoneyUtils.normalizeReference(ex.referenceNumber)
                        if (normRef != null && exRef != null) {
                            normRef == exRef
                        } else {
                            ex.duplicateKey == incoming.duplicateKey
                        }
                    }

                    if (matched == null) {
                        val newId = dao.insertTransaction(incoming)
                        existingList.add(incoming.copy(id = newId))
                        inserted++
                    } else if (matched.status == TransactionStatus.PENDING && incoming.status != TransactionStatus.PENDING) {
                        val upgraded = incoming.copy(id = matched.id)
                        dao.updateTransaction(upgraded)
                        existingList.remove(matched)
                        existingList.add(upgraded)
                        updatedPending++
                    } else {
                        skipped++
                    }
                }
            }
        }
        refreshRepeatingPayments()
        return ImportSummary(
            insertedCount = inserted,
            updatedPendingCount = updatedPending,
            skippedDuplicateCount = skipped
        )
    }

    /**
     * Imports a JSON backup file in either Replace Everything mode or Merge mode.
     * Merge mode uses the time-aware duplicate key + Pending-to-Success in-place update rule.
     */
    suspend fun importFromBackupJson(jsonString: String, replaceEverything: Boolean): ImportSummary {
        val root = JSONObject(jsonString)
        val txArray = root.optJSONArray("transactions") ?: JSONArray()
        val catArray = root.optJSONArray("categories") ?: JSONArray()
        val memArray = root.optJSONArray("merchantMemories") ?: JSONArray()
        val budgetArray = root.optJSONArray("budgets") ?: JSONArray()
        val repArray = root.optJSONArray("repeatingPayments") ?: JSONArray()

        val parsedCategories = (0 until catArray.length()).map { i ->
            val o = catArray.getJSONObject(i)
            CategoryEntity(
                name = o.getString("name"),
                type = runCatching { CategoryType.valueOf(o.optString("type", "EXPENSE")) }.getOrDefault(CategoryType.EXPENSE),
                colorHex = o.optString("colorHex", "#E8DFC8"),
                iconName = o.optString("iconName", "category"),
                isDefault = o.optBoolean("isDefault", false)
            )
        }

        val parsedMemories = (0 until memArray.length()).map { i ->
            val o = memArray.getJSONObject(i)
            MerchantMemoryEntity(
                normalizedMerchant = o.getString("normalizedMerchant"),
                displayMerchant = o.optString("displayMerchant", o.getString("normalizedMerchant")),
                categoryName = o.getString("categoryName"),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
            )
        }

        val parsedBudgets = (0 until budgetArray.length()).map { i ->
            val o = budgetArray.getJSONObject(i)
            BudgetEntity(
                categoryName = o.getString("categoryName"),
                monthlyLimitPaise = o.getLong("monthlyLimitPaise")
            )
        }

        val parsedRepeating = (0 until repArray.length()).map { i ->
            val o = repArray.getJSONObject(i)
            RepeatingPaymentEntity(
                id = o.getString("id"),
                merchant = o.getString("merchant"),
                category = o.optString("category", "Subscriptions"),
                usualAmountPaise = o.getLong("usualAmountPaise"),
                usualGapDays = o.optInt("usualGapDays", 30),
                nextExpectedTimestamp = o.getLong("nextExpectedTimestamp"),
                state = runCatching { RepeatingState.valueOf(o.optString("state", "SUGGESTED")) }.getOrDefault(RepeatingState.SUGGESTED)
            )
        }

        val parsedTransactions = (0 until txArray.length()).map { i ->
            val o = txArray.getJSONObject(i)
            val timestamp = o.getLong("timestamp")
            val hasExplicitTime = o.optBoolean("hasExplicitTime", true)
            val amountPaise = o.getLong("amountPaise")
            val merchant = o.getString("merchant")
            val refRaw = if (o.isNull("referenceNumber")) null else o.optString("referenceNumber").takeIf { it.isNotBlank() && it != "null" }
            val dupKey = o.optString("duplicateKey").ifBlank {
                MoneyUtils.buildDuplicateKey(refRaw, timestamp, hasExplicitTime, amountPaise, merchant)
            }
            TransactionEntity(
                id = 0L,
                timestamp = timestamp,
                hasExplicitTime = hasExplicitTime,
                amountPaise = amountPaise,
                type = runCatching { TransactionType.valueOf(o.optString("type", "EXPENSE")) }.getOrDefault(TransactionType.EXPENSE),
                merchant = merchant,
                category = o.optString("category", "Other"),
                paymentApp = o.optString("paymentApp", "Unknown"),
                referenceNumber = refRaw,
                status = runCatching { TransactionStatus.valueOf(o.optString("status", "SUCCESS")) }.getOrDefault(TransactionStatus.SUCCESS),
                note = o.optString("note", ""),
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                source = runCatching { TransactionSource.valueOf(o.optString("source", "IMPORT")) }.getOrDefault(TransactionSource.IMPORT),
                duplicateKey = dupKey
            )
        }

        var inserted = 0
        var updatedPending = 0
        var skipped = 0

        database.withTransaction {
            if (replaceEverything) {
                dao.clearAllTransactions()
                dao.clearAllCategories()
                dao.clearAllMerchantMemories()
                dao.clearAllBudgets()
                dao.clearAllRepeatingPayments()

                dao.insertCategoriesIgnore(DefaultCategories.ALL_DEFAULTS)
                dao.insertCategoriesIgnore(parsedCategories)
                dao.insertMerchantMemories(parsedMemories)
                dao.insertBudgets(parsedBudgets)
                dao.insertRepeatingPayments(parsedRepeating)
                dao.insertTransactions(parsedTransactions)
                inserted = parsedTransactions.size
            } else {
                dao.insertCategoriesIgnore(parsedCategories)
                dao.insertMerchantMemories(parsedMemories)
                dao.insertBudgets(parsedBudgets)

                val existingList = dao.getAllTransactionsOnce().toMutableList()
                for (incoming in parsedTransactions) {
                    val normRef = MoneyUtils.normalizeReference(incoming.referenceNumber)
                    val matched = existingList.firstOrNull { ex ->
                        val exRef = MoneyUtils.normalizeReference(ex.referenceNumber)
                        if (normRef != null && exRef != null) {
                            normRef == exRef
                        } else {
                            ex.duplicateKey == incoming.duplicateKey
                        }
                    }

                    if (matched == null) {
                        val newId = dao.insertTransaction(incoming)
                        existingList.add(incoming.copy(id = newId))
                        inserted++
                    } else if (matched.status == TransactionStatus.PENDING && incoming.status != TransactionStatus.PENDING) {
                        val upgraded = incoming.copy(id = matched.id)
                        dao.updateTransaction(upgraded)
                        existingList.remove(matched)
                        existingList.add(upgraded)
                        updatedPending++
                    } else {
                        skipped++
                    }
                }
            }
        }
        refreshRepeatingPayments()
        return ImportSummary(
            insertedCount = inserted,
            updatedPendingCount = updatedPending,
            skippedDuplicateCount = skipped
        )
    }
}
