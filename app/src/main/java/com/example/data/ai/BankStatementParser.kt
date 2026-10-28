package com.example.data.ai

import com.example.data.rules.DraftTransaction
import java.util.Locale

/**
 * Pluggable interface for deterministic bank statement parsers (Part 3.7).
 *
 * Implementations must only return true in [canParse] when they positively identify
 * a verified column header layout for that bank. Unmatched statements or lines
 * are routed through [StatementLineFilter] + Gemini text extraction.
 */
interface BankStatementParser {
    val bankId: String
    fun canParse(headerSample: String): Boolean
    fun parse(rawText: String): List<DraftTransaction>
}

object BankStatementParserRegistry {
    private val parsers: List<BankStatementParser> = emptyList()

    fun findMatchingParser(rawText: String): BankStatementParser? {
        val headerSample = rawText.lineSequence().take(25).joinToString("\n")
        return parsers.firstOrNull { it.canParse(headerSample) }
    }
}

data class FilteredStatementResult(
    val filteredText: String,
    val keptTransactionRowCount: Int,
    val preFilterTokens: Int,
    val postFilterTokens: Int
)

/**
 * Pre-AI line filter for multi-row bank/card statements (Part 2.3).
 *
 * Keeps lines containing a date + money amount and wrapped narration continuation lines
 * immediately following a transaction row (up to 2 continuation lines per row).
 * Drops page headers, footers, page numbers, IFSC/MICR/branch metadata, opening/closing balance
 * lines, summary tables, and legal boilerplate.
 */
object StatementLineFilter {

    private val DATE_TOKEN_REGEX = Regex(
        """\b(?:\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}[\s-](?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*(?:[\s,-]+\d{2,4})?|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+\d{1,2}(?:,?\s+\d{2,4})?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val MONEY_AMOUNT_REGEX = Regex(
        """(?:(?:₹|Rs\.?|INR)\s*[0-9,]+(?:\.\d{1,2})?|\b[0-9]{1,3}(?:,[0-9]{2,3})*\.\d{2}\b|\b\d+\.\d{2}\b)""",
        RegexOption.IGNORE_CASE
    )

    private val DROP_LINE_REGEX = Regex(
        """^\s*(?:Page\s+\d+(?:\s+of\s+\d+)?|Statement\s+of\s+Account|Account\s+Statement|Customer\s+ID|Account\s+No|Account\s+Number|Branch\s+Name|Branch\s+Code|IFSC\s*Code|IFSC\s*:|MICR\s*Code|Nomination|Opening\s+Balance|Closing\s+Balance|Total\s+Debit|Total\s+Credit|Statement\s+Summary|Summary\s+of\s+Accounts|This\s+is\s+a\s+computer\s+generated|End\s+of\s+Statement|Terms\s+and\s+Conditions|Registered\s+Office|GSTIN|CIN\s*:).*$""",
        RegexOption.IGNORE_CASE
    )

    private val TABLE_HEADER_REGEX = Regex(
        """^\s*(?:Date|Txn\s+Date|Value\s+Date|Tran\s+Date)\s+(?:Narration|Particulars|Description|Remarks|Transaction\s+Details).*$""",
        RegexOption.IGNORE_CASE
    )

    fun filterStatementLines(rawText: String): FilteredStatementResult {
        val preFilterTokens = estimateTokens(rawText)
        if (rawText.isBlank()) {
            return FilteredStatementResult("", 0, preFilterTokens, 0)
        }

        val lines = rawText.lines()
        val keptLines = mutableListOf<String>()
        var keptRowCount = 0
        var continuationAllowance = 0

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (DROP_LINE_REGEX.matches(line) || TABLE_HEADER_REGEX.matches(line)) {
                continuationAllowance = 0
                continue
            }

            val lower = line.lowercase(Locale.US)
            if (lower.startsWith("opening balance") ||
                lower.startsWith("closing balance") ||
                lower.startsWith("brought forward") ||
                lower.startsWith("carried forward")
            ) {
                continuationAllowance = 0
                continue
            }

            val hasDate = DATE_TOKEN_REGEX.containsMatchIn(line)
            val hasMoney = MONEY_AMOUNT_REGEX.containsMatchIn(line)

            if (hasDate && hasMoney) {
                keptLines.add(line)
                keptRowCount++
                continuationAllowance = 2
            } else if (hasDate && continuationAllowance == 0 && looksLikeSplitRowStart(line)) {
                // Some PDFs place date + narration on line 1 and amount on line 2
                keptLines.add(line)
                continuationAllowance = 2
            } else if (continuationAllowance > 0) {
                if (hasMoney && !keptLines.lastOrNull().orEmpty().let { MONEY_AMOUNT_REGEX.containsMatchIn(it) }) {
                    // Completes a split row that started with a date on the previous line
                    keptRowCount++
                }
                keptLines.add(line)
                continuationAllowance--
            }
        }

        // If fewer than 2 rows matched strict date+amount filtering, fall back to keeping non-boilerplate lines
        val finalFiltered = if (keptRowCount >= 2 && keptLines.isNotEmpty()) {
            keptLines.joinToString("\n")
        } else {
            lines.map { it.trim() }
                .filter { it.isNotEmpty() && !DROP_LINE_REGEX.matches(it) && !TABLE_HEADER_REGEX.matches(it) }
                .joinToString("\n")
        }

        val postFilterTokens = estimateTokens(finalFiltered)
        return FilteredStatementResult(
            filteredText = finalFiltered,
            keptTransactionRowCount = keptRowCount,
            preFilterTokens = preFilterTokens,
            postFilterTokens = postFilterTokens
        )
    }

    private fun looksLikeSplitRowStart(line: String): Boolean {
        val upper = line.uppercase(Locale.US)
        return upper.contains("UPI/") ||
            upper.contains("NEFT") ||
            upper.contains("IMPS") ||
            upper.contains("POS ") ||
            upper.contains("ATM") ||
            upper.contains("ACH") ||
            upper.contains("BIL/")
    }

    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return ((text.length + 3) / 4).coerceAtLeast(1)
    }
}
