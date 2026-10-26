package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Exact integer money (paise) and time-aware duplicate key utilities.
 * All monetary amounts in SnapSpend are stored as Long in paise (e.g., 15050L = ₹150.50).
 * Never use Double for storing or summing currency.
 */
object MoneyUtils {

    /**
     * Converts paise (Long) into an Indian-formatted Rupee string (e.g., 15050L -> "₹150.50", 2000L -> "₹20").
     */
    fun formatPaise(
        paise: Long,
        currencySymbol: String = "₹",
        showPlusForPositive: Boolean = false,
        forceDecimals: Boolean = false
    ): String {
        val isNegative = paise < 0L
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val remPaise = absPaise % 100L

        val formattedRupees = formatIndianNumber(rupees)
        val decimalPart = if (remPaise > 0L || forceDecimals) {
            String.format(Locale.US, ".%02d", remPaise)
        } else {
            ""
        }

        val signPrefix = when {
            isNegative -> "-"
            showPlusForPositive && paise > 0L -> "+"
            else -> ""
        }
        return "$signPrefix$currencySymbol$formattedRupees$decimalPart"
    }

    /**
     * Formats a non-negative whole number using Indian numbering system (e.g., 1,25,000).
     */
    private fun formatIndianNumber(number: Long): String {
        val str = number.toString()
        if (str.length <= 3) return str
        val lastThree = str.substring(str.length - 3)
        val remaining = str.substring(0, str.length - 3)
        val sb = StringBuilder()
        var count = 0
        for (i in remaining.length - 1 downTo 0) {
            sb.append(remaining[i])
            count++
            if (count == 2 && i > 0) {
                sb.append(',')
                count = 0
            }
        }
        return sb.reverse().toString() + "," + lastThree
    }

    /**
     * Converts paise (Long) to an editable plain text string (e.g. 15050L -> "150.50", 2000L -> "20").
     */
    fun paiseToEditableRupeeString(paise: Long): String {
        if (paise == 0L) return ""
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val rem = absPaise % 100L
        return if (rem == 0L) {
            rupees.toString()
        } else {
            String.format(Locale.US, "%d.%02d", rupees, rem)
        }
    }

    /**
     * Parses a user-entered or OCR-extracted Rupee string (e.g. "150.50", "₹1,250", "20")
     * deterministically into whole paise (Long) using BigDecimal (never Double).
     */
    fun parseRupeesToPaise(rawInput: String): Long? {
        val cleaned = rawInput
            .replace("₹", "")
            .replace("INR", "", ignoreCase = true)
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .replace(",", "")
            .replace(" ", "")
            .trim()
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd < BigDecimal.ZERO) return null
            bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalizes a merchant name for Merchant Memory and duplicate key matching.
     */
    fun normalizeMerchant(merchant: String): String {
        val (cleaned, _) = cleanRawUpiOrBankMerchant(merchant)
        return cleaned
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .ifEmpty { "unknown" }
    }

    /**
     * Cleans cryptic Indian UPI, NEFT, IMPS, and POS strings (such as
     * "UPI/41298312/SWIGGY INSTAMART/BANGALORE/YESB" or "POS 4421 BLUE TOKAI MUMBAI")
     * into a clean human-readable display name ("Swiggy Instamart") and returns
     * Pair(cleanDisplayName, originalRawIfModified).
     */
    fun cleanRawUpiOrBankMerchant(raw: String): Pair<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "Unknown" to ""

        val looksLikeRawBankString = trimmed.contains("/") ||
            trimmed.contains("*") ||
            trimmed.startsWith("UPI", ignoreCase = true) ||
            trimmed.startsWith("NEFT", ignoreCase = true) ||
            trimmed.startsWith("IMPS", ignoreCase = true) ||
            trimmed.startsWith("POS ", ignoreCase = true) ||
            trimmed.startsWith("ACH ", ignoreCase = true)

        if (!looksLikeRawBankString) {
            return trimmed to ""
        }

        val noiseTokens = setOf(
            "upi", "neft", "imps", "rtgs", "pos", "ach", "ecs", "bil", "mmt", "nfs",
            "paytm", "gpay", "phonepe", "yesb", "hdfc", "icici", "sbin", "utib", "kkbk",
            "okaxis", "okhdfcbank", "okicici", "oksbi", "ybl", "ibl", "axl", "paytmqr",
            "bangalore", "bengaluru", "mumbai", "delhi", "hyderabad", "chennai", "pune",
            "india", "in", "pvt", "ltd", "limited", "private", "payment", "transfer"
        )

        val segments = trimmed
            .split("/", "*", "-", "|", "_")
            .map { it.trim() }
            .filter { seg ->
                if (seg.length < 2) return@filter false
                val lower = seg.lowercase(Locale.US)
                if (lower in noiseTokens) return@filter false
                // Skip pure numeric or alphanumeric bank/UTR codes (e.g. "4129831290", "HDFC0001234")
                val digitCount = seg.count { it.isDigit() }
                val letterCount = seg.count { it.isLetter() }
                if (letterCount == 0 || digitCount > letterCount) return@filter false
                if (Regex("^[a-zA-Z]{4}0[a-zA-Z0-9]{6}$").matches(seg)) return@filter false // IFSC
                true
            }

