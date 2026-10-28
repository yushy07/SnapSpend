package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.ui.DateRangeFilter
import com.example.ui.PaymentModeFilter
import com.example.ui.SnapSpendUiState
import com.example.ui.components.DarkLiveInsightCard
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.PaleMintIllustratedHeroCard
import com.example.ui.components.SegmentedPillToggle
import com.example.ui.components.SeparatedPastelTransactionList
import com.example.ui.components.TinySectionLabel
import com.example.ui.components.TransactionsFilterBottomSheet
import com.example.ui.components.UnderlinedActionLink
import com.example.ui.components.UppercaseScreenHeader
import com.example.ui.components.WhitePillDropdown
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialLavender
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MarkScreenEntrancePlayedEffect
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.staggeredScreenEntrance
import com.example.util.MoneyUtils
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.border

@Composable
fun TransactionsScreen(
    uiState: SnapSpendUiState,
    onSearchQueryChange: (String) -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onTypeFilterChange: (TransactionType?) -> Unit,
    onPaymentAppFilterChange: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onPaymentModeFilterChange: (PaymentModeFilter) -> Unit = {},
    onTopUpCashWalletClick: () -> Unit = {},
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val filters = uiState.filterState
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(
        filters.dateRange,
        filters.selectedCategory,
        filters.selectedType,
        filters.selectedPaymentApp,
        filters.paymentModeFilter
    ) {
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
            listState.animateScrollToItem(0)
        }
    }

    val activeFilterLabels = buildList {
        if (filters.dateRange != DateRangeFilter.ALL) add(filters.dateRange.label)
        when (filters.selectedType) {
            TransactionType.EXPENSE -> add("Expense")
            TransactionType.EARNING -> add("Earning")
            TransactionType.TRANSFER -> add("Transfer")
            null -> Unit
        }
        if (filters.selectedCategory != null) add(filters.selectedCategory)
        if (filters.selectedPaymentApp != null) add(filters.selectedPaymentApp)
    }

    val activeFilterCount = activeFilterLabels.size
    val hasActiveFilter = filters.searchQuery.isNotBlank() || activeFilterCount > 0 || filters.paymentModeFilter != PaymentModeFilter.ALL

    val matchingTotalPaise = uiState.filteredTransactions
        .filter { it.status == TransactionStatus.SUCCESS }
        .sumOf { it.amountPaise }

    val topFilteredCategory = uiState.thisMonthCategorySlices.firstOrNull()
    val liveReportTitle = if (topFilteredCategory != null) {
        "${topFilteredCategory.categoryName.uppercase()} SPIKE"
    } else {
        "ACTIVITY SUMMARY"
    }
    val liveReportBody = if (topFilteredCategory != null) {
        "${topFilteredCategory.categoryName} is ${topFilteredCategory.percentage}% of spending this month (${MoneyUtils.formatPaise(topFilteredCategory.amountPaise)}). Tap to filter or review."
    } else {
        "${uiState.filteredTransactions.size} payments totaling ${MoneyUtils.formatPaise(matchingTotalPaise)} in the selected view."
    }

    val reducedMotion = LocalReducedMotion.current
    MarkScreenEntrancePlayedEffect(screenKey = "transactions")

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .testTag("transactions_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. REPORTS Header + White Pill Dropdown ("This Month v") + Pale Mint Illustrated Hero Area (only when empty)
        item(key = "tx_header") {
            Column(
                modifier = Modifier.staggeredScreenEntrance(screenKey = "transactions", index = 0),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UppercaseScreenHeader(
                    title = "TRANSACTIONS",
                    leadingIcon = Icons.AutoMirrored.Filled.FormatListBulleted,
                    leadingIconBg = PaleMintHero,
                    leadingIconTint = InkBlack,
                    titleColor = tokens.primaryText,
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (hasActiveFilter) {
                                UnderlinedActionLink(
                                    text = "Reset",
                                    onClick = {
                                        focusManager.clearFocus()
                                        onClearFilters()
                                    },
                                    testTag = "reset_filters_link"
                                )
                            }
                            WhitePillDropdown(
                                label = filters.dateRange.label,
                                menuOptions = DateRangeFilter.entries.map { it.label },
                                selectedOption = filters.dateRange.label,
                                onOptionSelected = { pickedLabel ->
                                    focusManager.clearFocus()
                                    DateRangeFilter.entries.firstOrNull { it.label == pickedLabel }?.let(onDateRangeChange)
                                },
                                onClick = {
                                    val entries = DateRangeFilter.entries
                                    val nextIdx = (entries.indexOf(filters.dateRange) + 1) % entries.size
                                    onDateRangeChange(entries[nextIdx])
                                },
                                testTag = "reports_date_dropdown"
                            )
                        }
                    }
                )

                // 3-Way Segmented Filter Pill: All | Online | Cash
                SegmentedPillToggle(
                    options = PaymentModeFilter.entries,
                    selectedOption = filters.paymentModeFilter,
                    labelProvider = { it.label },
                    onOptionSelected = {
                        focusManager.clearFocus()
                        onPaymentModeFilterChange(it)
                    },
                    optionTestTagProvider = { "tx_payment_mode_${it.name.lowercase()}" }
                )

                // Dedicated Cash Ledger Summary Card when "Cash" is selected
                if (filters.paymentModeFilter == PaymentModeFilter.CASH) {
                    CashLedgerSummaryCard(
                        thisMonthCashSpentPaise = uiState.thisMonthCashSpentPaise,
                        cashInHandPaise = uiState.cashInHandPaise,
                        onTopUpClick = onTopUpCashWalletClick
                    )
                }

                // Show the robot illustration ONLY when the list is empty and not in Cash Ledger mode
                if (uiState.groupedTransactions.isEmpty() && filters.paymentModeFilter != PaymentModeFilter.CASH) {
                    PaleMintIllustratedHeroCard(
                        modifier = Modifier.testTag("reports_pale_mint_hero")
                    )
                }
            }
        }

        // 2. Search Pill with Filter Icon directly below the Pale Mint Hero Area
        item(key = "tx_search_and_filter_bar") {
            Column(
                modifier = Modifier.staggeredScreenEntrance(screenKey = "transactions", index = 1),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = filters.searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transactions_search_input"),
                    placeholder = {
                        Text(
                            text = "Search merchants, notes or ₹ amount...",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = InkBlack.copy(alpha = 0.6f)
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = InkBlack
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (filters.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear search",
                                        tint = InkBlack
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    showFilterSheet = true
                                },
                                modifier = Modifier.testTag("open_filters_sheet_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Open Filters",
                                    tint = if (activeFilterCount > 0) EditorialCoral else InkBlack
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { focusManager.clearFocus() },
                        onDone = { focusManager.clearFocus() }
                    ),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PureWhite,
                        unfocusedContainerColor = PureWhite,
                        focusedBorderColor = InkBlack,
                        unfocusedBorderColor = InkBlack,
                        focusedTextColor = InkBlack,
                        unfocusedTextColor = InkBlack,
                        cursorColor = InkBlack
                    )
                )

                // Active filters summary pill when filters are applied
                if (activeFilterLabels.isNotEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .clickable { showFilterSheet = true }
                            .testTag("active_filters_summary_bar"),
                        shape = CircleShape,
                        color = SunnyYellow,
                        contentColor = BrightCardBlack,
                        border = BorderStroke(1.5.dp, BrightCardBlack)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered by: ${activeFilterLabels.joinToString(" · ")}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                color = BrightCardBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Edit",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = BrightCardBlack,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. LIVE INSIGHTS Card on Transactions screen in Strong Lavender (#C9A0FF)
        item(key = "reports_live_insight") {
            Column(
                modifier = Modifier.staggeredScreenEntrance(screenKey = "transactions", index = 2),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TinySectionLabel(text = "LIVE INSIGHTS")
                DarkLiveInsightCard(
                    title = liveReportTitle,
                    bodyText = liveReportBody,
                    iconBadgeColor = EditorialCoral,
                    icon = Icons.Default.Receipt,
                    onClick = { showFilterSheet = true },
                    cardColor = EditorialLavender,
                    showViewDetailsFooter = true,
                    testTag = "reports_live_insight_card"
                )
            }
        }

        // 4. Day-grouped payment cards (or Pale Mint empty state card)
        if (uiState.groupedTransactions.isEmpty()) {
            item(key = "tx_empty_state") {
                val emptyShape = RoundedCornerShape(28.dp)
                Editorial3DLayerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredScreenEntrance(screenKey = "transactions", index = 3),
                    shape = emptyShape,
                    extrusionColor = InkBlack,
                    extrusionBorderColor = InkBlack,
                    depthX = 4.dp,
                    depthY = 6.dp
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = emptyShape,
                        color = PaleMintHero,
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack)
                    ) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Text(
                                text = if (hasActiveFilter) "NO MATCHING PAYMENTS" else "YOUR LIST IS EMPTY",
                                style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                                color = InkBlack
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (hasActiveFilter) {
                                    "Try resetting your filters or search query to see all saved payments."
                                } else {
                                    "Tap the + button below to scan a screenshot, speak a note, or add by hand."
                                },
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = InkBlack.copy(alpha = 0.82f)
                            )
                            if (hasActiveFilter) {
                                Spacer(modifier = Modifier.height(14.dp))
                                Editorial3DLayerBox(
                                    shape = CircleShape,
                                    extrusionColor = InkBlack,
                                    depthX = 2.5.dp,
                                    depthY = 3.5.dp
                                ) {
                                    Surface(
                                        onClick = {
                                            focusManager.clearFocus()
                                            onClearFilters()
                                        },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("reset_tx_filters_button"),
                                        shape = CircleShape,
                                        color = SunnyYellow,
                                        contentColor = InkBlack,
                                        border = BorderStroke(1.6.dp, InkBlack)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = InkBlack,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "RESET FILTERS",
                                                style = CondensedCardTitleStyle.copy(fontSize = 16.sp),
                                                color = InkBlack
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.groupedTransactions,
                key = { _, (dayHeader, _) -> "header_$dayHeader" }
            ) { groupIdx, (dayHeader, dayList) ->
                Column(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = Motion.normalTween(reducedMotion),
                            placementSpec = Motion.physicalIntOffsetSpring(reducedMotion),
                            fadeOutSpec = Motion.quickTween(reducedMotion)
                        )
                        .staggeredScreenEntrance(screenKey = "transactions", index = 3 + groupIdx)
                ) {
                    TinySectionLabel(
                        text = dayHeader,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SeparatedPastelTransactionList(
                        transactions = dayList,
                        onTransactionClick = onTransactionClick,
                        categories = uiState.categories,
                        highlightFirstCardInLime = false
                    )
                }
            }
        }
    }

    if (showFilterSheet) {
        TransactionsFilterBottomSheet(
            filterState = filters,
            categories = uiState.categories,
            availablePaymentApps = uiState.availablePaymentApps,
            matchingCount = uiState.filteredTransactions.size,
            matchingTotalPaise = matchingTotalPaise,
            onDismiss = { showFilterSheet = false },
            onDateRangeChange = onDateRangeChange,
            onTypeFilterChange = onTypeFilterChange,
            onCategoryFilterChange = onCategoryFilterChange,
            onPaymentAppFilterChange = onPaymentAppFilterChange,
            onClearAll = {
                onClearFilters()
                showFilterSheet = false
            }
        )
    }
}

/**
 * Sunny Yellow (#FFD23F) 3D Cash Wallet & Ledger Summary Card shown when the `Cash` filter pill is active:
 * - Shows "CASH SPENT THIS MONTH: ₹X" and "CASH IN HAND: ₹Y"
 * - Includes a "+ Top Up / ATM Withdrawal" quick button that opens the Add Payment editor pre-set to Transfer + Cash.
 */
@Composable
private fun CashLedgerSummaryCard(
    thisMonthCashSpentPaise: Long,
    cashInHandPaise: Long,
    onTopUpClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(28.dp)
    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cash_ledger_summary_card"),
        shape = cardShape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            color = SunnyYellow,
            contentColor = InkBlack,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                            .border(1.5.dp, InkBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Cash Wallet",
                            tint = InkBlack,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "CASH WALLET & LEDGER",
                            style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                            color = InkBlack
                        )
                        Text(
                            text = "ATM withdrawals & cash top-ups add to Cash in Hand without double-counting as expenses",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = InkBlack.copy(alpha = 0.78f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cash_spent_this_month_box"),
                        shape = RoundedCornerShape(18.dp),
                        color = PureWhite,
                        border = BorderStroke(1.6.dp, InkBlack)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "CASH SPENT THIS MONTH: ${MoneyUtils.formatPaise(thisMonthCashSpentPaise)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack.copy(alpha = 0.76f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = MoneyUtils.formatPaise(thisMonthCashSpentPaise),
                                style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                                color = InkBlack
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("cash_in_hand_box"),
                        shape = RoundedCornerShape(18.dp),
                        color = PureWhite,
                        border = BorderStroke(1.6.dp, InkBlack)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "CASH IN HAND: ${MoneyUtils.formatPaise(cashInHandPaise, showPlusForPositive = false)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack.copy(alpha = 0.76f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (cashInHandPaise < 0L) {
                                    "-${MoneyUtils.formatPaise(kotlin.math.abs(cashInHandPaise))}"
                                } else {
                                    MoneyUtils.formatPaise(cashInHandPaise)
                                },
                                style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                                color = if (cashInHandPaise < 0L) EditorialCoral else InkBlack
                            )
                        }
                    }
                }

                Editorial3DLayerBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    extrusionColor = InkBlack,
                    extrusionBorderColor = InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        onClick = onTopUpClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("cash_wallet_top_up_button"),
                        shape = CircleShape,
                        color = EditorialMint,
                        contentColor = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = InkBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "+ TOP UP / ATM WITHDRAWAL",
                                style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                color = InkBlack
                            )
                        }
                    }
                }
            }
        }
    }
}
