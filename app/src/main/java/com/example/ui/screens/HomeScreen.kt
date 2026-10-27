package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.ui.ScanUiState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.ui.SnapSpendUiState
import com.example.ui.components.BrightCardCornerIconButton
import com.example.ui.components.DarkLiveInsightCard
import com.example.ui.components.DaySpendChipData
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.ResponsiveTabularNumberText
import com.example.ui.components.RoundArrowButton
import com.example.ui.components.SpendingOverTimeAreaChartCard
import com.example.ui.components.StackedPastelTransactionDeck
import com.example.ui.components.TinySectionLabel
import com.example.ui.components.UnderlinedActionLink
import com.example.ui.components.WhitePillChip
import com.example.ui.components.WideSecondaryPillButton
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialLavender
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.EditorialPink
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MarkScreenEntrancePlayedEffect
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PaleMintTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.rememberAnimatedPaise
import com.example.ui.theme.staggeredScreenEntrance
import com.example.util.MoneyUtils
import com.example.util.MonthEndEstimateResult
import com.example.util.MonthEndMath
import com.example.util.QuickRepeatChip
import java.util.Calendar
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    uiState: SnapSpendUiState,
    scanState: ScanUiState = ScanUiState.Idle,
    onRetryScan: () -> Unit = {},
    onPickImagesClick: () -> Unit,
    onPickPdfClick: () -> Unit,
    onAiQuickNoteClick: () -> Unit,
    onTrySampleScanClick: () -> Unit = {},
    onManualAddClick: () -> Unit,
    onSeeAllTransactionsClick: () -> Unit,
    onSeeChartsClick: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onRetryQueuedScansClick: () -> Unit = {},
    onSetBudgetClick: () -> Unit = {},
    onQuickRepeatTap: (QuickRepeatChip) -> Unit = {},
    onQuickRepeatLongPress: (QuickRepeatChip) -> Unit = {},
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    // Calculate budget/income usage ratio for the balance card's progress bar
    val totalMonthlyBudgetLimitPaise = uiState.budgets.sumOf { it.monthlyLimitPaise }
    val referenceTotalPaise = when {
        totalMonthlyBudgetLimitPaise > 0L -> totalMonthlyBudgetLimitPaise
        uiState.thisMonthEarnedPaise > 0L -> uiState.thisMonthEarnedPaise
        uiState.thisMonthSpentPaise > 0L -> uiState.thisMonthSpentPaise
        else -> 1L
    }
    val usedFraction = if (uiState.thisMonthSpentPaise <= 0L) {
        0f
    } else {
        (uiState.thisMonthSpentPaise.toFloat() / referenceTotalPaise.toFloat()).coerceIn(0.04f, 1f)
    }
    val usedPercentInt = if (referenceTotalPaise > 0L && uiState.thisMonthSpentPaise > 0L) {
        ((uiState.thisMonthSpentPaise * 100L) / referenceTotalPaise).toInt().coerceIn(1, 999)
    } else {
        0
    }
    val progressSubtitle = when {
        totalMonthlyBudgetLimitPaise > 0L ->
            "$usedPercentInt% of ${MoneyUtils.formatPaise(totalMonthlyBudgetLimitPaise)} budget used"
        uiState.thisMonthEarnedPaise > 0L ->
            "$usedPercentInt% of ${MoneyUtils.formatPaise(uiState.thisMonthEarnedPaise)} income spent"
        uiState.thisMonthSpentPaise > 0L ->
            "${MoneyUtils.formatPaise(uiState.thisMonthSpentPaise)} spent this month"
        else -> "Earned − Spent · ₹0 recorded this month"
    }

    // Build 100% locally calculated day-of-week spending data (Mon-Sun) for the Indigo Area Chart (zero hardcoded numbers)
    var areaPeriodToggle by rememberSaveable { mutableStateOf("This Week") }
    val weeklyDayEntries = remember(uiState.allTransactions, areaPeriodToggle) {
        computeWeeklyDayChipData(uiState.allTransactions, areaPeriodToggle)
    }
    val dailyTargetAveragePaise = remember(totalMonthlyBudgetLimitPaise) {
        if (totalMonthlyBudgetLimitPaise > 0L) totalMonthlyBudgetLimitPaise / 30L else 0L
    }

    // Build 100% locally calculated math insights for the Dark Live Insight Cards (no AI, no network)
    val topCategorySlice = uiState.thisMonthCategorySlices.firstOrNull()
    val primaryInsightTitle = when {
        topCategorySlice != null && topCategorySlice.percentage >= 30 ->
            "${topCategorySlice.categoryName.uppercase()} SPEND UP"
        uiState.activeBudgetAlerts.isNotEmpty() ->
            "${uiState.activeBudgetAlerts.first().categoryName.uppercase()} BUDGET ALERT"
        uiState.allTransactions.isEmpty() ->
            "READY FOR YOUR FIRST PAYMENT"
        else -> "STABILIZING SPEND PACE"
    }
    val primaryInsightBody = uiState.localMathInsights.firstOrNull()
        ?: if (topCategorySlice != null) {
            "${topCategorySlice.categoryName} is ${topCategorySlice.percentage}% of this month's total (${MoneyUtils.formatPaise(topCategorySlice.amountPaise)})."
        } else {
            "No payments recorded yet. Scan a UPI screenshot, speak a note, or add by hand."
        }

    val secondaryInsightTitle = if (uiState.localMathInsights.size > 1) {
        "FOOD & MERCHANT SPIKE"
    } else {
        "MONTHLY CASHFLOW PACE"
    }
    val secondaryInsightBody = uiState.localMathInsights.getOrNull(1)
        ?: "Total spent is ${MoneyUtils.formatPaise(uiState.thisMonthSpentPaise)} across ${uiState.allTransactions.count { it.type == TransactionType.EXPENSE }} expenses this month."

    MarkScreenEntrancePlayedEffect(screenKey = "home")

    val animatedSpentText = rememberAnimatedPaise(targetPaise = uiState.thisMonthSpentPaise)
    val animatedEarnedText = rememberAnimatedPaise(targetPaise = uiState.thisMonthEarnedPaise)

    val monthEndEstimate = uiState.monthEndEstimate ?: remember(uiState.allTransactions, totalMonthlyBudgetLimitPaise) {
        MonthEndMath.computeMonthEndEstimate(
            transactions = uiState.allTransactions,
            totalMonthlyBudgetPaise = totalMonthlyBudgetLimitPaise,
            anchorMillis = System.currentTimeMillis()
        )
    }
    var showMonthEndInfoDialog by rememberSaveable { mutableStateOf(false) }

    if (showMonthEndInfoDialog) {
        MonthEndFormulaInfoDialog(onDismiss = { showMonthEndInfoDialog = false })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top 3D White Search Pill Bar
        item(key = "home_header") {
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance("home", 0)
                    .testTag("home_top_search_pill"),
                shape = CircleShape,
                extrusionColor = if (SnapSpendThemeTokens.colors.isDark) SunnyYellow else InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 3.dp,
                depthY = 4.dp
            ) {
                Surface(
                    onClick = onSeeAllTransactionsClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = CircleShape,
                    color = PureWhite,
                    contentColor = InkBlack,
                    border = BorderStroke(1.8.dp, InkBlack)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = InkBlack,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (topCategorySlice != null) {
                                    "Top: ${topCategorySlice.categoryName} · Search payments"
                                } else {
                                    "Search payments, merchants, notes..."
                                },
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = InkBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        RoundArrowButton(
                            onClick = onSeeAllTransactionsClick,
                            onColoredCard = true, // Black circle with white arrow
                            size = 38.dp
                        )
                    }
                }
            }
        }

        // Optional Queued Scans Retry Banner (AI Fallback mechanism)
        if (uiState.queuedScansCount > 0) {
            item(key = "home_queued_scans_banner") {
                Editorial3DLayerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_queued_scans_banner"),
                    shape = RoundedCornerShape(24.dp),
                    extrusionColor = InkBlack,
                    depthX = 4.dp,
                    depthY = 5.dp
                ) {
                    Surface(
                        onClick = onRetryQueuedScansClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = EditorialAqua,
                        contentColor = BrightCardBlack,
                        border = BorderStroke(1.8.dp, BrightCardBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${uiState.queuedScansCount} QUEUED AI SCAN${if (uiState.queuedScansCount > 1) "S" else ""} WAITING",
                                    style = CondensedCardTitleStyle.copy(fontSize = 20.sp),
                                    color = BrightCardBlack
                                )
                                Text(
                                    text = "Tap to retry reading with Gemini now",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrightCardBlack.copy(alpha = 0.8f)
                                )
                            }
                            BrightCardCornerIconButton(
                                icon = Icons.Default.Refresh,
                                contentDescription = "Retry queued scans",
                                parentCardColor = EditorialAqua,
                                onClick = onRetryQueuedScansClick
                            )
                        }
                    }
                }
            }
        }

        // 2. HOME / INSIGHT CARDS (100% locally calculated with simple math, dark 3D #1C1C1E card)
        item(key = "home_live_insights_section") {
            Column(
                modifier = Modifier.staggeredScreenEntrance("home", 1),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TinySectionLabel(text = "LIVE INSIGHTS")

                DarkLiveInsightCard(
                    title = primaryInsightTitle,
                    bodyText = primaryInsightBody,
                    iconBadgeColor = SunnyYellow,
                    icon = Icons.Default.Storefront,
                    onClick = onSeeChartsClick,
                    cardColor = EditorialPink,
                    showViewDetailsFooter = false,
                    testTag = "home_primary_insight_card"
                )
            }
        }

        // 3. SPENDING OVER TIME (3D Indigo #5B5CF6 Area Chart Card with Mon-Sun % circles, 3D Coral area & dashed budget line)
        item(key = "home_indigo_area_chart") {
            SpendingOverTimeAreaChartCard(
                title = "SPENDING OVER TIME",
                periodLabel = areaPeriodToggle,
                dayEntries = weeklyDayEntries,
                monthlyBudgetAveragePaise = dailyTargetAveragePaise,
                onPeriodDropdownClick = {
                    areaPeriodToggle = if (areaPeriodToggle == "This Week") "This Month" else "This Week"
                },
                onSetBudgetClick = onSetBudgetClick,
                modifier = Modifier.staggeredScreenEntrance("home", 2)
            )
        }

        // 4. Secondary Live Insight Card (Strong Aqua #8FE3F0) with black text & outline
        item(key = "home_secondary_insight_card") {
            DarkLiveInsightCard(
                title = secondaryInsightTitle,
                bodyText = secondaryInsightBody,
                iconBadgeColor = EditorialCoral,
                icon = Icons.Default.Receipt,
                onClick = onSeeChartsClick,
                cardColor = EditorialAqua,
                showViewDetailsFooter = true,
                modifier = Modifier.staggeredScreenEntrance("home", 3),
                testTag = "home_secondary_insight_card"
            )
        }

        // 4b. Separate 3D Month-End Estimate Card directly below Monthly Cashflow Pace
        item(key = "home_month_end_estimate_card") {
            MonthEndEstimate3DCard(
                estimate = monthEndEstimate,
                onSetBudgetClick = onSetBudgetClick,
                onInfoClick = { showMonthEndInfoDialog = true },
                modifier = Modifier.staggeredScreenEntrance("home", 4)
            )
        }

        // 5. Balance & Cashflow 3D Bento Summary (Sunny Yellow 3D Touch-Tilt Balance + 3D Coral Spent + 3D Mint Earned)
        item(key = "home_bento_grid") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance("home", 4),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TinySectionLabel(text = "MONTHLY SNAPSHOT")

                TouchTiltHeroBalanceCard(
                    balancePaise = uiState.thisMonthLeftPaise,
                    usedFraction = usedFraction,
                    subtitle = progressSubtitle,
                    cardColor = SunnyYellow,
                    onCornerClick = onSeeChartsClick
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BentoStatTile(
                        labelCaps = "TOTAL SPENT",
                        bigValueText = animatedSpentText,
                        shortLineText = "${uiState.allTransactions.count { it.amountPaise > 0 }} payments tracked",
                        cardColor = EditorialCoral,
                        cornerIcon = Icons.AutoMirrored.Outlined.TrendingDown,
                        onTileClick = onSeeAllTransactionsClick,
                        modifier = Modifier.weight(1f),
                        testTag = "home_spent_card"
                    )

                    BentoStatTile(
                        labelCaps = "TOTAL EARNED",
                        bigValueText = animatedEarnedText,
                        shortLineText = "Income & cashbacks",
                        cardColor = EditorialMint,
                        cornerIcon = Icons.AutoMirrored.Outlined.TrendingUp,
                        onTileClick = onSeeAllTransactionsClick,
                        modifier = Modifier.weight(1f),
                        testTag = "home_earned_card"
                    )
                }
            }
        }

        if (scanState !is ScanUiState.Idle) {
            item(key = "home_live_scan_progress_card") {
                LiveScanProgressHomeCard(
                    scanState = scanState,
                    onRetryScan = onRetryScan,
                    modifier = Modifier.staggeredScreenEntrance("home", 4)
                )
            }
        }

        // 6. Wide 3D Secondary Pill Button ("+ Add payment by hand") in Strong Lavender (#C9A0FF)
        item(key = "home_wide_add_button") {
            WideSecondaryPillButton(
                label = "+ Add payment by hand",
                leadingIcon = Icons.Default.Add,
                onClick = onManualAddClick,
                containerColor = EditorialLavender,
                modifier = Modifier.staggeredScreenEntrance("home", 5),
                testTag = "home_manual_add_button"
            )
        }

        // 6b. QUICK REPEATS row (above Recent Activity; hidden if there are none)
        if (uiState.quickRepeatChips.isNotEmpty()) {
            item(key = "home_quick_repeats_section") {
                QuickRepeatsRowSection(
                    chips = uiState.quickRepeatChips,
                    disabledKeys = uiState.disabledQuickRepeatKeys,
                    onChipTap = onQuickRepeatTap,
                    onChipLongPress = onQuickRepeatLongPress,
                    modifier = Modifier.staggeredScreenEntrance("home", 6)
                )
            }
        }

        // 7. Recent Activity List (3D Stacked Deck)
        item(key = "home_recent_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance("home", 6),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TinySectionLabel(text = "RECENT ACTIVITY")
                UnderlinedActionLink(
                    text = "See all",
                    onClick = onSeeAllTransactionsClick,
                    testTag = "home_see_all_link"
                )
            }
        }

        item(key = "home_recent_transactions") {
            Box(modifier = Modifier.staggeredScreenEntrance("home", 7)) {
                if (uiState.last5Transactions.isEmpty()) {
                    EmptyRecentTransactionsCard(
                        onPickImagesClick = onPickImagesClick,
                        onPickPdfClick = onPickPdfClick,
                        onAiQuickNoteClick = onAiQuickNoteClick,
                        onManualAddClick = onManualAddClick
                    )
                } else {
                    StackedPastelTransactionDeck(
                        transactions = uiState.last5Transactions,
                        onTransactionClick = onTransactionClick,
                        categories = uiState.categories,
                        highlightTopCardInLime = false
                    )
                }
            }
        }
    }
}

