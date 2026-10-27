package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import com.example.ui.sound.LocalRetroSoundManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.local.CategoryType
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.ui.SnapSpendUiState
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.ResponsiveTabularNumberText
import com.example.ui.components.StatusDotPill
import com.example.ui.components.UppercaseScreenHeader
import com.example.ui.components.categoryIconVector
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.CondensedScreenTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.EditorialPink
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.MarkScreenEntrancePlayedEffect
import com.example.ui.theme.Motion
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.dialogScaleFadeEntrance
import com.example.ui.theme.rememberAnimatedPaise
import com.example.ui.theme.staggeredScreenEntrance
import com.example.util.MoneyUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class DeckCardPalette(
    val backgroundColor: Color,
    val contentColor: Color,
    val isDarkCard: Boolean
)

private val DeckCardPalettes = listOf(
    DeckCardPalette(backgroundColor = EditorialCoral, contentColor = InkBlack, isDarkCard = false), // Coral (#FF6F59)
    DeckCardPalette(backgroundColor = InkBlack, contentColor = PureWhite, isDarkCard = true),       // Black (#0B0B0B)
    DeckCardPalette(backgroundColor = SunnyYellow, contentColor = InkBlack, isDarkCard = false),    // Sunny Yellow (#FFD23F)
    DeckCardPalette(backgroundColor = EditorialAqua, contentColor = InkBlack, isDarkCard = false),  // Aqua (#8FE3F0)
    DeckCardPalette(backgroundColor = EditorialPink, contentColor = InkBlack, isDarkCard = false),  // Pink (#FF9EC7)
    DeckCardPalette(backgroundColor = EditorialMint, contentColor = InkBlack, isDarkCard = false)   // Mint (#9FE3C0)
)

private data class FolderCategoryItem(
    val categoryName: String,
    val categoryType: CategoryType,
    val categoryDotColor: Color,
    val deckPalette: DeckCardPalette,
    val spentPaise: Long,
    val percentageOfTotal: Int,
    val budgetLimitPaise: Long?,
    val budgetUsagePercent: Int?,
    val recentTransactions: List<TransactionEntity>
)

private fun parseCategoryColorSafe(colorHex: String?): Color {
    if (colorHex.isNullOrBlank()) return EditorialCoral
    return try {
        val clean = colorHex.trim().removePrefix("#")
        val parsed = clean.toLong(16)
        if (clean.length == 6) {
            Color(0xFF000000L or parsed)
        } else if (clean.length == 8) {
            Color(parsed)
        } else {
            EditorialCoral
        }
    } catch (_: Exception) {
        EditorialCoral
    }
}

/**
 * Categories Screen with a Swipeable 3D Stacked Card Deck:
 * - Full Periwinkle Indigo (#5B5CF6) edge-to-edge background
 * - Expenses | Earnings toggle above the deck (default: Expenses)
 * - Auto-scrolling category chip strip at the top (active front card chip highlighted in Sunny Yellow)
 * - Full category deck ordered by highest spending this month (or normal category order when no spending yet)
 * - Up to 4 peeking cards behind the front card (36dp peeking strip, 5% scale step, 14dp depth shift, subtle darkening)
 * - Interactive left/right swipe with 12° tilt, 8° 3D rotationY, dynamic shadow stretch, haptic tick on commit,
 *   fly-off & spring-back-to-rear animation, and a "X / N" counter pill underneath.
 */
