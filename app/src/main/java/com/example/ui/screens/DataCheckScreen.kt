package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ScanLogEntry
import com.example.data.local.ScanPath
import com.example.data.local.TransactionEntity
import com.example.ui.ScanLabItemResult
import com.example.ui.ScanLabUiState
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.TinySectionLabel
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.IncomeDarkGreen
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleAquaTint
import com.example.ui.theme.PaleLavenderTint
import com.example.ui.theme.PaleMintTint
import com.example.ui.theme.PalePinkTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Single month row in the Data Check "MONTH BY MONTH (last 12 months)" section.
 * Computed exclusively via plain SQL SUM and COUNT queries on the database.
 */
data class DataCheckMonthRow(
    val monthLabel: String, // e.g. "Oct 2026"
    val startMillis: Long,
    val endMillis: Long,
    val moneyInPaise: Long,
    val moneyOutPaise: Long,
    val paymentCount: Int
)

/**
 * Complete read-only snapshot of Data Check SQL diagnostics.
 */
data class DataCheckSnapshot(
    val totalActiveCount: Int = 0,
    val successCount: Int = 0,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val transferCount: Int = 0,
    val trashedCount: Int = 0,
    val onlineActiveCount: Int = 0,
    val cashActiveCount: Int = 0,
    val thisMonthSpentSqlPaise: Long = 0L,
    val monthlyRows: List<DataCheckMonthRow> = emptyList(),
    val last10SavedRows: List<TransactionEntity> = emptyList(),
    val scanTimingLogs: List<ScanLogEntry> = emptyList()
) {
    /**
     * Builds the complete plain-text diagnostic report for the COPY REPORT button.
     */
    fun buildPlainTextReport(homeThisMonthSpentPaise: Long): String {
        val isMatch = thisMonthSpentSqlPaise == homeThisMonthSpentPaise
        val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        return buildString {
            appendLine("=== SNAPSPEND DATA CHECK REPORT ===")
            appendLine()
            appendLine("1. COUNTS")
            appendLine("Total transactions (active): $totalActiveCount")
            appendLine("Success: $successCount")
            appendLine("Pending: $pendingCount")
            appendLine("Failed: $failedCount")
            appendLine("Transfer: $transferCount")
            appendLine("Trashed: $trashedCount")
            appendLine("Online (active): $onlineActiveCount")
            appendLine("Cash (active): $cashActiveCount")
            appendLine()
            appendLine("2. COMPARE WITH HOME (THIS MONTH SPENT)")
            appendLine("Data Check (SQL): ${MoneyUtils.formatExactPaise(thisMonthSpentSqlPaise)} ($thisMonthSpentSqlPaise paise)")
            appendLine("Home Screen: ${MoneyUtils.formatExactPaise(homeThisMonthSpentPaise)} ($homeThisMonthSpentPaise paise)")
            if (isMatch) {
                appendLine("Status: MATCH")
            } else {
                appendLine(
                    "Status: MISMATCH (Data Check: ${MoneyUtils.formatExactPaise(thisMonthSpentSqlPaise)} vs Home: ${MoneyUtils.formatExactPaise(homeThisMonthSpentPaise)})"
                )
            }
            appendLine()
            appendLine("3. MONTH BY MONTH (LAST 12 MONTHS)")
            if (monthlyRows.isEmpty()) {
                appendLine("(No months loaded)")
            } else {
                monthlyRows.forEach { row ->
                    appendLine(
                        "${row.monthLabel}: In ${MoneyUtils.formatExactPaise(row.moneyInPaise)} | Out ${MoneyUtils.formatExactPaise(row.moneyOutPaise)} | Payments: ${row.paymentCount}"
                    )
                }
            }
            appendLine()
            appendLine("4. LAST 10 SAVED ROWS")
            if (last10SavedRows.isEmpty()) {
                appendLine("(No saved rows)")
            } else {
                last10SavedRows.forEach { tx ->
                    val formattedDate = dateTimeFormat.format(Date(tx.timestamp))
                    appendLine(
                        "id=${tx.id} | date=$formattedDate (${tx.timestamp}) | amountPaise=${tx.amountPaise} | type=${tx.type.name} | status=${tx.status.name} | paymentMode=${tx.paymentMode} | merchant=${tx.merchant} | category=${tx.category} | source=${tx.source.name} | referenceNumber=${tx.referenceNumber ?: "null"} | duplicateKey=${tx.duplicateKey}"
                    )
                }
            }
            appendLine()
            appendLine("5. SCAN TIMINGS (LAST 50 SCANS, LOCAL ONLY)")
            if (scanTimingLogs.isEmpty()) {
                appendLine("(No scan timings recorded yet)")
            } else {
                val totalScans = scanTimingLogs.size
                val avgTimeMs = scanTimingLogs.map { it.totalMs }.average().roundToInt()
                val avgTokens = scanTimingLogs.map { it.totalTokens }.average().roundToInt()
                appendLine("Total logged scans: $totalScans")
                appendLine("Average time per scan: ${avgTimeMs}ms")
                appendLine("Average tokens per scan: $avgTokens")
                val pathBreakdown = ScanPath.entries.joinToString(" | ") { path ->
                    val count = scanTimingLogs.count { it.path == path }
                    val pct = ((count.toDouble() / totalScans.toDouble()) * 100.0).roundToInt()
                    "${path.name}: $pct% ($count)"
                }
                appendLine("Path breakdown: $pathBreakdown")
                appendLine("Table (path | readMs | ocrMs | regexMs | textAiMs | visionMs | totalMs | retries | promptTok | outTok | filterTok):")
                scanTimingLogs.forEachIndexed { idx, entry ->
                    val filterTokStr = if (entry.preFilterTokens > 0) "${entry.preFilterTokens}->${entry.postFilterTokens}" else "-"
                    appendLine(
                        "#${idx + 1} | ${entry.path.name} | read=${entry.readFileMs}ms | ocr=${entry.ocrTextMs}ms | regex=${entry.regexMs}ms | textAi=${entry.geminiTextMs}ms | vision=${entry.visionFallbackMs}ms | total=${entry.totalMs}ms | retries=${entry.retries} | promptTok=${entry.promptTokens} | outTok=${entry.outputTokens} | filterTok=$filterTokStr"
                    )
                }
            }
        }.trimEnd()
    }
}