        val bestCandidate = segments.firstOrNull()
            ?.replace(Regex("^(POS|UPI|NEFT|IMPS)\\s+\\d*\\s*", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\\s+(PVT\\.?\\s*LTD\\.?|PRIVATE\\s+LIMITED|LIMITED|LTD\\.?)$", RegexOption.IGNORE_CASE), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()

        if (bestCandidate.isBlank()) {
            return trimmed to ""
        }

        // Title-case if all uppercase or all lowercase
        val formatted = if (bestCandidate == bestCandidate.uppercase(Locale.US) ||
            bestCandidate == bestCandidate.lowercase(Locale.US)
        ) {
            bestCandidate.split(" ").joinToString(" ") { word ->
                word.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            }
        } else {
            bestCandidate
        }

        val rawSaved = if (!formatted.equals(trimmed, ignoreCase = true)) trimmed else ""
        return formatted to rawSaved
    }

    /**
     * Normalizes a reference/transaction number. Returns null if empty or "unknown".
     */
    fun normalizeReference(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        val cleaned = ref.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]"), "")
        if (cleaned.isEmpty() || cleaned == "unknown" || cleaned == "null" || cleaned == "none" || cleaned == "na") {
            return null
        }
        return cleaned
    }

    /**
     * Builds the deterministic duplicate key:
     * 1. If a reference number is present -> "ref:<normalizedRef>"
     * 2. Otherwise -> "comp:<yyyyMMdd_HHmm>:<amountPaise>:<normalizedMerchant>"
     *    (if explicit HH:mm time was not on the screenshot, uses "0000" for the time slot).
     */
    fun buildDuplicateKey(
        referenceNumber: String?,
        timestampMillis: Long,
        hasExplicitTime: Boolean,
        amountPaise: Long,
        merchant: String
    ): String {
        val normRef = normalizeReference(referenceNumber)
        if (normRef != null) {
            return "ref:$normRef"
        }
        val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestampMillis))
        val timeStr = if (hasExplicitTime) {
            SimpleDateFormat("HHmm", Locale.US).format(Date(timestampMillis))
        } else {
            "0000"
        }
        val normMerchant = normalizeMerchant(merchant)
        return "comp:${dateStr}_${timeStr}:$amountPaise:$normMerchant"
    }

    /**
     * Parses ISO date ("YYYY-MM-DD") and 24h time ("HH:mm" or "unknown") into epoch millis
     * and returns Pair(timestampMillis, hasExplicitTime).
     */
    fun parseDateAndTime(dateIso: String?, time24h: String?): Pair<Long, Boolean> {
        val cal = Calendar.getInstance()
        var parsedDate = false
        if (!dateIso.isNullOrBlank() && dateIso.lowercase(Locale.US) != "unknown") {
            val parts = dateIso.trim().split("-", "/")
            if (parts.size == 3) {
                val y = parts[0].toIntOrNull()
                val m = parts[1].toIntOrNull()
                val d = parts[2].toIntOrNull()
                if (y != null && m != null && d != null && y in 2000..2100 && m in 1..12 && d in 1..31) {
                    cal.set(Calendar.YEAR, y)
                    cal.set(Calendar.MONTH, m - 1)
                    cal.set(Calendar.DAY_OF_MONTH, d)
                    parsedDate = true
                }
            }
        }
        var hasTime = false
        if (!time24h.isNullOrBlank() && time24h.lowercase(Locale.US) != "unknown") {
            val cleanTime = time24h.trim().split(":")
            if (cleanTime.size >= 2) {
                val hh = cleanTime[0].toIntOrNull()
                val mm = cleanTime[1].take(2).toIntOrNull()
                if (hh != null && mm != null && hh in 0..23 && mm in 0..59) {
                    cal.set(Calendar.HOUR_OF_DAY, hh)
                    cal.set(Calendar.MINUTE, mm)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    hasTime = true
                }
            }
        }
        if (!hasTime && parsedDate) {
            cal.set(Calendar.HOUR_OF_DAY, 12)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return Pair(cal.timeInMillis, hasTime)
    }

    fun formatDayHeader(timestampMillis: Long): String {
        val targetCal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
        val todayCal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

        return when {
            isSameDay(targetCal, todayCal) -> "Today · " + SimpleDateFormat("dd MMM", Locale.US).format(Date(timestampMillis))
            isSameDay(targetCal, yesterdayCal) -> "Yesterday · " + SimpleDateFormat("dd MMM", Locale.US).format(Date(timestampMillis))
            else -> SimpleDateFormat("EEE, dd MMM yyyy", Locale.US).format(Date(timestampMillis))
        }
    }

    fun formatShortDateTime(timestampMillis: Long, hasExplicitTime: Boolean): String {
        val pattern = if (hasExplicitTime) "dd MMM, hh:mm a" else "dd MMM yyyy"
        return SimpleDateFormat(pattern, Locale.US).format(Date(timestampMillis))
    }

    fun formatIsoDate(timestampMillis: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestampMillis))
    }

    fun formatTime24h(timestampMillis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.US).format(Date(timestampMillis))
    }

    fun isSameDay(c1: Calendar, c2: Calendar): Boolean {
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
            c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
