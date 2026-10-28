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
     * Converts paise (Long) into an Indian-formatted Rupee string with exact 2-digit paise decimals
     * (e.g., 123450L -> "₹1,234.50", 2000L -> "₹20.00", 0L -> "₹0.00").
     */
    fun formatExactPaise(
        paise: Long,
        currencySymbol: String = "₹",
        showPlusForPositive: Boolean = false
    ): String = formatPaise(
        paise = paise,
        currencySymbol = currencySymbol,
        showPlusForPositive = showPlusForPositive,
        forceDecimals = true
    )

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
     * Converts paise (Long) to an editable plain text string (e.g. 15050L -> "150.50", 2000L -> "20", -5000L -> "-50").
     */
    fun paiseToEditableRupeeString(paise: Long): String {
        if (paise == 0L) return ""
        val isNegative = paise < 0L
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val rem = absPaise % 100L
        val sign = if (isNegative) "-" else ""
        return if (rem == 0L) {
            "$sign$rupees"
        } else {
            String.format(Locale.US, "%s%d.%02d", sign, rupees, rem)
        }
    }

    /**
     * Compact Indian currency formatter for charts and chips (e.g. 15000000L -> "₹1.5L", 2500000L -> "₹25k").
     */
    fun formatPaiseCompact(paise: Long, currencySymbol: String = "₹"): String {
        val isNegative = paise < 0L
        val absPaise = abs(paise)
        val rupees = absPaise / 100L
        val sign = if (isNegative) "-" else ""
        return when {
            rupees >= 10_000_000L -> String.format(Locale.US, "%s%s%.1fCr", sign, currencySymbol, rupees / 10_000_000.0)
            rupees >= 100_000L -> String.format(Locale.US, "%s%s%.1fL", sign, currencySymbol, rupees / 100_000.0)
            rupees >= 1_000L -> String.format(Locale.US, "%s%s%.1fk", sign, currencySymbol, rupees / 1_000.0)
            else -> formatPaise(paise, currencySymbol)
        }
    }

    /**
     * Parses a user-entered or OCR-extracted Rupee string (e.g. "150.50", "₹1,250", "-20")
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

    data class ParsedDateResult(
        val dateIso: String?,
        val time24h: String?,
        val dateFound: Boolean,
        val rejectedFutureDate: Boolean = false
    )

    private val MONTH_MAP = mapOf(
        "jan" to 1, "january" to 1,
        "feb" to 2, "february" to 2,
        "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4,
        "may" to 5,
        "jun" to 6, "june" to 6,
        "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8,
        "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11,
        "dec" to 12, "december" to 12
    )

    private val MONTH_SHORT_NAMES = arrayOf(
        "", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    // Matches "28 Oct 2026", "28 October 2026 at 9:42 am", "28-Oct-2026", "28 Oct 26", "28 Oct"
    private val DAY_FIRST_MONTH_NAME_REGEX = Regex(
        """\b(\d{1,2})[\s\-]+(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)(?:[\s,\-]+(\d{2,4}))?(?:\s*(?:at|,|-)?\s*(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?))?(?=\b|\s|$)""",
        RegexOption.IGNORE_CASE
    )

    // Matches "Oct 28, 2026", "October 28 2026 at 9:42 pm", "Oct 28"
    private val MONTH_FIRST_NAME_REGEX = Regex(
        """\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+(\d{1,2})(?:,\s*|\s+)(\d{4}|\d{2})?(?:\s*(?:at|,|-)?\s*(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm)?))?(?=\b|\s|$)""",
        RegexOption.IGNORE_CASE
    )

    // Also match "Oct 28" without year
    private val MONTH_FIRST_NO_YEAR_REGEX = Regex(
        """\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+(\d{1,2})\b""",
        RegexOption.IGNORE_CASE
    )

    // Matches Indian day-first numeric dates: "28/10/2026", "28-10-2026", "28/10/26", "04/10/2026"
    private val NUMERIC_DATE_REGEX = Regex(
        """\b(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\b"""
    )

    private val STANDALONE_TIME_REGEX = Regex(
        """\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))\b|\b([01]?\d|2[0-3]):([0-5]\d)\b"""
    )

    /**
     * Parses Indian receipt/UPI dates:
     * - "28 Oct 2026", "Oct 28, 2026", "28 October 2026 at 9:42 am", "28 Oct", "28/10/26", "28-10-2026"
     * - Two-digit years become 20xx.
     * - If the year is missing ("28 Oct"), uses the most recent such date that is NOT in the future.
     * - Rejects future dates (beyond end of today in device time zone).
     */
    fun parseFlexibleIndianDate(
        text: String,
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedDateResult {
        if (text.isBlank()) return ParsedDateResult(null, null, false)

        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val endOfTodayCal = (nowCal.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }

        val standaloneTime = extractTime24h(text)

        // Check explicit "Today" / "Yesterday"
        if (Regex("""\bToday\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val iso = formatIsoDate(nowCal.timeInMillis)
            return ParsedDateResult(dateIso = iso, time24h = standaloneTime, dateFound = true)
        }
        if (Regex("""\bYesterday\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            val yest = (nowCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
            val iso = formatIsoDate(yest.timeInMillis)
            return ParsedDateResult(dateIso = iso, time24h = standaloneTime, dateFound = true)
        }

        var sawRejectedFuture = false

        // 1. Day-first with month name ("28 Oct 2026", "28 October 2026 at 9:42 am", "28 Oct")
        for (match in DAY_FIRST_MONTH_NAME_REGEX.findAll(text)) {
            val day = match.groupValues[1].toIntOrNull() ?: continue
            val monthName = match.groupValues[2].lowercase(Locale.US)
            val month = MONTH_MAP[monthName] ?: continue
            val yearRaw = match.groupValues[3].takeIf { it.isNotBlank() }
            val inlineTime = match.groupValues[4].takeIf { it.isNotBlank() }?.let { parseTimeTokenTo24h(it) }

            val resolved = resolveCandidateDate(day, month, yearRaw, nowCal, endOfTodayCal)
            if (resolved != null) {
                if (resolved.second) {
                    sawRejectedFuture = true
                } else if (resolved.first != null) {
                    return ParsedDateResult(
                        dateIso = resolved.first,
                        time24h = inlineTime ?: standaloneTime,
                        dateFound = true
                    )
                }
            }
        }

        // 2. Month-first with year ("Oct 28, 2026", "Oct 28 2026")
        for (match in MONTH_FIRST_NAME_REGEX.findAll(text)) {
            val monthName = match.groupValues[1].lowercase(Locale.US)
            val month = MONTH_MAP[monthName] ?: continue
            val day = match.groupValues[2].toIntOrNull() ?: continue
            val yearRaw = match.groupValues[3].takeIf { it.isNotBlank() }
            val inlineTime = match.groupValues[4].takeIf { it.isNotBlank() }?.let { parseTimeTokenTo24h(it) }

            val resolved = resolveCandidateDate(day, month, yearRaw, nowCal, endOfTodayCal)
            if (resolved != null) {
                if (resolved.second) {
                    sawRejectedFuture = true
                } else if (resolved.first != null) {
                    return ParsedDateResult(
                        dateIso = resolved.first,
                        time24h = inlineTime ?: standaloneTime,
                        dateFound = true
                    )
                }
            }
        }

        // 3. Month-first without year ("Oct 28")
        for (match in MONTH_FIRST_NO_YEAR_REGEX.findAll(text)) {
            val monthName = match.groupValues[1].lowercase(Locale.US)
            val month = MONTH_MAP[monthName] ?: continue
            val day = match.groupValues[2].toIntOrNull() ?: continue

            val resolved = resolveCandidateDate(day, month, null, nowCal, endOfTodayCal)
            if (resolved != null && !resolved.second && resolved.first != null) {
                return ParsedDateResult(
                    dateIso = resolved.first,
                    time24h = standaloneTime,
                    dateFound = true
                )
            }
        }

        // 4. Numeric day-first ("28/10/26", "28-10-2026") or ISO ("2026-10-28")
        for (match in NUMERIC_DATE_REGEX.findAll(text)) {
            val p1 = match.groupValues[1].toIntOrNull() ?: continue
            val p2 = match.groupValues[2].toIntOrNull() ?: continue
            val p3Str = match.groupValues[3]
            val p3 = p3Str.toIntOrNull() ?: continue

            val (day, month, yearRaw) = if (match.groupValues[1].length == 4 && p1 in 1990..2100) {
                Triple(p3, p2, match.groupValues[1])
            } else {
                Triple(p1, p2, p3Str)
            }

            val resolved = resolveCandidateDate(day, month, yearRaw, nowCal, endOfTodayCal)
            if (resolved != null) {
                if (resolved.second) {
                    sawRejectedFuture = true
                } else if (resolved.first != null) {
                    return ParsedDateResult(
                        dateIso = resolved.first,
                        time24h = standaloneTime,
                        dateFound = true
                    )
                }
            }
        }

        return ParsedDateResult(
            dateIso = null,
            time24h = standaloneTime,
            dateFound = false,
            rejectedFutureDate = sawRejectedFuture
        )
    }

    /**
     * Resolves (day, month, optionalYear) into Pair(isoDateString?, isRejectedFuture).
     * Returns null if day/month is invalid (e.g. month > 12 or Feb 30).
     */
    private fun resolveCandidateDate(
        day: Int,
        month: Int,
        yearRaw: String?,
        nowCal: Calendar,
        endOfTodayCal: Calendar
    ): Pair<String?, Boolean>? {
        if (month !in 1..12 || day !in 1..31) return null

        val year = when {
            yearRaw.isNullOrBlank() -> {
                // Missing year: pick current year if <= today, else previous year
                val currentYear = nowCal.get(Calendar.YEAR)
                val candidateThisYear = buildStrictCalendar(currentYear, month, day) ?: return null
                if (candidateThisYear.timeInMillis <= endOfTodayCal.timeInMillis) {
                    currentYear
                } else {
                    currentYear - 1
                }
            }
            yearRaw.length == 2 -> {
                val twoDigit = yearRaw.toIntOrNull() ?: return null
                2000 + twoDigit
            }
            yearRaw.length == 4 -> {
                yearRaw.toIntOrNull() ?: return null
            }
            else -> return null
        }

        if (year !in 1990..2100) return null
        val cal = buildStrictCalendar(year, month, day) ?: return null
        if (cal.timeInMillis > endOfTodayCal.timeInMillis) {
            return null to true
        }
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day) to false
    }

    private fun buildStrictCalendar(year: Int, month: Int, day: Int): Calendar? {
        return try {
            Calendar.getInstance().apply {
                isLenient = false
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 12)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                // Trigger validation
                timeInMillis
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTime24h(text: String): String? {
        // Prefer AM/PM time first so dates or ratios aren't mistaken for 24h time
        val amPmRegex = Regex("""\b(\d{1,2}:\d{2}\s*(?:AM|PM|am|pm))\b""")
        amPmRegex.find(text)?.let { m ->
            parseTimeTokenTo24h(m.groupValues[1])?.let { return it }
        }
        val match = STANDALONE_TIME_REGEX.find(text) ?: return null
        return parseTimeTokenTo24h(match.value)
    }

    private fun parseTimeTokenTo24h(rawTime: String): String? {
        val cleaned = rawTime.trim()
        return try {
            if (cleaned.contains(Regex("""(?i)am|pm"""))) {
                val normalized = cleaned.replace(Regex("""\s+"""), " ").uppercase(Locale.US)
                val parsed = SimpleDateFormat("hh:mm a", Locale.US).parse(normalized)
                if (parsed != null) SimpleDateFormat("HH:mm", Locale.US).format(parsed) else null
            } else {
                val parts = cleaned.split(":")
                val hh = parts.getOrNull(0)?.toIntOrNull() ?: return null
                val mm = parts.getOrNull(1)?.take(2)?.toIntOrNull() ?: return null
                if (hh in 0..23 && mm in 0..59) {
                    String.format(Locale.US, "%02d:%02d", hh, mm)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts Indian day-first numeric dates ("04/10/2026", "04-10-26") into unambiguous
     * month-name dates ("4 Oct 2026") before sending text to Gemini so DD/MM and MM/DD are never confused.
     */
    fun normalizeNumericDatesInTextForAi(text: String): String {
        if (text.isBlank()) return text
        return NUMERIC_DATE_REGEX.replace(text) { match ->
            val d = match.groupValues[1].toIntOrNull()
            val m = match.groupValues[2].toIntOrNull()
            val yStr = match.groupValues[3]
            val y = when (yStr.length) {
                2 -> yStr.toIntOrNull()?.let { 2000 + it }
                4 -> yStr.toIntOrNull()
                else -> null
            }
            if (d != null && m != null && y != null && d in 1..31 && m in 1..12 && y in 1990..2100) {
                "$d ${MONTH_SHORT_NAMES[m]} $y"
            } else {
                match.value
            }
        }
    }

    /**
     * Computes a lowercase hex SHA-256 hash of normalized text for local SKIPPED_KNOWN deduplication.
     */
    fun sha256Hex(input: String): String {
        val normalized = input.trim().lowercase(Locale.US).replace(Regex("""\s+"""), " ")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
