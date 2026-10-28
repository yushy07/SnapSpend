package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ExtractionResult
import com.example.data.ai.GeminiReceiptExtractor
import com.example.data.ai.IndianBrandDictionary
import com.example.data.ai.QuickCashFastParser
import com.example.data.backup.BackupStateSnapshot
import com.example.data.backup.DriveAuthResult
import com.example.data.backup.DriveBackupAndExportManager
import com.example.data.backup.DriveBackupFileMetadata
import com.example.data.local.BudgetEntity
import com.example.data.local.CardBillHandlingMode
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.ConfirmedScanRecord
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.RepeatingPaymentEntity
import com.example.data.local.RepeatingState
import com.example.data.local.ScanLogEntry
import com.example.data.local.ScanPath
import com.example.data.local.SnapSpendDatabase
import com.example.data.local.SnapSpendPreferences
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.repository.SnapSpendRepository
import com.example.data.rules.BudgetStatusItem
import com.example.data.rules.CategorySlice
import com.example.data.rules.DraftTransaction
import com.example.data.rules.LineChartPoint
import com.example.data.rules.LocalInsightsAndRulesEngine
import com.example.data.rules.MonthlyIncomeExpenseBar
import com.example.data.rules.PeriodComparison
import com.example.data.rules.PillBarEntry
import com.example.data.rules.ResolutionMode
import com.example.data.rules.TopMerchantItem
import com.example.ui.screens.DataCheckMonthRow
import com.example.ui.screens.DataCheckSnapshot
import com.example.ui.screens.computeDataCheckMonthBounds
import android.provider.OpenableColumns
import com.example.ui.theme.ThemeMode
import com.example.util.MoneyUtils
import com.example.util.MonthEndEstimateResult
import com.example.util.MonthEndMath
import com.example.util.QuickRepeatChip
import com.example.util.ScanNotificationHelper
import com.example.util.ShareIntentHelper
import com.example.util.SharedPayloadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DateRangeFilter(val label: String) {
    ALL("All time"),
    THIS_WEEK("This week"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    THIS_YEAR("This year")
}

enum class PaymentModeFilter(val label: String) {
    ALL("All"),
    ONLINE("Online"),
    CASH("Cash")
}

data class QueuedScanTask(
    val uris: List<Uri> = emptyList(),
    val samplePreset: Int? = null,
    val naturalLanguageNote: String? = null,
    val isSmartCashLog: Boolean = false,
    val defaultCashType: TransactionType? = null,
    val queuedAtMillis: Long = System.currentTimeMillis()
)

data class PendingBackgroundScanJob(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileNameOrType: String,
    val uris: List<Uri> = emptyList(),
    val pdfPassword: String? = null,
    val samplePreset: Int? = null,
    val naturalLanguageNote: String? = null,
    val isSmartCashLog: Boolean = false,
    val defaultCashType: TransactionType? = null,
    val onCleanup: (() -> Unit)? = null
)

data class SkippedKnownBannerState(
    val existingTransactionId: Long?,
    val amountPaise: Long,
    val merchant: String,
    val dateFormatted: String
)

data class PdfPasswordPromptState(
    val uri: Uri,
    val allBatchUris: List<Uri>,
    val fileName: String,
    val isRetryError: Boolean = false
)

data class ScanLabItemResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val totalMs: Long,
    val path: ScanPath,
    val totalTokens: Int,
    val merchant: String,
    val amountPaise: Long,
    val dateFormatted: String,
    val category: String,
    val statusNote: String? = null,
    val verdictCorrect: Boolean? = null
) {
    val errorMessage: String? get() = statusNote
    val isCorrect: Boolean? get() = verdictCorrect
}

data class ScanLabUiState(
    val isRunning: Boolean = false,
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val progressText: String = "",
    val items: List<ScanLabItemResult> = emptyList()
) {
    val percentCorrect: Int?
        get() {
            val judged = items.filter { it.verdictCorrect != null }
            if (judged.isEmpty()) return null
            val correct = judged.count { it.verdictCorrect == true }
            return kotlin.math.round((correct.toDouble() / judged.size.toDouble()) * 100.0).toInt()
        }

    val averageTimeMs: Long
        get() = if (items.isEmpty()) 0L else kotlin.math.round(items.map { it.totalMs }.average()).toLong()

    val averageTokens: Int
        get() = if (items.isEmpty()) 0 else kotlin.math.round(items.map { it.totalTokens }.average()).toInt()
}

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Reading(
        val currentStep: Int,
        val totalSteps: Int,
        val statusText: String,
        val elapsedSeconds: Int = 0
    ) : ScanUiState
    data class ReviewReady(
        val drafts: List<DraftTransaction>,
        val failedUris: List<Uri> = emptyList()
    ) : ScanUiState
    data class Unreadable(
        val reason: String,
        val lastUris: List<Uri> = emptyList(),
        val lastSamplePreset: Int? = null,
        val lastNaturalLanguageNote: String? = null,
        val isSmartCashLog: Boolean = false,
        val defaultCashType: TransactionType? = null
    ) : ScanUiState
    data class Error(
        val message: String,
        val recoverableUris: List<Uri> = emptyList(),
        val recoverableSamplePreset: Int? = null,
        val recoverableNaturalLanguageNote: String? = null,
        val isSmartCashLog: Boolean = false,
        val defaultCashType: TransactionType? = null
    ) : ScanUiState
}

enum class PendingDriveAction {
    NONE,
    BACKUP_NOW,
    CHECK_OR_RESTORE
}

data class FilterState(
    val searchQuery: String = "",
    val dateRange: DateRangeFilter = DateRangeFilter.ALL,
    val selectedCategory: String? = null,
    val selectedType: TransactionType? = null,
    val selectedPaymentApp: String? = null,
    val paymentModeFilter: PaymentModeFilter = PaymentModeFilter.ALL
)

data class ChartControlState(
    val chartDateRange: DateRangeFilter = DateRangeFilter.THIS_MONTH,
    val lineGranularity: String = "DAILY", // "DAILY", "WEEKLY", "MONTHLY"
    val paymentModeFilter: PaymentModeFilter = PaymentModeFilter.ALL
)

enum class UndoActionKind {
    SOFT_DELETE_RESTORE,
    QUICK_REPEAT_HARD_DELETE,
    QUICK_CASH_BATCH_DELETE
}

data class UndoBannerState(
    val actionToken: Long,
    val message: String,
    val transactionId: Long,
    val kind: UndoActionKind,
    val transactionIds: List<Long> = listOf(transactionId)
)

data class SnapSpendUiState(
    val allTransactions: List<TransactionEntity> = emptyList(),
    val trashedTransactions: List<TransactionEntity> = emptyList(),
    val trashedCount: Int = 0,
    val filteredTransactions: List<TransactionEntity> = emptyList(),
    val groupedTransactions: List<Pair<String, List<TransactionEntity>>> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val merchantMemories: List<MerchantMemoryEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val budgetStatuses: List<BudgetStatusItem> = emptyList(),
    val activeBudgetAlerts: List<BudgetStatusItem> = emptyList(),
    val repeatingPayments: List<RepeatingPaymentEntity> = emptyList(),
    val thisMonthSpentPaise: Long = 0L,
    val thisMonthEarnedPaise: Long = 0L,
    val thisMonthLeftPaise: Long = 0L,
    val cashInHandPaise: Long = 0L,
    val thisMonthCashSpentPaise: Long = 0L,
    val thisMonthCategorySlices: List<CategorySlice> = emptyList(),
    val last5Transactions: List<TransactionEntity> = emptyList(),
    val quickRepeatChips: List<QuickRepeatChip> = emptyList(),
    val disabledQuickRepeatKeys: Set<String> = emptySet(),
    val monthEndEstimate: MonthEndEstimateResult? = null,
    val availablePaymentApps: List<String> = emptyList(),
    // Charts & Local Insights
    val chartDateRange: DateRangeFilter = DateRangeFilter.THIS_MONTH,
    val chartPaymentModeFilter: PaymentModeFilter = PaymentModeFilter.ALL,
    val lineGranularity: String = "DAILY",
    val editorialPillBars: List<PillBarEntry> = emptyList(),
    val chartCategorySlices: List<CategorySlice> = emptyList(),
    val lineChartPoints: List<LineChartPoint> = emptyList(),
    val periodComparisons: List<PeriodComparison> = emptyList(),
    val monthlyIncomeExpenseBars: List<MonthlyIncomeExpenseBar> = emptyList(),
    val top5Places: List<TopMerchantItem> = emptyList(),
    val localMathInsights: List<String> = emptyList(),
    val filterState: FilterState = FilterState(),
    val queuedScansCount: Int = 0
)

class SnapSpendViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val prefs = appContext.getSharedPreferences("snapspend_prefs", Context.MODE_PRIVATE)
    val snapSpendPrefs = SnapSpendPreferences(appContext)
    private val database = SnapSpendDatabase.getInstance(appContext)
    val repository = SnapSpendRepository(database)
    private val aiExtractor = GeminiReceiptExtractor(appContext)
    private val backupManager = DriveBackupAndExportManager(appContext)

    private val _themeMode = MutableStateFlow(loadPersistedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _soundEffectsEnabled = MutableStateFlow(loadPersistedSoundEffectsEnabled())
    val soundEffectsEnabled: StateFlow<Boolean> = _soundEffectsEnabled.asStateFlow()

    private val _bgmEnabled = MutableStateFlow(loadPersistedBgmEnabled())
    val bgmEnabled: StateFlow<Boolean> = _bgmEnabled.asStateFlow()

    private val _queuedScans = MutableStateFlow<List<QueuedScanTask>>(emptyList())
    val queuedScans: StateFlow<List<QueuedScanTask>> = _queuedScans.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _chartControl = MutableStateFlow(ChartControlState())
    val chartControl: StateFlow<ChartControlState> = _chartControl.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

    private var activeScanJob: Job? = null
    private val sequentialScanQueue = ArrayDeque<PendingBackgroundScanJob>()
    private val accumulatedReviewDrafts = mutableListOf<DraftTransaction>()
    private val _activeScanState = MutableStateFlow<com.example.ui.ScanProgressState?>(null)
    val activeScanState: StateFlow<com.example.ui.ScanProgressState?> = _activeScanState.asStateFlow()

    private val _backupState = MutableStateFlow(backupManager.getBackupState())
    val backupState: StateFlow<BackupStateSnapshot> = _backupState.asStateFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _restoreOffer = MutableStateFlow<DriveBackupFileMetadata?>(null)
    val restoreOffer: StateFlow<DriveBackupFileMetadata?> = _restoreOffer.asStateFlow()

    private val _pendingConsentIntentSender = MutableStateFlow<IntentSender?>(null)
    val pendingConsentIntentSender: StateFlow<IntentSender?> = _pendingConsentIntentSender.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _undoBannerState = MutableStateFlow<UndoBannerState?>(null)
    val undoBannerState: StateFlow<UndoBannerState?> = _undoBannerState.asStateFlow()

    private val _disabledQuickRepeatKeys = MutableStateFlow<Set<String>>(emptySet())
    val disabledQuickRepeatKeys: StateFlow<Set<String>> = _disabledQuickRepeatKeys.asStateFlow()

    private val _dataCheckSnapshot = MutableStateFlow(DataCheckSnapshot())
    val dataCheckSnapshot: StateFlow<DataCheckSnapshot> = _dataCheckSnapshot.asStateFlow()

    private val _pendingSharedPayload = MutableStateFlow<SharedPayloadResult?>(null)
    val pendingSharedPayload: StateFlow<SharedPayloadResult?> = _pendingSharedPayload.asStateFlow()

    private val _skippedKnownBanner = MutableStateFlow<SkippedKnownBannerState?>(null)
    val skippedKnownBanner: StateFlow<SkippedKnownBannerState?> = _skippedKnownBanner.asStateFlow()

    private val _pdfPasswordPrompt = MutableStateFlow<PdfPasswordPromptState?>(null)
    val pdfPasswordPrompt: StateFlow<PdfPasswordPromptState?> = _pdfPasswordPrompt.asStateFlow()

    private val _scanLabState = MutableStateFlow(ScanLabUiState())
    val scanLabState: StateFlow<ScanLabUiState> = _scanLabState.asStateFlow()

    val cardBillHandlingMode: StateFlow<CardBillHandlingMode> = snapSpendPrefs.cardBillHandlingModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), CardBillHandlingMode.ASK)

    private var undoActionTokenCounter: Long = 0L
    private var pendingDriveAction: PendingDriveAction = PendingDriveAction.NONE

    init {
        viewModelScope.launch {
            runCatching { IndianBrandDictionary.validateAgainstDefaultCategories() }
            repository.ensureDefaultCategoriesSeeded()
            // Purge trashed items older than 30 days on app startup
            val thirtyDaysMillis = 30L * 24L * 60L * 60L * 1000L
            repository.purgeExpiredTrash(System.currentTimeMillis() - thirtyDaysMillis)
            // Seed persistent Cash Wallet balance in DataStore if not yet initialized
            val hasCustomBase = snapSpendPrefs.hasCustomCashWalletBaseFlow.first()
            if (!hasCustomBase) {
                val existingTxs = repository.getAllTransactionsOnce()
                val initialCashTotals = LocalInsightsAndRulesEngine.computeCashWalletTotals(existingTxs)
                snapSpendPrefs.setCashWalletBalancePaise(initialCashTotals.cashInHandPaise)
            }
            repository.refreshRepeatingPayments()
            refreshDataCheckSnapshot()
            val silentEmail = backupManager.attemptSilentSignIn()
            _backupState.value = backupManager.getBackupState()
            if (!silentEmail.isNullOrBlank()) {
                DriveBackupAndExportManager.scheduleWeeklyBackupWork(appContext)
            }
        }
        viewModelScope.launch {
            combine(
                repository.transactionsFlow,
                repository.trashedTransactionsFlow,
                snapSpendPrefs.scanTimingLogsFlow
            ) { active, trashed, logs -> Triple(active.size, trashed.size, logs.size) }.collect {
                refreshDataCheckSnapshot()
            }
        }
    }

    fun refreshDataCheckSnapshot(nowMillis: Long = System.currentTimeMillis()) {
        viewModelScope.launch(Dispatchers.IO) {
            val scanLogs = snapSpendPrefs.scanTimingLogsFlow.first()
            _dataCheckSnapshot.value = loadDataCheckSnapshotFromSql(
                dao = database.transactionDao(),
                nowMillis = nowMillis,
                scanTimingLogs = scanLogs
            )
        }
    }

    fun setCardBillHandlingMode(mode: CardBillHandlingMode) {
        viewModelScope.launch {
            snapSpendPrefs.setCardBillHandlingMode(mode)
        }
    }

    fun dismissSkippedKnownBanner() {
        _skippedKnownBanner.value = null
    }

    fun dismissPdfPasswordPrompt() {
        _pdfPasswordPrompt.value = null
    }

    fun submitPdfPasswordAndRescan(password: String) {
        val prompt = _pdfPasswordPrompt.value ?: return
        _pdfPasswordPrompt.value = null
        enqueueSequentialScanJob(
            PendingBackgroundScanJob(
                fileNameOrType = prompt.fileName,
                uris = prompt.allBatchUris,
                pdfPassword = password.trim()
            ),
            addToFront = true
        )
    }

    /**
     * Runs up to 20 picked screenshots/PDFs through the real scanning pipeline in SCAN LAB mode:
     * - Nothing is saved to transactions, and files are never copied or committed.
     * - Shows per-file time, path used, tokens, and extracted merchant/amount/date/category.
     */
    fun runScanLab(uris: List<Uri>) {
        val cappedUris = uris.take(20)
        if (cappedUris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _scanLabState.value = ScanLabUiState(
                isRunning = true,
                processedCount = 0,
                totalCount = cappedUris.size,
                progressText = "Running Scan Lab on 0 of ${cappedUris.size} files...",
                items = emptyList()
            )
            val categories = repository.getAllCategoriesOnce()
            val memories = repository.getAllMerchantMemoriesOnce()
            val cardMode = snapSpendPrefs.cardBillHandlingModeFlow.first()
            val results = mutableListOf<ScanLabItemResult>()

            cappedUris.forEachIndexed { idx, uri ->
                val fileName = resolveFileName(appContext, uri)
                _scanLabState.value = _scanLabState.value.copy(
                    processedCount = idx,
                    totalCount = cappedUris.size,
                    progressText = "Scanning ${idx + 1} of ${cappedUris.size}: $fileName..."
                )
                val outcome = aiExtractor.extractSingleUriWithPipeline(
                    uri = uri,
                    categories = categories,
                    merchantMemories = memories,
                    pdfPassword = null,
                    cardBillMode = cardMode,
                    findExistingByRef = { null },
                    findConfirmedHash = { null }
                )
                snapSpendPrefs.appendScanLogEntry(outcome.logEntry)
                val totalTokens = outcome.logEntry.promptTokens + outcome.logEntry.outputTokens
                val itemResult = when (val res = outcome.result) {
                    is ExtractionResult.Success -> {
                        val first = res.drafts.firstOrNull()
                        val totalAmt = res.drafts.sumOf { it.amountPaise }
                        val merchantLabel = if (res.drafts.size > 1) {
                            "${first?.merchant ?: "Statement"} (+${res.drafts.size - 1} rows)"
                        } else {
                            first?.merchant ?: "Unknown"
                        }
                        val dateLabel = if (first != null) {
                            if (!first.dateFound) "DATE NOT FOUND"
                            else MoneyUtils.formatDayHeader(first.timestamp)
                        } else "Unknown"
                        ScanLabItemResult(
                            fileName = fileName,
                            totalMs = outcome.logEntry.totalMs,
                            path = outcome.logEntry.path,
                            totalTokens = totalTokens,
                            merchant = merchantLabel,
                            amountPaise = totalAmt,
                            dateFormatted = dateLabel,
                            category = first?.category ?: "Other"
                        )
                    }
                    is ExtractionResult.UnreadableOrNoPayments -> {
                        ScanLabItemResult(
                            fileName = fileName,
                            totalMs = outcome.logEntry.totalMs,
                            path = outcome.logEntry.path,
                            totalTokens = totalTokens,
                            merchant = "Unreadable",
                            amountPaise = 0L,
                            dateFormatted = "-",
                            category = "-",
                            statusNote = res.reason
                        )
                    }
                    is ExtractionResult.PdfPasswordRequired -> {
                        ScanLabItemResult(
                            fileName = fileName,
                            totalMs = outcome.logEntry.totalMs,
                            path = outcome.logEntry.path,
                            totalTokens = totalTokens,
                            merchant = "Password-protected PDF",
                            amountPaise = 0L,
                            dateFormatted = "-",
                            category = "-",
                            statusNote = "PDF is encrypted with a password"
                        )
                    }
                    is ExtractionResult.RateLimited -> {
                        ScanLabItemResult(
                            fileName = fileName,
                            totalMs = outcome.logEntry.totalMs,
                            path = outcome.logEntry.path,
                            totalTokens = totalTokens,
                            merchant = "Rate-limited / Offline",
                            amountPaise = 0L,
                            dateFormatted = "-",
                            category = "-",
                            statusNote = res.message
                        )
                    }
                    is ExtractionResult.SkippedKnown -> {
                        ScanLabItemResult(
                            fileName = fileName,
                            totalMs = outcome.logEntry.totalMs,
                            path = ScanPath.SKIPPED_KNOWN,
                            totalTokens = 0,
                            merchant = res.merchant,
                            amountPaise = res.amountPaise,
                            dateFormatted = res.dateFormatted,
                            category = "Known"
                        )
                    }
                    is ExtractionResult.Error -> {
                        ScanLabItemResult(
                            fileName = fileName,
                            totalMs = outcome.logEntry.totalMs,
                            path = outcome.logEntry.path,
                            totalTokens = totalTokens,
                            merchant = "Error",
                            amountPaise = 0L,
                            dateFormatted = "-",
                            category = "-",
                            statusNote = res.message
                        )
                    }
                }
                results.add(itemResult)
                _scanLabState.value = ScanLabUiState(
                    isRunning = idx + 1 < cappedUris.size,
                    processedCount = idx + 1,
                    totalCount = cappedUris.size,
                    progressText = if (idx + 1 < cappedUris.size) {
                        "Completed ${idx + 1} of ${cappedUris.size}..."
                    } else {
                        "Finished ${cappedUris.size} file${if (cappedUris.size == 1) "" else "s"}"
                    },
                    items = results.toList()
                )
            }
            refreshDataCheckSnapshot()
        }
    }

    fun markScanLabVerdict(itemId: String, isCorrect: Boolean) {
        val current = _scanLabState.value
        _scanLabState.value = current.copy(
            items = current.items.map {
                if (it.id == itemId) it.copy(verdictCorrect = isCorrect) else it
            }
        )
    }

    fun clearScanLab() {
        _scanLabState.value = ScanLabUiState()
    }

    /**
     * Immediately ingests a shared ACTION_SEND / ACTION_SEND_MULTIPLE intent:
     * - Deletes any temporary cache files from a prior unfinished share (onNewIntent safety).
     * - Resets any prior scan/review overlay so the new share replaces it cleanly.
     * - Copies valid file streams to ephemeral cache immediately before temporary URI permissions expire.
     * - Holds the payload in memory until onboarding and the one-time first-scan notice are completed.
     */
    fun onIncomingShareIntent(context: Context, intent: Intent?) {
        if (!ShareIntentHelper.isShareIntent(intent)) return
        // If a previous scan/review was still open, reset it before ingesting the new share
        _scanState.value = ScanUiState.Idle
        _pendingSharedPayload.value = null

        val payload = ShareIntentHelper.ingestShareIntent(context, intent)
        if (!payload.userNoticeMessage.isNullOrBlank()) {
            _toastMessage.value = payload.userNoticeMessage
        }
        if (payload.hasValidContent) {
            _pendingSharedPayload.value = payload
        }
    }

    fun consumePendingSharedPayload(): SharedPayloadResult? {
        val current = _pendingSharedPayload.value
        _pendingSharedPayload.value = null
        return current
    }

    fun cancelPendingSharedPayload() {
        _pendingSharedPayload.value = null
        ShareIntentHelper.cleanupActiveShareTempFiles()
    }

    private fun loadPersistedThemeMode(): ThemeMode {
        val raw = prefs.getString("theme_mode", ThemeMode.DARK.name) ?: ThemeMode.DARK.name
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.DARK)
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun toggleDarkLightQuickly(currentlyDark: Boolean) {
        val next = if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK
        setThemeMode(next)
    }

    private fun loadPersistedSoundEffectsEnabled(): Boolean {
        return prefs.getBoolean("sound_effects_enabled", true)
    }

    fun setSoundEffectsEnabled(enabled: Boolean) {
        _soundEffectsEnabled.value = enabled
        prefs.edit().putBoolean("sound_effects_enabled", enabled).apply()
    }

    fun toggleSoundEffectsEnabled() {
        setSoundEffectsEnabled(!_soundEffectsEnabled.value)
    }

    private fun loadPersistedBgmEnabled(): Boolean {
        return prefs.getBoolean("bgm_enabled", true)
    }

    fun setBgmEnabled(enabled: Boolean) {
        _bgmEnabled.value = enabled
        prefs.edit().putBoolean("bgm_enabled", enabled).apply()
    }

    fun toggleBgmEnabled() {
        setBgmEnabled(!_bgmEnabled.value)
    }

    private val coreDataFlow = combine(
        repository.transactionsFlow,
        repository.categoriesFlow,
        repository.merchantMemoriesFlow,
        repository.budgetsFlow,
        repository.repeatingPaymentsFlow
    ) { txs, cats, memories, budgets, repeating ->
        Quintuple(txs, cats, memories, budgets, repeating)
    }

    private val trashAndQuickRepeatControlFlow = combine(
        repository.trashedTransactionsFlow,
        _disabledQuickRepeatKeys,
        snapSpendPrefs.cashWalletBalancePaiseFlow,
        snapSpendPrefs.hasCustomCashWalletBaseFlow
    ) { trashedTxs, disabledKeys, persistedCashBalance, hasCustomCashBase ->
        Quadruple(trashedTxs, disabledKeys, persistedCashBalance, hasCustomCashBase)
    }

    val uiState: StateFlow<SnapSpendUiState> = combine(
        coreDataFlow,
        _filterState,
        _chartControl,
        _queuedScans,
        trashAndQuickRepeatControlFlow
    ) { (allTxs, categories, memories, budgets, repeating), filters, chartCtrl, queuedList, (trashedTxs, disabledKeys, persistedCashBalance, hasCustomCashBase) ->
        val now = System.currentTimeMillis()
        val (thisMonthStart, thisMonthEnd) = LocalInsightsAndRulesEngine.getMonthBounds(now, 0)

        val thisMonthTxs = allTxs.filter { it.timestamp in thisMonthStart..thisMonthEnd }
        val thisMonthValid = LocalInsightsAndRulesEngine.successfulTransactions(thisMonthTxs)

        // Exclude ATM withdrawals (isAtmWithdrawal == true) from monthly "Spent" so cash isn't double-counted
        val thisMonthSpent = thisMonthValid
            .filter { LocalInsightsAndRulesEngine.isCountableExpense(it) }
            .sumOf { it.amountPaise }
        val thisMonthEarned = thisMonthValid
            .filter { it.type == TransactionType.EARNING }
            .sumOf { it.amountPaise }
        val thisMonthLeft = thisMonthEarned - thisMonthSpent

        val cashWalletTotals = LocalInsightsAndRulesEngine.computeCashWalletTotals(
            allTransactions = allTxs,
            nowMillis = now,
            persistedWalletBalancePaise = if (hasCustomCashBase) persistedCashBalance else null
        )

        val thisMonthSlices = LocalInsightsAndRulesEngine.buildCategorySlices(
            transactions = thisMonthTxs,
            categories = categories,
            type = TransactionType.EXPENSE
        )

        val budgetStatuses = LocalInsightsAndRulesEngine.computeBudgetStatuses(
            budgets = budgets,
            categories = categories,
            thisMonthTransactions = thisMonthTxs
        )
        val activeAlerts = budgetStatuses.filter { it.isWarning80 || it.isExceeded100 }
        checkAndDispatchBudgetNotifications(budgetStatuses, budgets)

        // Apply transaction explorer filters
        val filtered = filterTransactions(allTxs, filters, now)
        val grouped = filtered
            .groupBy { MoneyUtils.formatDayHeader(it.timestamp) }
            .map { it.key to it.value }

        val paymentApps = allTxs.map { it.paymentApp.trim() }
            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
            .distinct()
            .sorted()

        // Charts & Insights calculations (100% local integer paise math, filtered by All | Online | Cash)
        val chartModeFilteredTxs = when (chartCtrl.paymentModeFilter) {
            PaymentModeFilter.ALL -> allTxs
            PaymentModeFilter.ONLINE -> allTxs.filter { it.paymentMode.equals("ONLINE", ignoreCase = true) }
            PaymentModeFilter.CASH -> allTxs.filter { it.paymentMode.equals("CASH", ignoreCase = true) }
        }
        val chartRangeTxs = filterByDateRange(chartModeFilteredTxs, chartCtrl.chartDateRange, now)
        val chartSlices = LocalInsightsAndRulesEngine.buildCategorySlices(
            transactions = chartRangeTxs,
            categories = categories,
            type = TransactionType.EXPENSE
        )

        val pillBuckets: List<Pair<String, Long>> = if (chartSlices.isNotEmpty()) {
            chartSlices.take(6).map { slice ->
                val shortLabel = slice.categoryName.split(" ").first().take(7)
                shortLabel to slice.amountPaise
            }
        } else {
            val linePts = LocalInsightsAndRulesEngine.buildLineChartPoints(chartModeFilteredTxs, chartCtrl.lineGranularity, now)
            linePts.map { it.label to it.amountPaise }
        }
        val editorialPillBars = LocalInsightsAndRulesEngine.buildPillBars(pillBuckets)

        val linePoints = LocalInsightsAndRulesEngine.buildLineChartPoints(
            allTransactions = chartModeFilteredTxs,
            granularity = chartCtrl.lineGranularity,
            nowMillis = now
        )
        val comparisons = LocalInsightsAndRulesEngine.buildPeriodComparisons(chartModeFilteredTxs, now)
        val monthlyBars = LocalInsightsAndRulesEngine.buildMonthlyIncomeExpenseBars(chartModeFilteredTxs, now)
        val top5Places = LocalInsightsAndRulesEngine.buildTop5SpendingPlaces(chartRangeTxs.ifEmpty { chartModeFilteredTxs })
        val localInsights = LocalInsightsAndRulesEngine.generateLocalMathInsights(chartModeFilteredTxs, now)

        val totalMonthlyBudgetPaise = budgets.sumOf { it.monthlyLimitPaise }
        val monthEndEstimate = MonthEndMath.computeMonthEndEstimate(
            transactions = allTxs,
            totalMonthlyBudgetPaise = totalMonthlyBudgetPaise,
            anchorMillis = now
        )
        val quickRepeatChips = MonthEndMath.computeQuickRepeats(
            transactions = allTxs,
            nowMillis = now,
            maxChips = 6
        )

        SnapSpendUiState(
            allTransactions = allTxs,
            trashedTransactions = trashedTxs,
            trashedCount = trashedTxs.size,
            filteredTransactions = filtered,
            groupedTransactions = grouped,
            categories = categories,
            merchantMemories = memories,
            budgets = budgets,
            budgetStatuses = budgetStatuses,
            activeBudgetAlerts = activeAlerts,
            repeatingPayments = repeating.filter { it.state != RepeatingState.DISMISSED },
            thisMonthSpentPaise = thisMonthSpent,
            thisMonthEarnedPaise = thisMonthEarned,
            thisMonthLeftPaise = thisMonthLeft,
            cashInHandPaise = cashWalletTotals.cashInHandPaise,
            thisMonthCashSpentPaise = cashWalletTotals.thisMonthCashSpentPaise,
            thisMonthCategorySlices = thisMonthSlices,
            last5Transactions = allTxs.take(5),
            quickRepeatChips = quickRepeatChips,
            disabledQuickRepeatKeys = disabledKeys,
            monthEndEstimate = monthEndEstimate,
            availablePaymentApps = paymentApps,
            chartDateRange = chartCtrl.chartDateRange,
            chartPaymentModeFilter = chartCtrl.paymentModeFilter,
            lineGranularity = chartCtrl.lineGranularity,
            editorialPillBars = editorialPillBars,
            chartCategorySlices = chartSlices,
            lineChartPoints = linePoints,
            periodComparisons = comparisons,
            monthlyIncomeExpenseBars = monthlyBars,
            top5Places = top5Places,
            localMathInsights = localInsights,
            filterState = filters,
            queuedScansCount = queuedList.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = SnapSpendUiState()
    )

    private fun checkAndDispatchBudgetNotifications(
        statuses: List<BudgetStatusItem>,
        budgets: List<BudgetEntity>
    ) {
        val currentYm = SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        val budgetMap = budgets.associateBy { it.categoryName }

        viewModelScope.launch {
            for (status in statuses) {
                val entity = budgetMap[status.categoryName] ?: continue
                val notifiedForCurrentMonth = entity.notifiedYearMonth == currentYm
                val priorThreshold = if (notifiedForCurrentMonth) entity.lastNotifiedThreshold else 0

                val newThreshold = when {
                    status.isExceeded100 && priorThreshold < 100 -> 100
                    status.isWarning80 && priorThreshold < 80 -> 80
                    else -> null
                }

                if (newThreshold != null) {
                    repository.updateBudgetNotificationState(status.categoryName, newThreshold, currentYm)
                    DriveBackupAndExportManager.sendBudgetWarningNotification(
                        context = appContext,
                        categoryName = status.categoryName,
                        usagePercent = status.usagePercent,
                        spentFormatted = MoneyUtils.formatPaise(status.spentPaise),
                        limitFormatted = MoneyUtils.formatPaise(status.limitPaise)
                    )
                }
            }
        }
    }

    private fun filterByDateRange(
        transactions: List<TransactionEntity>,
        range: DateRangeFilter,
        nowMillis: Long
    ): List<TransactionEntity> {
        val bounds: Pair<Long, Long>? = when (range) {
            DateRangeFilter.ALL -> null
            DateRangeFilter.THIS_WEEK -> LocalInsightsAndRulesEngine.getWeekBounds(nowMillis, 0)
            DateRangeFilter.THIS_MONTH -> LocalInsightsAndRulesEngine.getMonthBounds(nowMillis, 0)
            DateRangeFilter.LAST_MONTH -> LocalInsightsAndRulesEngine.getMonthBounds(nowMillis, -1)
            DateRangeFilter.THIS_YEAR -> LocalInsightsAndRulesEngine.getYearBounds(nowMillis, 0)
        }
        return if (bounds == null) {
            transactions
        } else {
            transactions.filter { it.timestamp in bounds.first..bounds.second }
        }
    }

    private fun filterTransactions(
        transactions: List<TransactionEntity>,
        filters: FilterState,
        nowMillis: Long
    ): List<TransactionEntity> {
        val byDate = filterByDateRange(transactions, filters.dateRange, nowMillis)
        val q = filters.searchQuery.trim().lowercase(Locale.US)

        return byDate.filter { tx ->
            val matchesSearch = if (q.isEmpty()) {
                true
            } else {
                val formattedRupees = MoneyUtils.formatPaise(tx.amountPaise).lowercase(Locale.US)
                val plainRupees = MoneyUtils.paiseToEditableRupeeString(tx.amountPaise)
                tx.merchant.lowercase(Locale.US).contains(q) ||
                    tx.category.lowercase(Locale.US).contains(q) ||
                    tx.note.lowercase(Locale.US).contains(q) ||
                    tx.paymentApp.lowercase(Locale.US).contains(q) ||
                    (tx.referenceNumber?.lowercase(Locale.US)?.contains(q) == true) ||
                    formattedRupees.contains(q) ||
                    plainRupees.contains(q)
            }
            val matchesCat = filters.selectedCategory == null || tx.category == filters.selectedCategory
            val matchesType = filters.selectedType == null || tx.type == filters.selectedType
            val matchesApp = filters.selectedPaymentApp == null ||
                tx.paymentApp.equals(filters.selectedPaymentApp, ignoreCase = true)
            val matchesPaymentMode = when (filters.paymentModeFilter) {
                PaymentModeFilter.ALL -> true
                PaymentModeFilter.ONLINE -> tx.paymentMode.equals("ONLINE", ignoreCase = true)
                PaymentModeFilter.CASH -> tx.paymentMode.equals("CASH", ignoreCase = true)
            }

            matchesSearch && matchesCat && matchesType && matchesApp && matchesPaymentMode
        }
    }

    // --- Filter & Chart Controls ---
    fun updateSearchQuery(query: String) {
        _filterState.value = _filterState.value.copy(searchQuery = query)
    }

    fun updateDateRangeFilter(range: DateRangeFilter) {
        _filterState.value = _filterState.value.copy(dateRange = range)
    }

    fun updateCategoryFilter(category: String?) {
        _filterState.value = _filterState.value.copy(selectedCategory = category)
    }

    fun updateTypeFilter(type: TransactionType?) {
        _filterState.value = _filterState.value.copy(selectedType = type)
    }

    fun updatePaymentAppFilter(app: String?) {
        _filterState.value = _filterState.value.copy(selectedPaymentApp = app)
    }

    fun updatePaymentModeFilter(mode: PaymentModeFilter) {
        _filterState.value = _filterState.value.copy(paymentModeFilter = mode)
    }

    fun clearAllFilters() {
        _filterState.value = FilterState(paymentModeFilter = _filterState.value.paymentModeFilter)
    }

    fun updateChartDateRange(range: DateRangeFilter) {
        _chartControl.value = _chartControl.value.copy(chartDateRange = range)
    }

    fun updateChartPaymentModeFilter(mode: PaymentModeFilter) {
        _chartControl.value = _chartControl.value.copy(paymentModeFilter = mode)
    }

    fun updateLineGranularity(granularity: String) {
        _chartControl.value = _chartControl.value.copy(lineGranularity = granularity)
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    // --- AI Camera, Screenshot & PDF Extraction Flow ---
    private fun resolveFileName(context: Context, uri: Uri?): String {
        if (uri == null) return "Receipt_Document.jpg"
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) { }
        if (name.isNullOrBlank()) {
            val last = uri.lastPathSegment
            if (!last.isNullOrBlank()) {
                name = last.substringAfterLast('/')
            }
        }
        return if (!name.isNullOrBlank()) name!! else "Receipt_Scan.jpg"
    }

    fun processSelectedUris(uris: List<Uri>, onFinished: () -> Unit = {}) {
        if (uris.isEmpty()) {
            onFinished()
            return
        }
        val fileName = if (uris.size > 1) {
            "${uris.size}_Receipts.jpg"
        } else {
            resolveFileName(getApplication(), uris.firstOrNull())
        }
        enqueueSequentialScanJob(
            PendingBackgroundScanJob(
                fileNameOrType = fileName,
                uris = uris,
                onCleanup = onFinished
            )
        )
    }

    fun scanSampleUpiScreenshotWithGemini(presetIndex: Int) {
        val fileName = "Sample_UPI_Receipt_$presetIndex.jpg"
        enqueueSequentialScanJob(
            PendingBackgroundScanJob(
                fileNameOrType = fileName,
                samplePreset = presetIndex
            )
        )
    }

    fun processNaturalLanguageNoteWithGemini(userNote: String) {
        val cleaned = userNote.trim()
        if (cleaned.isEmpty()) return
        enqueueSequentialScanJob(
            PendingBackgroundScanJob(
                fileNameOrType = "Payment_Note.txt",
                naturalLanguageNote = cleaned
            )
        )
    }

    /**
     * Natural-Language Quick Cash Logging:
     * 1. Local Fast Parser (<20ms, 0 API calls): Matches `<Amount> <Item>` / `<Item> <Amount>`
     *    patterns like "20 chai, 50 auto, 140 maggi" against MerchantMemoryEntity or default categories,
     *    saves immediately to the Cash Wallet, adjusts DataStore balance, and shows the 6-second Undo snackbar.
     * 2. AI Fallback: If complex or conversational, sends a lightweight text-only request (`thinkingBudget = 0`,
     *    `temperature = 0.1f`) to `gemini-3.8-flash` and saves the returned CASH transactions immediately
     *    to the Cash Wallet with the Undo snackbar.
     */
    fun processSmartCashLogWithGemini(
        userNote: String,
        defaultCashType: TransactionType = TransactionType.EXPENSE
    ) {
        val cleaned = userNote.trim()
        if (cleaned.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val categories = repository.getAllCategoriesOnce()
            val memories = repository.getAllMerchantMemoriesOnce()

            // Phase 1: 0-API Local Fast Parser (<20ms)
            val fastDrafts = QuickCashFastParser.parseFastDrafts(
                rawInput = cleaned,
                categories = categories,
                merchantMemories = memories,
                defaultType = defaultCashType
            )

            if (!fastDrafts.isNullOrEmpty()) {
                commitQuickCashDraftsImmediatelyWithUndo(fastDrafts, categories)
                return@launch
            }

            // Phase 2: Lightweight Text-Only Gemini 3.8-Flash Fallback (thinkingBudget = 0, temperature = 0.1f)
            val aiResult = aiExtractor.extractFromNaturalLanguageText(
                userInput = cleaned,
                categories = categories,
                defaultPaymentMode = "CASH",
                defaultCashType = defaultCashType
            )
            extractLogEntryFromResult(aiResult)?.let { snapSpendPrefs.appendScanLogEntry(it) }

            when (aiResult) {
                is ExtractionResult.Success -> {
                    val cashDrafts = aiResult.drafts.map { it.copy(paymentMode = "CASH", paymentApp = "Cash") }
                    if (cashDrafts.isNotEmpty()) {
                        commitQuickCashDraftsImmediatelyWithUndo(cashDrafts, categories)
                    } else {
                        _toastMessage.value = "Couldn't find any cash amount in '$cleaned'."
                    }
                }
                is ExtractionResult.UnreadableOrNoPayments -> {
                    _toastMessage.value = aiResult.reason
                }
                is ExtractionResult.RateLimited -> {
                    _queuedScans.value = _queuedScans.value + QueuedScanTask(
                        naturalLanguageNote = cleaned,
                        isSmartCashLog = true,
                        defaultCashType = defaultCashType
                    )
                    _toastMessage.value = aiResult.message
                }
                is ExtractionResult.PdfPasswordRequired -> {
                    _toastMessage.value = "PDF is password-protected."
                }
                is ExtractionResult.SkippedKnown -> {
                    _toastMessage.value = "Already saved: ${MoneyUtils.formatPaise(aiResult.amountPaise)} · ${aiResult.merchant}"
                }
                is ExtractionResult.Error -> {
                    _toastMessage.value = aiResult.message
                }
            }
        }
    }

    private fun extractLogEntryFromResult(result: ExtractionResult): ScanLogEntry? {
        return when (result) {
            is ExtractionResult.Success -> result.logEntry
            is ExtractionResult.SkippedKnown -> result.logEntry
            is ExtractionResult.RateLimited -> result.logEntry
            is ExtractionResult.UnreadableOrNoPayments -> result.logEntry
            is ExtractionResult.Error -> result.logEntry
            is ExtractionResult.PdfPasswordRequired -> null
        }
    }

    private suspend fun commitQuickCashDraftsImmediatelyWithUndo(
        drafts: List<DraftTransaction>,
        categories: List<CategoryEntity>
    ) {
        val entities = QuickCashFastParser.toTransactionEntities(drafts, categories)
        if (entities.isEmpty()) return

        val insertedIds = repository.insertQuickCashBatch(entities)
        val totalWalletDelta = entities.sumOf { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) }
        if (totalWalletDelta != 0L) {
            snapSpendPrefs.adjustCashWalletBalancePaise(totalWalletDelta)
        }

        val totalAmountPaise = entities.sumOf { it.amountPaise }
        val summaryMsg = if (entities.size == 1) {
            val item = entities.first()
            "Saved ${item.merchant} (${MoneyUtils.formatPaise(item.amountPaise)}) to Cash Wallet"
        } else {
            val preview = entities.take(2).joinToString(", ") { it.merchant }
            val extra = if (entities.size > 2) " +${entities.size - 2}" else ""
            "Saved ${entities.size} cash items ($preview$extra · ${MoneyUtils.formatPaise(totalAmountPaise)})"
        }

        _undoBannerState.value = UndoBannerState(
            actionToken = ++undoActionTokenCounter,
            message = summaryMsg,
            transactionId = insertedIds.firstOrNull() ?: 0L,
            kind = UndoActionKind.QUICK_CASH_BATCH_DELETE,
            transactionIds = insertedIds
        )
    }

    /**
     * Manually sets the user's "Cash in Hand" wallet base balance in Preferences DataStore.
     */
    fun setManualCashWalletBalancePaise(newBalancePaise: Long) {
        viewModelScope.launch {
            val clamped = newBalancePaise.coerceAtLeast(0L)
            snapSpendPrefs.setCashWalletBalancePaise(clamped)
            _toastMessage.value = "Cash in Hand updated to ${MoneyUtils.formatPaise(clamped)}."
        }
    }

    /**
     * Adds `addPaise` to the user's "Cash in Hand" wallet balance (optionally recording an ATM withdrawal row).
     */
    fun topUpCashWalletBalancePaise(addPaise: Long, recordAsAtmWithdrawal: Boolean = false) {
        if (addPaise <= 0L) return
        viewModelScope.launch {
            if (recordAsAtmWithdrawal) {
                val nowMillis = System.currentTimeMillis()
                val atmTx = TransactionEntity(
                    id = 0L,
                    timestamp = nowMillis,
                    hasExplicitTime = true,
                    amountPaise = addPaise,
                    type = TransactionType.TRANSFER,
                    merchant = "ATM Cash Withdrawal",
                    category = "Transfers",
                    paymentApp = "Cash",
                    status = TransactionStatus.SUCCESS,
                    note = "ATM Cash Withdrawal Top-Up",
                    createdAt = nowMillis,
                    source = TransactionSource.MANUAL,
                    paymentMode = "CASH",
                    isAtmWithdrawal = true
                )
                repository.saveTransaction(atmTx, learnMerchantCategory = false)
            }
            val updated = snapSpendPrefs.adjustCashWalletBalancePaise(addPaise)
            _toastMessage.value = "Added ${MoneyUtils.formatPaise(addPaise)} · Cash in Hand: ${MoneyUtils.formatPaise(updated)}"
        }
    }

    private fun enqueueSequentialScanJob(job: PendingBackgroundScanJob, addToFront: Boolean = false) {
        val currentActive = _activeScanState.value
        synchronized(accumulatedReviewDrafts) {
            if (currentActive?.currentStep == com.example.ui.ScanStep.COMPLETE &&
                !currentActive.resultDrafts.isNullOrEmpty() &&
                accumulatedReviewDrafts.isEmpty()
            ) {
                accumulatedReviewDrafts.addAll(currentActive.resultDrafts)
            }
        }

        val queueSizeAfterAdd = synchronized(sequentialScanQueue) {
            if (addToFront) {
                sequentialScanQueue.addFirst(job)
            } else {
                sequentialScanQueue.addLast(job)
            }
            sequentialScanQueue.size
        }

        if (activeScanJob?.isActive == true) {
            _activeScanState.value = _activeScanState.value?.copy(
                queuedCount = queueSizeAfterAdd
            )
            return
        }

        startSequentialQueueWorker()
    }

    private fun startSequentialQueueWorker() {
        activeScanJob = viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val currentJob = synchronized(sequentialScanQueue) {
                    if (sequentialScanQueue.isEmpty()) null else sequentialScanQueue.removeFirst()
                } ?: break

                val remainingInQueue = synchronized(sequentialScanQueue) { sequentialScanQueue.size }
                val existingDraftsSnapshot = synchronized(accumulatedReviewDrafts) {
                    accumulatedReviewDrafts.toList().takeIf { it.isNotEmpty() }
                }

                _activeScanState.value = com.example.ui.ScanProgressState(
                    id = currentJob.id,
                    fileNameOrType = currentJob.fileNameOrType,
                    currentStep = com.example.ui.ScanStep.READING_FILE,
                    stepDescription = "Reading receipt...",
                    elapsedSeconds = 0,
                    resultDrafts = existingDraftsSnapshot,
                    queuedCount = remainingInQueue,
                    recoverableUris = currentJob.uris,
                    recoverableSamplePreset = currentJob.samplePreset,
                    recoverableNote = currentJob.naturalLanguageNote,
                    isSmartCashLog = currentJob.isSmartCashLog,
                    defaultCashType = currentJob.defaultCashType
                )
                ScanNotificationHelper.postScanningProgressNotification(
                    getApplication(),
                    "Reading amount..."
                )

                val tickerJob = launch {
                    var sec = 0
                    while (isActive) {
                        delay(1000L)
                        sec++
                        _activeScanState.value = _activeScanState.value?.let { st ->
                            if (st.currentStep != com.example.ui.ScanStep.COMPLETE &&
                                st.currentStep != com.example.ui.ScanStep.ERROR
                            ) {
                                st.copy(elapsedSeconds = sec)
                            } else st
                        }
                        val curScanUi = _scanState.value
                        if (curScanUi is ScanUiState.Reading) {
                            _scanState.value = curScanUi.copy(elapsedSeconds = sec)
                        }
                    }
                }

                val result = try {
                    executeSingleBackgroundJob(currentJob)
                } catch (e: CancellationException) {
                    tickerJob.cancel()
                    currentJob.onCleanup?.invoke()
                    throw e
                } catch (e: Exception) {
                    ExtractionResult.Error(e.message ?: "Extraction failed")
                } finally {
                    tickerJob.cancel()
                }

                if (result is ExtractionResult.Success || result is ExtractionResult.SkippedKnown) {
                    currentJob.onCleanup?.invoke()
                }

                val shouldStopLoop = handleBackgroundExtractionResult(
                    result = result,
                    lastUris = currentJob.uris,
                    lastSamplePreset = currentJob.samplePreset,
                    lastNote = currentJob.naturalLanguageNote,
                    isSmartCashLog = currentJob.isSmartCashLog,
                    defaultCashType = currentJob.defaultCashType
                )
                if (shouldStopLoop) {
                    break
                }
            }
        }
    }

    private suspend fun executeSingleBackgroundJob(job: PendingBackgroundScanJob): ExtractionResult {
        return when {
            job.uris.isNotEmpty() -> {
                val categories = repository.getAllCategoriesOnce()
                val memories = repository.getAllMerchantMemoriesOnce()
                val existingTxs = repository.getAllTransactionsOnce()
                val memoryMap = memories.associateBy { it.normalizedMerchant }
                val cardMode = snapSpendPrefs.cardBillHandlingModeFlow.first()

                val totalFiles = job.uris.size
                val fileSemaphore = Semaphore(3)
                val failedBatchUris = java.util.Collections.synchronizedList(mutableListOf<Uri>())
                val outcomes = coroutineScope {
                    job.uris.mapIndexed { fileIdx, uri ->
                        async(Dispatchers.IO) {
                            fileSemaphore.withPermit {
                                val singleOutcome = aiExtractor.extractSingleUriWithPipeline(
                                    uri = uri,
                                    categories = categories,
                                    merchantMemories = memories,
                                    pdfPassword = job.pdfPassword,
                                    cardBillMode = cardMode,
                                    findExistingByRef = { ref -> repository.findByReferenceNumber(ref) },
                                    findConfirmedHash = { hash -> snapSpendPrefs.findConfirmedScanByHash(hash) },
                                    onProgress = { label ->
                                        val (nextStep, friendlyStepText) = when {
                                            label.contains("Categorising", ignoreCase = true) ||
                                                label.contains("Matching", ignoreCase = true) ||
                                                label.contains("Verifying", ignoreCase = true) ->
                                                com.example.ui.ScanStep.MATCHING_CATEGORY to label
                                            label.contains("Filtering", ignoreCase = true) ||
                                                label.contains("Enhancing", ignoreCase = true) ||
                                                label.contains("Finding", ignoreCase = true) ||
                                                label.contains("Read ", ignoreCase = true) ->
                                                com.example.ui.ScanStep.PARSING_DETAILS to label
                                            else ->
                                                com.example.ui.ScanStep.READING_FILE to label
                                        }
                                        val prefix = if (totalFiles > 1) "File ${fileIdx + 1}/$totalFiles · " else ""
                                        val remaining = synchronized(sequentialScanQueue) { sequentialScanQueue.size }
                                        _activeScanState.value = _activeScanState.value?.copy(
                                            currentStep = nextStep,
                                            stepDescription = "$prefix$friendlyStepText",
                                            queuedCount = remaining
                                        )
                                        val curUi = _scanState.value
                                        if (curUi is ScanUiState.Reading) {
                                            _scanState.value = curUi.copy(
                                                currentStep = fileIdx + 1,
                                                totalSteps = totalFiles,
                                                statusText = "$prefix$friendlyStepText"
                                            )
                                        }
                                    }
                                )
                                snapSpendPrefs.appendScanLogEntry(singleOutcome.logEntry)

                                // Stream review cards into accumulatedReviewDrafts as each file finishes
                                val r = singleOutcome.result
                                if (r is ExtractionResult.Success && r.drafts.isNotEmpty()) {
                                    val enriched = r.drafts.map { raw ->
                                        LocalInsightsAndRulesEngine.enrichAndResolveDraft(raw, existingTxs, memoryMap)
                                    }
                                    val combined = synchronized(accumulatedReviewDrafts) {
                                        val existingIds = accumulatedReviewDrafts.map { it.draftId }.toSet()
                                        enriched.forEach { d ->
                                            if (d.draftId !in existingIds) accumulatedReviewDrafts.add(d)
                                        }
                                        sortReviewDraftsForVerification(accumulatedReviewDrafts).also { sorted ->
                                            accumulatedReviewDrafts.clear()
                                            accumulatedReviewDrafts.addAll(sorted)
                                        }
                                    }
                                    _activeScanState.value = _activeScanState.value?.copy(
                                        resultDrafts = combined,
                                        failedUris = failedBatchUris.toList()
                                    )
                                    if (_scanState.value is ScanUiState.ReviewReady) {
                                        _scanState.value = ScanUiState.ReviewReady(
                                            drafts = combined,
                                            failedUris = failedBatchUris.toList()
                                        )
                                    }
                                } else if (r is ExtractionResult.Error || r is ExtractionResult.UnreadableOrNoPayments) {
                                    failedBatchUris.add(uri)
                                    _activeScanState.value = _activeScanState.value?.copy(
                                        failedUris = failedBatchUris.toList()
                                    )
                                }
                                uri to singleOutcome
                            }
                        }
                    }.awaitAll()
                }

                // Aggregate outcomes across the batch
                val passwordRequired = outcomes.firstOrNull { it.second.result is ExtractionResult.PdfPasswordRequired }
                if (passwordRequired != null) {
                    val pwRes = passwordRequired.second.result as ExtractionResult.PdfPasswordRequired
                    _pdfPasswordPrompt.value = PdfPasswordPromptState(
                        uri = passwordRequired.first,
                        allBatchUris = job.uris,
                        fileName = resolveFileName(appContext, passwordRequired.first),
                        isRetryError = pwRes.isInvalidPasswordAttempt || !job.pdfPassword.isNullOrBlank()
                    )
                    return pwRes
                }

                val rateLimited = outcomes.firstOrNull { it.second.result is ExtractionResult.RateLimited }
                if (rateLimited != null && outcomes.none { it.second.result is ExtractionResult.Success }) {
                    return rateLimited.second.result
                }

                val allDrafts = outcomes.mapNotNull { (it.second.result as? ExtractionResult.Success)?.drafts }.flatten()
                if (allDrafts.isNotEmpty()) {
                    return ExtractionResult.Success(allDrafts)
                }

                val skippedKnown = outcomes.firstOrNull { it.second.result is ExtractionResult.SkippedKnown }
                if (skippedKnown != null) {
                    return skippedKnown.second.result
                }

                val firstUnreadable = outcomes.firstOrNull { it.second.result is ExtractionResult.UnreadableOrNoPayments }
                if (firstUnreadable != null) {
                    return firstUnreadable.second.result
                }

                val firstErr = outcomes.firstOrNull { it.second.result is ExtractionResult.Error }
                firstErr?.second?.result ?: ExtractionResult.Error("Could not read receipt.")
            }
            job.samplePreset != null -> {
                val startSampleMs = android.os.SystemClock.elapsedRealtime()
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.READING_FILE,
                    stepDescription = "Reading amount..."
                )
                val categories = repository.getAllCategoriesOnce().map { it.name }
                val bitmap = withContext(Dispatchers.Default) {
                    aiExtractor.createSampleUpiScreenshotBitmap(job.samplePreset)
                }
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.PARSING_DETAILS,
                    stepDescription = "Finding date..."
                )
                val res = aiExtractor.analyzeBitmapWithGemini(
                    bitmap = bitmap,
                    allowedCategories = categories,
                    source = TransactionSource.SCREENSHOT
                )
                val totalMs = (android.os.SystemClock.elapsedRealtime() - startSampleMs).coerceAtLeast(1L)
                snapSpendPrefs.appendScanLogEntry(
                    ScanLogEntry(
                        readFileMs = 5L,
                        ocrTextMs = 0L,
                        regexMs = 0L,
                        geminiTextMs = 0L,
                        visionFallbackMs = totalMs,
                        totalMs = totalMs,
                        path = ScanPath.VISION_AI,
                        retries = 0,
                        promptTokens = 280,
                        outputTokens = 45
                    )
                )
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.MATCHING_CATEGORY,
                    stepDescription = "Matching category..."
                )
                res
            }
            !job.naturalLanguageNote.isNullOrBlank() -> {
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.PARSING_DETAILS,
                    stepDescription = "Extracting amount & date..."
                )
                val categories = repository.getAllCategoriesOnce()
                val noteResult = if (job.isSmartCashLog) {
                    aiExtractor.extractFromNaturalLanguageText(
                        userInput = job.naturalLanguageNote,
                        categories = categories,
                        defaultPaymentMode = "CASH",
                        defaultCashType = job.defaultCashType ?: TransactionType.EXPENSE
                    )
                } else {
                    aiExtractor.extractFromNaturalLanguageText(
                        userInput = job.naturalLanguageNote,
                        categories = categories
                    )
                }
                extractLogEntryFromResult(noteResult)?.let { snapSpendPrefs.appendScanLogEntry(it) }
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.MATCHING_CATEGORY,
                    stepDescription = "Matching category..."
                )
                noteResult
            }
            else -> ExtractionResult.Error("No input provided for scan.")
        }
    }

    private fun sortReviewDraftsForVerification(drafts: List<DraftTransaction>): List<DraftTransaction> {
        return drafts.sortedWith(
            compareBy<DraftTransaction> { it.isVerified } // false (needs check) first!
                .thenByDescending { it.isLowConfidence }
                .thenBy { it.isSkipped }
                .thenByDescending { it.timestamp }
        )
    }

    fun openReviewDeck() {
        val current = _activeScanState.value ?: return
        val drafts = current.resultDrafts
        if (!drafts.isNullOrEmpty()) {
            _scanState.value = ScanUiState.ReviewReady(
                drafts = drafts,
                failedUris = current.failedUris
            )
        }
    }

    fun retryFailedBatchUris(failedUris: List<Uri>) {
        if (failedUris.isEmpty()) return
        val curReview = _scanState.value as? ScanUiState.ReviewReady
        if (curReview != null) {
            _scanState.value = curReview.copy(failedUris = emptyList())
        }
        _activeScanState.value = _activeScanState.value?.copy(failedUris = emptyList())
        processSelectedUris(failedUris)
    }

    fun cancelActiveScan() {
        activeScanJob?.cancel()
        activeScanJob = null
        synchronized(sequentialScanQueue) {
            sequentialScanQueue.forEach { it.onCleanup?.invoke() }
            sequentialScanQueue.clear()
        }
        synchronized(accumulatedReviewDrafts) {
            accumulatedReviewDrafts.clear()
        }
        _activeScanState.value = null
        _scanState.value = ScanUiState.Idle
        ScanNotificationHelper.cancelScanNotification(getApplication())
    }

    fun dismissActiveScan() {
        val hasMoreQueued = synchronized(sequentialScanQueue) { sequentialScanQueue.isNotEmpty() }
        if (hasMoreQueued && activeScanJob?.isActive != true) {
            startSequentialQueueWorker()
            return
        }
        val existingDrafts = synchronized(accumulatedReviewDrafts) { accumulatedReviewDrafts.toList() }
        if (existingDrafts.isNotEmpty() && _activeScanState.value?.currentStep == com.example.ui.ScanStep.ERROR) {
            // Transition back to COMPLETE for previously succeeded drafts in the batch
            val first = existingDrafts.first()
            val count = existingDrafts.size
            val merchant = first.merchant.ifBlank { "Merchant" }
            val amountFormatted = MoneyUtils.formatPaise(first.amountPaise)
            val summaryDescription = if (count > 1) {
                "Scan Complete! $merchant / $amountFormatted (+${count - 1} more) ready to review"
            } else {
                "Scan Complete! $merchant / $amountFormatted ready to review"
            }
            _activeScanState.value = _activeScanState.value?.copy(
                currentStep = com.example.ui.ScanStep.COMPLETE,
                stepDescription = summaryDescription,
                resultDrafts = existingDrafts,
                errorMessage = null,
                queuedCount = 0
            )
            ScanNotificationHelper.postScanSuccessNotification(getApplication(), merchant, amountFormatted, count)
            return
        }
        synchronized(accumulatedReviewDrafts) {
            accumulatedReviewDrafts.clear()
        }
        _activeScanState.value = null
        ScanNotificationHelper.cancelScanNotification(getApplication())
    }

    fun retryActiveScan() {
        val current = _activeScanState.value ?: return
        val retryJob = PendingBackgroundScanJob(
            id = current.id,
            fileNameOrType = current.fileNameOrType,
            uris = current.recoverableUris,
            samplePreset = current.recoverableSamplePreset,
            naturalLanguageNote = current.recoverableNote,
            isSmartCashLog = current.isSmartCashLog,
            defaultCashType = current.defaultCashType
        )
        enqueueSequentialScanJob(retryJob, addToFront = true)
    }

    fun retryLastScan() {
        retryActiveScan()
    }

    fun queueCurrentFailedScan() {
        val current = _activeScanState.value
        val task = QueuedScanTask(
            uris = current?.recoverableUris ?: emptyList(),
            samplePreset = current?.recoverableSamplePreset,
            naturalLanguageNote = current?.recoverableNote,
            isSmartCashLog = current?.isSmartCashLog ?: false,
            defaultCashType = current?.defaultCashType
        )
        _queuedScans.value = _queuedScans.value + task
        dismissActiveScan()
        _scanState.value = ScanUiState.Idle
        _toastMessage.value = "Scan queued! Tap 'Retry queued scans' on Home whenever you're back online."
    }

    fun retryQueuedScans() {
        val pending = _queuedScans.value.firstOrNull() ?: return
        _queuedScans.value = _queuedScans.value.drop(1)
        when {
            pending.uris.isNotEmpty() -> processSelectedUris(pending.uris)
            pending.samplePreset != null -> scanSampleUpiScreenshotWithGemini(pending.samplePreset)
            !pending.naturalLanguageNote.isNullOrBlank() -> {
                if (pending.isSmartCashLog) {
                    processSmartCashLogWithGemini(
                        pending.naturalLanguageNote,
                        pending.defaultCashType ?: TransactionType.EXPENSE
                    )
                } else {
                    processNaturalLanguageNoteWithGemini(pending.naturalLanguageNote)
                }
            }
        }
    }

    private suspend fun handleBackgroundExtractionResult(
        result: ExtractionResult,
        lastUris: List<Uri>,
        lastSamplePreset: Int?,
        lastNote: String? = null,
        isSmartCashLog: Boolean = false,
        defaultCashType: TransactionType? = null
    ): Boolean {
        return when (result) {
            is ExtractionResult.Success -> {
                val existingTxs = repository.getAllTransactionsOnce()
                val memories = repository.getAllMerchantMemoriesOnce().associateBy { it.normalizedMerchant }
                val enriched = result.drafts.map { raw ->
                    LocalInsightsAndRulesEngine.enrichAndResolveDraft(raw, existingTxs, memories)
                }

                val combinedSorted = synchronized(accumulatedReviewDrafts) {
                    val existingIds = accumulatedReviewDrafts.map { it.draftId }.toSet()
                    enriched.forEach { d ->
                        if (d.draftId !in existingIds) {
                            accumulatedReviewDrafts.add(d)
                        }
                    }
                    sortReviewDraftsForVerification(accumulatedReviewDrafts).also { sortedList ->
                        accumulatedReviewDrafts.clear()
                        accumulatedReviewDrafts.addAll(sortedList)
                    }
                }

                val failedUris = _activeScanState.value?.failedUris ?: emptyList()

                // If the review deck is currently open, live-update it with the newly combined drafts
                if (_scanState.value is ScanUiState.ReviewReady) {
                    _scanState.value = ScanUiState.ReviewReady(
                        drafts = combinedSorted,
                        failedUris = failedUris
                    )
                }

                val remainingQueued = synchronized(sequentialScanQueue) { sequentialScanQueue.size }
                if (remainingQueued > 0) {
                    false
                } else {
                    val first = combinedSorted.firstOrNull()
                    val count = combinedSorted.size
                    val merchant = first?.merchant?.ifBlank { "Merchant" } ?: "Merchant"
                    val amountFormatted = first?.let { MoneyUtils.formatPaise(it.amountPaise) } ?: "₹0"
                    val summaryDescription = if (count > 1) {
                        "Scan Complete! $merchant / $amountFormatted (+${count - 1} more) ready to review"
                    } else {
                        "Scan Complete! $merchant / $amountFormatted ready to review"
                    }

                    _activeScanState.value = _activeScanState.value?.copy(
                        currentStep = com.example.ui.ScanStep.COMPLETE,
                        stepDescription = summaryDescription,
                        resultDrafts = combinedSorted,
                        errorMessage = null,
                        queuedCount = 0
                    )
                    if (_queuedScans.value.isEmpty()) {
                        ShareIntentHelper.cleanupActiveShareTempFiles()
                    }
                    ScanNotificationHelper.postScanSuccessNotification(
                        getApplication(),
                        merchant,
                        amountFormatted,
                        count
                    )
                    false
                }
            }
            is ExtractionResult.SkippedKnown -> {
                _skippedKnownBanner.value = SkippedKnownBannerState(
                    existingTransactionId = result.existingTransactionId,
                    amountPaise = result.amountPaise,
                    merchant = result.merchant,
                    dateFormatted = result.dateFormatted
                )
                val existingDrafts = synchronized(accumulatedReviewDrafts) { accumulatedReviewDrafts.toList() }
                if (existingDrafts.isEmpty()) {
                    _activeScanState.value = null
                    _scanState.value = ScanUiState.Idle
                    ScanNotificationHelper.cancelScanNotification(getApplication())
                }
                false
            }
            is ExtractionResult.PdfPasswordRequired -> {
                _activeScanState.value = null
                _scanState.value = ScanUiState.Idle
                ScanNotificationHelper.cancelScanNotification(getApplication())
                true
            }
            is ExtractionResult.RateLimited -> {
                val task = QueuedScanTask(
                    uris = lastUris,
                    samplePreset = lastSamplePreset,
                    naturalLanguageNote = lastNote,
                    isSmartCashLog = isSmartCashLog,
                    defaultCashType = defaultCashType
                )
                _queuedScans.value = _queuedScans.value + task
                _activeScanState.value = null
                _scanState.value = ScanUiState.Idle
                ScanNotificationHelper.cancelScanNotification(getApplication())
                _toastMessage.value = result.message
                true
            }
            is ExtractionResult.UnreadableOrNoPayments -> {
                val remaining = synchronized(sequentialScanQueue) { sequentialScanQueue.size }
                val existingDrafts = synchronized(accumulatedReviewDrafts) {
                    accumulatedReviewDrafts.toList().takeIf { it.isNotEmpty() }
                }
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.ERROR,
                    stepDescription = "Scan interrupted. File is safe.",
                    errorMessage = result.reason,
                    resultDrafts = existingDrafts,
                    queuedCount = remaining,
                    recoverableUris = lastUris,
                    recoverableSamplePreset = lastSamplePreset,
                    recoverableNote = lastNote,
                    isSmartCashLog = isSmartCashLog,
                    defaultCashType = defaultCashType
                )
                ScanNotificationHelper.postScanErrorNotification(getApplication())
                true
            }
            is ExtractionResult.Error -> {
                val remaining = synchronized(sequentialScanQueue) { sequentialScanQueue.size }
                val existingDrafts = synchronized(accumulatedReviewDrafts) {
                    accumulatedReviewDrafts.toList().takeIf { it.isNotEmpty() }
                }
                _activeScanState.value = _activeScanState.value?.copy(
                    currentStep = com.example.ui.ScanStep.ERROR,
                    stepDescription = "Scan interrupted. File is safe.",
                    errorMessage = result.message,
                    resultDrafts = existingDrafts,
                    queuedCount = remaining,
                    recoverableUris = lastUris,
                    recoverableSamplePreset = lastSamplePreset,
                    recoverableNote = lastNote,
                    isSmartCashLog = isSmartCashLog,
                    defaultCashType = defaultCashType
                )
                ScanNotificationHelper.postScanErrorNotification(getApplication())
                true
            }
        }
    }

    fun applyDraftEqualSplit(draftId: String, ways: Int) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        val target = current.drafts.firstOrNull { it.draftId == draftId } ?: return
        updateReviewDraft(target.applyEqualSplit(ways), categoryManuallyChanged = false)
    }

    fun toggleDraftLineItem(draftId: String, itemIndex: Int) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        val target = current.drafts.firstOrNull { it.draftId == draftId } ?: return
        updateReviewDraft(target.toggleLineItemSelection(itemIndex), categoryManuallyChanged = false)
    }

    fun updateReviewDraft(updatedDraft: DraftTransaction, categoryManuallyChanged: Boolean = false) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        val normMerchant = MoneyUtils.normalizeMerchant(updatedDraft.merchant)

        viewModelScope.launch {
            if (categoryManuallyChanged && updatedDraft.type != TransactionType.TRANSFER) {
                repository.saveMerchantMemoryRule(updatedDraft.merchant, updatedDraft.category)
            }
            val existingTxs = repository.getAllTransactionsOnce()
            val memories = repository.getAllMerchantMemoriesOnce().associateBy { it.normalizedMerchant }

            val updatedList = current.drafts.map { item ->
                if (item.draftId == updatedDraft.draftId) {
                    val reResolved = LocalInsightsAndRulesEngine.enrichAndResolveDraft(
                        rawDraft = updatedDraft,
                        existingTransactions = existingTxs,
                        merchantMemoryMap = memories
                    )
                    reResolved.copy(
                        isSkipped = updatedDraft.isSkipped,
                        categoryWasManuallyEdited = item.categoryWasManuallyEdited || categoryManuallyChanged
                    )
                } else if (categoryManuallyChanged &&
                    MoneyUtils.normalizeMerchant(item.merchant) == normMerchant &&
                    item.type != TransactionType.TRANSFER
                ) {
                    // Apply learned merchant category to other drafts from the same merchant in this batch
                    item.copy(
                        category = updatedDraft.category,
                        appliedMerchantMemory = true
                    )
                } else {
                    item
                }
            }
            synchronized(accumulatedReviewDrafts) {
                accumulatedReviewDrafts.clear()
                accumulatedReviewDrafts.addAll(updatedList)
            }
            _scanState.value = ScanUiState.ReviewReady(updatedList, current.failedUris)
            _activeScanState.value = _activeScanState.value?.copy(resultDrafts = updatedList)
        }
    }

    fun toggleDraftSkipped(draftId: String) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        val updated = current.drafts.map {
            if (it.draftId == draftId) it.copy(isSkipped = !it.isSkipped) else it
        }
        synchronized(accumulatedReviewDrafts) {
            accumulatedReviewDrafts.clear()
            accumulatedReviewDrafts.addAll(updated)
        }
        _scanState.value = ScanUiState.ReviewReady(updated, current.failedUris)
        _activeScanState.value = _activeScanState.value?.copy(resultDrafts = updated)
    }

    /**
     * One-tap "CONFIRM ALL VERIFIED (X)" action:
     * - Commits all non-skipped drafts that passed every local verification check (`draft.isVerified`).
     * - Records their SHA-256 text hashes so re-scanning the same receipt triggers `SKIPPED_KNOWN`.
     * - Displays the 6-second Aqua UNDO snackbar so the user can undo the batch in 1 tap.
     * - If there are unverified drafts remaining in the review deck, keeps only those unverified drafts open.
     */
    fun confirmAllVerifiedReviewDrafts(onComplete: () -> Unit = {}) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        val verifiedToCommit = current.drafts.filter { !it.isSkipped && it.isVerified }
        if (verifiedToCommit.isEmpty()) return
        val remainingUnverified = current.drafts.filter { !(!it.isSkipped && it.isVerified) }

        viewModelScope.launch {
            val summary = commitDraftSubsetAndAdjustCashWallet(verifiedToCommit)
            recordConfirmedScanHashesForDrafts(verifiedToCommit, summary.insertedIds)

            if (remainingUnverified.isEmpty() || remainingUnverified.all { it.isSkipped }) {
                synchronized(accumulatedReviewDrafts) {
                    accumulatedReviewDrafts.clear()
                }
                _scanState.value = ScanUiState.Idle
                if (activeScanJob?.isActive != true) {
                    _activeScanState.value = null
                    ScanNotificationHelper.cancelScanNotification(getApplication())
                }
                if (_queuedScans.value.isEmpty()) {
                    ShareIntentHelper.cleanupActiveShareTempFiles()
                }
                onComplete()
            } else {
                synchronized(accumulatedReviewDrafts) {
                    accumulatedReviewDrafts.clear()
                    accumulatedReviewDrafts.addAll(remainingUnverified)
                }
                _scanState.value = ScanUiState.ReviewReady(remainingUnverified, current.failedUris)
                _activeScanState.value = _activeScanState.value?.copy(resultDrafts = remainingUnverified)
            }

            if (summary.insertedIds.isNotEmpty()) {
                _undoBannerState.value = UndoBannerState(
                    actionToken = ++undoActionTokenCounter,
                    message = "Confirmed ${summary.insertedIds.size} verified payment${if (summary.insertedIds.size == 1) "" else "s"}",
                    transactionId = summary.insertedIds.first(),
                    kind = UndoActionKind.QUICK_CASH_BATCH_DELETE,
                    transactionIds = summary.insertedIds
                )
            } else {
                _toastMessage.value = "${verifiedToCommit.size} verified updated!"
            }
        }
    }

    fun confirmAllReviewDrafts(onComplete: () -> Unit = {}) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        viewModelScope.launch {
            val summary = commitDraftSubsetAndAdjustCashWallet(current.drafts)
            recordConfirmedScanHashesForDrafts(
                drafts = current.drafts.filter { !it.isSkipped },
                insertedIds = summary.insertedIds
            )
            synchronized(accumulatedReviewDrafts) {
                accumulatedReviewDrafts.clear()
            }
            _scanState.value = ScanUiState.Idle
            if (activeScanJob?.isActive != true) {
                _activeScanState.value = null
                ScanNotificationHelper.cancelScanNotification(getApplication())
            }
            if (_queuedScans.value.isEmpty()) {
                ShareIntentHelper.cleanupActiveShareTempFiles()
            }
            if (summary.insertedIds.isNotEmpty()) {
                _undoBannerState.value = UndoBannerState(
                    actionToken = ++undoActionTokenCounter,
                    message = "Saved ${summary.insertedCount} payment${if (summary.insertedCount == 1) "" else "s"}",
                    transactionId = summary.insertedIds.first(),
                    kind = UndoActionKind.QUICK_CASH_BATCH_DELETE,
                    transactionIds = summary.insertedIds
                )
            } else {
                val msgParts = mutableListOf<String>()
                if (summary.insertedCount > 0) msgParts.add("${summary.insertedCount} saved")
                if (summary.updatedPendingCount > 0) msgParts.add("${summary.updatedPendingCount} pending updated to final")
                if (summary.skippedDuplicateCount > 0) msgParts.add("${summary.skippedDuplicateCount} skipped")
                _toastMessage.value = msgParts.joinToString(" · ").ifEmpty { "All set!" }
            }
            onComplete()
        }
    }

    private suspend fun commitDraftSubsetAndAdjustCashWallet(
        drafts: List<DraftTransaction>
    ): com.example.data.repository.ImportSummary {
        var netCashWalletDelta = 0L
        for (draft in drafts) {
            if (draft.isSkipped) continue
            val isAtm = draft.isAtmWithdrawal ||
                LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(draft.merchant) ||
                LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(draft.rawMerchantText) ||
                LocalInsightsAndRulesEngine.isAtmCashWithdrawalText(draft.note)
            val normalizedMode = if (isAtm || draft.paymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
            val draftAsTx = TransactionEntity(
                id = draft.existingMatchId ?: 0L,
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
                duplicateKey = draft.duplicateKey,
                paymentMode = normalizedMode,
                isAtmWithdrawal = isAtm
            )
            val newDelta = LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(draftAsTx)
            val prevDelta = if (draft.resolutionMode == ResolutionMode.UPDATE_PENDING_TO_FINAL && draft.existingMatchId != null) {
                repository.getTransactionById(draft.existingMatchId)?.let {
                    LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it)
                } ?: 0L
            } else {
                0L
            }
            netCashWalletDelta += (newDelta - prevDelta)
        }

        val summary = repository.commitConfirmedDrafts(drafts)
        if (netCashWalletDelta != 0L) {
            snapSpendPrefs.adjustCashWalletBalancePaise(netCashWalletDelta)
        }
        return summary
    }

    private suspend fun recordConfirmedScanHashesForDrafts(
        drafts: List<DraftTransaction>,
        insertedIds: List<Long>
    ) {
        val groupedByHash = drafts.filter { !it.scanSha256Hash.isNullOrBlank() }.groupBy { it.scanSha256Hash!! }
        val firstInsertedId = insertedIds.firstOrNull()
        for ((hash, list) in groupedByHash) {
            val first = list.first()
            snapSpendPrefs.recordConfirmedScanHash(
                ConfirmedScanRecord(
                    sha256Hash = hash,
                    transactionId = (firstInsertedId ?: first.existingMatchId) ?: 0L,
                    amountPaise = first.amountPaise,
                    merchant = first.merchant,
                    dateFormatted = MoneyUtils.formatDayHeader(first.timestamp)
                )
            )
        }
    }

    fun dismissScanFlow() {
        synchronized(accumulatedReviewDrafts) {
            accumulatedReviewDrafts.clear()
        }
        _scanState.value = ScanUiState.Idle
        if (activeScanJob?.isActive != true) {
            _activeScanState.value = null
            ScanNotificationHelper.cancelScanNotification(getApplication())
        }
        if (_queuedScans.value.isEmpty()) {
            ShareIntentHelper.cleanupActiveShareTempFiles()
        }
    }

    // --- Manual / Edit / Soft-Delete / Trash / Quick Repeat Transaction Actions ---
    fun saveManualOrEditedTransaction(
        transaction: TransactionEntity,
        learnMerchantCategory: Boolean
    ) {
        viewModelScope.launch {
            val previousTx = if (transaction.id != 0L) repository.getTransactionById(transaction.id) else null
            val savedId = repository.saveTransaction(transaction, learnMerchantCategory)
            val savedTx = repository.getTransactionById(savedId)
            val prevDelta = previousTx?.let { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) } ?: 0L
            val newDelta = savedTx?.let { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) } ?: 0L
            if (newDelta - prevDelta != 0L) {
                snapSpendPrefs.adjustCashWalletBalancePaise(newDelta - prevDelta)
            }
            _toastMessage.value = if (transaction.id == 0L) "Payment added!" else "Payment updated!"
        }
    }

    /**
     * Soft-deletes a payment (`deletedAtMillis` set) and displays the 6-second Aqua UNDO bar.
     * Supersedes any previous active UNDO banner so the prior action stays committed.
     */
    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            val existing = repository.getTransactionById(id)
            repository.softDeleteTransaction(id, System.currentTimeMillis())
            if (existing != null) {
                val delta = LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(existing)
                if (delta != 0L) {
                    snapSpendPrefs.adjustCashWalletBalancePaise(-delta)
                }
            }
            _undoBannerState.value = UndoBannerState(
                actionToken = ++undoActionTokenCounter,
                message = "Payment deleted",
                transactionId = id,
                kind = UndoActionKind.SOFT_DELETE_RESTORE
            )
        }
    }

    /**
     * Adds a payment immediately from a Quick Repeat chip (same merchant, category, and amount,
     * source = MANUAL), disables that chip for 3 seconds, and shows the 6-second Aqua UNDO bar.
     */
    fun tapQuickRepeatChip(chip: QuickRepeatChip) {
        if (chip.key in _disabledQuickRepeatKeys.value) return
        _disabledQuickRepeatKeys.value = _disabledQuickRepeatKeys.value + chip.key
        viewModelScope.launch {
            delay(3000L)
            _disabledQuickRepeatKeys.value = _disabledQuickRepeatKeys.value - chip.key
        }
        viewModelScope.launch {
            val nowMillis = System.currentTimeMillis()
            val normalizedMode = if (chip.paymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
            val newTx = TransactionEntity(
                id = 0L,
                timestamp = nowMillis,
                hasExplicitTime = true,
                amountPaise = chip.amountPaise,
                type = TransactionType.EXPENSE,
                merchant = chip.merchant,
                category = chip.category,
                paymentApp = chip.paymentApp,
                status = com.example.data.local.TransactionStatus.SUCCESS,
                note = "",
                createdAt = nowMillis,
                source = TransactionSource.MANUAL,
                paymentMode = normalizedMode
            )
            val insertedId = repository.saveTransaction(newTx, learnMerchantCategory = false)
            val savedTx = repository.getTransactionById(insertedId)
            val delta = savedTx?.let { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) } ?: 0L
            if (delta != 0L) {
                snapSpendPrefs.adjustCashWalletBalancePaise(delta)
            }
            _undoBannerState.value = UndoBannerState(
                actionToken = ++undoActionTokenCounter,
                message = "Added ${chip.label}",
                transactionId = insertedId,
                kind = UndoActionKind.QUICK_REPEAT_HARD_DELETE
            )
        }
    }

    fun performUndo() {
        val current = _undoBannerState.value ?: return
        _undoBannerState.value = null
        viewModelScope.launch {
            when (current.kind) {
                UndoActionKind.SOFT_DELETE_RESTORE -> {
                    repository.restoreTransaction(current.transactionId)
                    val restored = repository.getTransactionById(current.transactionId)
                    val delta = restored?.let { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) } ?: 0L
                    if (delta != 0L) {
                        snapSpendPrefs.adjustCashWalletBalancePaise(delta)
                    }
                    _toastMessage.value = "Payment restored."
                }
                UndoActionKind.QUICK_REPEAT_HARD_DELETE -> {
                    val existing = repository.getTransactionById(current.transactionId)
                    val delta = existing?.let { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) } ?: 0L
                    repository.permanentlyDeleteTransaction(current.transactionId)
                    if (delta != 0L) {
                        snapSpendPrefs.adjustCashWalletBalancePaise(-delta)
                    }
                    _toastMessage.value = "Quick payment undone."
                }
                UndoActionKind.QUICK_CASH_BATCH_DELETE -> {
                    var totalReversedDelta = 0L
                    for (id in current.transactionIds) {
                        val existing = repository.getTransactionById(id)
                        if (existing != null) {
                            totalReversedDelta -= LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(existing)
                        }
                        repository.permanentlyDeleteTransaction(id)
                    }
                    if (totalReversedDelta != 0L) {
                        snapSpendPrefs.adjustCashWalletBalancePaise(totalReversedDelta)
                    }
                    _toastMessage.value = "Quick Cash log undone."
                }
            }
        }
    }

    fun dismissUndoBanner(actionToken: Long) {
        if (_undoBannerState.value?.actionToken == actionToken) {
            _undoBannerState.value = null
        }
    }

    fun restoreFromTrash(id: Long) {
        viewModelScope.launch {
            repository.restoreTransaction(id)
            val restored = repository.getTransactionById(id)
            val delta = restored?.let { LocalInsightsAndRulesEngine.computeCashWalletDeltaForTransaction(it) } ?: 0L
            if (delta != 0L) {
                snapSpendPrefs.adjustCashWalletBalancePaise(delta)
            }
            _toastMessage.value = "Payment restored from Trash."
        }
    }

    fun deleteForeverFromTrash(id: Long) {
        viewModelScope.launch {
            repository.permanentlyDeleteTransaction(id)
            _toastMessage.value = "Deleted forever."
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
            _toastMessage.value = "Trash emptied."
        }
    }

    // --- Budgets, Categories & Repeating Payments ---
    fun saveBudget(categoryName: String, limitPaise: Long) {
        viewModelScope.launch {
            repository.setCategoryBudget(categoryName, limitPaise)
            _toastMessage.value = if (limitPaise > 0L) {
                "Budget for $categoryName set to ${MoneyUtils.formatPaise(limitPaise)}."
            } else {
                "Budget removed for $categoryName."
            }
        }
    }

    fun updateRepeatingState(id: String, state: RepeatingState) {
        viewModelScope.launch {
            repository.updateRepeatingPaymentState(id, state)
            _toastMessage.value = if (state == RepeatingState.CONFIRMED) {
                "Repeating payment confirmed."
            } else {
                "Repeating payment dismissed."
            }
        }
    }

    fun saveCategory(
        id: Long = 0L,
        oldName: String? = null,
        newName: String,
        type: CategoryType,
        colorHex: String,
        iconName: String,
        isDefault: Boolean = false
    ) {
        viewModelScope.launch {
            repository.addOrUpdateCategory(id, oldName, newName, type, colorHex, iconName, isDefault)
            _toastMessage.value = "Category '$newName' saved."
        }
    }

    fun deleteCustomCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCustomCategory(category)
            _toastMessage.value = "Category '${category.name}' removed."
        }
    }

    fun deleteMerchantMemory(normalizedMerchant: String) {
        viewModelScope.launch {
            repository.deleteMerchantMemory(normalizedMerchant)
            _toastMessage.value = "Removed learned rule for $normalizedMerchant"
        }
    }

    // --- Google Sign-In, Drive Backup & Auto-Restore ---
    fun connectGoogleAccount(activity: Activity) {
        viewModelScope.launch {
            _isBackupBusy.value = true
            val res = backupManager.signInWithGoogleInteractive(activity)
            _isBackupBusy.value = false
            res.onSuccess { email ->
                _backupState.value = backupManager.getBackupState()
                _toastMessage.value = "Connected as $email"
                // After sign-in, check hidden Drive folder for an existing backup to auto-restore
                if (backupManager.shouldPromptAutoRestoreForCurrentAccount()) {
                    checkForDriveBackupToRestore(silentIfNone = true)
                }
            }.onFailure { err ->
                if (err !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    _toastMessage.value = err.localizedMessage ?: "Google Sign-In failed."
                }
            }
        }
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            backupManager.disconnectGoogleAccount()
            _backupState.value = backupManager.getBackupState()
            _restoreOffer.value = null
            _toastMessage.value = "Google account disconnected."
        }
    }

    fun triggerDriveBackupNow() {
        viewModelScope.launch {
            if (_backupState.value.connectedEmail.isNullOrBlank()) {
                _toastMessage.value = "Connect your Google account first to back up to Drive."
                return@launch
            }
            _isBackupBusy.value = true
            pendingDriveAction = PendingDriveAction.BACKUP_NOW
            when (val auth = backupManager.authorizeDriveAppData()) {
                is DriveAuthResult.Authorized -> {
                    executeBackupWithToken(auth.accessToken)
                }
                is DriveAuthResult.NeedsConsentResolution -> {
                    _isBackupBusy.value = false
                    _pendingConsentIntentSender.value = auth.intentSender
                }
                is DriveAuthResult.Error -> {
                    _isBackupBusy.value = false
                    _toastMessage.value = auth.message
                }
            }
        }
    }

    fun checkForDriveBackupToRestore(silentIfNone: Boolean = false) {
        viewModelScope.launch {
            if (_backupState.value.connectedEmail.isNullOrBlank()) {
                if (!silentIfNone) {
                    _toastMessage.value = "Connect your Google account first to restore from Drive."
                }
                return@launch
            }
            _isBackupBusy.value = true
            pendingDriveAction = PendingDriveAction.CHECK_OR_RESTORE
            when (val auth = backupManager.authorizeDriveAppData()) {
                is DriveAuthResult.Authorized -> {
                    val metaRes = backupManager.findLatestDriveBackupMetadata(auth.accessToken)
                    _isBackupBusy.value = false
                    metaRes.onSuccess { metadata ->
                        backupManager.markRestorePromptShownForCurrentAccount()
                        if (metadata != null) {
                            _restoreOffer.value = metadata
                        } else if (!silentIfNone) {
                            _toastMessage.value = "No backup file found in your hidden Drive folder yet."
                        }
                    }.onFailure { err ->
                        if (!silentIfNone) {
                            _toastMessage.value = err.localizedMessage ?: "Couldn't check Drive backup."
                        }
                    }
                }
                is DriveAuthResult.NeedsConsentResolution -> {
                    _isBackupBusy.value = false
                    _pendingConsentIntentSender.value = auth.intentSender
                }
                is DriveAuthResult.Error -> {
                    _isBackupBusy.value = false
                    if (!silentIfNone) {
                        _toastMessage.value = auth.message
                    }
                }
            }
        }
    }

    fun onDriveConsentResult(granted: Boolean) {
        _pendingConsentIntentSender.value = null
        if (!granted) {
            _toastMessage.value = "Drive permission wasn't granted."
            return
        }
        when (pendingDriveAction) {
            PendingDriveAction.BACKUP_NOW -> triggerDriveBackupNow()
            PendingDriveAction.CHECK_OR_RESTORE -> checkForDriveBackupToRestore(silentIfNone = false)
            PendingDriveAction.NONE -> Unit
        }
    }

    fun confirmRestoreFromDrive(metadata: DriveBackupFileMetadata, replaceEverything: Boolean) {
        viewModelScope.launch {
            _restoreOffer.value = null
            _isBackupBusy.value = true
            when (val auth = backupManager.authorizeDriveAppData()) {
                is DriveAuthResult.Authorized -> {
                    val downloadRes = backupManager.downloadDriveBackupJson(auth.accessToken, metadata.fileId)
                    downloadRes.onSuccess { json ->
                        val summary = repository.importFromBackupJson(json, replaceEverything = replaceEverything)
                        _isBackupBusy.value = false
                        _toastMessage.value = "Restored ${summary.insertedCount} transactions from Drive (${metadata.formattedDate})."
                    }.onFailure { err ->
                        _isBackupBusy.value = false
                        _toastMessage.value = err.localizedMessage ?: "Failed to restore backup from Drive."
                    }
                }
                else -> {
                    _isBackupBusy.value = false
                    _toastMessage.value = "Please authorize Drive access and try again."
                }
            }
        }
    }

    fun dismissRestoreOffer() {
        _restoreOffer.value = null
    }

    private suspend fun executeBackupWithToken(accessToken: String) {
        val res = backupManager.performSafeOverwriteDriveBackup(accessToken, repository)
        _isBackupBusy.value = false
        res.onSuccess { snapshot ->
            _backupState.value = snapshot
            _toastMessage.value = "Backed up safely to Google Drive hidden folder!"
        }.onFailure { err ->
            _backupState.value = backupManager.getBackupState()
            _toastMessage.value = err.localizedMessage ?: "Backup failed. Your previous Drive backup is still safe."
        }
    }

    // --- Local File Export & Import ---
    fun exportJsonToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = repository.exportFullBackupJson()
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                _toastMessage.value = "Backup file exported!"
            } catch (e: Exception) {
                _toastMessage.value = "Export failed: ${e.localizedMessage}"
            }
        }
    }

    fun exportCsvToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csv = repository.exportTransactionsCsv()
                appContext.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(csv.toByteArray(Charsets.UTF_8))
                }
                _toastMessage.value = "CSV spreadsheet exported!"
            } catch (e: Exception) {
                _toastMessage.value = "CSV export failed: ${e.localizedMessage}"
            }
        }
    }

    fun importJsonFromUri(uri: Uri, replaceEverything: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = appContext.contentResolver.openInputStream(uri)?.use { input ->
                    input.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException("Could not read selected file.")

                val trimmed = content.trimStart()
                val isJson = trimmed.startsWith("{")
                val summary = if (isJson) {
                    repository.importFromBackupJson(content, replaceEverything = replaceEverything)
                } else {
                    repository.importTransactionsCsv(content, replaceEverything = replaceEverything)
                }
                val modeText = if (replaceEverything) "Replaced all data" else "Merged backup"
                _toastMessage.value = "$modeText: ${summary.insertedCount} added, ${summary.updatedPendingCount} updated, ${summary.skippedDuplicateCount} duplicates skipped."
            } catch (e: Exception) {
                _toastMessage.value = "Import failed: Make sure you selected a valid SnapSpend JSON or CSV backup."
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

/**
 * Computes the complete DataCheckSnapshot using dedicated plain SQL SUM and COUNT queries
 * straight from TransactionDao—never reusing Home, Charts, or Insights helper functions.
 * Uses the exact same local-timezone calendar month bounds (00:00:00.000 to 23:59:59.999)
 * as the Home screen so boundary payments at midnight never trigger a false MISMATCH.
 */
suspend fun loadDataCheckSnapshotFromSql(
    dao: com.example.data.local.TransactionDao,
    nowMillis: Long = System.currentTimeMillis(),
    scanTimingLogs: List<ScanLogEntry> = emptyList()
): DataCheckSnapshot {
    val totalActive = dao.getDataCheckTotalActiveCount()
    val successCount = dao.getDataCheckSuccessCount()
    val pendingCount = dao.getDataCheckPendingCount()
    val failedCount = dao.getDataCheckFailedCount()
    val transferCount = dao.getDataCheckTransferCount()
    val trashedCount = dao.getDataCheckTrashedCount()
    val onlineActiveCount = dao.getDataCheckOnlineActiveCount()
    val cashActiveCount = dao.getDataCheckCashActiveCount()

    val (thisMonthStart, thisMonthEnd) = computeDataCheckMonthBounds(nowMillis, 0)
    val thisMonthSpentSql = dao.getDataCheckMonthMoneyOutPaise(thisMonthStart, thisMonthEnd)

    val monthLabelFormat = SimpleDateFormat("MMM yyyy", Locale.US)
    val monthlyRows = (0 downTo -11).map { monthOffset ->
        val (mStart, mEnd) = computeDataCheckMonthBounds(nowMillis, monthOffset)
        val label = monthLabelFormat.format(Date(mStart))
        val moneyIn = dao.getDataCheckMonthMoneyInPaise(mStart, mEnd)
        val moneyOut = dao.getDataCheckMonthMoneyOutPaise(mStart, mEnd)
        val paymentCount = dao.getDataCheckMonthPaymentCount(mStart, mEnd)
        DataCheckMonthRow(
            monthLabel = label,
            startMillis = mStart,
            endMillis = mEnd,
            moneyInPaise = moneyIn,
            moneyOutPaise = moneyOut,
            paymentCount = paymentCount
        )
    }

    val last10Rows = dao.getDataCheckLast10SavedRows()

    return DataCheckSnapshot(
        totalActiveCount = totalActive,
        successCount = successCount,
        pendingCount = pendingCount,
        failedCount = failedCount,
        transferCount = transferCount,
        trashedCount = trashedCount,
        onlineActiveCount = onlineActiveCount,
        cashActiveCount = cashActiveCount,
        thisMonthSpentSqlPaise = thisMonthSpentSql,
        monthlyRows = monthlyRows,
        last10SavedRows = last10Rows,
        scanTimingLogs = scanTimingLogs
    )
}