/**
 * Computes Mon-Sun spending amounts and percentages on-device using plain math.
 * Zero hardcoded fallback percentages: when no transactions exist, all days are 0%.
 */
private fun computeWeeklyDayChipData(
    transactions: List<TransactionEntity>,
    periodLabel: String = "This Week"
): List<DaySpendChipData> {
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val sumsByDayIndex = LongArray(7)

    val now = System.currentTimeMillis()
    val (startMillis, endMillis) = if (periodLabel.equals("This Month", ignoreCase = true)) {
        com.example.data.rules.LocalInsightsAndRulesEngine.getMonthBounds(now, 0)
    } else {
        com.example.data.rules.LocalInsightsAndRulesEngine.getWeekBounds(now, 0)
    }

    val cal = Calendar.getInstance()
    val expenseTxs = transactions.filter {
        it.type == TransactionType.EXPENSE &&
            it.status == TransactionStatus.SUCCESS &&
            it.timestamp in startMillis..endMillis
    }

    expenseTxs.forEach { tx ->
        cal.timeInMillis = tx.timestamp
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val idx = when (dow) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            Calendar.SUNDAY -> 6
            else -> 0
        }
        sumsByDayIndex[idx] += tx.amountPaise
    }

    val totalWeekSpend = sumsByDayIndex.sum()

    return dayNames.mapIndexed { idx, label ->
        val amt = sumsByDayIndex[idx]
        val pct = if (totalWeekSpend > 0L) {
            ((amt * 100L) / totalWeekSpend).toInt().coerceIn(0, 100)
        } else {
            0
        }
        DaySpendChipData(
            dayShort = label,
            amountPaise = amt,
            percentOfPeakOrTotal = pct
        )
    }
}

