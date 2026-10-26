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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.ui.DateRangeFilter
import com.example.ui.SnapSpendUiState
import com.example.ui.components.DarkLiveInsightCard
import com.example.ui.components.PaleMintIllustratedHeroCard
import com.example.ui.components.SeparatedPastelTransactionList
import com.example.ui.components.TinySectionLabel
import com.example.ui.components.TransactionsFilterBottomSheet
import com.example.ui.components.UnderlinedActionLink
import com.example.ui.components.UppercaseScreenHeader
import com.example.ui.components.WhitePillDropdown
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSubtleBorder
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialLavender
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryGreyText
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.util.MoneyUtils

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
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val filters = uiState.filterState
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }

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
    val hasActiveFilter = filters.searchQuery.isNotBlank() || activeFilterCount > 0

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

    LazyColumn(
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
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                    onClick = onClearFilters,
                                    testTag = "reset_filters_link"
                                )
                            }
                            WhitePillDropdown(
                                label = filters.dateRange.label,
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

                // Show the robot illustration ONLY when the list is empty
                if (uiState.groupedTransactions.isEmpty()) {
                    PaleMintIllustratedHeroCard(
                        modifier = Modifier.testTag("reports_pale_mint_hero")
                    )
                }
            }
        }

        // 2. Search Pill with Filter Icon directly below the Pale Mint Hero Area
        item(key = "tx_search_and_filter_bar") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                onClick = { showFilterSheet = true },
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
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
                    }
                }
            }
        } else {
            itemsIndexed(
                items = uiState.groupedTransactions,
                key = { _, (dayHeader, _) -> "header_$dayHeader" }
            ) { groupIdx, (dayHeader, dayList) ->
                Column(modifier = Modifier.animateItem()) {
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
