package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.util.Locale

// ============================================================================
// 1. Core Surfaces (Deep Charcoal-Slate Dark Mode & Warm Peach Cream Light Mode)
// ============================================================================
val TrueBlackBg = Color(0xFF181A1F)           // Dark mode Deep Charcoal-Slate background (#181A1F)
val DarkCardSurface = Color(0xFF23262E)       // Elevated dark surface cards, dialogs & sheets (#23262E)
val DarkSubtleBorder = Color(0xFF383C4A)      // Distinct border/outline for dark surfaces (#383C4A)
val SecondaryGreyText = Color(0xFFB0B4C0)     // High-contrast secondary text on dark surfaces (#B0B4C0)

val EditorialBg = Color(0xFFF9E4D4)           // Light mode Warm Peach Cream background (#F9E4D4)
val InkBlack = Color(0xFF0B0B0B)              // Pure black (#0B0B0B) for text, icons & borders on ALL colored cards
val BrightCardBlack = InkBlack
val InkMuted = Color(0xFF4A4A52)              // Secondary dark grey for subtitles
val PureWhite = Color(0xFFFFFFFF)             // Pure white (#FFFFFF) for small controls & inner card boxes
val IncomeDarkGreen = Color(0xFF0F7A3E)       // Darker green (#0F7A3E) for income amounts on pale transaction cards

// ============================================================================
// 2. Signature Editorial Palette — Strong & Pale Tints (Same in Light & Dark)
// ============================================================================
// Yellow: #FFD23F / #FFF0B3
val SunnyYellow = Color(0xFFFFD23F)
val CardYellowStrong = SunnyYellow
val PaleYellowTint = Color(0xFFFFF0B3)
val CardYellowPale = PaleYellowTint

// Coral: #FF6F59 / #FFD3CB
val EditorialCoral = Color(0xFFFF6F59)
val CardCoralStrong = EditorialCoral
val PaleCoralTint = Color(0xFFFFD3CB)
val CardCoralPale = PaleCoralTint
val LighterCoral = Color(0xFFFF8A76)          // Lighter coral for inner boxes on Coral bar chart card: #FF8A76
val FadedCoralBar = Color(0xFFFF9E8D)         // Faded lighter coral for unselected comparison bars

// Mint: #9FE3C0 / #CFF2DF
val EditorialMint = Color(0xFF9FE3C0)
val CardMintStrong = EditorialMint
val PaleMintTint = Color(0xFFCFF2DF)
val CardMintPale = PaleMintTint
val PaleMintHero = EditorialMint              // Mint (#9FE3C0) hero & empty state card

// Aqua: #8FE3F0 / #CDF3F8
val EditorialAqua = Color(0xFF8FE3F0)
val CardAquaStrong = EditorialAqua
val PaleAquaTint = Color(0xFFCDF3F8)
val CardAquaPale = PaleAquaTint

// Pink: #FF9EC7 / #FFD3E6
val EditorialPink = Color(0xFFFF9EC7)
val CardPinkStrong = EditorialPink
val PalePinkTint = Color(0xFFFFD3E6)
val CardPinkPale = PalePinkTint

// Lavender: #C9A0FF / #E3D1FF
val EditorialLavender = Color(0xFFC9A0FF)
val CardLavenderStrong = EditorialLavender
val PaleLavenderTint = Color(0xFFE3D1FF)
val CardLavenderPale = PaleLavenderTint

// Indigo: #5B5CF6 (kept as is)
val PeriwinkleIndigo = Color(0xFF5B5CF6)

// ============================================================================
// 3. Compatibility Aliases & Palettes
// ============================================================================
val DarkNavySlateBg = TrueBlackBg
val DarkSlateCard = DarkCardSurface
val DarkSlateDivider = DarkSubtleBorder
val DarkSoftGreyBlueText = SecondaryGreyText
val DarkSecondaryButtonBg = EditorialLavender
val DarkSegmentedPillBg = EditorialAqua