/**
 * Wide Sunny Yellow (#FFD23F) balance card with 3D touch-tilt perspective, extruded depth block, and pure black (#0B0B0B) text.
 */
@Composable
private fun TouchTiltHeroBalanceCard(
    balancePaise: Long,
    usedFraction: Float,
    subtitle: String,
    cardColor: Color,
    onCornerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current.density
    val isDark = SnapSpendThemeTokens.colors.isDark
    val reducedMotion = LocalReducedMotion.current

    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    var isHeroPressed by remember { mutableStateOf(false) }
    var shineCenter by remember { mutableStateOf(Offset.Unspecified) }

    val animatedTiltX by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else tiltX,
        animationSpec = Motion.physicalSpring(reducedMotion),
        label = "heroTiltX"
    )
    val animatedTiltY by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else tiltY,
        animationSpec = Motion.physicalSpring(reducedMotion),
        label = "heroTiltY"
    )
    val pressFraction by animateFloatAsState(
        targetValue = if (isHeroPressed) 1f else 0f,
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "heroPressFraction"
    )

    val animatedProgress by animateFloatAsState(
        targetValue = usedFraction.coerceIn(0f, 1f),
        animationSpec = Motion.chartTween(reducedMotion),
        label = "budgetProgress"
    )

    val formattedBalance = rememberAnimatedPaise(
        targetPaise = balancePaise,
        includeSignForNegative = true
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, end = 6.dp)
            .testTag("home_hero_balance_card")
    ) {
        // Extruded 3D depth shadow base behind the card
        val shadowOffsetX = (6f - animatedTiltY * 0.45f).roundToInt()
        val shadowOffsetY = (8f + animatedTiltX * 0.45f).roundToInt()
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(shadowOffsetX.dp.roundToPx(), shadowOffsetY.dp.roundToPx()) }
                .clip(RoundedCornerShape(28.dp))
                .background(if (isDark) EditorialCoral else BrightCardBlack)
                .border(1.5.dp, BrightCardBlack, RoundedCornerShape(28.dp))
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    IntOffset(
                        x = (shadowOffsetX * pressFraction).dp.roundToPx(),
                        y = (shadowOffsetY * pressFraction).dp.roundToPx()
                    )
                }
                .graphicsLayer {
                    rotationX = animatedTiltX
                    rotationY = animatedTiltY
                    cameraDistance = 14f * density
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        isHeroPressed = true
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val h = size.height.toFloat().coerceAtLeast(1f)

                        fun updateTilt(pos: Offset) {
                            val normX = ((pos.x / w) - 0.5f) * 2f
                            val normY = ((pos.y / h) - 0.5f) * 2f
                            tiltY = (normX * 7.5f).coerceIn(-8f, 8f)
                            tiltX = (-normY * 7.5f).coerceIn(-8f, 8f)
                            shineCenter = pos
                        }

                        updateTilt(down.position)
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                updateTilt(change.position)
                            }
                        } while (event.changes.any { it.pressed })

                        isHeroPressed = false
                        tiltX = 0f
                        tiltY = 0f
                        shineCenter = Offset.Unspecified
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (shineCenter != Offset.Unspecified) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    PureWhite.copy(alpha = 0.28f),
                                    Color.Transparent
                                ),
                                center = shineCenter,
                                radius = size.maxDimension * 0.65f
                            )
                        )
                    }
                },
            shape = RoundedCornerShape(28.dp),
            color = cardColor,
            contentColor = BrightCardBlack,
            border = BorderStroke(2.dp, BrightCardBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp)
            ) {
                // Top Row: Uppercase Condensed Label + Black Circle Arrow Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "WHAT'S LEFT",
                        style = CondensedCardTitleStyle.copy(fontSize = 20.sp),
                        color = BrightCardBlack
                    )

                    BrightCardCornerIconButton(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = "View budget breakdown",
                        parentCardColor = cardColor,
                        onClick = onCornerClick,
                        testTag = "hero_card_corner_button"
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Big number (48-52sp, tabular numerals, pure black #0B0B0B)
                ResponsiveTabularNumberText(
                    text = formattedBalance,
                    maxFontSize = 52.sp,
                    minFontSize = 30.sp,
                    color = BrightCardBlack,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.testTag("hero_balance_value")
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = BrightCardBlack.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Thin progress bar showing how much of the budget/income is used
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(BrightCardBlack.copy(alpha = 0.14f))
                        .border(1.2.dp, BrightCardBlack.copy(alpha = 0.45f), CircleShape)
                ) {
                    if (animatedProgress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress.coerceAtLeast(0.04f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(BrightCardBlack)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BentoStatTile(
    labelCaps: String,
    bigValueText: String,
    shortLineText: String,
    cardColor: Color,
    cornerIcon: ImageVector,
    onTileClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    val shape = RoundedCornerShape(28.dp)

    Editorial3DLayerBox(
        modifier = taggedModifier,
        shape = shape,
        extrusionColor = if (SnapSpendThemeTokens.colors.isDark) PureWhite.copy(alpha = 0.25f) else BrightCardBlack,
        extrusionBorderColor = BrightCardBlack,
        depthX = 4.dp,
        depthY = 6.dp,
        enableInteractiveTilt = true
    ) {
        Surface(
            onClick = onTileClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = cardColor,
            contentColor = BrightCardBlack,
            border = BorderStroke(1.8.dp, BrightCardBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = labelCaps.uppercase(),
                        style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                        color = BrightCardBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    BrightCardCornerIconButton(
                        icon = cornerIcon,
                        contentDescription = labelCaps,
                        parentCardColor = cardColor,
                        onClick = onTileClick
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                ResponsiveTabularNumberText(
                    text = bigValueText,
                    maxFontSize = 36.sp,
                    minFontSize = 20.sp,
                    color = BrightCardBlack,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = shortLineText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = BrightCardBlack.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyRecentTransactionsCard(
    onPickImagesClick: () -> Unit,
    onPickPdfClick: () -> Unit,
    onAiQuickNoteClick: () -> Unit,
    onManualAddClick: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Editorial3DLayerBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("empty_recent_card"),
        shape = shape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = EditorialPink,
            contentColor = InkBlack,
            border = BorderStroke(
                width = 1.8.dp,
                color = InkBlack
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "NO PAYMENTS YET",
                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                    color = InkBlack
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Upload a UPI screenshot, speak or type a quick note, or add your first payment by hand.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = InkBlack.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WhitePillChip(
                        label = "Scan screenshot",
                        selected = true,
                        leadingIcon = Icons.Default.DocumentScanner,
                        onClick = onPickImagesClick
                    )
                    WhitePillChip(
                        label = "Voice / text AI",
                        selected = false,
                        leadingIcon = Icons.Default.Mic,
                        onClick = onAiQuickNoteClick
                    )
                    WhitePillChip(
                        label = "Scan PDF",
                        selected = false,
                        leadingIcon = Icons.Default.PictureAsPdf,
                        onClick = onPickPdfClick
                    )
                    WhitePillChip(
                        label = "Add by hand",
                        selected = false,
                        leadingIcon = Icons.Default.Add,
                        onClick = onManualAddClick
                    )
                }
            }
        }
    }
}

/**
 * Separate 3D Card directly below Monthly Cashflow Pace showing:
 * a) "At this pace you'll spend ₹X by month end" (or "Add a few more payments to see an estimate")
 * b) "SAFE TO SPEND PER DAY: ₹Y" / "Over budget by ₹X" (in coral) / "Set a budget to see this" (opens budget setup)
 * Plus a small (i) button that opens a card explaining both formulas in plain words.
 */
@Composable
private fun MonthEndEstimate3DCard(
    estimate: MonthEndEstimateResult,
    onSetBudgetClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    val isDark = SnapSpendThemeTokens.colors.isDark

    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("month_end_estimate_card"),
        shape = shape,
        extrusionColor = if (isDark) SunnyYellow else InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = PaleMintHero,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MONTH-END ESTIMATE",
                        style = CondensedCardTitleStyle.copy(fontSize = 20.sp),
                        color = InkBlack
                    )

                    Surface(
                        onClick = onInfoClick,
                        modifier = Modifier
                            .size(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("month_end_info_button"),
                        shape = CircleShape,
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = "How month-end estimate is calculated",
                                tint = InkBlack,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Line a) Month-end pace estimate
                Text(
                    text = estimate.paceLineText,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = InkBlack,
                    modifier = Modifier.testTag("month_end_pace_line")
                )

                // Line b) Safe to spend per day / Over budget / Set a budget
                if (!estimate.hasBudget) {
                    Surface(
                        onClick = onSetBudgetClick,
                        shape = CircleShape,
                        color = SunnyYellow,
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack),
                        modifier = Modifier.testTag("month_end_set_budget_button")
                    ) {
                        Text(
                            text = estimate.safeLineText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                textDecoration = TextDecoration.Underline
                            ),
                            color = InkBlack
                        )
                    }
                } else if (estimate.isOverBudget) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack),
                        modifier = Modifier.testTag("month_end_safe_line")
                    ) {
                        Text(
                            text = estimate.safeLineText,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = EditorialCoral
                        )
                    }
                } else {
                    Text(
                        text = estimate.safeLineText,
                        style = CondensedCardTitleStyle.copy(
                            fontSize = 19.sp,
                            fontFeatureSettings = "tnum"
                        ),
                        color = InkBlack,
                        modifier = Modifier.testTag("month_end_safe_line")
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthEndFormulaInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("month_end_info_dialog"),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 5.dp,
            depthY = 7.dp
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                contentColor = InkBlack,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "HOW THIS IS CALCULATED",
                            style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                            color = InkBlack
                        )
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp),
                            shape = CircleShape,
                            color = SunnyYellow,
                            border = BorderStroke(1.5.dp, InkBlack)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = InkBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "1. Month-end pace:\nWe take what you've spent on successful expenses so far this month, multiply by the total days in this month, and divide by the days passed so far. Shown once you have at least 5 payments and 5 days have passed.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkBlack
                    )

                    Text(
                        text = "2. Safe to spend per day:\nWe subtract what you've spent so far from your total monthly budget, then divide by the days left in the month (counting today), rounded down to the nearest paisa. Transfers, failed, pending, and trashed items are ignored.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkBlack
                    )
                }
            }
        }
    }
}

