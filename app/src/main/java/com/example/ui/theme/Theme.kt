package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

@Immutable
data class SnapSpendColors(
    val isDark: Boolean,
    val appBackground: Color,
    val slateOrNeutralCard: Color,
    val dividerColor: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val sectionTitleColor: Color,
    // Colored cards (ALWAYS use InkBlack #0B0B0B for text & icons on top)
    val heroBalanceCard: Color,
    val spentCard: Color,
    val earnedCard: Color,
    val topCategoryCard: Color,
    val budgetOrChartCard: Color,
    val brightPalette: List<Color>,
    // Controls & Buttons
    val wideSecondaryButtonBg: Color,
    val wideSecondaryButtonContent: Color,
    val segmentedTrackBg: Color,
    val segmentedSelectedPillBg: Color,
    val segmentedSelectedText: Color,
    val segmentedUnselectedText: Color,
    val fabBg: Color,
    val fabIconColor: Color,
    // Transaction row amounts
    val incomeAmountColor: Color,
    val expenseAmountColor: Color,
    // Borders & chart slice gaps
    val cardBorder: Color,
    val chartSliceGapColor: Color
) {
    val background: Color get() = appBackground
    val cardSurface: Color get() = slateOrNeutralCard
    val subtleDivider: Color get() = dividerColor
    val selectedPillBg: Color get() = segmentedSelectedPillBg
    val fabIcon: Color get() = fabIconColor
}

val LightSnapSpendColors = SnapSpendColors(
    isDark = false,
    appBackground = EditorialBg,                 // #F9E4D4 Warm Peach Cream light background
    slateOrNeutralCard = PureWhite,              // #FFFFFF cards in light mode
    dividerColor = Color(0xFFE0E2DC),
    primaryText = InkBlack,                      // #0B0B0B
    secondaryText = InkMuted,                    // #4A4A52
    sectionTitleColor = InkBlack,
    heroBalanceCard = SunnyYellow,               // #FFD23F
    spentCard = EditorialCoral,                  // #FF6F59
    earnedCard = EditorialMint,                  // #9FE3C0
    topCategoryCard = PeriwinkleIndigo,          // #5B5CF6
    budgetOrChartCard = EditorialAqua,           // #8FE3F0
    brightPalette = listOf(SunnyYellow, EditorialCoral, EditorialAqua, EditorialMint, PaleMintHero),
    wideSecondaryButtonBg = DarkCardSurface,     // #1C1C1E
    wideSecondaryButtonContent = PureWhite,      // #FFFFFF
    segmentedTrackBg = PureWhite,
    segmentedSelectedPillBg = EditorialAqua,     // #8FE3F0
    segmentedSelectedText = InkBlack,
    segmentedUnselectedText = InkBlack,
    fabBg = SunnyYellow,                         // #FFD23F
    fabIconColor = InkBlack,                     // #0B0B0B
    incomeAmountColor = Color(0xFF0E7A46),
    expenseAmountColor = InkBlack,
    cardBorder = InkBlack,
    chartSliceGapColor = EditorialBg
)

val DarkSnapSpendColors = SnapSpendColors(
    isDark = true,
    appBackground = TrueBlackBg,                 // #181A1F Deep Charcoal-Slate dark background
    slateOrNeutralCard = DarkCardSurface,        // #23262E elevated dark surface cards
    dividerColor = DarkSubtleBorder,             // #383C4A legible dark outline/divider
    primaryText = PureWhite,                     // #FFFFFF on dark surfaces
    secondaryText = SecondaryGreyText,           // #B0B4C0 secondary grey text
    sectionTitleColor = PureWhite,               // #FFFFFF
    heroBalanceCard = SunnyYellow,               // #FFD23F
    spentCard = EditorialCoral,                  // #FF6F59
    earnedCard = EditorialMint,                  // #9FE3C0
    topCategoryCard = PeriwinkleIndigo,          // #5B5CF6
    budgetOrChartCard = EditorialAqua,           // #8FE3F0
    brightPalette = listOf(SunnyYellow, EditorialCoral, EditorialAqua, EditorialMint, PeriwinkleIndigo),
    wideSecondaryButtonBg = DarkCardSurface,     // #23262E
    wideSecondaryButtonContent = PureWhite,      // #FFFFFF
    segmentedTrackBg = DarkCardSurface,          // #23262E
    segmentedSelectedPillBg = EditorialAqua,     // #8FE3F0
    segmentedSelectedText = InkBlack,            // #0B0B0B on Aqua pill
    segmentedUnselectedText = SecondaryGreyText, // #B0B4C0
    fabBg = SunnyYellow,                         // #FFD23F
    fabIconColor = InkBlack,                     // #0B0B0B
    incomeAmountColor = EditorialMint,           // #9FE3C0
    expenseAmountColor = PureWhite,              // #FFFFFF
    cardBorder = DarkSubtleBorder,               // #383C4A
    chartSliceGapColor = TrueBlackBg             // #181A1F
)