@Composable
fun CategoriesFolderScreen(
    uiState: SnapSpendUiState,
    onSaveBudget: (categoryName: String, limitPaise: Long) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit = {},
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    var selectedTypeName by rememberSaveable { mutableStateOf(CategoryType.EXPENSE.name) }
    val selectedCategoryType = remember(selectedTypeName) {
        runCatching { CategoryType.valueOf(selectedTypeName) }.getOrDefault(CategoryType.EXPENSE)
    }

    val activeDeckCategories = remember(
        selectedCategoryType,
        uiState.categories,
        uiState.thisMonthCategorySlices,
        uiState.budgetStatuses,
        uiState.allTransactions
    ) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStartMillis = cal.timeInMillis

        val matchingTxType = if (selectedCategoryType == CategoryType.EXPENSE) {
            TransactionType.EXPENSE
        } else {
            TransactionType.EARNING
        }

        val categoryEntityMap = uiState.categories
            .filter { it.type == selectedCategoryType }
            .associateBy { it.name.lowercase(Locale.US) }

        val sliceMap = if (selectedCategoryType == CategoryType.EXPENSE) {
            uiState.thisMonthCategorySlices.associateBy { it.categoryName.lowercase(Locale.US) }
        } else {
            emptyMap()
        }

        val budgetMap = if (selectedCategoryType == CategoryType.EXPENSE) {
            uiState.budgetStatuses.associateBy { it.categoryName.lowercase(Locale.US) }
        } else {
            emptyMap()
        }

        // Preserve normal category order from uiState.categories first
        val baseCategoryNames = buildList {
            uiState.categories
                .filter { it.type == selectedCategoryType }
                .forEach { add(it.name) }
            if (selectedCategoryType == CategoryType.EXPENSE) {
                uiState.thisMonthCategorySlices.forEach { slice ->
                    if (none { it.equals(slice.categoryName, ignoreCase = true) }) {
                        add(slice.categoryName)
                    }
                }
            } else {
                uiState.allTransactions
                    .filter { it.type == TransactionType.EARNING && it.status == TransactionStatus.SUCCESS }
                    .forEach { tx ->
                        if (none { it.equals(tx.category, ignoreCase = true) }) {
                            add(tx.category)
                        }
                    }
            }
        }

        // Calculate monthly totals for Earnings if in EARNING mode
        val monthlyTxTotalsByCategory = uiState.allTransactions
            .asSequence()
            .filter {
                it.type == matchingTxType &&
                    it.status == TransactionStatus.SUCCESS &&
                    it.timestamp >= monthStartMillis
            }
            .groupBy { it.category.lowercase(Locale.US) }
            .mapValues { (_, txs) -> txs.sumOf { it.amountPaise } }

        val totalModePaise = if (selectedCategoryType == CategoryType.EXPENSE) {
            uiState.thisMonthCategorySlices.sumOf { it.amountPaise }.takeIf { it > 0L }
                ?: monthlyTxTotalsByCategory.values.sum()
        } else {
            monthlyTxTotalsByCategory.values.sum()
        }

        val rawItems = baseCategoryNames.mapIndexed { originalIndex, name ->
            val key = name.lowercase(Locale.US)
            val entity = categoryEntityMap[key]
            val slice = sliceMap[key]
            val budget = budgetMap[key]
            val monthAmount = if (selectedCategoryType == CategoryType.EXPENSE) {
                slice?.amountPaise ?: budget?.spentPaise ?: monthlyTxTotalsByCategory[key] ?: 0L
            } else {
                monthlyTxTotalsByCategory[key] ?: 0L
            }
            val pct = if (selectedCategoryType == CategoryType.EXPENSE && slice != null) {
                slice.percentage
            } else if (totalModePaise > 0L && monthAmount > 0L) {
                ((monthAmount * 100f) / totalModePaise.toFloat()).roundToInt().coerceIn(0, 100)
            } else {
                0
            }

            val recentTxs = uiState.allTransactions
                .filter {
                    it.category.equals(name, ignoreCase = true) &&
                        it.type == matchingTxType &&
                        it.status == TransactionStatus.SUCCESS
                }
                .take(4)

            Pair(
                originalIndex,
                FolderCategoryItem(
                    categoryName = name,
                    categoryType = selectedCategoryType,
                    categoryDotColor = parseCategoryColorSafe(entity?.colorHex),
                    deckPalette = DeckCardPalettes[originalIndex % DeckCardPalettes.size],
                    spentPaise = monthAmount,
                    percentageOfTotal = pct,
                    budgetLimitPaise = budget?.limitPaise,
                    budgetUsagePercent = budget?.usagePercent,
                    recentTransactions = recentTxs
                )
            )
        }

        val hasAnySpending = rawItems.any { it.second.spentPaise > 0L }
        val sortedItems = if (hasAnySpending) {
            rawItems.sortedWith(
                compareByDescending<Pair<Int, FolderCategoryItem>> { it.second.spentPaise }
                    .thenBy { it.first }
            ).map { it.second }
        } else {
            rawItems.map { it.second }
        }

        // Re-assign cycling deck colors in deck order so adjacent cards in the stack always have distinct colors
        sortedItems.mapIndexed { deckIdx, item ->
            item.copy(deckPalette = DeckCardPalettes[deckIdx % DeckCardPalettes.size])
        }
    }

    var selectedIndex by rememberSaveable(selectedTypeName) { mutableIntStateOf(0) }
    val safeSelectedIndex = if (activeDeckCategories.isEmpty()) {
        0
    } else {
        selectedIndex.mod(activeDeckCategories.size)
    }

    val chipListState = rememberLazyListState()
    LaunchedEffect(safeSelectedIndex, selectedTypeName, activeDeckCategories.size) {
        if (activeDeckCategories.isNotEmpty() && safeSelectedIndex in activeDeckCategories.indices) {
            chipListState.animateScrollToItem(index = safeSelectedIndex)
        }
    }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var budgetCategoryTarget by remember { mutableStateOf("") }
    var budgetInputText by remember { mutableStateOf("") }

    // Read-only check: whether the user has at least 2 distinct calendar months of expense data
    val hasAtLeastTwoMonthsOfData = remember(uiState.allTransactions) {
        val monthFormatter = SimpleDateFormat("yyyy-MM", Locale.US)
        uiState.allTransactions
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE && it.status == TransactionStatus.SUCCESS && it.amountPaise > 0L }
            .map { monthFormatter.format(Date(it.timestamp)) }
            .distinct()
            .take(2)
            .count() >= 2
    }

    // Shared trigger so tapping top chips or the front arrow button can animate through the deck
    var programmaticStepRequest by remember { mutableIntStateOf(0) }
    var programmaticTargetIndex by remember { mutableIntStateOf(-1) }
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current

    MarkScreenEntrancePlayedEffect(screenKey = "categories")

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PeriwinkleIndigo) // Full indigo (#5B5CF6) edge-to-edge screen
            .testTag("categories_indigo_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = topContentPadding,
                bottom = bottomContentPadding
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header: Round White Icon Circle + Bold Uppercase "CATEGORIES" + Expenses | Earnings Toggle
            item(key = "categories_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredScreenEntrance(screenKey = "categories", index = 0),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UppercaseScreenHeader(
                        title = "CATEGORIES",
                        leadingIcon = Icons.Default.Restaurant,
                        leadingIconBg = PureWhite,
                        leadingIconTint = InkBlack,
                        titleColor = PureWhite,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    ExpensesEarningsDeckToggle(
                        selectedType = selectedCategoryType,
                        onSelectType = { newType ->
                            if (newType != selectedCategoryType) {
                                selectedTypeName = newType.name
                                selectedIndex = 0
                            }
                        }
                    )
                }
            }

            // 2. Auto-scrolling Category Chip Row (active front card chip highlighted in Sunny Yellow)
            if (activeDeckCategories.isNotEmpty()) {
                item(key = "categories_quick_strip") {
                    LazyRow(
                        state = chipListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredScreenEntrance(screenKey = "categories", index = 1),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(end = 8.dp)
                    ) {
                        itemsIndexed(
                            items = activeDeckCategories,
                            key = { _, cat -> "${cat.categoryType.name}_${cat.categoryName}" }
                        ) { idx, cat ->
                            val isSelected = idx == safeSelectedIndex
                            val chipInteraction = remember { MutableInteractionSource() }
                            val isChipPressed by chipInteraction.collectIsPressedAsState()
                            val chipPressFraction by animateFloatAsState(
                                targetValue = if (isChipPressed) 1f else 0f,
                                animationSpec = Motion.pressSpring(reducedMotion),
                                label = "cat_chip_press"
                            )
                            val dxPx = with(density) { 2.dp.toPx() }
                            val dyPx = with(density) { 3.dp.toPx() }

                            Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .offset(x = 2.dp, y = 3.dp)
                                        .clip(CircleShape)
                                        .background(InkBlack)
                                )
                                Surface(
                                    onClick = {
                                        Motion.performLightTapHaptic(view)
                                        if (idx != safeSelectedIndex) {
                                            programmaticTargetIndex = idx
                                            programmaticStepRequest++
                                        }
                                    },
                                    interactionSource = chipInteraction,
                                    shape = CircleShape,
                                    color = if (isSelected) SunnyYellow else PureWhite.copy(alpha = 0.22f),
                                    contentColor = if (isSelected) InkBlack else PureWhite,
                                    border = BorderStroke(
                                        width = 1.4.dp,
                                        color = if (isSelected) InkBlack else PureWhite.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .graphicsLayer {
                                            translationX = dxPx * chipPressFraction
                                            translationY = dyPx * chipPressFraction
                                        }
                                        .semantics {
                                            contentDescription = "${cat.categoryName} category chip"
                                        }
                                        .testTag("category_folder_chip_$idx")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(cat.categoryDotColor)
                                                .border(1.dp, InkBlack.copy(alpha = 0.55f), CircleShape)
                                        )
                                        Text(
                                            text = cat.categoryName.uppercase(),
                                            style = CondensedCardTitleStyle.copy(fontSize = 14.sp),
                                            color = if (isSelected) InkBlack else PureWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. Swipeable 3D Stacked Category Deck + Counter Pill underneath
            item(key = "stacked_folder_tabs") {
                Box(modifier = Modifier.staggeredScreenEntrance(screenKey = "categories", index = 2)) {
                    if (activeDeckCategories.isEmpty()) {
                        Editorial3DLayerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(28.dp),
                            extrusionColor = InkBlack,
                            extrusionBorderColor = InkBlack,
                            depthX = 4.dp,
                            depthY = 6.dp
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                color = SunnyYellow,
                                contentColor = InkBlack,
                                border = BorderStroke(1.8.dp, InkBlack)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "NO CATEGORIES YET",
                                        style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                        color = InkBlack
                                    )
                                    Text(
                                        text = "Switch between Expenses and Earnings or add new payments to start tracking.",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = InkBlack.copy(alpha = 0.85f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        SwipeableCategoryFolderDeck(
                            categories = activeDeckCategories,
                            selectedIndex = safeSelectedIndex,
                            programmaticStepRequest = programmaticStepRequest,
                            programmaticTargetIndex = programmaticTargetIndex,
                            hasAtLeastTwoMonthsOfData = hasAtLeastTwoMonthsOfData,
                            onSelectIndex = { newIndex ->
                                selectedIndex = newIndex
                            },
                            onEditBudgetClick = { catName, currentLimitPaise ->
                                budgetCategoryTarget = catName
                                budgetInputText = if (currentLimitPaise != null && currentLimitPaise > 0L) {
                                    (currentLimitPaise / 100L).toString()
                                } else {
                                    ""
                                }
                                showBudgetDialog = true
                            },
                            onTransactionClick = onTransactionClick
                        )
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        val tokens = SnapSpendThemeTokens.colors
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            modifier = Modifier.dialogScaleFadeEntrance(),
            containerColor = tokens.cardSurface,
            titleContentColor = tokens.primaryText,
            textContentColor = tokens.primaryText,
            title = {
                Text(
                    text = "BUDGET · ${budgetCategoryTarget.uppercase()}",
                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                    color = tokens.primaryText
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your monthly limit in ₹ Rupees. We'll track this category on the front folder card.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = budgetInputText,
                        onValueChange = { budgetInputText = it },
                        label = { Text("Budget for this category", color = tokens.secondaryText) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = tokens.primaryText,
                            unfocusedTextColor = tokens.primaryText,
                            focusedBorderColor = if (tokens.isDark) SunnyYellow else InkBlack,
                            unfocusedBorderColor = tokens.subtleDivider
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("category_budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = MoneyUtils.parseRupeesToPaise(budgetInputText)
                        if (paise != null && paise > 0L) {
                            onSaveBudget(budgetCategoryTarget, paise)
                            showBudgetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunnyYellow,
                        contentColor = InkBlack
                    ),
                    shape = CircleShape,
                    modifier = Modifier.testTag("save_category_budget_button")
                ) {
                    Text("Save budget", fontWeight = FontWeight.ExtraBold, color = InkBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ExpensesEarningsDeckToggle(
    selectedType: CategoryType,
    onSelectType: (CategoryType) -> Unit
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val isExpense = selectedType == CategoryType.EXPENSE

    Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 3.dp)
                .clip(CircleShape)
                .background(InkBlack)
        )
        Surface(
            shape = CircleShape,
            color = PureWhite,
            border = BorderStroke(1.6.dp, InkBlack),
            modifier = Modifier.testTag("categories_type_toggle")
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .width(152.dp)
                    .height(30.dp)
                    .padding(3.dp)
            ) {
                val segmentWidth = maxWidth / 2f
                val indicatorOffsetX by animateDpAsState(
                    targetValue = if (isExpense) 0.dp else segmentWidth,
                    animationSpec = Motion.physicalSpring(reducedMotion),
                    label = "expenses_earnings_pill_slide"
                )
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffsetX)
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(SunnyYellow)
                        .border(1.2.dp, InkBlack, CircleShape)
                )
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckTypeSegmentPill(
                        label = "Expenses",
                        selected = isExpense,
                        onClick = {
                            Motion.performLightTapHaptic(view)
                            onSelectType(CategoryType.EXPENSE)
                        },
                        testTag = "categories_toggle_expenses",
                        modifier = Modifier.weight(1f)
                    )
                    DeckTypeSegmentPill(
                        label = "Earnings",
                        selected = !isExpense,
                        onClick = {
                            Motion.performLightTapHaptic(view)
                            onSelectType(CategoryType.EARNING)
                        },
                        testTag = "categories_toggle_earnings",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckTypeSegmentPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val reducedMotion = LocalReducedMotion.current
    val textColor by animateColorAsState(
        targetValue = if (selected) InkBlack else InkBlack.copy(alpha = 0.65f),
        animationSpec = Motion.quickTween(reducedMotion),
        label = "deck_type_text_color"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { contentDescription = "$label categories toggle" }
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp
            ),
            color = textColor
        )
    }
}

/**
 * Interactive 3D Swipeable Category Card Deck:
 * - Draws the front card (depth = 0) plus up to 4 cards behind it (depth = 1..4).
 * - Each card behind the front card peeks out above it as a 36.dp strip showing:
 *   [● category color dot] [BOLD UPPERCASE CATEGORY NAME] ................. [₹ Amount]
 * - Each level further back scales down by 5% per level, shifts by 14.dp/36.dp per level, and is darkened slightly.
 * - Dragging left or right tilts the front card up to 12°, adds an 8° 3D rotationY turn with cameraDistance,
 *   stretches its 3D shadow, and smoothly interpolates the 4 back cards forward in real time.
 * - Dragging past 30% of width or flicking fast triggers a haptic tick, flies the front card off to the side,
 *   and loops it back into the rear of the deck with a smooth spring while advancing the next card to the front.
 */
@Composable
private fun SwipeableCategoryFolderDeck(
    categories: List<FolderCategoryItem>,
    selectedIndex: Int,
    programmaticStepRequest: Int,
    programmaticTargetIndex: Int,
    hasAtLeastTwoMonthsOfData: Boolean,
    onSelectIndex: (Int) -> Unit,
    onEditBudgetClick: (categoryName: String, currentLimitPaise: Long?) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit
) {
    if (categories.isEmpty()) return

    val density = LocalDensity.current
    val densityFactor = density.density
    val haptic = LocalHapticFeedback.current
    val soundManager = LocalRetroSoundManager.current
    val coroutineScope = rememberCoroutineScope()

    val deckSize = categories.size
    val maxBackCards = min(4, (deckSize - 1).coerceAtLeast(0))

    val stripPeekHeight = 36.dp
    val stripPeekPx = with(density) { stripPeekHeight.toPx() }
    val levelShiftDownPx = with(density) { 14.dp.toPx() }

    var cardWidthPx by remember { mutableFloatStateOf(1000f) }
    val dragOffsetX = remember { Animatable(0f) }
    val flyOffProgress = remember { Animatable(0f) } // 0f during drag, 1f when flying off / returning to back
    var isAnimatingSwipe by remember { mutableStateOf(false) }

    // State for the card returning into the back of the deck right after a swipe
    var returningBackCard by remember { mutableStateOf<FolderCategoryItem?>(null) }
    var returningDirectionSign by remember { mutableFloatStateOf(1f) }
    val returningCardAnim = remember { Animatable(1f) }

    // Helper function to animate a single-step advance or multi-step chip jump
    suspend fun animateSingleStep(directionSign: Float = -1f, nextIndex: Int = (selectedIndex + 1) % deckSize) {
        if (isAnimatingSwipe || deckSize <= 1) {
            onSelectIndex(nextIndex)
            return
        }
        isAnimatingSwipe = true
        soundManager?.playSwoosh()
        val outgoingItem = categories.getOrNull(selectedIndex)
        val targetFlyX = directionSign * (cardWidthPx * 1.12f)

        // Phase 1: Fly off the side while rotating and fading, while back cards move 100% forward
        coroutineScope {
            launch {
                dragOffsetX.animateTo(
                    targetValue = targetFlyX,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
                )
            }
            launch {
                flyOffProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 210, easing = FastOutSlowInEasing)
                )
            }
        }

        // Advance the deck index and snap dragOffsetX back to 0 so the promoted card is now depth 0
        returningBackCard = outgoingItem
        returningDirectionSign = directionSign
        onSelectIndex(nextIndex)
        dragOffsetX.snapTo(0f)
        flyOffProgress.snapTo(0f)

        // Phase 2: Outgoing card springs into the back of the deck
        returningCardAnim.snapTo(0f)
        isAnimatingSwipe = false
        returningCardAnim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
        returningBackCard = null
    }

    // Respond to top chip taps by animating cards before the target chip to the back in order
    LaunchedEffect(programmaticStepRequest) {
        if (programmaticStepRequest > 0 &&
            programmaticTargetIndex in categories.indices &&
            programmaticTargetIndex != selectedIndex &&
            !isAnimatingSwipe
        ) {
            val forwardSteps = (programmaticTargetIndex - selectedIndex + deckSize) % deckSize
            if (forwardSteps in 1..3) {
                var currentIdx = selectedIndex
                repeat(forwardSteps) {
                    val stepNext = (currentIdx + 1) % deckSize
                    animateSingleStep(directionSign = -1f, nextIndex = stepNext)
                    currentIdx = stepNext
                    delay(20L)
                }
            } else {
                animateSingleStep(directionSign = -1f, nextIndex = programmaticTargetIndex)
            }
        }
    }

    val swipeThresholdPx = (cardWidthPx * 0.30f).coerceAtLeast(120f)
    // Progress in [0f..1f] representing how far the user has dragged toward committing the swipe
    val rawDragFraction = (abs(dragOffsetX.value) / swipeThresholdPx).coerceIn(0f, 1f)
    val advancementFraction = maxOf(rawDragFraction, flyOffProgress.value).coerceIn(0f, 1f)

    val frontTopPadding = stripPeekHeight * maxBackCards

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    if (size.width > 0) {
                        cardWidthPx = size.width.toFloat()
                    }
                }
        ) {
            // Optional: Card returning to the back of the stack right after being swiped off
            val returningItem = returningBackCard
            if (returningItem != null && maxBackCards > 0) {
                val backDepth = maxBackCards.toFloat()
                val animVal = returningCardAnim.value
                PeekingBackDeckStrip(
                    item = returningItem,
                    depthIndex = maxBackCards,
                    maxBackCards = maxBackCards,
                    stripPeekHeight = stripPeekHeight,
                    stripPeekPx = stripPeekPx,
                    levelShiftDownPx = levelShiftDownPx,
                    effectiveDepth = backDepth,
                    extraTranslationX = returningDirectionSign * (1f - animVal) * (cardWidthPx * 0.45f),
                    extraAlpha = animVal.coerceIn(0f, 1f),
                    onClick = {
                        val idx = categories.indexOfFirst { it.categoryName == returningItem.categoryName }
                        if (idx >= 0 && !isAnimatingSwipe) {
                            coroutineScope.launch {
                                animateSingleStep(directionSign = -1f, nextIndex = idx)
                            }
                        }
                    },
                    modifier = Modifier.zIndex(0f)
                )
            }

            // Draw up to 4 peeking back cards from furthest back (maxBackCards) down to 1
            for (depth in maxBackCards downTo 1) {
                val catIndex = (selectedIndex + depth) % deckSize
                val backItem = categories[catIndex]
                val effectiveDepth = (depth.toFloat() - advancementFraction).coerceAtLeast(0f)

                val legacyTestTag = when (depth) {
                    1 -> "folder_tab_coral_back"
                    2 -> "folder_tab_black_middle"
                    else -> "folder_tab_back_$depth"
                }

                PeekingBackDeckStrip(
                    item = backItem,
                    depthIndex = depth,
                    maxBackCards = maxBackCards,
                    stripPeekHeight = stripPeekHeight,
                    stripPeekPx = stripPeekPx,
                    levelShiftDownPx = levelShiftDownPx,
                    effectiveDepth = effectiveDepth,
                    extraTranslationX = 0f,
                    extraAlpha = 1f,
                    testTag = legacyTestTag,
                    onClick = {
                        if (!isAnimatingSwipe) {
                            coroutineScope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                animateSingleStep(directionSign = -1f, nextIndex = catIndex)
                            }
                        }
                    },
                    modifier = Modifier.zIndex((maxBackCards - depth + 1).toFloat())
                )
            }

            // Front Interactive Swipeable Card (depth = 0)
            val frontItem = categories[selectedIndex]
            FrontSwipeableCategoryCard(
                item = frontItem,
                positionInDeck = selectedIndex + 1,
                totalInDeck = deckSize,
                frontTopPadding = frontTopPadding,
                dragOffsetXProvider = { dragOffsetX.value },
                cardWidthPx = cardWidthPx,
                densityFactor = densityFactor,
                hasAtLeastTwoMonthsOfData = hasAtLeastTwoMonthsOfData,
                onCycleNextCategory = {
                    if (!isAnimatingSwipe && deckSize > 1) {
                        coroutineScope.launch {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            animateSingleStep(
                                directionSign = -1f,
                                nextIndex = (selectedIndex + 1) % deckSize
                            )
                        }
                    }
                },
                onEditBudgetClick = onEditBudgetClick,
                onTransactionClick = onTransactionClick,
                modifier = Modifier
                    .zIndex((maxBackCards + 2).toFloat())
                    .pointerInput(selectedIndex, deckSize, isAnimatingSwipe) {
                        if (deckSize <= 1) return@pointerInput
                        val velocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = {
                                velocityTracker.resetTracking()
                            },
                            onDragCancel = {
                                if (!isAnimatingSwipe) {
                                    coroutineScope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                if (isAnimatingSwipe) return@detectHorizontalDragGestures
                                val velocityX = velocityTracker.calculateVelocity().x
                                val currentX = dragOffsetX.value
                                val threshold = (cardWidthPx * 0.30f).coerceAtLeast(110f)
                                val shouldCommit = abs(currentX) >= threshold || abs(velocityX) >= 950f

                                if (shouldCommit) {
                                    val dir = when {
                                        abs(currentX) > 20f -> sign(currentX)
                                        velocityX != 0f -> sign(velocityX)
                                        else -> -1f
                                    }
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    coroutineScope.launch {
                                        animateSingleStep(
                                            directionSign = dir,
                                            nextIndex = (selectedIndex + 1) % deckSize
                                        )
                                    }
                                } else {
                                    coroutineScope.launch {
                                        dragOffsetX.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (!isAnimatingSwipe) {
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    coroutineScope.launch {
                                        dragOffsetX.snapTo(dragOffsetX.value + dragAmount)
                                    }
                                }
                            }
                        )
                    }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Counter Pill under the deck (e.g. "3 / 18")
        DeckCounterPill(
            currentIndexOneBased = selectedIndex + 1,
            totalCount = deckSize
        )
    }
}

/**
 * Peeking card behind the front card:
 * - Exposes a ~36dp strip above the card in front of it.
 * - Shows the category's own color as a small dot next to the bold uppercase condensed category name,
 *   and its formatted monthly amount on the right.
 * - Scales down ~5% per level (`scaleX = 1f - 0.05f * effectiveDepth`), shifts by depth, and darkens slightly
 *   per depth level so the front card clearly pops.
 */
@Composable
private fun PeekingBackDeckStrip(
    item: FolderCategoryItem,
    depthIndex: Int,
    maxBackCards: Int,
    stripPeekHeight: Dp,
    stripPeekPx: Float,
    levelShiftDownPx: Float,
    effectiveDepth: Float,
    extraTranslationX: Float,
    extraAlpha: Float,
    onClick: () -> Unit,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    val palette = item.deckPalette
    val tabShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    // Each card behind the front card peeks out above it by 36.dp per level.
    // As you drag, effectiveDepth decreases from `depthIndex` toward `depthIndex - 1`,
    // smoothly scaling up (+5%) and shifting forward by 14dp/36dp into the next slot!
    val baseTopPx = (maxBackCards - effectiveDepth) * stripPeekPx
    val depthDropCompensationPx = (depthIndex - effectiveDepth) * levelShiftDownPx * 0.25f
    val scaleFactor = (1f - 0.05f * effectiveDepth).coerceIn(0.75f, 1f)
    val darkenAlpha = (0.065f * effectiveDepth).coerceIn(0f, 0.30f)

    val cardHeight = stripPeekHeight + 34.dp
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        modifier = taggedModifier
            .fillMaxWidth()
            .padding(end = 6.dp)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0f)
                scaleX = scaleFactor
                scaleY = (1f - 0.025f * effectiveDepth).coerceIn(0.85f, 1f)
                translationY = baseTopPx - depthDropCompensationPx
                translationX = extraTranslationX
                alpha = extraAlpha
            }
            .semantics {
                contentDescription = "${item.categoryName}, ${MoneyUtils.formatPaise(item.spentPaise)}, card ${depthIndex + 1} in deck. Tap to bring to front."
            }
    ) {
        // 3D Offset Extrusion Shadow behind peeking card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .offset(x = 4.dp, y = 4.dp)
                .clip(tabShape)
                .background(
                    if (palette.isDarkCard) EditorialAqua.copy(alpha = 0.65f) else InkBlack
                )
        )

        // Peeking Card Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .clip(tabShape)
                .background(palette.backgroundColor)
                .border(
                    width = 1.8.dp,
                    color = if (palette.isDarkCard) PureWhite.copy(alpha = 0.35f) else InkBlack,
                    shape = tabShape
                )
                .clickable(role = Role.Button, onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(stripPeekHeight)
                    .padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category's own color as a small dot next to the name
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(item.categoryDotColor)
                            .border(
                                width = 1.2.dp,
                                color = if (palette.isDarkCard) PureWhite else InkBlack,
                                shape = CircleShape
                            )
                    )
                    Text(
                        text = item.categoryName.uppercase(),
                        style = CondensedCardTitleStyle.copy(fontSize = 17.sp),
                        color = palette.contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = MoneyUtils.formatPaise(item.spentPaise),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = palette.contentColor
                )
            }

            // Slight darkening overlay per depth level so the front card clearly stands out
            if (darkenAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(InkBlack.copy(alpha = darkenAlpha))
                )
            }
        }
    }
}

@Composable
private fun FrontSwipeableCategoryCard(
    item: FolderCategoryItem,
    positionInDeck: Int,
    totalInDeck: Int,
    frontTopPadding: Dp,
    dragOffsetXProvider: () -> Float,
    cardWidthPx: Float,
    densityFactor: Float,
    hasAtLeastTwoMonthsOfData: Boolean,
    onCycleNextCategory: () -> Unit,
    onEditBudgetClick: (categoryName: String, currentLimitPaise: Long?) -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = item.deckPalette
    val activeCategoryName = item.categoryName
    val activeSpentPaise = item.spentPaise
    val activePercentage = item.percentageOfTotal
    val activeBudgetLimitPaise = item.budgetLimitPaise
    val activeBudgetUsage = item.budgetUsagePercent
    val isExpenseMode = item.categoryType == CategoryType.EXPENSE

    val frontShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = frontTopPadding, end = 6.dp, bottom = 8.dp)
            .graphicsLayer {
                val dx = dragOffsetXProvider()
                val normalized = (dx / cardWidthPx.coerceAtLeast(1f)).coerceIn(-1.2f, 1.2f)
                translationX = dx
                translationY = abs(normalized) * 10f * densityFactor
                // Rotate up to ~12 degrees in the direction of the drag
                rotationZ = (normalized * 12f).coerceIn(-14f, 14f)
                // Slight 3D turn (rotationY ~8 degrees) with realistic cameraDistance
                rotationY = (normalized * 8f).coerceIn(-10f, 10f)
                cameraDistance = 16f * densityFactor
                // Fade slightly only when flying far off the side
                alpha = (1f - (abs(normalized) - 0.65f).coerceAtLeast(0f) * 1.4f).coerceIn(0.15f, 1f)
            }
            .semantics {
                contentDescription = "${activeCategoryName}, ${MoneyUtils.formatPaise(activeSpentPaise)} this month, card $positionInDeck of $totalInDeck. Swipe left or right for next category."
            }
            .testTag("folder_card_front_yellow")
    ) {
        // Dynamic 3D Shadow block that stretches slightly as you drag
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset {
                    val dx = dragOffsetXProvider()
                    val stretchFactor = (abs(dx) / (cardWidthPx * 0.35f).coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val baseX = (6.dp.value * densityFactor).roundToInt()
                    val baseY = (8.dp.value * densityFactor).roundToInt()
                    val extraX = (stretchFactor * 5.dp.value * densityFactor).roundToInt()
                    val extraY = (stretchFactor * 5.dp.value * densityFactor).roundToInt()
                    IntOffset(x = baseX + extraX, y = baseY + extraY)
                }
                .clip(frontShape)
                .background(if (palette.isDarkCard) EditorialAqua.copy(alpha = 0.72f) else InkBlack)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = frontShape,
            color = palette.backgroundColor,
            contentColor = palette.contentColor,
            border = BorderStroke(
                width = 2.2.dp,
                color = if (palette.isDarkCard) PureWhite.copy(alpha = 0.42f) else InkBlack
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                // Big Uppercase Condensed Category Name + Category Color Dot + Circle Arrow Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(item.categoryDotColor)
                                .border(
                                    width = 1.8.dp,
                                    color = if (palette.isDarkCard) PureWhite else InkBlack,
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = activeCategoryName.uppercase(),
                            style = CondensedScreenTitleStyle.copy(
                                fontSize = 32.sp,
                                lineHeight = 34.sp
                            ),
                            color = palette.contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Circle arrow button (black circle with white arrow, or white circle with black arrow on the black card)
                    DeckFrontArrowButton(
                        isDarkCard = palette.isDarkCard,
                        onClick = onCycleNextCategory,
                        testTag = "folder_front_next_button"
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status pills with colored dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val volumeLabel = when {
                        !isExpenseMode && activeSpentPaise > 0L -> "Earned · $activePercentage%"
                        !isExpenseMode -> "No earnings yet"
                        activePercentage >= 30 -> "High spend · $activePercentage%"
                        activePercentage >= 12 -> "Moderate · $activePercentage%"
                        activeSpentPaise > 0L -> "Active · $activePercentage%"
                        else -> "No spend yet"
                    }
                    val paceLabel: String? = when {
                        !isExpenseMode -> null
                        activeBudgetUsage != null && activeBudgetUsage >= 100 -> "Over budget ($activeBudgetUsage%)"
                        activeBudgetUsage != null && activeBudgetUsage >= 80 -> "Near limit ($activeBudgetUsage%)"
                        activeBudgetUsage != null -> "Steady ($activeBudgetUsage% used)"
                        hasAtLeastTwoMonthsOfData -> "Steady pace"
                        else -> null
                    }

                    StatusDotPill(
                        text = volumeLabel,
                        dotColor = item.categoryDotColor,
                        backgroundColor = PureWhite.copy(alpha = 0.92f),
                        textColor = InkBlack
                    )
                    if (paceLabel != null) {
                        StatusDotPill(
                            text = paceLabel,
                            dotColor = PeriwinkleIndigo,
                            backgroundColor = PureWhite.copy(alpha = 0.92f),
                            textColor = InkBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Compact Editorial 3D Storefront Illustration so "Category total" box stays fully visible above + ADD
                val reducedMotion = LocalReducedMotion.current
                val bobOffsetDp = if (activeSpentPaise == 0L && !reducedMotion) {
                    val infiniteTransition = rememberInfiniteTransition(label = "empty_category_bob")
                    val bob by infiniteTransition.animateFloat(
                        initialValue = -Motion.BOB_AMPLITUDE.value,
                        targetValue = Motion.BOB_AMPLITUDE.value,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = Motion.BOB_CYCLE_MS, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "empty_category_bob_float"
                    )
                    bob.dp
                } else {
                    0.dp
                }
                CategoryStorefrontIllustration(
                    categoryName = activeCategoryName,
                    accentDotColor = item.categoryDotColor,
                    isDarkCard = palette.isDarkCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(104.dp)
                        .graphicsLayer {
                            translationY = bobOffsetDp.toPx()
                        }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // White "CATEGORY TOTAL" Box with Progress Bar & "Set budget" Button
                val animatedSpentText = rememberAnimatedPaise(targetPaise = activeSpentPaise)
                Editorial3DLayerBox(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    extrusionColor = if (palette.isDarkCard) PureWhite.copy(alpha = 0.28f) else InkBlack,
                    depthX = 3.dp,
                    depthY = 4.dp
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.6.dp, InkBlack)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp, vertical = 13.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "CATEGORY TOTAL",
                                        style = CondensedCardTitleStyle.copy(fontSize = 14.sp),
                                        color = InkBlack.copy(alpha = 0.78f)
                                    )
                                    ResponsiveTabularNumberText(
                                        text = animatedSpentText,
                                        maxFontSize = 28.sp,
                                        minFontSize = 20.sp,
                                        color = InkBlack
                                    )
                                }

                                if (isExpenseMode) {
                                    val view = LocalView.current
                                    Surface(
                                        onClick = {
                                            Motion.performLightTapHaptic(view)
                                            onEditBudgetClick(activeCategoryName, activeBudgetLimitPaise)
                                        },
                                        shape = CircleShape,
                                        color = InkBlack,
                                        contentColor = PureWhite,
                                        modifier = Modifier.testTag("folder_set_budget_button")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = null,
                                                tint = PureWhite,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = if (activeBudgetLimitPaise != null) "Edit budget" else "Set budget",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = PureWhite
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Progress bar (animated smoothly from 0f / previous fraction to target fraction)
                            val progressFraction = when {
                                activeBudgetLimitPaise != null && activeBudgetLimitPaise > 0L ->
                                    (activeSpentPaise.toFloat() / activeBudgetLimitPaise.toFloat()).coerceIn(0f, 1f)
                                activeSpentPaise > 0L -> (activePercentage / 100f).coerceIn(0.08f, 1f)
                                else -> 0f
                            }
                            var barEntered by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) { barEntered = true }
                            val animatedProgressFraction by animateFloatAsState(
                                targetValue = if (barEntered || reducedMotion) progressFraction else 0f,
                                animationSpec = Motion.chartTween(reducedMotion),
                                label = "category_budget_progress"
                            )
                            val barColor = when {
                                activeBudgetUsage != null && activeBudgetUsage >= 80 -> EditorialCoral
                                else -> EditorialMint
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(14.dp)
                                    .clip(CircleShape)
                                    .background(InkBlack.copy(alpha = 0.12f))
                                    .border(1.4.dp, InkBlack, CircleShape)
                            ) {
                                if (animatedProgressFraction > 0.005f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animatedProgressFraction.coerceAtLeast(0.06f))
                                            .fillMaxHeight()
                                            .clip(CircleShape)
                                            .background(barColor)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = when {
                                    !isExpenseMode -> "$activePercentage% of this month's total earnings"
                                    activeBudgetLimitPaise != null ->
                                        "${MoneyUtils.formatPaise(activeSpentPaise)} of ${MoneyUtils.formatPaise(activeBudgetLimitPaise)} monthly budget (${activeBudgetUsage ?: 0}%)"
                                    else ->
                                        "$activePercentage% of this month's total spending · Tap 'Set budget' to add a cap"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack.copy(alpha = 0.82f)
                            )
                        }
                    }
                }

                // Recent Transactions inside the Front Folder Card
                val recentList = item.recentTransactions
                if (recentList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "RECENT IN ${activeCategoryName.uppercase()}",
                        style = CondensedCardTitleStyle.copy(fontSize = 16.sp),
                        color = palette.contentColor
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Editorial3DLayerBox(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        extrusionColor = if (palette.isDarkCard) PureWhite.copy(alpha = 0.25f) else InkBlack,
                        depthX = 3.dp,
                        depthY = 4.dp
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = PureWhite.copy(alpha = 0.96f),
                            contentColor = InkBlack,
                            border = BorderStroke(1.6.dp, InkBlack)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                recentList.forEachIndexed { idx, tx ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onTransactionClick(tx) }
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(CircleShape)
                                                    .background(InkBlack),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = categoryIconVector(tx.category, tx.type),
                                                    contentDescription = null,
                                                    tint = PureWhite,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = tx.merchant.uppercase(),
                                                    style = CondensedCardTitleStyle.copy(fontSize = 16.sp),
                                                    color = InkBlack,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                ) {
                                                    Text(
                                                        text = tx.category,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                        color = InkBlack.copy(alpha = 0.78f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (tx.paymentMode.equals("CASH", ignoreCase = true)) {
                                                        com.example.ui.components.CashModeBadge()
                                                    }
                                                    Text(
                                                        text = "· ${MoneyUtils.formatShortDateTime(tx.timestamp, tx.hasExplicitTime)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = InkBlack.copy(alpha = 0.72f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        val signPrefix = if (tx.type == TransactionType.EARNING) "+" else "-"
                                        Text(
                                            text = "$signPrefix${MoneyUtils.formatPaise(tx.amountPaise)}",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Black,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = InkBlack
                                        )
                                    }
                                    if (idx < recentList.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 14.dp),
                                            thickness = 1.dp,
                                            color = InkBlack.copy(alpha = 0.15f)
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
}

@Composable
private fun DeckFrontArrowButton(
    isDarkCard: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val bg = if (isDarkCard) PureWhite else InkBlack
    val fg = if (isDarkCard) InkBlack else PureWhite
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "deck_arrow_press_scale"
    )
    Box(
        modifier = Modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .border(1.5.dp, if (isDarkCard) InkBlack else PureWhite.copy(alpha = 0.25f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    Motion.performLightTapHaptic(view)
                    onClick()
                }
            )
            .semantics { contentDescription = "Next category folder" }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "Next category folder",
            tint = fg,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DeckCounterPill(
    currentIndexOneBased: Int,
    totalCount: Int
) {
    Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 2.dp, y = 3.dp)
                .clip(CircleShape)
                .background(InkBlack)
        )
        Surface(
            shape = CircleShape,
            color = PureWhite,
            contentColor = InkBlack,
            border = BorderStroke(1.5.dp, InkBlack),
            modifier = Modifier
                .semantics {
                    contentDescription = "Category $currentIndexOneBased of $totalCount"
                }
                .testTag("categories_deck_counter_pill")
        ) {
            Text(
                text = "$currentIndexOneBased / $totalCount",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                style = CondensedCardTitleStyle.copy(
                    fontSize = 14.sp,
                    fontFeatureSettings = "tnum"
                ),
                color = InkBlack
            )
        }
    }
}

/**
 * Playful 3D hand-drawn style storefront & lantern illustration on the front category card.
 */
@Composable
private fun CategoryStorefrontIllustration(
    categoryName: String,
    accentDotColor: Color,
    isDarkCard: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeColor = InkBlack
        val stroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        // Ground shadow oval
        drawOval(
            color = if (isDarkCard) PureWhite.copy(alpha = 0.16f) else InkBlack.copy(alpha = 0.18f),
            topLeft = Offset(w * 0.14f, h * 0.84f),
            size = Size(w * 0.72f, h * 0.12f)
        )

        // Decorative left & right background foliage circles with 3D offset shadows
        drawCircle(
            color = strokeColor,
            radius = h * 0.22f,
            center = Offset(w * 0.25f + 3.dp.toPx(), h * 0.38f + 3.dp.toPx())
        )
        drawCircle(
            color = accentDotColor,
            radius = h * 0.22f,
            center = Offset(w * 0.25f, h * 0.38f)
        )
        drawCircle(
            color = strokeColor,
            radius = h * 0.22f,
            center = Offset(w * 0.25f, h * 0.38f),
            style = stroke
        )

        drawCircle(
            color = strokeColor,
            radius = h * 0.24f,
            center = Offset(w * 0.74f + 3.dp.toPx(), h * 0.40f + 3.dp.toPx())
        )
        drawCircle(
            color = EditorialMint,
            radius = h * 0.24f,
            center = Offset(w * 0.74f, h * 0.40f)
        )
        drawCircle(
            color = strokeColor,
            radius = h * 0.24f,
            center = Offset(w * 0.74f, h * 0.40f),
            style = stroke
        )

        // Central Storefront Booth with 3D extruded shadow block
        val boothLeft = w * 0.27f
        val boothTop = h * 0.20f
        val boothW = w * 0.46f
        val boothH = h * 0.66f

        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(boothLeft + 5.dp.toPx(), boothTop + 5.dp.toPx()),
            size = Size(boothW, boothH),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = Color(0xFFFFF7E2),
            topLeft = Offset(boothLeft, boothTop),
            size = Size(boothW, boothH),
            cornerRadius = CornerRadius(16f, 16f)
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(boothLeft, boothTop),
            size = Size(boothW, boothH),
            cornerRadius = CornerRadius(16f, 16f),
            style = stroke
        )

        // Awning Roof
        val roofPath = Path().apply {
            moveTo(boothLeft - 12.dp.toPx(), boothTop + 16.dp.toPx())
            lineTo(boothLeft + 8.dp.toPx(), boothTop - 7.dp.toPx())
            lineTo(boothLeft + boothW - 8.dp.toPx(), boothTop - 7.dp.toPx())
            lineTo(boothLeft + boothW + 12.dp.toPx(), boothTop + 16.dp.toPx())
            close()
        }
        drawPath(path = roofPath, color = EditorialCoral)
        drawPath(path = roofPath, color = strokeColor, style = stroke)

        // Inner Aqua counter window
        drawRoundRect(
            color = EditorialAqua,
            topLeft = Offset(boothLeft + boothW * 0.14f, boothTop + boothH * 0.28f),
            size = Size(boothW * 0.46f, boothH * 0.42f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(boothLeft + boothW * 0.14f, boothTop + boothH * 0.28f),
            size = Size(boothW * 0.46f, boothH * 0.42f),
            cornerRadius = CornerRadius(10f, 10f),
            style = stroke
        )

        // Hanging Signboard Banner on the right of the booth
        val bannerLeft = boothLeft + boothW * 0.66f
        val bannerTop = boothTop + boothH * 0.24f
        val bannerW = boothW * 0.22f
        val bannerH = boothH * 0.50f
        drawRoundRect(
            color = PureWhite,
            topLeft = Offset(bannerLeft, bannerTop),
            size = Size(bannerW, bannerH),
            cornerRadius = CornerRadius(7f, 7f)
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(bannerLeft, bannerTop),
            size = Size(bannerW, bannerH),
            cornerRadius = CornerRadius(7f, 7f),
            style = stroke
        )

        // Decorative Coral counter base
        drawRoundRect(
            color = EditorialCoral,
            topLeft = Offset(boothLeft - 5.dp.toPx(), boothTop + boothH * 0.76f),
            size = Size(boothW + 10.dp.toPx(), boothH * 0.24f),
            cornerRadius = CornerRadius(10f, 10f)
        )
        drawRoundRect(
            color = strokeColor,
            topLeft = Offset(boothLeft - 5.dp.toPx(), boothTop + boothH * 0.76f),
            size = Size(boothW + 10.dp.toPx(), boothH * 0.24f),
            cornerRadius = CornerRadius(10f, 10f),
            style = stroke
        )
    }
}
