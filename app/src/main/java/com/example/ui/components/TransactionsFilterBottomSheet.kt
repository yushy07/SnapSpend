package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.TransactionType
import com.example.ui.DateRangeFilter
import com.example.ui.FilterState
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CandyLavenderPurple
import com.example.ui.theme.CandyLimeGreen
import com.example.ui.theme.CandySkyBlue
import com.example.ui.theme.CandySunshineYellow
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.PastelBeige
import com.example.ui.theme.PastelCyan
import com.example.ui.theme.PastelPink
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.util.MoneyUtils

/**
 * Editorial Modal Bottom Sheet component for filtering the Transactions list by:
 * 1. Date Range (All Time, This Week, This Month, Last Month, This Year)
 * 2. Payment Type (All, Expense / Money Out, Earning / Money In, Transfer) & Payment App
 * 3. Category (All categories + Expense/Earning categories)
 *
 * Works seamlessly in both Dark Mode (deep navy-slate sheet with candy-pop cards & #0B0B0B text)
 * and Light Mode.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionsFilterBottomSheet(
    filterState: FilterState,
    categories: List<CategoryEntity>,
    availablePaymentApps: List<String>,
    matchingCount: Int,
    matchingTotalPaise: Long,
    onDismiss: () -> Unit,
    onDateRangeChange: (DateRangeFilter) -> Unit,
    onTypeFilterChange: (TransactionType?) -> Unit,
    onCategoryFilterChange: (String?) -> Unit,
    onPaymentAppFilterChange: (String?) -> Unit,
    onClearAll: () -> Unit
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark

    val activeFilterCount = listOf(
        filterState.dateRange != DateRangeFilter.ALL,
        filterState.selectedCategory != null,
        filterState.selectedType != null,
        filterState.selectedPaymentApp != null
    ).count { it }

    val visibleCategories = categories.filter { cat ->
        when (filterState.selectedType) {
            TransactionType.EXPENSE -> cat.type == CategoryType.EXPENSE
            TransactionType.EARNING -> cat.type == CategoryType.EARNING
            else -> true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) tokens.cardSurface else PureWhite,
        contentColor = tokens.primaryText,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier.testTag("transactions_filter_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Sheet Header: Title + Reset All + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Filter payments",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = tokens.primaryText
                    )
                    Text(
                        text = if (activeFilterCount == 0) {
                            "Showing all time periods, categories & payment types"
                        } else {
                            "$activeFilterCount active filter${if (activeFilterCount > 1) "s" else ""} applied"
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.secondaryText
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (activeFilterCount > 0) {
                        UnderlinedActionLink(
                            text = "Reset all",
                            onClick = onClearAll,
                            testTag = "sheet_reset_all_filters_button"
                        )
                    }
                    BlackCircleIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Close filters",
                        size = 40.dp,
                        onClick = onDismiss,
                        testTag = "close_filters_sheet_button"
                    )
                }
            }

            // 1. Date Range Section (Sunshine Yellow in Dark Mode / Pastel Beige in Light Mode)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filter_section_date_range"),
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) CandySunshineYellow else PastelBeige,
                contentColor = BrightCardBlack,
                border = BorderStroke(1.8.dp, BrightCardBlack)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Date range",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = BrightCardBlack
                        )
                        StatusPillBadge(
                            text = filterState.dateRange.label,
                            backgroundColor = PureWhite
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DateRangeFilter.entries.forEach { range ->
                            WhitePillChip(
                                label = range.label,
                                selected = filterState.dateRange == range,
                                onClick = { onDateRangeChange(range) },
                                testTag = "filter_date_${range.name.lowercase()}"
                            )
                        }
                    }
                }
            }

            // 2. Payment Type & Method Section (Sky Blue in Dark Mode / Pastel Cyan in Light Mode)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filter_section_payment_type"),
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) CandySkyBlue else PastelCyan,
                contentColor = BrightCardBlack,
                border = BorderStroke(1.8.dp, BrightCardBlack)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Payment type",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = BrightCardBlack
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhitePillChip(
                            label = "All types",
                            selected = filterState.selectedType == null,
                            onClick = { onTypeFilterChange(null) },
                            testTag = "filter_type_all"
                        )
                        WhitePillChip(
                            label = "Expense (Out)",
                            selected = filterState.selectedType == TransactionType.EXPENSE,
                            onClick = {
                                onTypeFilterChange(
                                    if (filterState.selectedType == TransactionType.EXPENSE) null
                                    else TransactionType.EXPENSE
                                )
                            },
                            testTag = "filter_type_expense"
                        )
                        WhitePillChip(
                            label = "Earning (In)",
                            selected = filterState.selectedType == TransactionType.EARNING,
                            onClick = {
                                onTypeFilterChange(
                                    if (filterState.selectedType == TransactionType.EARNING) null
                                    else TransactionType.EARNING
                                )
                            },
                            testTag = "filter_type_earning"
                        )
                        WhitePillChip(
                            label = "Transfers",
                            selected = filterState.selectedType == TransactionType.TRANSFER,
                            onClick = {
                                onTypeFilterChange(
                                    if (filterState.selectedType == TransactionType.TRANSFER) null
                                    else TransactionType.TRANSFER
                                )
                            },
                            testTag = "filter_type_transfer"
                        )
                    }

                    if (availablePaymentApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Payment app",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                            color = BrightCardBlack
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            WhitePillChip(
                                label = "Any app",
                                selected = filterState.selectedPaymentApp == null,
                                onClick = { onPaymentAppFilterChange(null) },
                                testTag = "filter_app_all"
                            )
                            availablePaymentApps.forEach { app ->
                                val isSelected = filterState.selectedPaymentApp.equals(app, ignoreCase = true)
                                WhitePillChip(
                                    label = app,
                                    selected = isSelected,
                                    onClick = {
                                        onPaymentAppFilterChange(if (isSelected) null else app)
                                    },
                                    testTag = "filter_app_${app.lowercase().replace(" ", "_")}"
                                )
                            }
                        }
                    }
                }
            }

            // 3. Category Section (Lavender Purple in Dark Mode / Pastel Pink in Light Mode)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("filter_section_category"),
                shape = RoundedCornerShape(28.dp),
                color = if (isDark) CandyLavenderPurple else PastelPink,
                contentColor = BrightCardBlack,
                border = BorderStroke(1.8.dp, BrightCardBlack)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                            color = BrightCardBlack
                        )
                        StatusPillBadge(
                            text = filterState.selectedCategory ?: "All categories",
                            backgroundColor = PureWhite
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WhitePillChip(
                            label = "All categories",
                            selected = filterState.selectedCategory == null,
                            onClick = { onCategoryFilterChange(null) },
                            testTag = "filter_category_all"
                        )
                        visibleCategories.forEach { cat ->
                            val isSelected = filterState.selectedCategory == cat.name
                            WhitePillChip(
                                label = cat.name,
                                selected = isSelected,
                                onClick = {
                                    onCategoryFilterChange(if (isSelected) null else cat.name)
                                },
                                testTag = "filter_category_${cat.name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                            )
                        }
                    }
                }
            }

            // Primary Apply CTA Button with Live Count & Total Preview
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("apply_filters_button"),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) CandyLimeGreen else LimeAccent,
                    contentColor = BrightCardBlack
                ),
                border = BorderStroke(2.dp, BrightCardBlack),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Text(
                    text = if (matchingCount > 0) {
                        "Show $matchingCount payment${if (matchingCount == 1) "" else "s"} · ${MoneyUtils.formatPaise(matchingTotalPaise)}"
                    } else {
                        "Show 0 matching payments"
                    },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum"
                    ),
                    color = BrightCardBlack
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
