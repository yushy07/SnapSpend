package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import com.example.data.local.RepeatingState
import com.example.ui.DateRangeFilter
import com.example.ui.PaymentModeFilter
import com.example.ui.SnapSpendUiState
import com.example.ui.components.CoralComparisonBarChartCard
import com.example.ui.components.DarkLiveInsightCard
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.EditorialDonutChart
import com.example.ui.components.EditorialLineTrendCard
import com.example.ui.components.IncomeVsExpenseMonthlyCard
import com.example.ui.components.ResponsiveTabularNumberText
import com.example.ui.components.SegmentedPillToggle
import com.example.ui.components.TiltedRepeatingPaymentCard
import com.example.ui.components.TinySectionLabel
import com.example.ui.components.UppercaseScreenHeader
import com.example.ui.components.WhitePillDropdown
import com.example.ui.theme.CandyCardPalette
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialLavender
import com.example.ui.theme.EditorialPink
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PaleAquaTint
import com.example.ui.theme.PaleMintTint
import com.example.ui.theme.PalePinkTint
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.MarkScreenEntrancePlayedEffect
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.rememberAnimatedPaise
import com.example.ui.theme.staggeredScreenEntrance
import com.example.util.MoneyUtils
import kotlin.math.abs

enum class ChartsSubTab(val label: String, val testTag: String) {
    OVERVIEW("Overview", "charts_subtab_overview"),
    TRENDS("Trends", "charts_subtab_trends"),
    BUDGETS("Categories & Bills", "charts_subtab_budgets")
}