val CandySunshineYellow = SunnyYellow
val CandyOrange = EditorialCoral
val CandyLime = EditorialMint
val CandyLimeGreen = EditorialMint
val CandyLavender = EditorialLavender
val CandyLavenderPurple = EditorialLavender
val CandySkyBlue = EditorialAqua

val LimeAccent = SunnyYellow
val PastelPink = EditorialPink
val PastelCyan = EditorialAqua
val PastelBeige = PaleYellowTint
val WarningAmber = SunnyYellow
val SoftLavender = EditorialLavender

val CandyCardPalette = listOf(
    SunnyYellow,
    EditorialCoral,
    EditorialAqua,
    EditorialMint,
    EditorialPink,
    EditorialLavender
)
val DarkCandyCardPalette = CandyCardPalette
val EditorialCardPalette = CandyCardPalette

val PaleTintCardPalette = listOf(
    PaleYellowTint,
    PaleCoralTint,
    PaleMintTint,
    PaleAquaTint,
    PalePinkTint,
    PaleLavenderTint
)


fun parseEditorialColor(hex: String): Color {
    return try {
        val cleaned = hex.trim().removePrefix("#")
        val longVal = cleaned.toLong(16)
        if (cleaned.length == 6) {
            Color(longVal or 0xFF000000L)
        } else {
            Color(longVal)
        }
    } catch (e: Exception) {
        SunnyYellow
    }
}

/**
 * Maps a category's colorHex (and optional category name) to:
 * - first: solid category dot color
 * - second: pale tint card color from the 6-color palette (defaults to PaleYellowTint #FFF0B3 if no color)
 */
fun resolveCategoryDotAndPaleTint(
    categoryName: String?,
    colorHex: String?
): Pair<Color, Color> {
    val cleanHex = colorHex?.trim()?.removePrefix("#")?.uppercase(Locale.US).orEmpty()
    if (cleanHex.isEmpty()) {
        return SunnyYellow to PaleYellowTint
    }

    val dotColor = parseEditorialColor(cleanHex)
    val paleTint = when (cleanHex) {
        // Coral / Red / Orange family -> Pale Coral (#FFD3CB)
        "FF6F59", "FF5A36", "E11D48", "FF8A76", "FFD3CB" -> PaleCoralTint
        // Mint / Green family -> Pale Mint (#CFF2DF)
        "9FE3C0", "65D46E", "10B981", "22C55E", "A3E635", "A8D5C0", "CFF2DF" -> PaleMintTint
        // Aqua / Blue / Cyan family -> Pale Aqua (#CDF3F8)
        "8FE3F0", "2563EB", "06B6D4", "0EA5E9", "CDF3F8" -> PaleAquaTint
        // Pink / Rose / Magenta family -> Pale Pink (#FFD3E6)
        "FF9EC7", "FF85B3", "D946EF", "F472B6", "FFD3E6" -> PalePinkTint
        // Lavender / Purple / Indigo family -> Pale Lavender (#E3D1FF)
        "C9A0FF", "8B5CF6", "6366F1", "5B5CF6", "E3D1FF" -> PaleLavenderTint
        // Yellow / Amber / Gold family -> Pale Yellow (#FFF0B3)
        "FFD23F", "F59E0B", "EAB308", "FFF0B3" -> PaleYellowTint
        else -> {
            // Fallback by RGB dominance or category name if custom hex
            val r = dotColor.red
            val g = dotColor.green
            val b = dotColor.blue
            when {
                r > 0.85f && g > 0.72f && b < 0.45f -> PaleYellowTint
                r > 0.85f && g < 0.55f && b < 0.50f -> PaleCoralTint
                r > 0.80f && b > 0.60f -> PalePinkTint
                g > 0.72f && r < 0.68f && b < 0.80f -> PaleMintTint
                b > 0.75f && g > 0.65f -> PaleAquaTint
                b > 0.72f && r > 0.50f -> PaleLavenderTint
                else -> PaleYellowTint
            }
        }
    }
    return dotColor to paleTint
}
