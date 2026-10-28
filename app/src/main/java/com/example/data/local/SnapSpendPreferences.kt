package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

val Context.snapSpendDataStore: DataStore<Preferences> by preferencesDataStore(name = "snapspend_settings")

enum class ScanPath {
    FAST_PATH,
    TEXT_AI,
    VISION_AI,
    NOTE_AI,
    SKIPPED_KNOWN
}

/**
 * Privacy-safe local scan telemetry entry (Part A).
 * Never stores receipt text, merchant names, or transaction amounts.
 */
data class ScanLogEntry(
    val timestampMillis: Long = System.currentTimeMillis(),
    val readFileMs: Long = 0L,
    val ocrTextMs: Long = 0L,
    val regexMs: Long = 0L,
    val geminiTextMs: Long = 0L,
    val visionFallbackMs: Long = 0L,
    val totalMs: Long = 0L,
    val path: ScanPath = ScanPath.FAST_PATH,
    val retries: Int = 0,
    val promptTokens: Int = 0,
    val outputTokens: Int = 0,
    val preFilterTokens: Int = 0,
    val postFilterTokens: Int = 0
) {
    val totalTokens: Int
        get() = promptTokens + outputTokens
}

/**
 * Local-only SHA-256 record of a confirmed scan (Part D).
 * Stored exclusively in DataStore (never included in backups or exports) so re-scanning
 * the exact same receipt/screenshot skips Gemini immediately.
 */
data class ConfirmedScanRecord(
    val sha256Hash: String,
    val transactionId: Long,
    val amountPaise: Long,
    val merchant: String,
    val dateFormatted: String
)

enum class CardBillHandlingMode(val label: String) {
    ASK("Ask"),
    TRANSFER("Transfer"),
    EXPENSE("Expense");

    companion object {
        fun fromName(name: String?): CardBillHandlingMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ASK
        }
    }
}

/**
 * Isolated settings store using Jetpack Preferences DataStore.
 * Completely separate from the SQLite/Room transaction database, JSON backups, CSV exports,
 * and Google Drive backups.
 */
class SnapSpendPreferences(private val context: Context) {

    companion object {
        val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val KEY_HAS_SEEN_AI_SCAN_NOTICE = booleanPreferencesKey("has_seen_ai_scan_notice")
        val KEY_HAS_PROMPTED_BUDGET_NOTIFICATIONS = booleanPreferencesKey("has_prompted_budget_notifications")
        val KEY_SOUND_EFFECTS_ENABLED = booleanPreferencesKey("sound_effects_enabled")
        val KEY_BGM_ENABLED = booleanPreferencesKey("bgm_enabled")
        val KEY_CASH_WALLET_BALANCE_PAISE = longPreferencesKey("cash_wallet_balance_paise")
        val KEY_HAS_CUSTOM_CASH_WALLET_BASE = booleanPreferencesKey("has_custom_cash_wallet_base")
        val KEY_SCAN_TIMING_LOGS_JSON = stringPreferencesKey("scan_timing_logs_json")
        val KEY_CONFIRMED_SCAN_HASHES_JSON = stringPreferencesKey("confirmed_scan_hashes_json")
        val KEY_CARD_BILL_HANDLING_MODE = stringPreferencesKey("card_bill_handling_mode")
        private const val MAX_SCAN_LOG_ENTRIES = 50
        private const val MAX_CONFIRMED_SCAN_HASHES = 200
    }