val LocalSnapSpendColors = staticCompositionLocalOf { DarkSnapSpendColors }

private val SnapSpendLightColorScheme = lightColorScheme(
    primary = PeriwinkleIndigo,
    onPrimary = PureWhite,
    primaryContainer = SunnyYellow,
    onPrimaryContainer = InkBlack,
    secondary = EditorialCoral,
    onSecondary = InkBlack,
    secondaryContainer = LighterCoral,
    onSecondaryContainer = InkBlack,
    tertiary = EditorialAqua,
    onTertiary = InkBlack,
    tertiaryContainer = PaleMintHero,
    onTertiaryContainer = InkBlack,
    background = EditorialBg,
    onBackground = InkBlack,
    surface = PureWhite,
    onSurface = InkBlack,
    surfaceVariant = PaleMintHero,
    onSurfaceVariant = InkBlack,
    outline = InkBlack
)

private val SnapSpendDarkColorScheme = darkColorScheme(
    primary = EditorialAqua,
    onPrimary = InkBlack,
    primaryContainer = PeriwinkleIndigo,
    onPrimaryContainer = PureWhite,
    secondary = EditorialCoral,
    onSecondary = InkBlack,
    secondaryContainer = SunnyYellow,
    onSecondaryContainer = InkBlack,
    tertiary = EditorialMint,
    onTertiary = InkBlack,
    tertiaryContainer = PaleMintHero,
    onTertiaryContainer = InkBlack,
    background = TrueBlackBg,
    onBackground = PureWhite,
    surface = DarkCardSurface,
    onSurface = PureWhite,
    surfaceVariant = DarkCardSurface,
    onSurfaceVariant = SecondaryGreyText,
    outline = DarkSubtleBorder
)

val SnapSpendShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

object SnapSpendThemeTokens {
    val colors: SnapSpendColors
        @Composable
        @ReadOnlyComposable
        get() = LocalSnapSpendColors.current
}

@Composable
fun SnapSpendTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val reducedMotion = rememberSystemReducedMotion()
    val targetColors = if (useDark) DarkSnapSpendColors else LightSnapSpendColors
    val colorSpec = Motion.normalTween<Color>(reducedMotion = reducedMotion)

    // 10. THEME TOGGLE: Colors cross-fade over 300ms across the whole app instead of snapping
    val animAppBg by animateColorAsState(targetColors.appBackground, colorSpec, label = "themeAppBg")
    val animCardSurface by animateColorAsState(targetColors.slateOrNeutralCard, colorSpec, label = "themeCardSurface")
    val animDivider by animateColorAsState(targetColors.dividerColor, colorSpec, label = "themeDivider")
    val animPrimaryText by animateColorAsState(targetColors.primaryText, colorSpec, label = "themePrimaryText")
    val animSecondaryText by animateColorAsState(targetColors.secondaryText, colorSpec, label = "themeSecondaryText")
    val animSectionTitle by animateColorAsState(targetColors.sectionTitleColor, colorSpec, label = "themeSectionTitle")
    val animCardBorder by animateColorAsState(targetColors.cardBorder, colorSpec, label = "themeCardBorder")
    val animSliceGap by animateColorAsState(targetColors.chartSliceGapColor, colorSpec, label = "themeSliceGap")

    val snapColors = targetColors.copy(
        appBackground = animAppBg,
        slateOrNeutralCard = animCardSurface,
        dividerColor = animDivider,
        primaryText = animPrimaryText,
        secondaryText = animSecondaryText,
        sectionTitleColor = animSectionTitle,
        cardBorder = animCardBorder,
        chartSliceGapColor = animSliceGap
    )

    val baseScheme = if (useDark) SnapSpendDarkColorScheme else SnapSpendLightColorScheme
    val m3Scheme = baseScheme.copy(
        background = animAppBg,
        onBackground = animPrimaryText,
        surface = animCardSurface,
        onSurface = animPrimaryText,
        onSurfaceVariant = animSecondaryText,
        outline = animCardBorder
    )

    CompositionLocalProvider(
        LocalSnapSpendColors provides snapColors,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = m3Scheme,
            typography = Typography,
            shapes = SnapSpendShapes,
            content = content
        )
    }
}
