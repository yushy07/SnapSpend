package com.example.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.ExtractionResult
import com.example.data.ai.GeminiReceiptExtractor
import com.example.data.backup.BackupStateSnapshot
import com.example.data.backup.DriveAuthResult
import com.example.data.backup.DriveBackupAndExportManager
import com.example.data.backup.DriveBackupFileMetadata
import com.example.data.local.BudgetEntity
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.MerchantMemoryEntity
import com.example.data.local.RepeatingPaymentEntity
import com.example.data.local.RepeatingState
import com.example.data.local.SnapSpendDatabase
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
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
import com.example.data.rules.TopMerchantItem
import com.example.ui.theme.ThemeMode
import com.example.util.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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

data class QueuedScanTask(
    val uris: List<Uri> = emptyList(),
    val samplePreset: Int? = null,
    val naturalLanguageNote: String? = null,
    val queuedAtMillis: Long = System.currentTimeMillis()
)

sealed interface ScanUiState {
    data object Idle : ScanUiState
    data class Reading(val currentStep: Int, val totalSteps: Int, val statusText: String) : ScanUiState
    data class ReviewReady(val drafts: List<DraftTransaction>) : ScanUiState
    data class Unreadable(
        val reason: String,
        val lastUris: List<Uri> = emptyList(),
        val lastSamplePreset: Int? = null,
        val lastNaturalLanguageNote: String? = null
    ) : ScanUiState
    data class Error(
        val message: String,
        val recoverableUris: List<Uri> = emptyList(),
        val recoverableSamplePreset: Int? = null,
        val recoverableNaturalLanguageNote: String? = null
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
    val selectedPaymentApp: String? = null
)

data class ChartControlState(
    val chartDateRange: DateRangeFilter = DateRangeFilter.THIS_MONTH,
    val lineGranularity: String = "DAILY" // "DAILY", "WEEKLY", "MONTHLY"
)

data class SnapSpendUiState(
    val allTransactions: List<TransactionEntity> = emptyList(),
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
    val thisMonthCategorySlices: List<CategorySlice> = emptyList(),
    val last5Transactions: List<TransactionEntity> = emptyList(),
    val availablePaymentApps: List<String> = emptyList(),
    // Charts & Local Insights
    val chartDateRange: DateRangeFilter = DateRangeFilter.THIS_MONTH,
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
    private val database = SnapSpendDatabase.getInstance(appContext)
    val repository = SnapSpendRepository(database)
    private val aiExtractor = GeminiReceiptExtractor(appContext)
    private val backupManager = DriveBackupAndExportManager(appContext)

    private val _themeMode = MutableStateFlow(loadPersistedThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _queuedScans = MutableStateFlow<List<QueuedScanTask>>(emptyList())
    val queuedScans: StateFlow<List<QueuedScanTask>> = _queuedScans.asStateFlow()

    private val _filterState = MutableStateFlow(FilterState())
    val filterState: StateFlow<FilterState> = _filterState.asStateFlow()

    private val _chartControl = MutableStateFlow(ChartControlState())
    val chartControl: StateFlow<ChartControlState> = _chartControl.asStateFlow()

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState.asStateFlow()

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

    private var pendingDriveAction: PendingDriveAction = PendingDriveAction.NONE

    init {
        viewModelScope.launch {
            repository.ensureDefaultCategoriesSeeded()
            repository.refreshRepeatingPayments()
            val silentEmail = backupManager.attemptSilentSignIn()
            _backupState.value = backupManager.getBackupState()
            if (!silentEmail.isNullOrBlank()) {
                DriveBackupAndExportManager.scheduleWeeklyBackupWork(appContext)
            }
        }
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

    private val coreDataFlow = combine(
        repository.transactionsFlow,
        repository.categoriesFlow,
        repository.merchantMemoriesFlow,
        repository.budgetsFlow,
        repository.repeatingPaymentsFlow
    ) { txs, cats, memories, budgets, repeating ->
        Quintuple(txs, cats, memories, budgets, repeating)
    }

    val uiState: StateFlow<SnapSpendUiState> = combine(
        coreDataFlow,
        _filterState,
        _chartControl,
        _queuedScans
    ) { (allTxs, categories, memories, budgets, repeating), filters, chartCtrl, queuedList ->
        val now = System.currentTimeMillis()
        val (thisMonthStart, thisMonthEnd) = LocalInsightsAndRulesEngine.getMonthBounds(now, 0)

        val thisMonthTxs = allTxs.filter { it.timestamp in thisMonthStart..thisMonthEnd }
        val thisMonthValid = LocalInsightsAndRulesEngine.successfulTransactions(thisMonthTxs)

        val thisMonthSpent = thisMonthValid
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountPaise }
        val thisMonthEarned = thisMonthValid
            .filter { it.type == TransactionType.EARNING }
            .sumOf { it.amountPaise }
        val thisMonthLeft = thisMonthEarned - thisMonthSpent

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

        // Charts & Insights calculations (100% local integer paise math)
        val chartRangeTxs = filterByDateRange(allTxs, chartCtrl.chartDateRange, now)
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
            val linePts = LocalInsightsAndRulesEngine.buildLineChartPoints(allTxs, chartCtrl.lineGranularity, now)
            linePts.map { it.label to it.amountPaise }
        }
        val editorialPillBars = LocalInsightsAndRulesEngine.buildPillBars(pillBuckets)

        val linePoints = LocalInsightsAndRulesEngine.buildLineChartPoints(
            allTransactions = allTxs,
            granularity = chartCtrl.lineGranularity,
            nowMillis = now
        )
        val comparisons = LocalInsightsAndRulesEngine.buildPeriodComparisons(allTxs, now)
        val monthlyBars = LocalInsightsAndRulesEngine.buildMonthlyIncomeExpenseBars(allTxs, now)
        val top5Places = LocalInsightsAndRulesEngine.buildTop5SpendingPlaces(chartRangeTxs.ifEmpty { allTxs })
        val localInsights = LocalInsightsAndRulesEngine.generateLocalMathInsights(allTxs, now)

        SnapSpendUiState(
            allTransactions = allTxs,
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
            thisMonthCategorySlices = thisMonthSlices,
            last5Transactions = allTxs.take(5),
            availablePaymentApps = paymentApps,
            chartDateRange = chartCtrl.chartDateRange,
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

            matchesSearch && matchesCat && matchesType && matchesApp
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

    fun clearAllFilters() {
        _filterState.value = FilterState()
    }

    fun updateChartDateRange(range: DateRangeFilter) {
        _chartControl.value = _chartControl.value.copy(chartDateRange = range)
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
    fun processSelectedUris(uris: List<Uri>, onFinished: () -> Unit = {}) {
        if (uris.isEmpty()) {
            onFinished()
            return
        }
        viewModelScope.launch {
            try {
                _scanState.value = ScanUiState.Reading(
                    1,
                    uris.size,
                    "Gemini is parsing amount, date & category..."
                )
                val categories = repository.getAllCategoriesOnce()
                val result = aiExtractor.extractFromUris(
                    uris = uris,
                    categories = categories,
                    onProgress = { step, total, label ->
                        _scanState.value = ScanUiState.Reading(step, total, label)
                    }
                )
                handleExtractionResult(result, lastUris = uris, lastSamplePreset = null)
            } finally {
                onFinished()
            }
        }
    }


    /**
     * Generates a realistic sample UPI & itemized receipt screenshot bitmap on-device and sends it
     * through the real Gemini AI extraction pipeline so Yu can test AI scanning anytime.
     */
    fun scanSampleUpiScreenshotWithGemini(presetIndex: Int) {
        viewModelScope.launch {
            _scanState.value = ScanUiState.Reading(1, 1, "Reading sample UPI receipt with Gemini...")
            val categories = repository.getAllCategoriesOnce().map { it.name }
            val bitmap = withContext(Dispatchers.Default) {
                aiExtractor.createSampleUpiScreenshotBitmap(presetIndex)
            }
            val result = aiExtractor.analyzeBitmapWithGemini(
                bitmap = bitmap,
                allowedCategories = categories,
                source = TransactionSource.SCREENSHOT
            )
            handleExtractionResult(result, lastUris = emptyList(), lastSamplePreset = presetIndex, lastNote = null)
        }
    }

    /**
     * Parses a single natural-language text or voice note into structured payment drafts
     * using Gemini (`gemini-4-argon`). Stored database history is never sent.
     */
    fun processNaturalLanguageNoteWithGemini(userNote: String) {
        val cleaned = userNote.trim()
        if (cleaned.isEmpty()) return
        viewModelScope.launch {
            _scanState.value = ScanUiState.Reading(1, 1, "Turning your note into a payment draft...")
            val categories = repository.getAllCategoriesOnce()
            val result = aiExtractor.extractFromNaturalLanguageText(
                userInput = cleaned,
                categories = categories
            )
            handleExtractionResult(result, lastUris = emptyList(), lastSamplePreset = null, lastNote = cleaned)
        }
    }

    fun retryLastScan() {
        when (val current = _scanState.value) {
            is ScanUiState.Error -> {
                if (current.recoverableUris.isNotEmpty()) {
                    processSelectedUris(current.recoverableUris)
                } else if (current.recoverableSamplePreset != null) {
                    scanSampleUpiScreenshotWithGemini(current.recoverableSamplePreset)
                } else if (!current.recoverableNaturalLanguageNote.isNullOrBlank()) {
                    processNaturalLanguageNoteWithGemini(current.recoverableNaturalLanguageNote)
                }
            }
            is ScanUiState.Unreadable -> {
                if (current.lastUris.isNotEmpty()) {
                    processSelectedUris(current.lastUris)
                } else if (current.lastSamplePreset != null) {
                    scanSampleUpiScreenshotWithGemini(current.lastSamplePreset)
                } else if (!current.lastNaturalLanguageNote.isNullOrBlank()) {
                    processNaturalLanguageNoteWithGemini(current.lastNaturalLanguageNote)
                }
            }
            else -> Unit
        }
    }

    fun queueCurrentFailedScan() {
        val current = _scanState.value as? ScanUiState.Error ?: return
        val task = QueuedScanTask(
            uris = current.recoverableUris,
            samplePreset = current.recoverableSamplePreset,
            naturalLanguageNote = current.recoverableNaturalLanguageNote
        )
        _queuedScans.value = _queuedScans.value + task
        _scanState.value = ScanUiState.Idle
        _toastMessage.value = "Scan queued! Tap 'Retry queued scans' on Home whenever you're back online."
    }

    fun retryQueuedScans() {
        val pending = _queuedScans.value.firstOrNull() ?: return
        _queuedScans.value = _queuedScans.value.drop(1)
        when {
            pending.uris.isNotEmpty() -> processSelectedUris(pending.uris)
            pending.samplePreset != null -> scanSampleUpiScreenshotWithGemini(pending.samplePreset)
            !pending.naturalLanguageNote.isNullOrBlank() -> processNaturalLanguageNoteWithGemini(pending.naturalLanguageNote)
        }
    }

    private suspend fun handleExtractionResult(
        result: ExtractionResult,
        lastUris: List<Uri>,
        lastSamplePreset: Int?,
        lastNote: String? = null
    ) {
        when (result) {
            is ExtractionResult.Success -> {
                val existingTxs = repository.getAllTransactionsOnce()
                val memories = repository.getAllMerchantMemoriesOnce().associateBy { it.normalizedMerchant }
                val enriched = result.drafts.map { raw ->
                    LocalInsightsAndRulesEngine.enrichAndResolveDraft(raw, existingTxs, memories)
                }
                // Highlight low-confidence items first on the review screen so Yu checks them first
                val sorted = enriched.sortedWith(
                    compareByDescending<DraftTransaction> { it.isLowConfidence }
                        .thenBy { it.isSkipped }
                        .thenByDescending { it.timestamp }
                )
                _scanState.value = ScanUiState.ReviewReady(sorted)
            }
            is ExtractionResult.UnreadableOrNoPayments -> {
                _scanState.value = ScanUiState.Unreadable(
                    reason = result.reason,
                    lastUris = lastUris,
                    lastSamplePreset = lastSamplePreset,
                    lastNaturalLanguageNote = lastNote
                )
            }
            is ExtractionResult.Error -> {
                _scanState.value = ScanUiState.Error(
                    message = result.message,
                    recoverableUris = lastUris,
                    recoverableSamplePreset = lastSamplePreset,
                    recoverableNaturalLanguageNote = lastNote
                )
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
            _scanState.value = ScanUiState.ReviewReady(updatedList)
        }
    }

    fun toggleDraftSkipped(draftId: String) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        val updated = current.drafts.map {
            if (it.draftId == draftId) it.copy(isSkipped = !it.isSkipped) else it
        }
        _scanState.value = ScanUiState.ReviewReady(updated)
    }

    fun confirmAllReviewDrafts(onComplete: () -> Unit = {}) {
        val current = _scanState.value as? ScanUiState.ReviewReady ?: return
        viewModelScope.launch {
            val summary = repository.commitConfirmedDrafts(current.drafts)
            _scanState.value = ScanUiState.Idle
            val msgParts = mutableListOf<String>()
            if (summary.insertedCount > 0) msgParts.add("${summary.insertedCount} saved")
            if (summary.updatedPendingCount > 0) msgParts.add("${summary.updatedPendingCount} pending updated to final")
            if (summary.skippedDuplicateCount > 0) msgParts.add("${summary.skippedDuplicateCount} skipped")
            _toastMessage.value = msgParts.joinToString(" · ").ifEmpty { "All set!" }
            onComplete()
        }
    }

    fun dismissScanFlow() {
        _scanState.value = ScanUiState.Idle
    }

    // --- Manual / Edit Transaction ---
    fun saveManualOrEditedTransaction(
        transaction: TransactionEntity,
        learnMerchantCategory: Boolean
    ) {
        viewModelScope.launch {
            repository.saveTransaction(transaction, learnMerchantCategory)
            _toastMessage.value = if (transaction.id == 0L) "Payment added!" else "Payment updated!"
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(id)
            _toastMessage.value = "Payment deleted."
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

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
