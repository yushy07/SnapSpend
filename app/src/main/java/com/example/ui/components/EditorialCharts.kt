package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.RepeatingPaymentEntity
import com.example.data.local.RepeatingState
import com.example.data.rules.BarHighlightRole
import com.example.data.rules.CategorySlice
import com.example.data.rules.LineChartPoint
import com.example.data.rules.MonthlyIncomeExpenseBar
import com.example.data.rules.PillBarEntry
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.FadedCoralBar
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LighterCoral
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleLavenderTint
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PaleMintTint
import com.example.ui.theme.PaleYellowTint
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryGreyText
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.parseEditorialColor
import com.example.ui.theme.rememberAnimatedPaise
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay


data class DaySpendChipData(
    val dayShort: String,
    val amountPaise: Long,
    val percentOfPeakOrTotal: Int
)

/**
 * 2. SPENDING OVER TIME (3D Indigo Area Chart Card):
 * - Big rounded 3D Periwinkle Indigo card (#5B5CF6) with an uppercase condensed title and a white "This Week v" dropdown pill
 * - A row of 7 3D round day chips (Mon-Sun) with the % of weekly spending, the selected day as a black circle (#0B0B0B)
 * - 3D Extruded Coral (#FF6F59) area chart (with 3D offset depth wall & vertical prism ribs) + dashed horizontal line
 *   for the monthly budget average, and two small legend pills at the bottom: "Current" and "Target"
 * - Text on colored elements is black (#0B0B0B). Zero hardcoded pre-fed percentages.
 */