/**
 * QUICK REPEATS row above "Recent activity":
 * Shows up to 6 chips (like "Chai ₹20") for successful expenses with the same merchant and amount
 * appearing 3+ times in the last 90 days.
 * - Tap: light haptic + adds a new payment now (source = manual) + 3s chip disable + 6s UNDO bar.
 * - Long-press: opens the add-by-hand sheet pre-filled so the user can change the amount.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QuickRepeatsRowSection(
    chips: List<QuickRepeatChip>,
    disabledKeys: Set<String>,
    onChipTap: (QuickRepeatChip) -> Unit,
    onChipLongPress: (QuickRepeatChip) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_quick_repeats_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TinySectionLabel(text = "QUICK REPEATS")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            chips.forEachIndexed { index, chip ->
                val isDisabled = chip.key in disabledKeys
                val chipColor = when (index % 4) {
                    0 -> SunnyYellow
                    1 -> EditorialAqua
                    2 -> EditorialPink
                    else -> EditorialMint
                }

                Editorial3DLayerBox(
                    modifier = Modifier
                        .graphicsLayer { alpha = if (isDisabled) 0.48f else 1f }
                        .testTag("quick_repeat_chip_$index"),
                    shape = CircleShape,
                    extrusionColor = InkBlack,
                    extrusionBorderColor = InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(CircleShape)
                            .combinedClickable(
                                enabled = !isDisabled,
                                onClick = {
                                    Motion.performLightTapHaptic(view)
                                    onChipTap(chip)
                                },
                                onLongClick = {
                                    Motion.performLightTapHaptic(view)
                                    onChipLongPress(chip)
                                }
                            ),
                        shape = CircleShape,
                        color = chipColor,
                        contentColor = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = InkBlack,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = chip.label,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = InkBlack,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveScanProgressHomeCard(
    scanState: ScanUiState,
    onRetryScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (scanState is ScanUiState.Idle) return

    val infiniteTransition = rememberInfiniteTransition(label = "scan_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val (titleText, bodyText, cardColor, actionLabel) = when (scanState) {
        is ScanUiState.Reading -> {
            Quad(
                "Gemini AI Scan in Progress",
                scanState.statusText.ifBlank { "Reading amount, date & category..." },
                PaleMintTint,
                null
            )
        }
        is ScanUiState.ReviewReady -> {
            Quad(
                "Scan ready!",
                "Tap to review ${scanState.drafts.size} payment draft(s)",
                SunnyYellow,
                "TAP TO REVIEW"
            )
        }
        is ScanUiState.Error -> {
            Quad(
                "Scan failed",
                scanState.message.ifBlank { "Tap to retry without re-uploading." },
                EditorialCoral,
                "TAP TO RETRY"
            )
        }
        is ScanUiState.Unreadable -> {
            Quad(
                "Unreadable document",
                scanState.reason.ifBlank { "Tap to retry scanning." },
                EditorialLavender,
                "TAP TO RETRY"
            )
        }
        else -> Quad("Scanning...", "Processing background task...", PaleMintTint, null)
    }

    Editorial3DLayerBox(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 3.dp,
        depthY = 5.dp
    ) {
        Surface(
            onClick = {
                if (scanState is ScanUiState.Error || scanState is ScanUiState.Unreadable) {
                    onRetryScan()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_scan_progress_card"),
            shape = RoundedCornerShape(24.dp),
            color = cardColor,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .graphicsLayer {
                                alpha = if (scanState is ScanUiState.Reading) pulseAlpha else 1f
                            }
                            .clip(CircleShape)
                            .background(if (scanState is ScanUiState.ReviewReady) InkBlack else SunnyYellow)
                            .border(1.5.dp, InkBlack, CircleShape)
                    )
                    Column {
                        Text(
                            text = titleText,
                            style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                            color = InkBlack
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bodyText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = InkBlack.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (actionLabel != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        onClick = {
                            if (scanState is ScanUiState.Error || scanState is ScanUiState.Unreadable) {
                                onRetryScan()
                            }
                        },
                        shape = CircleShape,
                        color = InkBlack,
                        contentColor = PureWhite,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = actionLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