/**
 * Computes exact local-timezone calendar start-of-month (00:00:00.000 on the 1st)
 * and end-of-month (23:59:59.999 on the last day of the month) bounds so Data Check's
 * SQL `BETWEEN :startMillis AND :endMillis` queries use the exact same millisecond bounds
 * as the Home screen.
 */
fun computeDataCheckMonthBounds(anchorMillis: Long, monthOffset: Int): Pair<Long, Long> {
    val cal = Calendar.getInstance().apply {
        timeInMillis = anchorMillis
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, monthOffset)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startMillis = cal.timeInMillis
    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
    cal.set(Calendar.HOUR_OF_DAY, 23)
    cal.set(Calendar.MINUTE, 59)
    cal.set(Calendar.SECOND, 59)
    cal.set(Calendar.MILLISECOND, 999)
    return Pair(startMillis, cal.timeInMillis)
}

@Composable
fun DataCheckScreen(
    snapshot: DataCheckSnapshot,
    homeThisMonthSpentPaise: Long,
    onBackClick: () -> Unit,
    onReportCopied: (String) -> Unit = {},
    scanLabState: ScanLabUiState = ScanLabUiState(),
    onPickScanLabFilesClick: () -> Unit = {},
    onMarkScanLabVerdict: (String, Boolean) -> Unit = { _, _ -> },
    onClearScanLab: () -> Unit = {},
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val colors = SnapSpendThemeTokens.colors

    val isMatch = snapshot.thisMonthSpentSqlPaise == homeThisMonthSpentPaise
    val exactTimestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(animationSpec = Motion.physicalIntSizeSpring(reducedMotion))
            .testTag("data_check_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header Row: Back Button + Title + COPY REPORT Button
        item(key = "data_check_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Editorial3DLayerBox(
                        shape = CircleShape,
                        extrusionColor = if (colors.isDark) SunnyYellow else InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 2.5.dp,
                        depthY = 3.5.dp
                    ) {
                        Surface(
                            onClick = {
                                Motion.performLightTapHaptic(view)
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("data_check_back_button"),
                            shape = CircleShape,
                            color = PureWhite,
                            contentColor = InkBlack,
                            border = BorderStroke(1.8.dp, InkBlack)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Settings",
                                    tint = InkBlack,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "DATA CHECK",
                            style = CondensedCardTitleStyle.copy(fontSize = 30.sp),
                            color = colors.primaryText
                        )
                        Text(
                            text = "Read-only plain SQL audit of stored transactions",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.secondaryText
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Editorial3DLayerBox(
                    shape = CircleShape,
                    extrusionColor = InkBlack,
                    extrusionBorderColor = InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        onClick = {
                            Motion.performLightTapHaptic(view)
                            val reportText = snapshot.buildPlainTextReport(homeThisMonthSpentPaise)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            clipboard?.setPrimaryClip(ClipData.newPlainText("SnapSpend Data Check Report", reportText))
                            onReportCopied(reportText)
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("copy_report_button"),
                        shape = CircleShape,
                        color = SunnyYellow,
                        contentColor = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy report",
                                tint = InkBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "COPY REPORT",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = InkBlack
                            )
                        }
                    }
                }
            }
        }

        // Section D (Prominently at top & part of the 4 sections): COMPARE WITH HOME
        item(key = "data_check_compare_home") {
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_check_compare_card"),
                shape = RoundedCornerShape(28.dp),
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = PaleLavenderTint,
                    contentColor = InkBlack,
                    border = BorderStroke(1.8.dp, InkBlack)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                TinySectionLabel(
                                    text = "PLAIN SQL VS HOME SCREEN",
                                    color = InkBlack.copy(alpha = 0.72f)
                                )
                                Text(
                                    text = "COMPARE WITH HOME",
                                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                                    color = InkBlack
                                )
                            }

                            // Mint "MATCH" pill when equal, or Coral "MISMATCH" pill with both numbers
                            Surface(
                                shape = CircleShape,
                                color = if (isMatch) EditorialMint else EditorialCoral,
                                contentColor = InkBlack,
                                border = BorderStroke(1.6.dp, InkBlack),
                                modifier = Modifier.testTag("data_check_match_pill")
                            ) {
                                Text(
                                    text = if (isMatch) {
                                        "MATCH"
                                    } else {
                                        "MISMATCH · ${MoneyUtils.formatExactPaise(snapshot.thisMonthSpentSqlPaise)} vs ${MoneyUtils.formatExactPaise(homeThisMonthSpentPaise)}"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = InkBlack,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
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
                                    .testTag("data_check_sql_spent_box"),
                                shape = RoundedCornerShape(18.dp),
                                color = PureWhite,
                                border = BorderStroke(1.5.dp, InkBlack)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Data Check (SQL)",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = InkBlack.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = MoneyUtils.formatExactPaise(snapshot.thisMonthSpentSqlPaise),
                                        style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                        color = InkBlack
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("data_check_home_spent_box"),
                                shape = RoundedCornerShape(18.dp),
                                color = PureWhite,
                                border = BorderStroke(1.5.dp, InkBlack)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Text(
                                        text = "Home Screen",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = InkBlack.copy(alpha = 0.75f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = MoneyUtils.formatExactPaise(homeThisMonthSpentPaise),
                                        style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                        color = InkBlack
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section A: COUNTS
        item(key = "data_check_counts") {
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_check_counts_card"),
                shape = RoundedCornerShape(28.dp),
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = PaleAquaTint,
                    contentColor = InkBlack,
                    border = BorderStroke(1.8.dp, InkBlack)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TinySectionLabel(
                            text = "MUTUALLY EXCLUSIVE SQL COUNTS",
                            color = InkBlack.copy(alpha = 0.72f)
                        )
                        Text(
                            text = "COUNTS",
                            style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                            color = InkBlack
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CountStatTile(
                                label = "Total Active",
                                value = snapshot.totalActiveCount.toString(),
                                bgColor = PureWhite,
                                testTag = "count_total_active",
                                modifier = Modifier.weight(1f)
                            )
                            CountStatTile(
                                label = "Success",
                                value = snapshot.successCount.toString(),
                                bgColor = PaleMintTint,
                                testTag = "count_success",
                                modifier = Modifier.weight(1f)
                            )
                            CountStatTile(
                                label = "Pending",
                                value = snapshot.pendingCount.toString(),
                                bgColor = SunnyYellow,
                                testTag = "count_pending",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CountStatTile(
                                label = "Failed",
                                value = snapshot.failedCount.toString(),
                                bgColor = PalePinkTint,
                                testTag = "count_failed",
                                modifier = Modifier.weight(1f)
                            )
                            CountStatTile(
                                label = "Transfer",
                                value = snapshot.transferCount.toString(),
                                bgColor = PureWhite,
                                testTag = "count_transfer",
                                modifier = Modifier.weight(1f)
                            )
                            CountStatTile(
                                label = "Trashed",
                                value = snapshot.trashedCount.toString(),
                                bgColor = PalePinkTint,
                                testTag = "count_trashed",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CountStatTile(
                                label = "Online (Active)",
                                value = snapshot.onlineActiveCount.toString(),
                                bgColor = PureWhite,
                                testTag = "count_online_active",
                                modifier = Modifier.weight(1f)
                            )
                            CountStatTile(
                                label = "Cash (Active)",
                                value = snapshot.cashActiveCount.toString(),
                                bgColor = SunnyYellow,
                                testTag = "count_cash_active",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Section B: MONTH BY MONTH (LAST 12 MONTHS)
        item(key = "data_check_monthly") {
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_check_monthly_card"),
                shape = RoundedCornerShape(28.dp),
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = PaleMintTint,
                    contentColor = InkBlack,
                    border = BorderStroke(1.8.dp, InkBlack)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TinySectionLabel(
                            text = "SUCCESS ONLY · EXCLUDES FAILED, PENDING & TRANSFER",
                            color = InkBlack.copy(alpha = 0.72f)
                        )
                        Text(
                            text = "MONTH BY MONTH (LAST 12 MONTHS)",
                            style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                            color = InkBlack
                        )

                        snapshot.monthlyRows.forEachIndexed { idx, row ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("data_check_month_row_$idx"),
                                shape = RoundedCornerShape(16.dp),
                                color = PureWhite,
                                border = BorderStroke(1.4.dp, InkBlack)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = row.monthLabel.uppercase(),
                                            style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                            color = InkBlack
                                        )
                                        StatusPillBadge(
                                            text = "${row.paymentCount} payment${if (row.paymentCount == 1) "" else "s"}",
                                            backgroundColor = PaleLavenderTint
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "In: ${MoneyUtils.formatExactPaise(row.moneyInPaise)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = IncomeDarkGreen
                                        )
                                        Text(
                                            text = "Out: ${MoneyUtils.formatExactPaise(row.moneyOutPaise)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFeatureSettings = "tnum"
                                            ),
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

        // Section C Header: LAST 10 SAVED ROWS
        item(key = "data_check_last10_header") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(SunnyYellow)
                        .border(1.5.dp, InkBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.FactCheck,
                        contentDescription = null,
                        tint = InkBlack,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "LAST 10 SAVED ROWS",
                        style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                        color = colors.primaryText
                    )
                    Text(
                        text = "Shown exactly as stored in SQLite (amount in integer paise)",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.secondaryText
                    )
                }
            }
        }

        if (snapshot.last10SavedRows.isEmpty()) {
            item(key = "data_check_last10_empty") {
                Editorial3DLayerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("data_check_empty_rows_card"),
                    shape = RoundedCornerShape(22.dp),
                    extrusionColor = InkBlack,
                    extrusionBorderColor = InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack)
                    ) {
                        Text(
                            text = "No saved transactions in the database yet.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = InkBlack,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
        } else {
            items(
                items = snapshot.last10SavedRows,
                key = { "raw_row_${it.id}" }
            ) { tx ->
                RawStoredTransactionCard(
                    tx = tx,
                    formattedDateTime = exactTimestampFormatter.format(Date(tx.timestamp))
                )
            }
        }

        // Section 5: SCAN TIMINGS (Local-only telemetry, last 50 scans)
        item(key = "data_check_scan_timings_card") {
            ScanTimingsDiagnosticCard(logs = snapshot.scanTimingLogs)
        }

        // Section 6: SCAN LAB (Interactive pipeline tester for up to 20 files, zero DB writes)
        item(key = "data_check_scan_lab_card") {
            ScanLabDiagnosticCard(
                scanLabState = scanLabState,
                onPickScanLabFilesClick = onPickScanLabFilesClick,
                onMarkScanLabVerdict = onMarkScanLabVerdict,
                onClearScanLab = onClearScanLab
            )
        }
    }
}

@Composable
private fun ScanTimingsDiagnosticCard(
    logs: List<ScanLogEntry>,
    modifier: Modifier = Modifier
) {
    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_check_scan_timings_card"),
        shape = RoundedCornerShape(28.dp),
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = PaleAquaTint,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TinySectionLabel(
                    text = "SECTION 5 · LOCAL TELEMETRY (LAST 50 SCANS)",
                    color = InkBlack.copy(alpha = 0.72f)
                )
                Text(
                    text = "SCAN TIMINGS",
                    style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                    color = InkBlack
                )
                Text(
                    text = "Zero receipt text, merchant names, or amounts are ever logged.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = InkBlack.copy(alpha = 0.78f)
                )

                if (logs.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No scans recorded yet. Scan a screenshot or PDF to inspect stage timings and token counts.",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = InkBlack,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    val totalScans = logs.size
                    val avgTimeMs = logs.map { it.totalMs }.average().roundToInt()
                    val avgTokens = logs.map { it.totalTokens }.average().roundToInt()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CountStatTile(
                            label = "SCANS LOGGED",
                            value = totalScans.toString(),
                            bgColor = PureWhite,
                            testTag = "scan_timings_total_count",
                            modifier = Modifier.weight(1f)
                        )
                        CountStatTile(
                            label = "AVG TIME",
                            value = "${avgTimeMs}ms",
                            bgColor = SunnyYellow,
                            testTag = "scan_timings_avg_time",
                            modifier = Modifier.weight(1f)
                        )
                        CountStatTile(
                            label = "AVG TOKENS",
                            value = avgTokens.toString(),
                            bgColor = EditorialMint,
                            testTag = "scan_timings_avg_tokens",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Path percentages
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "PATH BREAKDOWN (%)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack.copy(alpha = 0.75f)
                            )
                            ScanPath.entries.forEach { path ->
                                val count = logs.count { it.path == path }
                                val pct = ((count.toDouble() / totalScans.toDouble()) * 100.0).roundToInt()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = path.name,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = InkBlack
                                    )
                                    Text(
                                        text = "$pct% ($count)",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = InkBlack
                                    )
                                }
                            }
                        }
                    }

                    // Table of recent scans
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.5.dp, InkBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "STAGE TIMINGS TABLE (READ / OCR / REGEX / AI / VISION / TOTAL · TOKENS)",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack.copy(alpha = 0.75f)
                            )
                            logs.take(20).forEachIndexed { idx, entry ->
                                if (idx > 0) {
                                    HorizontalDivider(thickness = 1.dp, color = InkBlack.copy(alpha = 0.12f))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "#${idx + 1} · ${entry.path.name}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.ExtraBold
                                            ),
                                            color = InkBlack
                                        )
                                        Text(
                                            text = "${entry.totalMs}ms · ${entry.totalTokens} tok (r=${entry.retries})",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = InkBlack
                                        )
                                    }
                                    val filterSummary = if (entry.preFilterTokens > 0) {
                                        " | filter:${entry.preFilterTokens}->${entry.postFilterTokens}"
                                    } else {
                                        ""
                                    }
                                    Text(
                                        text = "read:${entry.readFileMs}ms | ocr:${entry.ocrTextMs}ms | rgx:${entry.regexMs}ms | ai:${entry.geminiTextMs}ms | vis:${entry.visionFallbackMs}ms | in:${entry.promptTokens}/out:${entry.outputTokens}$filterSummary",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        ),
                                        color = InkBlack.copy(alpha = 0.78f)
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

@Composable
private fun ScanLabDiagnosticCard(
    scanLabState: ScanLabUiState,
    onPickScanLabFilesClick: () -> Unit,
    onMarkScanLabVerdict: (String, Boolean) -> Unit,
    onClearScanLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_check_scan_lab_card"),
        shape = RoundedCornerShape(28.dp),
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = PaleLavenderTint,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TinySectionLabel(
                            text = "SECTION 6 · BENCHMARK UP TO 20 FILES",
                            color = InkBlack.copy(alpha = 0.72f)
                        )
                        Text(
                            text = "SCAN LAB",
                            style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                            color = InkBlack
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (scanLabState.items.isNotEmpty() && !scanLabState.isRunning) {
                            Surface(
                                onClick = {
                                    Motion.performLightTapHaptic(view)
                                    onClearScanLab()
                                },
                                shape = CircleShape,
                                color = PureWhite,
                                contentColor = InkBlack,
                                border = BorderStroke(1.5.dp, InkBlack),
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Text(
                                    text = "CLEAR",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = InkBlack,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }
                        }
                        Editorial3DLayerBox(
                            shape = CircleShape,
                            extrusionColor = InkBlack,
                            extrusionBorderColor = InkBlack,
                            depthX = 2.dp,
                            depthY = 3.dp
                        ) {
                            Surface(
                                onClick = {
                                    if (!scanLabState.isRunning) {
                                        Motion.performLightTapHaptic(view)
                                        onPickScanLabFilesClick()
                                    }
                                },
                                shape = CircleShape,
                                color = SunnyYellow,
                                contentColor = InkBlack,
                                border = BorderStroke(1.8.dp, InkBlack),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("scan_lab_pick_button")
                            ) {
                                Text(
                                    text = if (scanLabState.isRunning) {
                                        "RUNNING ${scanLabState.processedCount}/${scanLabState.totalCount}..."
                                    } else {
                                        "SCAN LAB"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = InkBlack,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Pick up to 20 screenshots or PDFs to test the real pipeline. Nothing is saved to transactions and files are never copied.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                    color = InkBlack.copy(alpha = 0.78f)
                )

                if (scanLabState.items.isNotEmpty()) {
                    scanLabState.items.forEach { item ->
                        ScanLabItemRowCard(
                            item = item,
                            onMarkVerdict = { isCorrect ->
                                onMarkScanLabVerdict(item.id, isCorrect)
                            }
                        )
                    }

                    // Summary footer: percent correct, average time, average tokens
                    val pctCorrect = scanLabState.percentCorrect
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CountStatTile(
                            label = "PERCENT CORRECT",
                            value = if (pctCorrect != null) "$pctCorrect%" else "—",
                            bgColor = EditorialMint,
                            testTag = "scan_lab_percent_correct",
                            modifier = Modifier.weight(1f)
                        )
                        CountStatTile(
                            label = "AVG TIME",
                            value = "${scanLabState.averageTimeMs}ms",
                            bgColor = SunnyYellow,
                            testTag = "scan_lab_avg_time",
                            modifier = Modifier.weight(1f)
                        )
                        CountStatTile(
                            label = "AVG TOKENS",
                            value = scanLabState.averageTokens.toString(),
                            bgColor = PureWhite,
                            testTag = "scan_lab_avg_tokens",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanLabItemRowCard(
    item: ScanLabItemResult,
    onMarkVerdict: (Boolean) -> Unit
) {
    val view = LocalView.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = PureWhite,
        contentColor = InkBlack,
        border = BorderStroke(1.5.dp, InkBlack),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = InkBlack,
                    modifier = Modifier.weight(1f)
                )
                StatusPillBadge(
                    text = "${item.path.name} · ${item.totalMs}ms · ${item.totalTokens} tok",
                    backgroundColor = EditorialAqua
                )
            }

            if (item.errorMessage != null) {
                Text(
                    text = "Error: ${item.errorMessage}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = EditorialCoral
                )
            } else {
                Text(
                    text = "${item.merchant} · ${MoneyUtils.formatExactPaise(item.amountPaise)} · ${item.dateFormatted} · ${item.category}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = InkBlack
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onMarkVerdict(true)
                    },
                    shape = CircleShape,
                    color = if (item.isCorrect == true) EditorialMint else PureWhite,
                    contentColor = InkBlack,
                    border = BorderStroke(1.5.dp, InkBlack),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("scan_lab_correct_${item.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = if (item.isCorrect == true) "✓ CORRECT" else "CORRECT",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = InkBlack
                        )
                    }
                }
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onMarkVerdict(false)
                    },
                    shape = CircleShape,
                    color = if (item.isCorrect == false) EditorialCoral else PureWhite,
                    contentColor = InkBlack,
                    border = BorderStroke(1.5.dp, InkBlack),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag("scan_lab_wrong_${item.id}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = if (item.isCorrect == false) "✗ WRONG" else "WRONG",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                            color = InkBlack
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountStatTile(
    label: String,
    value: String,
    bgColor: androidx.compose.ui.graphics.Color,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        color = bgColor,
        contentColor = InkBlack,
        border = BorderStroke(1.5.dp, InkBlack)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = InkBlack.copy(alpha = 0.78f),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                color = InkBlack
            )
        }
    }
}

@Composable
private fun RawStoredTransactionCard(
    tx: TransactionEntity,
    formattedDateTime: String,
    modifier: Modifier = Modifier
) {
    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("data_check_raw_row_${tx.id}"),
        shape = RoundedCornerShape(22.dp),
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 3.dp,
        depthY = 5.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = PureWhite,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROW #${tx.id} · ${tx.merchant}",
                        style = CondensedCardTitleStyle.copy(fontSize = 19.sp),
                        color = InkBlack
                    )
                    StatusPillBadge(
                        text = "${tx.amountPaise} paise (${MoneyUtils.formatExactPaise(tx.amountPaise)})",
                        backgroundColor = EditorialAqua
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    thickness = 1.dp,
                    color = InkBlack.copy(alpha = 0.15f)
                )

                RawKeyValueLine(key = "id", value = tx.id.toString())
                RawKeyValueLine(key = "date & time", value = "$formattedDateTime (${tx.timestamp})")
                RawKeyValueLine(key = "amountPaise", value = "${tx.amountPaise}")
                RawKeyValueLine(key = "type", value = tx.type.name)
                RawKeyValueLine(key = "status", value = tx.status.name)
                RawKeyValueLine(key = "paymentMode", value = tx.paymentMode)
                RawKeyValueLine(key = "merchant", value = tx.merchant)
                RawKeyValueLine(key = "category", value = tx.category)
                RawKeyValueLine(key = "source", value = tx.source.name)
                RawKeyValueLine(key = "referenceNumber", value = tx.referenceNumber ?: "null")
                RawKeyValueLine(key = "duplicateKey", value = tx.duplicateKey)
            }
        }
    }
}

@Composable
private fun RawKeyValueLine(
    key: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$key:",
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold
            ),
            color = InkBlack.copy(alpha = 0.72f),
            modifier = Modifier.width(128.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            ),
            color = InkBlack
        )
    }
}