@Composable
fun SpendingOverTimeAreaChartCard(
    title: String = "SPENDING OVER TIME",
    periodLabel: String = "This Week",
    dayEntries: List<DaySpendChipData>,
    monthlyBudgetAveragePaise: Long,
    onPeriodDropdownClick: () -> Unit,
    onSetBudgetClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val entries = if (dayEntries.size == 7) {
        dayEntries
    } else {
        val defaultDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        defaultDays.mapIndexed { idx, label ->
            dayEntries.getOrNull(idx) ?: DaySpendChipData(label, 0L, 0)
        }
    }

    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current

    val hasAnySpend = entries.any { it.amountPaise > 0L }
    val peakIndex = if (hasAnySpend) {
        entries.indices.maxByOrNull { entries[it].amountPaise } ?: 0
    } else {
        0
    }
    var selectedDayIndex by remember(entries) { mutableIntStateOf(peakIndex.coerceIn(0, 6)) }
    val cardShape = RoundedCornerShape(28.dp)

    // Progressive Area Chart entrance:
    // 1) Stroke line draws from left to right (0f -> 1f over CHART_MS)
    // 2) Area fill beneath it fades in smoothly right after (0f -> 1f over NORMAL_MS)
    val lineDrawProgress = remember { Animatable(if (reducedMotion) 1f else 0f) }
    val areaFillAlpha = remember { Animatable(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            lineDrawProgress.snapTo(1f)
            areaFillAlpha.snapTo(1f)
        } else {
            lineDrawProgress.snapTo(0f)
            areaFillAlpha.snapTo(0f)
            lineDrawProgress.animateTo(
                targetValue = 1f,
                animationSpec = Motion.chartTween(reducedMotion = false)
            )
            areaFillAlpha.animateTo(
                targetValue = 1f,
                animationSpec = Motion.normalTween(reducedMotion = false)
            )
        }
    }

    val maxAmount = (entries.maxOfOrNull { it.amountPaise } ?: 0L)
        .coerceAtLeast(monthlyBudgetAveragePaise)
        .coerceAtLeast(1L)

    // Smoothly interpolate each day's normalized height when switching filters (e.g. This Week <-> This Month)
    val animatedDayHeights = entries.mapIndexed { idx, item ->
        val targetNorm = if (hasAnySpend && maxAmount > 0L) {
            (item.amountPaise.toFloat() / maxAmount.toFloat()).coerceIn(0.14f, 0.90f)
        } else {
            0.14f
        }
        val animVal by animateFloatAsState(
            targetValue = targetNorm,
            animationSpec = Motion.physicalSpring(reducedMotion),
            label = "areaDayNorm_$idx"
        )
        animVal
    }

    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("indigo_area_chart_card"),
        shape = cardShape,
        extrusionColor = if (SnapSpendThemeTokens.colors.isDark) EditorialAqua.copy(alpha = 0.72f) else InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 5.dp,
        depthY = 7.dp,
        enableInteractiveTilt = true
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            color = PeriwinkleIndigo, // #5B5CF6
            contentColor = InkBlack,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Top Row: Bold Uppercase Condensed Title + White "This Week v" Dropdown Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = title.uppercase(),
                        style = CondensedCardTitleStyle.copy(
                            fontSize = 28.sp,
                            lineHeight = 30.sp
                        ),
                        color = InkBlack,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    WhitePillDropdown(
                        label = periodLabel,
                        onClick = onPeriodDropdownClick,
                        backgroundColor = PureWhite,
                        textColor = InkBlack,
                        testTag = "area_chart_period_dropdown"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Row of 7 Day Labels + 3D Round Day Chips (Mon-Sun) with % of weekly spending; selected day as Black Circle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    entries.forEachIndexed { idx, dayItem ->
                        val isSelected = idx == selectedDayIndex
                        val dayInteractionSource = remember { MutableInteractionSource() }
                        val isDayPressed by dayInteractionSource.collectIsPressedAsState()
                        val dayPressFraction by animateFloatAsState(
                            targetValue = if (isDayPressed) 1f else 0f,
                            animationSpec = Motion.pressSpring(reducedMotion),
                            label = "dayChipPress_$idx"
                        )
                        val dayShiftX = 2.dp * dayPressFraction
                        val dayShiftY = 3.dp * dayPressFraction
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(
                                    interactionSource = dayInteractionSource,
                                    indication = null
                                ) {
                                    Motion.performLightTapHaptic(view)
                                    selectedDayIndex = idx
                                }
                                .testTag("area_chart_day_chip_${dayItem.dayShort.lowercase()}")
                        ) {
                            Text(
                                text = dayItem.dayShort,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp
                                ),
                                color = InkBlack.copy(alpha = 0.88f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
                                // 3D extruded base under each day circle
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .offset(x = 2.dp, y = 3.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) SunnyYellow else InkBlack)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .offset(x = dayShiftX, y = dayShiftY)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isSelected -> InkBlack
                                                hasAnySpend && idx <= peakIndex -> PureWhite
                                                else -> PureWhite.copy(alpha = 0.72f)
                                            }
                                        )
                                        .border(
                                            width = 1.4.dp,
                                            color = InkBlack,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (hasAnySpend) "${dayItem.percentOfPeakOrTotal}%" else "–",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = if (hasAnySpend) 10.sp else 13.sp,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = if (isSelected) PureWhite else InkBlack,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3D Coral (#FF6F59) Area Chart with Extruded 3D Side Wall & Dashed Horizontal Target Line
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .padding(horizontal = 10.dp)
                ) {
                    val w = size.width
                    val h = size.height
                    val depthDx = 6.dp.toPx()
                    val depthDy = 7.dp.toPx()
                    val usableW = (w - depthDx).coerceAtLeast(1f)
                    val usableH = (h - depthDy).coerceAtLeast(1f)
                    val stepX = if (entries.size > 1) usableW / (entries.size - 1) else usableW

                    val points = entries.indices.map { index ->
                        val x = index * stepX
                        val normalized = animatedDayHeights.getOrElse(index) { 0.14f }
                        Offset(x, usableH - normalized * usableH)
                    }

                    val currentFillAlpha = areaFillAlpha.value.coerceIn(0f, 1f)
                    val currentLineDraw = lineDrawProgress.value.coerceIn(0f, 1f)

                    if (currentFillAlpha > 0.01f) {
                        // 1. 3D Extruded Back/Bottom Shadow Area Polygon (shifted by +depthDx, +depthDy)
                        val shadowAreaPath = Path().apply {
                            moveTo(points.first().x + depthDx, usableH + depthDy)
                            points.forEach { pt ->
                                lineTo(pt.x + depthDx, pt.y + depthDy)
                            }
                            lineTo(points.last().x + depthDx, usableH + depthDy)
                            close()
                        }
                        drawPath(
                            path = shadowAreaPath,
                            color = InkBlack.copy(alpha = 0.85f * currentFillAlpha)
                        )

                        // 2. 3D Top Extrusion Ribbon connecting front curve to back 3D curve
                        for (i in 0 until points.lastIndex) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val ribbonQuad = Path().apply {
                                moveTo(p1.x, p1.y)
                                lineTo(p2.x, p2.y)
                                lineTo(p2.x + depthDx, p2.y + depthDy)
                                lineTo(p1.x + depthDx, p1.y + depthDy)
                                close()
                            }
                            drawPath(
                                path = ribbonQuad,
                                color = Color(0xFFC84632).copy(alpha = currentFillAlpha)
                            )
                            drawPath(
                                path = ribbonQuad,
                                color = InkBlack.copy(alpha = currentFillAlpha),
                                style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round)
                            )
                        }

                        // 3. Front Coral filled area polygon
                        val areaPath = Path().apply {
                            moveTo(points.first().x, usableH)
                            points.forEach { pt ->
                                lineTo(pt.x, pt.y)
                            }
                            lineTo(points.last().x, usableH)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    EditorialCoral.copy(alpha = currentFillAlpha),
                                    LighterCoral.copy(alpha = 0.90f * currentFillAlpha)
                                )
                            )
                        )

                        // Subtle 3D vertical prism ribs inside the Coral polygon
                        points.forEach { pt ->
                            drawLine(
                                color = InkBlack.copy(alpha = 0.16f * currentFillAlpha),
                                start = pt,
                                end = Offset(pt.x, usableH),
                                strokeWidth = 1.2.dp.toPx()
                            )
                        }
                    }

                    // 4. Vertical guide line under selected day
                    val selectedX = points[selectedDayIndex].x
                    if (currentLineDraw >= (selectedDayIndex.toFloat() / (entries.size - 1).coerceAtLeast(1))) {
                        drawLine(
                            color = InkBlack.copy(alpha = 0.45f),
                            start = Offset(selectedX, 0f),
                            end = Offset(selectedX, usableH),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }

                    // 5. Top stroke & specular 3D highlight on the front Coral curve (draws left -> right)
                    val strokePath = Path().apply {
                        points.forEachIndexed { idx, pt ->
                            if (idx == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }
                    clipRect(
                        left = 0f,
                        top = 0f,
                        right = w * currentLineDraw,
                        bottom = h
                    ) {
                        drawPath(
                            path = strokePath,
                            color = InkBlack,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    // 6. Dashed horizontal line for the monthly budget average target (when set)
                    if (monthlyBudgetAveragePaise > 0L) {
                        val targetRatio = (monthlyBudgetAveragePaise.toFloat() / maxAmount.toFloat()).coerceIn(0.20f, 0.85f)
                        val targetY = usableH - targetRatio * usableH
                        drawLine(
                            color = PureWhite,
                            start = Offset(0f, targetY),
                            end = Offset(usableW * currentLineDraw, targetY),
                            strokeWidth = 2.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    }

                    // 7. 3D Selected point sphere marker
                    val selPt = points[selectedDayIndex]
                    if (currentLineDraw >= (selectedDayIndex.toFloat() / (entries.size - 1).coerceAtLeast(1))) {
                        drawCircle(
                            color = InkBlack,
                            radius = 8.dp.toPx(),
                            center = Offset(selPt.x + 2.dp.toPx(), selPt.y + 2.dp.toPx())
                        )
                        drawCircle(
                            color = SunnyYellow,
                            radius = 7.dp.toPx(),
                            center = selPt
                        )
                        drawCircle(
                            color = InkBlack,
                            radius = 7.dp.toPx(),
                            center = selPt,
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                if (!hasAnySpend) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Add your first payment to see this fill up",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        ),
                        color = InkBlack,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("area_chart_empty_hint")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Two small legend pills at the bottom: "Current" and "Target" (or "Set a budget" when unset)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDotPill(
                        text = "Current · ${MoneyUtils.formatPaise(entries[selectedDayIndex].amountPaise)}",
                        dotColor = EditorialCoral,
                        backgroundColor = PureWhite,
                        textColor = InkBlack
                    )

                    StatusDotPill(
                        text = if (monthlyBudgetAveragePaise > 0L) {
                            "Target · ${MoneyUtils.formatPaise(monthlyBudgetAveragePaise)}/day"
                        } else {
                            "Set a budget"
                        },
                        dotColor = EditorialAqua,
                        backgroundColor = PureWhite.copy(alpha = 0.90f),
                        textColor = InkBlack,
                        onClick = onSetBudgetClick,
                        testTag = "area_chart_target_pill"
                    )
                }
            }
        }
    }
}

/**
 * 3. MONTH / WEEK / YEAR COMPARISON (3D Coral Bar Chart Card #FF6F59):
 * - Big rounded 3D Coral card (#FF6F59) with two inner 3D Lighter Coral (#FF8A76) boxes at the top:
 *   "TOTAL SPENT" and "AVG PER DAY" (small uppercase label, big tabular number, a black change pill)
 * - Below, tall 3D extruded prism bars in faded lighter coral, the selected bar in Mint (#9FE3C0)
 *   with a 3D black tooltip bubble above it showing the amount,
 *   and the selected day/period label in a black pill at the bottom.
 * - Text on ALL colored cards is black (#0B0B0B). Zero hardcoded pre-fed deltas.
 */
@Composable
fun CoralComparisonBarChartCard(
    totalSpentPaise: Long,
    avgPerDayPaise: Long,
    totalDeltaLabel: String = "–",
    avgDeltaLabel: String = "–",
    comparisonSubtitle: String = "vs. previous period",
    bars: List<PillBarEntry>,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val hasNoData = totalSpentPaise <= 0L && bars.all { it.amountPaise <= 0L }
    val resolvedTotalDelta = if (hasNoData && totalDeltaLabel == "0%") "–" else totalDeltaLabel
    val resolvedAvgDelta = if (hasNoData && avgDeltaLabel == "0%") "–" else avgDeltaLabel
    val displayBars = if (bars.isNotEmpty()) {
        bars.take(7)
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").map { day ->
            PillBarEntry(
                label = day,
                amountPaise = 0L,
                percentageOfTotal = 0,
                fractionOfMax = 0.12f,
                role = BarHighlightRole.NORMAL
            )
        }
    }

    val defaultSelectedIdx = displayBars.indexOfFirst { it.role == BarHighlightRole.HIGHEST }
        .takeIf { it >= 0 } ?: 0
    var selectedBarIndex by remember(displayBars) {
        mutableIntStateOf(defaultSelectedIdx.coerceIn(0, (displayBars.size - 1).coerceAtLeast(0)))
    }
    val cardShape = RoundedCornerShape(30.dp)
    val innerBoxShape = RoundedCornerShape(22.dp)

    val animatedTotalSpentText = rememberAnimatedPaise(targetPaise = totalSpentPaise)
    val animatedAvgPerDayText = rememberAnimatedPaise(targetPaise = avgPerDayPaise)

    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("coral_comparison_bar_chart_card"),
        shape = cardShape,
        extrusionColor = if (SnapSpendThemeTokens.colors.isDark) SunnyYellow.copy(alpha = 0.78f) else InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 5.dp,
        depthY = 7.dp,
        enableInteractiveTilt = true
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            color = EditorialCoral, // #FF6F59
            contentColor = InkBlack,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Inner 3D Box 1: Lighter Coral (#FF8A76) — "TOTAL SPENT" + Black delta pill + Big Number
                Editorial3DLayerBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = innerBoxShape,
                    extrusionColor = InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = innerBoxShape,
                        color = LighterCoral, // #FF8A76
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 15.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL SPENT",
                                    style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                    color = InkBlack
                                )
                                BlackDeltaPill(text = resolvedTotalDelta)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            ResponsiveTabularNumberText(
                                text = animatedTotalSpentText,
                                maxFontSize = 42.sp,
                                minFontSize = 26.sp,
                                color = InkBlack,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                // Inner 3D Box 2: Lighter Coral (#FF8A76) — "AVG PER DAY" + Black delta pill + Big Number
                Editorial3DLayerBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = innerBoxShape,
                    extrusionColor = InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = innerBoxShape,
                        color = LighterCoral, // #FF8A76
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 15.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "AVG PER DAY",
                                    style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                    color = InkBlack
                                )
                                BlackDeltaPill(text = resolvedAvgDelta)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ResponsiveTabularNumberText(
                                    text = animatedAvgPerDayText,
                                    maxFontSize = 36.sp,
                                    minFontSize = 22.sp,
                                    color = InkBlack,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = comparisonSubtitle,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 14.sp
                                    ),
                                    color = InkBlack.copy(alpha = 0.80f),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Tall 3D Extruded Prism Bars; selected bar in Mint (#9FE3C0) with 3D Black Tooltip Bubble above it
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(222.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    displayBars.forEachIndexed { idx, bar ->
                        val isSelected = idx == selectedBarIndex
                        val targetFraction = if (bar.amountPaise > 0L) {
                            bar.fractionOfMax.coerceIn(0.24f, 1f)
                        } else {
                            0.14f
                        }

                        // Staggered bar growth from bottom (30ms apart) + smooth filter interpolation
                        var barStarted by remember { mutableStateOf(reducedMotion) }
                        LaunchedEffect(idx, reducedMotion) {
                            if (reducedMotion) {
                                barStarted = true
                            } else if (!barStarted) {
                                delay((idx * Motion.STAGGER_BAR_MS).toLong())
                                barStarted = true
                            }
                        }

                        val animFraction by animateFloatAsState(
                            targetValue = if (barStarted) targetFraction else 0.01f,
                            animationSpec = Motion.physicalSpring(reducedMotion),
                            label = "coral3dBarHeight_$idx"
                        )

                        // Selected bar's black tooltip bubble pops in with a bouncy scale spring
                        val tooltipScale by animateFloatAsState(
                            targetValue = if (isSelected) 1f else 0f,
                            animationSpec = Motion.popSpring(reducedMotion),
                            label = "tooltipScale_$idx"
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    Motion.performLightTapHaptic(view)
                                    selectedBarIndex = idx
                                }
                                .testTag("comparison_bar_$idx"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // 3D Black tooltip bubble above the selected bar showing the amount
                            Box(
                                modifier = Modifier.height(36.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                if (isSelected || tooltipScale > 0.05f) {
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 2.dp, bottom = 2.dp)
                                            .graphicsLayer {
                                                scaleX = tooltipScale
                                                scaleY = tooltipScale
                                                alpha = tooltipScale.coerceIn(0f, 1f)
                                                transformOrigin = TransformOrigin(0.5f, 1f)
                                            }
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .matchParentSize()
                                                .offset(x = 2.dp, y = 2.dp)
                                                .clip(CircleShape)
                                                .background(SunnyYellow)
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = InkBlack,
                                            contentColor = PureWhite
                                        ) {
                                            Text(
                                                text = MoneyUtils.formatPaise(bar.amountPaise),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 10.sp,
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = PureWhite,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 3D Extruded Bar Prism Column
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                val barShape = RoundedCornerShape(
                                    topStart = 12.dp,
                                    topEnd = 12.dp,
                                    bottomStart = 8.dp,
                                    bottomEnd = 8.dp
                                )
                                val barBrush = if (isSelected) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            EditorialMint, // #9FE3C0 Mint top
                                            PaleMintHero
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            FadedCoralBar,
                                            LighterCoral.copy(alpha = 0.65f)
                                        )
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animFraction.coerceIn(0.01f, 1f))
                                        .padding(end = 4.dp, bottom = 4.dp)
                                ) {
                                    // 3D Extruded Side & Bottom Prism Block
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .offset(x = 4.dp, y = 4.dp)
                                            .clip(barShape)
                                            .background(
                                                if (isSelected) InkBlack else InkBlack.copy(alpha = 0.45f)
                                            )
                                    )

                                    // Front 3D Bar Face
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(barShape)
                                            .background(barBrush)
                                            .border(
                                                width = if (isSelected) 1.6.dp else 1.2.dp,
                                                color = if (isSelected) InkBlack else InkBlack.copy(alpha = 0.45f),
                                                shape = barShape
                                            ),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // 3D Top Bevel Cap Highlight
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .width(16.dp)
                                                .height(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (isSelected) InkBlack else PureWhite.copy(alpha = 0.55f)
                                                )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Bottom label: Selected day/period label in a 3D black pill, others in black text
                            if (isSelected) {
                                Surface(
                                    shape = CircleShape,
                                    color = InkBlack,
                                    contentColor = PureWhite
                                ) {
                                    Text(
                                        text = bar.label.take(4),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.sp
                                        ),
                                        color = PureWhite,
                                        maxLines = 1
                                    )
                                }
                            } else {
                                Text(
                                    text = bar.label.take(4),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 11.sp
                                    ),
                                    color = InkBlack,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (totalSpentPaise <= 0L && displayBars.all { it.amountPaise <= 0L }) {
                    Text(
                        text = "Add your first payment to see this fill up",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp
                        ),
                        color = InkBlack,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .testTag("comparison_bar_chart_empty_hint")
                    )
                }
            }
        }
    }
}

/**
 * 5. REPEATING PAYMENTS (3D Slightly Tilted Cards):
 * - Show each repeating payment as a wide 3D pill-shaped card tilted about 4 degrees, in Coral (#FF6F59),
 *   Sunny Yellow (#FFD23F), or Aqua (#8FE3F0) with an extruded 3D offset base block
 * - Bold uppercase merchant name, a small status pill ("● Confirmed" or "● Suggested"), the next due date,
 *   a 3D black rounded-square tile on the right with the merchant's first letters, and a small arrow.
 * - Text on ALL colored cards is black (#0B0B0B).
 */
@Composable
fun TiltedRepeatingPaymentCard(
    item: RepeatingPaymentEntity,
    index: Int,
    onToggleStateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardPalette = listOf(PaleMintTint, PaleYellowTint)
    val cardColor = cardPalette[index % cardPalette.size]
    val tiltAngle = when (index % 3) {
        0 -> -3.4f
        1 -> -2.4f
        else -> 2.6f
    }
    val isConfirmed = item.state == RepeatingState.CONFIRMED
    val statusLabel = if (isConfirmed) "Confirmed" else "Suggested"
    val statusDotColor = if (isConfirmed) EditorialMint else EditorialCoral
    val nextDueFormatted = SimpleDateFormat("dd MMM", Locale.US).format(Date(item.nextExpectedTimestamp))
    val monogram = item.merchant
        .trim()
        .take(2)
        .ifEmpty { "R" }
    val shape = RoundedCornerShape(26.dp)

    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressFraction by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "tiltedRepeatingPress_${item.id}"
    )
    val pressOffsetX = 5.dp * pressFraction
    val pressOffsetY = 7.dp * pressFraction

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .graphicsLayer {
                rotationZ = tiltAngle
            }
            .testTag("tilted_repeating_card_${item.id}")
    ) {
        // 3D Extruded offset base behind the tilted card
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 5.dp, y = 7.dp)
                .clip(shape)
                .background(InkBlack)
                .border(1.5.dp, InkBlack, shape)
        )

        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onToggleStateClick()
            },
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .offset(x = pressOffsetX, y = pressOffsetY),
            shape = shape,
            color = cardColor,
            contentColor = InkBlack,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.merchant.uppercase(),
                        style = CondensedCardTitleStyle.copy(
                            fontSize = 24.sp,
                            lineHeight = 26.sp
                        ),
                        color = InkBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusDotPill(
                            text = statusLabel,
                            dotColor = statusDotColor,
                            backgroundColor = PureWhite.copy(alpha = 0.88f),
                            textColor = InkBlack
                        )
                        Text(
                            text = "Due $nextDueFormatted · ${MoneyUtils.formatPaise(item.usualAmountPaise)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = InkBlack.copy(alpha = 0.84f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // 3D Black rounded-square tile on the right with the merchant's letters + small arrow
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .offset(x = 2.dp, y = 3.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PureWhite.copy(alpha = 0.65f))
                        )
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(InkBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = monogram,
                                style = CondensedCardTitleStyle.copy(
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = PureWhite
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Manage repeating payment",
                        tint = InkBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


/**
 * 3D Extruded Donut / Pie Chart using the editorial palette (Sunny Yellow, Coral, Aqua, Mint, Periwinkle Indigo)
 * with a 3D bottom-extruded depth ring, inner bevel highlight, and black text on colored cards.
 */
@Composable
fun EditorialDonutChart(
    slices: List<CategorySlice>,
    totalSpentPaise: Long,
    modifier: Modifier = Modifier,
    chartSize: Dp = 176.dp,
    onBrightCard: Boolean = true,
    isOnDarkSurface: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val colors = SnapSpendThemeTokens.colors
    val useDarkSurfaceStyling = colors.isDark && isOnDarkSurface && !onBrightCard
    val textColor = if (useDarkSurfaceStyling) PureWhite else InkBlack
    val subTextColor = if (useDarkSurfaceStyling) SecondaryGreyText else InkBlack.copy(alpha = 0.78f)
    val sliceGapDegrees = if (slices.size > 1) 4f else 0f

    // Donut slices sweep clockwise from the top (-90°) one after another over CHART_MS (600ms)
    val sweepRevealAnim = remember { Animatable(if (reducedMotion) 360f else 0f) }
    LaunchedEffect(slices.isNotEmpty(), totalSpentPaise > 0L, reducedMotion) {
        if (reducedMotion) {
            sweepRevealAnim.snapTo(360f)
        } else if (slices.isNotEmpty() && totalSpentPaise > 0L) {
            if (sweepRevealAnim.value < 10f) {
                sweepRevealAnim.snapTo(0f)
                sweepRevealAnim.animateTo(
                    targetValue = 360f,
                    animationSpec = Motion.chartTween(reducedMotion = false)
                )
            } else {
                sweepRevealAnim.snapTo(360f)
            }
        }
    }

    val animatedCenterSpentText = rememberAnimatedPaise(targetPaise = totalSpentPaise)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Box(
            modifier = Modifier.size(chartSize),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val strokeWidth = 28.dp.toPx()
                val depthDx = 3.dp.toPx()
                val depthDy = 6.dp.toPx()
                val diameter = size.minDimension - strokeWidth - depthDy
                val topLeft = Offset(
                    (size.width - diameter) / 2f - depthDx / 2f,
                    (size.height - diameter) / 2f - depthDy / 2f
                )
                val shadowTopLeft = Offset(topLeft.x + depthDx, topLeft.y + depthDy)
                val arcSize = Size(diameter, diameter)
                val maxSweepBudget = sweepRevealAnim.value.coerceIn(0f, 360f)

                if (slices.isEmpty() || totalSpentPaise <= 0L) {
                    val dashedStroke = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(22f, 14f), 0f)
                    )
                    // 3D Extruded empty dashed ring shadow
                    drawArc(
                        color = InkBlack.copy(alpha = 0.28f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = shadowTopLeft,
                        size = arcSize,
                        style = dashedStroke
                    )
                    // Neutral grey dashed ring instead of mint ring when empty
                    drawArc(
                        color = if (useDarkSurfaceStyling) Color(0xFF5A5A6A) else Color(0xFF9A9AA6),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = dashedStroke
                    )
                } else {
                    // 1. 3D Extruded Lower Depth Ring (sweeps sequentially clockwise from -90°)
                    var shadowStartAngle = -90f
                    var consumedShadowSweep = 0f
                    slices.forEach { slice ->
                        val rawSweep = (slice.amountPaise.toFloat() / totalSpentPaise.toFloat()) * 360f
                        val allowedRawSweep = (maxSweepBudget - consumedShadowSweep).coerceIn(0f, rawSweep)
                        if (allowedRawSweep > 0.5f) {
                            val sweep = (allowedRawSweep - sliceGapDegrees).coerceAtLeast(1.5f)
                            val baseSliceColor = parseEditorialColor(slice.colorHex)
                            val extrudedShade = Color(
                                red = (baseSliceColor.red * 0.45f).coerceIn(0f, 1f),
                                green = (baseSliceColor.green * 0.45f).coerceIn(0f, 1f),
                                blue = (baseSliceColor.blue * 0.45f).coerceIn(0f, 1f),
                                alpha = 1f
                            )
                            drawArc(
                                color = extrudedShade,
                                startAngle = shadowStartAngle + sliceGapDegrees / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = shadowTopLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                        }
                        shadowStartAngle += rawSweep
                        consumedShadowSweep += rawSweep
                    }

                    // 2. Main Elevated Top 3D Donut Ring (sweeps sequentially clockwise from -90°)
                    var startAngle = -90f
                    var consumedTopSweep = 0f
                    slices.forEach { slice ->
                        val rawSweep = (slice.amountPaise.toFloat() / totalSpentPaise.toFloat()) * 360f
                        val allowedRawSweep = (maxSweepBudget - consumedTopSweep).coerceIn(0f, rawSweep)
                        if (allowedRawSweep > 0.5f) {
                            val sweep = (allowedRawSweep - sliceGapDegrees).coerceAtLeast(1.5f)
                            val sliceColor = parseEditorialColor(slice.colorHex)
                            drawArc(
                                color = sliceColor,
                                startAngle = startAngle + sliceGapDegrees / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            // Subtle 3D specular highlight on each slice
                            drawArc(
                                color = PureWhite.copy(alpha = 0.35f),
                                startAngle = startAngle + sliceGapDegrees / 2f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Butt)
                            )
                        }
                        startAngle += rawSweep
                        consumedTopSweep += rawSweep
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "SPENT",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = subTextColor
                )
                Text(
                    text = animatedCenterSpentText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum"
                    ),
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (slices.isEmpty()) {
                Text(
                    text = "No category data yet",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = subTextColor
                )
            } else {
                slices.take(5).forEach { slice ->
                    val dotColor = parseEditorialColor(slice.colorHex)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                                    .border(1.dp, InkBlack, CircleShape)
                            )
                            Text(
                                text = slice.categoryName,
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${slice.percentage}%",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFeatureSettings = "tnum"
                            ),
                            color = subTextColor
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditorialLineTrendCard(
    points: List<LineChartPoint>,
    granularity: String,
    onGranularityChange: (String) -> Unit,
    monthlyBudgetAveragePaise: Long = 0L,
    onSetBudgetClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val maxAmt = points.maxOfOrNull { it.amountPaise } ?: 0L
    val dayChips = points.take(7).map { pt ->
        DaySpendChipData(
            dayShort = pt.label.take(3),
            amountPaise = pt.amountPaise,
            percentOfPeakOrTotal = if (maxAmt > 0L) ((pt.amountPaise * 100L) / maxAmt).toInt().coerceIn(0, 100) else 0
        )
    }
    val nextGranularity = when (granularity) {
        "DAILY" -> "WEEKLY"
        "WEEKLY" -> "MONTHLY"
        else -> "DAILY"
    }
    val periodLabel = when (granularity) {
        "DAILY" -> "This Week"
        "WEEKLY" -> "4 Weeks"
        else -> "Monthly"
    }

    SpendingOverTimeAreaChartCard(
        title = "SPENDING OVER TIME",
        periodLabel = periodLabel,
        dayEntries = dayChips,
        monthlyBudgetAveragePaise = monthlyBudgetAveragePaise,
        onPeriodDropdownClick = { onGranularityChange(nextGranularity) },
        onSetBudgetClick = onSetBudgetClick,
        modifier = modifier
    )
}

@Composable
fun IncomeVsExpenseMonthlyCard(
    bars: List<MonthlyIncomeExpenseBar>,
    modifier: Modifier = Modifier
) {
    val maxPaise = bars.maxOfOrNull { maxOf(it.earnedPaise, it.spentPaise) }?.coerceAtLeast(100L) ?: 100L
    val cardBg = PaleLavenderTint
    val textColor = InkBlack
    val subColor = InkBlack.copy(alpha = 0.78f)
    val shape = RoundedCornerShape(28.dp)

    Editorial3DLayerBox(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp,
        enableInteractiveTilt = true
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = cardBg,
            contentColor = textColor,
            border = BorderStroke(
                width = 1.8.dp,
                color = InkBlack
            )
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "IN VS OUT",
                            style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                            color = textColor
                        )
                        Text(
                            text = "Monthly earnings vs spending",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = subColor
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDotPill(
                            text = "In",
                            dotColor = EditorialMint,
                            backgroundColor = PureWhite,
                            textColor = InkBlack
                        )
                        StatusDotPill(
                            text = "Out",
                            dotColor = EditorialCoral,
                            backgroundColor = PureWhite,
                            textColor = InkBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                val reducedMotion = LocalReducedMotion.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    bars.forEachIndexed { idx, item ->
                        val targetInRatio = if (item.earnedPaise > 0L) {
                            (item.earnedPaise.toFloat() / maxPaise.toFloat()).coerceIn(0.12f, 1f)
                        } else {
                            0.08f
                        }
                        val targetOutRatio = if (item.spentPaise > 0L) {
                            (item.spentPaise.toFloat() / maxPaise.toFloat()).coerceIn(0.12f, 1f)
                        } else {
                            0.08f
                        }

                        var barStarted by remember { mutableStateOf(reducedMotion) }
                        LaunchedEffect(idx, reducedMotion) {
                            if (reducedMotion) {
                                barStarted = true
                            } else if (!barStarted) {
                                delay((idx * Motion.STAGGER_BAR_MS).toLong())
                                barStarted = true
                            }
                        }

                        val inRatio by animateFloatAsState(
                            targetValue = if (barStarted) targetInRatio else 0.01f,
                            animationSpec = Motion.physicalSpring(reducedMotion),
                            label = "inBarRatio_$idx"
                        )
                        val outRatio by animateFloatAsState(
                            targetValue = if (barStarted) targetOutRatio else 0.01f,
                            animationSpec = Motion.physicalSpring(reducedMotion),
                            label = "outBarRatio_$idx"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 3D Mint "In" Column
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(inRatio.coerceIn(0.01f, 1f))
                                        .padding(end = 2.dp, bottom = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .offset(x = 2.dp, y = 2.dp)
                                            .clip(CircleShape)
                                            .background(InkBlack)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(EditorialMint)
                                            .border(1.dp, InkBlack, CircleShape)
                                    )
                                }
                                // 3D Coral "Out" Column
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .fillMaxHeight(outRatio.coerceIn(0.01f, 1f))
                                        .padding(end = 2.dp, bottom = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .offset(x = 2.dp, y = 2.dp)
                                            .clip(CircleShape)
                                            .background(InkBlack)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(EditorialCoral)
                                            .border(1.dp, InkBlack, CircleShape)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.monthLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = subColor
                            )
                        }
                    }
                }
            }
        }
    }
}
