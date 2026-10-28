package com.example.data.ai

import com.example.data.local.CategoryEntity
import com.example.data.local.DefaultCategories
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.util.MoneyUtils
import java.util.Locale

/**
 * Ultra-fast (<20ms, 0-API) local regex parser for Quick Cash multi-item inputs
 * such as "20 chai, 50 auto, 140 maggi" or "chai 20 and auto 50".
 *
 * Matches simple `<Amount> <Item>` or `<Item> <Amount>` comma/and/newline-separated
 * patterns and resolves each item against `MerchantMemoryEntity` or default categories.
 * If any segment is conversational or cannot be matched cleanly by the fast pattern,
 * returns `null` so the caller can fall back to text-only `gemini-3.8-flash`.
 */
object QuickCashFastParser {

    // Matches "<Amount> <Item>" e.g. "20 chai", "₹50 auto rickshaw", "140.50 maggi"
    private val AMOUNT_FIRST_REGEX = Regex(
        """^\s*(?:₹|Rs\.?|INR)?\s*(\d+(?:\.\d{1,2})?)\s*(?:rs\.?|rupees|\s+)\s*([a-zA-Z][a-zA-Z\s&'./\-]{1,35})\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Matches "<Item> <Amount>" e.g. "chai 20", "auto ₹50", "maggi 140"
    private val ITEM_FIRST_REGEX = Regex(
        """^\s*([a-zA-Z][a-zA-Z\s&'./\-]{1,35}?)\s+(?:₹|Rs\.?|INR)?\s*(\d+(?:\.\d{1,2})?)\s*(?:rs\.?|rupees)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    // Conversational indicators that should defer to Gemini text fallback
    private val CONVERSATIONAL_STOPWORDS_REGEX = Regex(
        """\b(?:yesterday|last\s+night|split|divided|between|people|ways|borrowed|lent|owed|because|while|after|before)\b""",
        RegexOption.IGNORE_CASE
    )

    private val SEGMENT_SPLIT_REGEX = Regex("""\s*(?:,|;|\n|\band\b|&|\+)\s*""", RegexOption.IGNORE_CASE)

    /**
     * Attempts to parse [rawInput] locally in <20ms with 0 API calls.
     * Returns a non-empty list of [DraftTransaction] with `paymentMode = "CASH"` if every
     * segment matches the fast `<Amount> <Item>` / `<Item> <Amount>` pattern; otherwise `null`.
     */
    fun parseFastDrafts(
        rawInput: String,
        categories: List<CategoryEntity>,
        merchantMemories: List<MerchantMemoryEntity>,
        defaultType: TransactionType = TransactionType.EXPENSE,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DraftTransaction>? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        if (CONVERSATIONAL_STOPWORDS_REGEX.containsMatchIn(trimmed)) return null

        val rawSegments = trimmed.split(SEGMENT_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (rawSegments.isEmpty()) return null

        val drafts = mutableListOf<DraftTransaction>()
        for ((index, seg) in rawSegments.withIndex()) {
            val m1 = AMOUNT_FIRST_REGEX.matchEntire(seg)
            val m2 = if (m1 == null) ITEM_FIRST_REGEX.matchEntire(seg) else null
            if (m1 == null && m2 == null) {
                return null
            }

            val amountStr = if (m1 != null) m1.groupValues[1] else m2!!.groupValues[2]
            val rawItemStr = if (m1 != null) m1.groupValues[2] else m2!!.groupValues[1]

            val paise = MoneyUtils.parseRupeesToPaise(amountStr) ?: return null
            if (paise <= 0L) return null

            val cleanedItem = cleanItemLabel(rawItemStr)
            if (cleanedItem.isBlank()) return null

            val isAtm = LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(cleanedItem) ||
                LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(seg)

            val resolvedType = when {
                isAtm -> TransactionType.TRANSFER
                defaultType == TransactionType.EARNING -> TransactionType.EARNING
                else -> TransactionType.EXPENSE
            }

            val resolvedCategory = resolveFastCategory(
                itemLabel = cleanedItem,
                type = resolvedType,
                isAtm = isAtm,
                categories = categories,
                memories = merchantMemories
            )

            val resolvedMerchant = if (isAtm) "ATM Cash Withdrawal" else cleanedItem
            val ts = nowMillis + index // preserve deterministic ordering across multi-item entries

            drafts.add(
                DraftTransaction(
                    timestamp = ts,
                    hasExplicitTime = true,
                    amountPaise = paise,
                    originalAmountPaise = paise,
                    type = resolvedType,
                    merchant = resolvedMerchant,
                    rawMerchantText = seg,
                    category = resolvedCategory,
                    paymentApp = "Cash",
                    referenceNumber = null,
                    status = TransactionStatus.SUCCESS,
                    confidence = 0.98f,
                    note = "Quick Cash: $seg",
                    splitWays = 1,
                    source = TransactionSource.MANUAL,
                    paymentMode = "CASH",
                    isAtmWithdrawal = isAtm
                )
            )
        }

        return drafts.takeIf { it.isNotEmpty() }
    }

    /**
     * Converts a list of [DraftTransaction] items into ready-to-insert [TransactionEntity] rows
     * with `paymentMode = "CASH"`.
     */
    fun toTransactionEntities(
        drafts: List<DraftTransaction>,
        categories: List<CategoryEntity>
    ): List<TransactionEntity> {
        val catIdByName = categories.associate { it.name.lowercase(Locale.US) to it.id }
        return drafts.map { draft ->
            val catId = catIdByName[draft.category.lowercase(Locale.US)]
                ?: DefaultCategories.categoryIdForName(draft.category)
            val dupKey = MoneyUtils.buildDuplicateKey(
                referenceNumber = draft.referenceNumber,
                timestampMillis = draft.timestamp,
                hasExplicitTime = draft.hasExplicitTime,
                amountPaise = draft.amountPaise,
                merchant = draft.merchant
            )
            TransactionEntity(
                id = 0L,
                timestamp = draft.timestamp,
                hasExplicitTime = draft.hasExplicitTime,
                amountPaise = draft.amountPaise,
                type = draft.type,
                merchant = draft.merchant,
                category = draft.category,
                categoryId = catId,
                paymentApp = "Cash",
                referenceNumber = draft.referenceNumber,
                status = TransactionStatus.SUCCESS,
                note = draft.note,
                createdAt = System.currentTimeMillis(),
                source = TransactionSource.MANUAL,
                duplicateKey = dupKey,
                paymentMode = "CASH",
                isAtmWithdrawal = draft.isAtmWithdrawal
            )
        }
    }

    private fun cleanItemLabel(raw: String): String {
        val stripped = raw
            .replace(Regex("""\b(?:for|on|at|in|cash|paid|spent)\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val target = stripped.ifBlank { raw.trim() }
        return target.split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.lowercase(Locale.US).replaceFirstChar { ch ->
                    if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                }
            }
    }

    fun resolveFastCategory(
        itemLabel: String,
        type: TransactionType,
        isAtm: Boolean,
        categories: List<CategoryEntity>,
        memories: List<MerchantMemoryEntity>
    ): String {
        if (isAtm || type == TransactionType.TRANSFER) return "Transfers"
        val allowedNames = categories.map { it.name }.ifEmpty {
            DefaultCategories.ALL_DEFAULTS.map { it.name }
        }
        if (type == TransactionType.EARNING) {
            return allowedNames.firstOrNull { it.equals("Other Income", ignoreCase = true) }
                ?: allowedNames.firstOrNull { it.contains("Income", ignoreCase = true) }
                ?: "Other Income"
        }

        val norm = MoneyUtils.normalizeMerchant(itemLabel)
        val lower = itemLabel.lowercase(Locale.US)

        // 1. Check MerchantMemoryEntity first
        val memoryMatch = memories.firstOrNull { mem ->
            mem.normalizedMerchant.equals(norm, ignoreCase = true) ||
                mem.displayMerchant.equals(itemLabel, ignoreCase = true) ||
                lower.contains(mem.normalizedMerchant.lowercase(Locale.US))
        }
        if (memoryMatch != null && allowedNames.any { it.equals(memoryMatch.categoryName, ignoreCase = true) }) {
            return allowedNames.first { it.equals(memoryMatch.categoryName, ignoreCase = true) }
        }

        // 2. Check UpiRegexPreProcessor dictionary
        val preProcessorMatch = UpiRegexPreProcessor.findMatchingCategory(itemLabel, categories, memories)
        if (preProcessorMatch != null && allowedNames.any { it.equals(preProcessorMatch, ignoreCase = true) }) {
            return allowedNames.first { it.equals(preProcessorMatch, ignoreCase = true) }
        }

        // 3. Fast Indian everyday cash item keywords
        val desiredCategory = when {
            lower.containsAny(
                "chai", "tea", "coffee", "maggi", "momo", "momos", "samosa", "kachori", "vada",
                "pav", "dosa", "idli", "poha", "paratha", "lunch", "dinner", "breakfast", "snack",
                "snacks", "biryani", "thali", "juice", "lassi", "pani puri", "chaat", "roll",
                "burger", "pizza", "sandwich", "bakery", "cake", "sweet", "mithai", "canteen", "dhaba", "cafe", "food"
            ) -> "Food & Dining"

            lower.containsAny(
                "auto", "rickshaw", "cab", "taxi", "bus", "metro", "train", "local", "ticket",
                "petrol", "diesel", "fuel", "cng", "parking", "toll", "rapido", "ola", "uber", "bike"
            ) -> "Transport & Fuel"

            lower.containsAny(
                "milk", "bread", "eggs", "egg", "curd", "dahi", "paneer", "butter", "veggies",
                "vegetables", "sabzi", "fruits", "fruit", "banana", "apple", "onion", "potato",
                "tomato", "kirana", "grocery", "groceries", "water", "ration", "atta", "rice", "dal"
            ) -> "Groceries"

            lower.containsAny(
                "medicine", "meds", "tablet", "chemist", "pharmacy", "doctor", "clinic", "bandage", "syrup"
            ) -> "Health & Medical"

            lower.containsAny(
                "pen", "notebook", "xerox", "print", "printout", "stationery", "clothes", "shirt",
                "shoes", "slippers", "bag", "market", "bazaar", "shop", "shopping", "haircut", "salon", "tailor"
            ) -> "Shopping"

            lower.containsAny(
                "maid", "cook", "laundry", "ironing", "dhobi", "garbage", "watchman", "plumber",
                "electrician", "repair", "cylinder", "gas", "bill", "recharge"
            ) -> "Bills & Utilities"

            lower.containsAny(
                "movie", "cinema", "game", "turf", "cricket", "badminton", "bowling", "outing"
            ) -> "Entertainment"

            else -> "Food & Dining"
        }

        return allowedNames.firstOrNull { it.equals(desiredCategory, ignoreCase = true) }
            ?: allowedNames.firstOrNull()
            ?: "Food & Dining"
    }

    private fun String.containsAny(vararg keywords: String): Boolean {
        val words = this.split(Regex("""\s+"""))
        return keywords.any { kw ->
            if (kw.contains(" ")) {
                this.contains(kw)
            } else {
                words.any { w -> w == kw || w.startsWith(kw) }
            }
        }
    }
}