    val hasSeenOnboardingFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
    }

    suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    val hasSeenAiScanNoticeFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_AI_SCAN_NOTICE] ?: false
    }

    suspend fun setHasSeenAiScanNotice(hasSeen: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_AI_SCAN_NOTICE] = hasSeen
        }
    }

    val hasPromptedBudgetNotificationsFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_PROMPTED_BUDGET_NOTIFICATIONS] ?: false
    }

    suspend fun setHasPromptedBudgetNotifications(hasPrompted: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_HAS_PROMPTED_BUDGET_NOTIFICATIONS] = hasPrompted
        }
    }

    val soundEffectsEnabledFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_SOUND_EFFECTS_ENABLED] ?: true
    }

    suspend fun setSoundEffectsEnabled(enabled: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_SOUND_EFFECTS_ENABLED] = enabled
        }
    }

    val bgmEnabledFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_BGM_ENABLED] ?: true
    }

    suspend fun setBgmEnabled(enabled: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_BGM_ENABLED] = enabled
        }
    }

    /**
     * Persistent Cash Wallet balance in whole paise stored in Preferences DataStore.
     */
    val cashWalletBalancePaiseFlow: Flow<Long> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_CASH_WALLET_BALANCE_PAISE] ?: 0L
    }

    val hasCustomCashWalletBaseFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_CUSTOM_CASH_WALLET_BASE] ?: false
    }

    suspend fun getCashWalletBalancePaiseOnce(): Long {
        return context.snapSpendDataStore.data.first()[KEY_CASH_WALLET_BALANCE_PAISE] ?: 0L
    }

    suspend fun setCashWalletBalancePaise(balancePaise: Long) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_CASH_WALLET_BALANCE_PAISE] = balancePaise
            prefs[KEY_HAS_CUSTOM_CASH_WALLET_BASE] = true
        }
    }

    suspend fun adjustCashWalletBalancePaise(deltaPaise: Long): Long {
        var updated = 0L
        context.snapSpendDataStore.edit { prefs ->
            val current = prefs[KEY_CASH_WALLET_BALANCE_PAISE] ?: 0L
            updated = current + deltaPaise
            prefs[KEY_CASH_WALLET_BALANCE_PAISE] = updated
            prefs[KEY_HAS_CUSTOM_CASH_WALLET_BASE] = true
        }
        return updated
    }

    /**
     * Card bill handling mode preference (Ask / Transfer / Expense), default ASK.
     */
    val cardBillHandlingModeFlow: Flow<CardBillHandlingMode> = context.snapSpendDataStore.data.map { prefs ->
        CardBillHandlingMode.fromName(prefs[KEY_CARD_BILL_HANDLING_MODE])
    }

    suspend fun getCardBillHandlingModeOnce(): CardBillHandlingMode {
        return CardBillHandlingMode.fromName(
            context.snapSpendDataStore.data.first()[KEY_CARD_BILL_HANDLING_MODE]
        )
    }

    suspend fun setCardBillHandlingMode(mode: CardBillHandlingMode) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_CARD_BILL_HANDLING_MODE] = mode.name
        }
    }

    /**
     * Last 50 scan timing & token log entries (never contains PII, merchant names, or amounts).
     */
    val scanTimingLogsFlow: Flow<List<ScanLogEntry>> = context.snapSpendDataStore.data.map { prefs ->
        parseScanLogsJson(prefs[KEY_SCAN_TIMING_LOGS_JSON])
    }

    suspend fun appendScanLogEntry(entry: ScanLogEntry) {
        context.snapSpendDataStore.edit { prefs ->
            val current = parseScanLogsJson(prefs[KEY_SCAN_TIMING_LOGS_JSON]).toMutableList()
            current.add(0, entry)
            val trimmed = current.take(MAX_SCAN_LOG_ENTRIES)
            prefs[KEY_SCAN_TIMING_LOGS_JSON] = encodeScanLogsJson(trimmed)
        }
    }

    suspend fun clearScanLogEntries() {
        context.snapSpendDataStore.edit { prefs ->
            prefs.remove(KEY_SCAN_TIMING_LOGS_JSON)
        }
    }

    /**
     * Last 200 confirmed scan SHA-256 hashes (local DataStore only, excluded from backups/exports).
     */
    suspend fun findConfirmedScanByHash(sha256Hash: String): ConfirmedScanRecord? {
        if (sha256Hash.isBlank()) return null
        val raw = context.snapSpendDataStore.data.first()[KEY_CONFIRMED_SCAN_HASHES_JSON]
        return parseConfirmedScansJson(raw).firstOrNull { it.sha256Hash == sha256Hash }
    }

    suspend fun recordConfirmedScanHash(record: ConfirmedScanRecord) {
        if (record.sha256Hash.isBlank()) return
        context.snapSpendDataStore.edit { prefs ->
            val current = parseConfirmedScansJson(prefs[KEY_CONFIRMED_SCAN_HASHES_JSON])
                .filterNot { it.sha256Hash == record.sha256Hash }
                .toMutableList()
            current.add(0, record)
            val trimmed = current.take(MAX_CONFIRMED_SCAN_HASHES)
            prefs[KEY_CONFIRMED_SCAN_HASHES_JSON] = encodeConfirmedScansJson(trimmed)
        }
    }

    private fun parseScanLogsJson(raw: String?): List<ScanLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<ScanLogEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val pathStr = obj.optString("path", ScanPath.FAST_PATH.name)
                val path = ScanPath.entries.firstOrNull { it.name == pathStr } ?: ScanPath.FAST_PATH
                list.add(
                    ScanLogEntry(
                        timestampMillis = obj.optLong("ts", 0L),
                        readFileMs = obj.optLong("readMs", 0L),
                        ocrTextMs = obj.optLong("ocrMs", 0L),
                        regexMs = obj.optLong("regexMs", 0L),
                        geminiTextMs = obj.optLong("aiTextMs", 0L),
                        visionFallbackMs = obj.optLong("visionMs", 0L),
                        totalMs = obj.optLong("totalMs", 0L),
                        path = path,
                        retries = obj.optInt("retries", 0),
                        promptTokens = obj.optInt("pTok", 0),
                        outputTokens = obj.optInt("oTok", 0),
                        preFilterTokens = obj.optInt("preTok", 0),
                        postFilterTokens = obj.optInt("postTok", 0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeScanLogsJson(entries: List<ScanLogEntry>): String {
        val arr = JSONArray()
        for (e in entries) {
            val obj = JSONObject().apply {
                put("ts", e.timestampMillis)
                put("readMs", e.readFileMs)
                put("ocrMs", e.ocrTextMs)
                put("regexMs", e.regexMs)
                put("aiTextMs", e.geminiTextMs)
                put("visionMs", e.visionFallbackMs)
                put("totalMs", e.totalMs)
                put("path", e.path.name)
                put("retries", e.retries)
                put("pTok", e.promptTokens)
                put("oTok", e.outputTokens)
                put("preTok", e.preFilterTokens)
                put("postTok", e.postFilterTokens)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parseConfirmedScansJson(raw: String?): List<ConfirmedScanRecord> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            val list = ArrayList<ConfirmedScanRecord>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val hash = obj.optString("h", "")
                if (hash.isNotBlank()) {
                    list.add(
                        ConfirmedScanRecord(
                            sha256Hash = hash,
                            transactionId = obj.optLong("id", 0L),
                            amountPaise = obj.optLong("a", 0L),
                            merchant = obj.optString("m", "Unknown"),
                            dateFormatted = obj.optString("d", "")
                        )
                    )
                }
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun encodeConfirmedScansJson(records: List<ConfirmedScanRecord>): String {
        val arr = JSONArray()
        for (r in records) {
            val obj = JSONObject().apply {
                put("h", r.sha256Hash)
                put("id", r.transactionId)
                put("a", r.amountPaise)
                put("m", r.merchant)
                put("d", r.dateFormatted)
            }
            arr.put(obj)
        }
        return arr.toString()
    }
}