@Composable
fun ChartsAndInsightsScreen(
    uiState: SnapSpendUiState,
    onChartDateRangeChange: (DateRangeFilter) -> Unit,
    onLineGranularityChange: (String) -> Unit,
    onSaveBudget: (categoryName: String, limitPaise: Long) -> Unit,
    onRepeatingStateChange: (id: String, state: RepeatingState) -> Unit,
    onChartPaymentModeFilterChange: (PaymentModeFilter) -> Unit = {},
    onSetBudgetClick: () -> Unit = {},
    onTransactionClick: (com.example.data.local.TransactionEntity) -> Unit = {},
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark

    var activeSubTab by rememberSaveable { mutableStateOf(ChartsSubTab.OVERVIEW) }
    var selectedCategoryDropdownLabel by rememberSaveable { mutableStateOf("All Categories") }

    val availableCategoryNames = uiState.chartCategorySlices.map { it.categoryName }
    val activeCategoryFilter = selectedCategoryDropdownLabel.takeIf {
        it != "All Categories" && it in availableCategoryNames
    }
    val displayedCategorySlices = if (activeCategoryFilter != null) {
        uiState.chartCategorySlices.filter { it.categoryName == activeCategoryFilter }
    } else {
        uiState.chartCategorySlices
    }

    val rangeSpentPaise = displayedCategorySlices.sumOf { it.amountPaise }
        .takeIf { it > 0L || activeCategoryFilter != null || uiState.chartPaymentModeFilter != PaymentModeFilter.ALL }
        ?: uiState.thisMonthSpentPaise

    val daysDivisor = when (uiState.chartDateRange) {
        DateRangeFilter.THIS_WEEK -> 7L
        DateRangeFilter.THIS_MONTH -> 30L
        DateRangeFilter.LAST_MONTH -> 30L
        DateRangeFilter.THIS_YEAR -> 365L
        DateRangeFilter.ALL -> 30L
    }
    val avgPerDayPaise = (rangeSpentPaise / daysDivisor).coerceAtLeast(0L)

    // Compute local math delta string for the black delta pills matching the active date range
    val periodComparisonForRange = when (uiState.chartDateRange) {
        DateRangeFilter.THIS_WEEK -> uiState.periodComparisons.firstOrNull { it.title.contains("Week", ignoreCase = true) }
        DateRangeFilter.THIS_YEAR -> uiState.periodComparisons.firstOrNull { it.title.contains("Year", ignoreCase = true) }
        else -> uiState.periodComparisons.firstOrNull { it.title.contains("Month", ignoreCase = true) }
    } ?: uiState.periodComparisons.firstOrNull()
    val periodDiff: Int? = periodComparisonForRange?.deltaPercent
    val hasAnyPeriodSpend = rangeSpentPaise > 0L || (periodComparisonForRange?.previousSpentPaise ?: 0L) > 0L
    val totalDeltaPillText = when {
        !hasAnyPeriodSpend -> "–"
        periodDiff != null && periodDiff > 0 -> "↑ ${periodDiff}%"
        periodDiff != null && periodDiff < 0 -> "↓ ${abs(periodDiff)}%"
        else -> "0%"
    }
    val avgDeltaPillText = when {
        !hasAnyPeriodSpend -> "–"
        periodDiff != null && periodDiff > 0 -> "↑ ${periodDiff}%"
        periodDiff != null && periodDiff < 0 -> "↓ ${abs(periodDiff)}%"
        else -> "0%"
    }

    val totalMonthlyBudgetLimitPaise = uiState.budgets.sumOf { it.monthlyLimitPaise }
    val dailyTargetAveragePaise = if (totalMonthlyBudgetLimitPaise > 0L) totalMonthlyBudgetLimitPaise / 30L else 0L

    val screenEntranceKey = "charts_${activeSubTab.name}"
    MarkScreenEntrancePlayedEffect(screenKey = screenEntranceKey)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("charts_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Bold Uppercase Condensed Screen Title ("REVENUE") + White Pill Dropdowns + Sub-Tab Toggle
        item(key = "charts_header") {
            Column(
                modifier = Modifier.staggeredScreenEntrance(screenKey = screenEntranceKey, index = 0),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                UppercaseScreenHeader(
                    title = "SPENDING",
                    leadingIcon = Icons.AutoMirrored.Filled.TrendingUp,
                    leadingIconBg = PureWhite,
                    leadingIconTint = InkBlack,
                    titleColor = tokens.primaryText
                )

                // White 3D Pill Dropdowns with Chevrons ("This Week v", "All Categories v")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WhitePillDropdown(
                        label = uiState.chartDateRange.label,
                        onClick = {
                            val entries = DateRangeFilter.entries
                            val nextIdx = (entries.indexOf(uiState.chartDateRange) + 1) % entries.size
                            onChartDateRangeChange(entries[nextIdx])
                        },
                        modifier = Modifier.weight(1f),
                        fillWidth = true,
                        testTag = "charts_date_range_dropdown"
                    )
                    WhitePillDropdown(
                        label = activeCategoryFilter ?: "All Categories",
                        onClick = {
                            val options = listOf("All Categories") + availableCategoryNames
                            val currentIdx = options.indexOf(activeCategoryFilter ?: "All Categories").coerceAtLeast(0)
                            selectedCategoryDropdownLabel = options[(currentIdx + 1) % options.size]
                        },
                        modifier = Modifier.weight(1f),
                        fillWidth = true,
                        testTag = "charts_category_dropdown"
                    )
                }

                // 3-Way Segmented Filter Pill: All | Online | Cash
                SegmentedPillToggle(
                    options = PaymentModeFilter.entries,
                    selectedOption = uiState.chartPaymentModeFilter,
                    labelProvider = { it.label },
                    onOptionSelected = onChartPaymentModeFilterChange,
                    optionTestTagProvider = { "charts_payment_mode_${it.name.lowercase()}" }
                )

                SegmentedPillToggle(
                    options = ChartsSubTab.entries,
                    selectedOption = activeSubTab,
                    labelProvider = { it.label },
                    onOptionSelected = { activeSubTab = it },
                    optionTestTagProvider = { it.testTag }
                )
            }
        }

        when (activeSubTab) {
            ChartsSubTab.OVERVIEW -> {
                // 1. MONTH / WEEK / YEAR COMPARISON (3D Rounded Coral Card #FF6F59 with 2 Lighter Coral #FF8A76 boxes + 3D Mint selected bar)
                item(key = "overview_bar_chart") {
                    Box(modifier = Modifier.staggeredScreenEntrance(screenKey = screenEntranceKey, index = 1)) {
                        CoralComparisonBarChartCard(
                            totalSpentPaise = rangeSpentPaise,
                            avgPerDayPaise = avgPerDayPaise,
                            totalDeltaLabel = totalDeltaPillText,
                            avgDeltaLabel = avgDeltaPillText,
                            comparisonSubtitle = "vs. previous\nperiod",
                            bars = uiState.editorialPillBars
                        )
                    }
                }

                // 2. HOME / INSIGHT CARDS (100% locally calculated with simple math, dark 3D #1C1C1E cards)
                item(key = "overview_insights") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredScreenEntrance(screenKey = screenEntranceKey, index = 2)
                            .testTag("local_math_insights_card"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TinySectionLabel(text = "LIVE INSIGHTS · SAVED ON YOUR PHONE")

                        val insights = uiState.localMathInsights.ifEmpty {
                            listOf("No payments recorded yet. Add or scan a payment to see your spending insights.")
                        }
                        val insightCardColors = listOf(EditorialLavender, SunnyYellow, EditorialPink)
                        insights.forEachIndexed { idx, sentence ->
                            val badgeColors = listOf(EditorialCoral, SunnyYellow, EditorialAqua)
                            val titleText = when {
                                uiState.allTransactions.isEmpty() -> "READY FOR YOUR FIRST PAYMENT"
                                idx == 0 -> "CATEGORY SPEND SPIKE"
                                idx == 1 -> "STABILIZING DAILY PACE"
                                else -> "MERCHANT FREQUENCY"
                            }
                            DarkLiveInsightCard(
                                title = titleText,
                                bodyText = sentence,
                                iconBadgeColor = badgeColors[idx % badgeColors.size],
                                icon = if (idx == 0) Icons.Default.Receipt else Icons.Default.Lightbulb,
                                onClick = { activeSubTab = ChartsSubTab.BUDGETS },
                                cardColor = insightCardColors[idx % insightCardColors.size],
                                showViewDetailsFooter = (idx == 0)
                            )
                        }
                    }
                }

                // 3. REPEATING PAYMENTS (3D Slightly Tilted Cards in Coral, Yellow, or Aqua)
                item(key = "overview_tilted_repeating") {
                    val activeRepeating = uiState.repeatingPayments.filter {
                        it.state != RepeatingState.DISMISSED
                    }
                    if (activeRepeating.isNotEmpty()) {
                        Column(
                            modifier = Modifier.staggeredScreenEntrance(screenKey = screenEntranceKey, index = 3),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            UppercaseScreenHeader(
                                title = "REPEATING PAYMENTS",
                                leadingIcon = Icons.Default.Autorenew,
                                leadingIconBg = PureWhite,
                                leadingIconTint = InkBlack,
                                titleColor = tokens.primaryText
                            )
                            activeRepeating.forEachIndexed { idx, item ->
                                TiltedRepeatingPaymentCard(
                                    item = item,
                                    index = idx,
                                    onToggleStateClick = {
                                        val nextState = if (item.state == RepeatingState.CONFIRMED) {
                                            RepeatingState.SUGGESTED
                                        } else {
                                            RepeatingState.CONFIRMED
                                        }
                                        onRepeatingStateChange(item.id, nextState)
                                    }
                                )
                            }
                        }
                    }
                }

                // 4. 3D Category Pie / Donut Chart Card on Pale Aqua (#CDF3F8)
                item(key = "overview_donut") {
                    val donutShape = RoundedCornerShape(28.dp)
                    Editorial3DLayerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredScreenEntrance(screenKey = screenEntranceKey, index = 4),
                        shape = donutShape,
                        extrusionColor = InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 4.dp,
                        depthY = 6.dp,
                        enableInteractiveTilt = true
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = donutShape,
                            color = PaleAquaTint,
                            contentColor = InkBlack,
                            border = BorderStroke(
                                width = 1.8.dp,
                                color = InkBlack
                            )
                        ) {
                            Column(modifier = Modifier.padding(22.dp)) {
                                Text(
                                    text = "BY CATEGORY",
                                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                                    color = InkBlack
                                )
                                Text(
                                    text = "Share of total spending (${uiState.chartDateRange.label})",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = InkBlack.copy(alpha = 0.78f)
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                EditorialDonutChart(
                                    slices = displayedCategorySlices,
                                    totalSpentPaise = rangeSpentPaise,
                                    onBrightCard = true,
                                    isOnDarkSurface = false
                                )
                            }
                        }
                    }
                }

                // 5. 3D Top 5 Places Leaderboard on Pale Pink (#FFD3E6)
                item(key = "overview_top_5_merchants") {
                    val topPlacesShape = RoundedCornerShape(28.dp)
                    Editorial3DLayerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredScreenEntrance(screenKey = screenEntranceKey, index = 5),
                        shape = topPlacesShape,
                        extrusionColor = InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 4.dp,
                        depthY = 6.dp,
                        enableInteractiveTilt = true
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = topPlacesShape,
                            color = PalePinkTint,
                            contentColor = InkBlack,
                            border = BorderStroke(
                                width = 1.8.dp,
                                color = InkBlack
                            )
                        ) {
                            Column(modifier = Modifier.padding(22.dp)) {
                                Text(
                                    text = "TOP 5 PLACES",
                                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                                    color = InkBlack
                                )
                                Text(
                                    text = "Merchants where you spent the most",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = InkBlack.copy(alpha = 0.78f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                if (uiState.top5Places.isEmpty()) {
                                    Text(
                                        text = "No merchant data in this date range yet.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = InkBlack.copy(alpha = 0.78f)
                                    )
                                } else {
                                    Column {
                                        uiState.top5Places.forEachIndexed { idx, merchant ->
                                            val rankBadgeColor = CandyCardPalette[idx % CandyCardPalette.size]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    // 3D Rank Badge
                                                    Box(modifier = Modifier.padding(end = 2.dp, bottom = 2.dp)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .offset(x = 2.dp, y = 2.dp)
                                                                .clip(CircleShape)
                                                                .background(InkBlack)
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .size(36.dp)
                                                                .clip(CircleShape)
                                                                .background(rankBadgeColor)
                                                                .border(1.2.dp, InkBlack, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "${idx + 1}",
                                                                style = MaterialTheme.typography.labelLarge.copy(
                                                                    fontWeight = FontWeight.Black
                                                                ),
                                                                color = InkBlack
                                                            )
                                                        }
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = merchant.merchant.uppercase(),
                                                            style = CondensedCardTitleStyle.copy(fontSize = 20.sp),
                                                            color = InkBlack,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${merchant.count} payment${if (merchant.count > 1) "s" else ""}",
                                                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                            color = InkBlack.copy(alpha = 0.78f)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = MoneyUtils.formatPaise(merchant.totalSpentPaise),
                                                    style = MaterialTheme.typography.titleMedium.copy(
                                                        fontWeight = FontWeight.ExtraBold,
                                                        fontFeatureSettings = "tnum"
                                                    ),
                                                    color = InkBlack
                                                )
                                            }
                                            if (idx < uiState.top5Places.lastIndex) {
                                                HorizontalDivider(
                                                    thickness = 1.dp,
                                                    color = InkBlack.copy(alpha = 0.16f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            ChartsSubTab.TRENDS -> {
                // 1. SPENDING OVER TIME (3D Indigo #5B5CF6 Area Chart Card)
                item(key = "trends_line_chart") {
                    Box(modifier = Modifier.staggeredScreenEntrance(screenKey = screenEntranceKey, index = 1)) {
                        EditorialLineTrendCard(
                            points = uiState.lineChartPoints,
                            granularity = uiState.lineGranularity,
                            onGranularityChange = onLineGranularityChange,
                            monthlyBudgetAveragePaise = dailyTargetAveragePaise,
                            onSetBudgetClick = onSetBudgetClick
                        )
                    }
                }

                // 2. 3D Income vs Expense Bar Chart Card
                item(key = "trends_income_vs_expense") {
                    Box(modifier = Modifier.staggeredScreenEntrance(screenKey = screenEntranceKey, index = 2)) {
                        IncomeVsExpenseMonthlyCard(
                            bars = uiState.monthlyIncomeExpenseBars
                        )
                    }
                }

                // 3. 3D This Month vs Last Month Comparison Card (Sunny Yellow #FFD23F with black text)
                item(key = "trends_mom_comparison") {
                    val mom = uiState.periodComparisons.firstOrNull {
                        it.title.contains("Month", ignoreCase = true)
                    } ?: uiState.periodComparisons.firstOrNull()
                    val diffPct: Int? = mom?.deltaPercent
                    val momHeadline = when {
                        diffPct == null -> "FIRST MONTH TRACKED"
                        diffPct > 0 -> "UP $diffPct% VS LAST MONTH"
                        diffPct < 0 -> "DOWN ${abs(diffPct)}% VS LAST MONTH"
                        else -> "SAME AS LAST MONTH"
                    }
                    val momShape = RoundedCornerShape(28.dp)
                    val animatedCurrentMonthText = rememberAnimatedPaise(
                        targetPaise = mom?.currentSpentPaise ?: uiState.thisMonthSpentPaise
                    )
                    val animatedLastMonthText = rememberAnimatedPaise(
                        targetPaise = mom?.previousSpentPaise ?: 0L
                    )

                    Editorial3DLayerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredScreenEntrance(screenKey = screenEntranceKey, index = 3),
                        shape = momShape,
                        extrusionColor = if (isDark) EditorialCoral else InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 5.dp,
                        depthY = 7.dp,
                        enableInteractiveTilt = true
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = momShape,
                            color = SunnyYellow,
                            contentColor = InkBlack,
                            border = BorderStroke(2.dp, InkBlack)
                        ) {
                            Column(modifier = Modifier.padding(22.dp)) {
                                Text(
                                    text = "MONTH OVER MONTH",
                                    style = CondensedCardTitleStyle.copy(fontSize = 16.sp),
                                    color = InkBlack.copy(alpha = 0.78f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = momHeadline,
                                    style = CondensedCardTitleStyle.copy(fontSize = 28.sp),
                                    color = InkBlack
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Editorial3DLayerBox(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        extrusionColor = InkBlack,
                                        depthX = 2.dp,
                                        depthY = 3.dp
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            color = PureWhite,
                                            border = BorderStroke(1.5.dp, InkBlack)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "THIS MONTH",
                                                    style = CondensedCardTitleStyle.copy(fontSize = 15.sp),
                                                    color = InkBlack.copy(alpha = 0.7f)
                                                )
                                                ResponsiveTabularNumberText(
                                                    text = animatedCurrentMonthText,
                                                    maxFontSize = 26.sp,
                                                    minFontSize = 18.sp,
                                                    color = InkBlack
                                                )
                                            }
                                        }
                                    }
                                    Editorial3DLayerBox(
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        extrusionColor = InkBlack,
                                        depthX = 2.dp,
                                        depthY = 3.dp
                                    ) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            color = PureWhite,
                                            border = BorderStroke(1.5.dp, InkBlack)
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    text = "LAST MONTH",
                                                    style = CondensedCardTitleStyle.copy(fontSize = 15.sp),
                                                    color = InkBlack.copy(alpha = 0.7f)
                                                )
                                                ResponsiveTabularNumberText(
                                                    text = animatedLastMonthText,
                                                    maxFontSize = 26.sp,
                                                    minFontSize = 18.sp,
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
            }

            ChartsSubTab.BUDGETS -> {
                // 1. Embedded 3D Indigo Stacked Folder-Tab Categories card
                item(key = "budgets_folder_categories") {
                    Editorial3DLayerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredScreenEntrance(screenKey = screenEntranceKey, index = 1),
                        shape = RoundedCornerShape(30.dp),
                        extrusionColor = if (isDark) SunnyYellow.copy(alpha = 0.75f) else InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 4.dp,
                        depthY = 6.dp
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(30.dp),
                            color = PeriwinkleIndigo,
                            border = BorderStroke(2.dp, InkBlack)
                        ) {
                            Box(modifier = Modifier.height(680.dp)) {
                                CategoriesFolderScreen(
                                    uiState = uiState,
                                    onSaveBudget = onSaveBudget,
                                    onTransactionClick = onTransactionClick,
                                    bottomContentPadding = 24.dp
                                )
                            }
                        }
                    }
                }

                // 2. Repeating Payments (3D Slightly Tilted Cards in Coral, Yellow, Aqua)
                item(key = "budgets_repeating_payments") {
                    Column(
                        modifier = Modifier.staggeredScreenEntrance(screenKey = screenEntranceKey, index = 2),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UppercaseScreenHeader(
                            title = "REPEATING PAYMENTS",
                            leadingIcon = Icons.Default.Storefront,
                            leadingIconBg = PureWhite,
                            leadingIconTint = InkBlack,
                            titleColor = tokens.primaryText
                        )

                        val activeRepeating = uiState.repeatingPayments.filter {
                            it.state != RepeatingState.DISMISSED
                        }

                        if (activeRepeating.isEmpty()) {
                            Editorial3DLayerBox(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(24.dp),
                                extrusionColor = InkBlack,
                                extrusionBorderColor = InkBlack,
                                depthX = 3.dp,
                                depthY = 5.dp
                            ) {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(24.dp),
                                    color = PaleMintTint,
                                    contentColor = InkBlack,
                                    border = BorderStroke(
                                        width = 1.8.dp,
                                        color = InkBlack
                                    )
                                ) {
                                    Text(
                                        text = "No repeating bills detected yet. Once the same merchant appears across multiple months, it will show up here as a 3D tilted pill card.",
                                        modifier = Modifier.padding(18.dp),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = InkBlack.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        } else {
                            activeRepeating.forEachIndexed { idx, item ->
                                TiltedRepeatingPaymentCard(
                                    item = item,
                                    index = idx,
                                    onToggleStateClick = {
                                        val nextState = if (item.state == RepeatingState.CONFIRMED) {
                                            RepeatingState.SUGGESTED
                                        } else {
                                            RepeatingState.CONFIRMED
                                        }
                                        onRepeatingStateChange(item.id, nextState)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
