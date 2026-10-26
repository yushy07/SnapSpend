package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.example.R

// Bold Uppercase Condensed font (Barlow Condensed) for all screen titles & card titles
val BarlowCondensedFontFamily = FontFamily(
    Font(R.font.barlow_condensed, FontWeight.Normal),
    Font(R.font.barlow_condensed_500, FontWeight.Medium),
    Font(R.font.barlow_condensed_600, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_700, FontWeight.Bold),
    Font(R.font.barlow_condensed_800, FontWeight.ExtraBold),
    Font(R.font.barlow_condensed_800, FontWeight.Black)
)

// Clean modern sans-serif with tabular numerals for money, numbers & body text
val InterTightFontFamily = FontFamily(
    Font(R.font.inter_tight, FontWeight.Normal),
    Font(R.font.inter_tight_500, FontWeight.Medium),
    Font(R.font.inter_tight_600, FontWeight.SemiBold),
    Font(R.font.inter_tight_700, FontWeight.Bold),
    Font(R.font.inter_tight_800, FontWeight.ExtraBold),
    Font(R.font.inter_tight_800, FontWeight.Black)
)

private val CrispLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

// Tabular numeral TextStyle modifier helper for money amounts so columns always align
val TabularMoneyStyle = TextStyle(
    fontFamily = InterTightFontFamily,
    fontWeight = FontWeight.ExtraBold,
    fontFeatureSettings = "tnum",
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = CrispLineHeightStyle
)

// Bold Condensed Uppercase Screen Title style (34-40sp, tight editorial spacing, zero clipping)
val CondensedScreenTitleStyle = TextStyle(
    fontFamily = BarlowCondensedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 36.sp,
    lineHeight = 40.sp,
    letterSpacing = 0.2.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = CrispLineHeightStyle
)

// Bold Condensed Uppercase Card Title style (22-28sp, crisp editorial spacing)
val CondensedCardTitleStyle = TextStyle(
    fontFamily = BarlowCondensedFontFamily,
    fontWeight = FontWeight.Bold,
    fontSize = 23.sp,
    lineHeight = 26.sp,
    letterSpacing = 0.15.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = CrispLineHeightStyle
)

val Typography = Typography(
    // Big money & stat numbers (40sp+, clean bold sans-serif with tabular numerals)
    displayLarge = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 48.sp,
        lineHeight = 54.sp,
        letterSpacing = (-1.1).sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    displayMedium = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 42.sp,
        lineHeight = 47.sp,
        letterSpacing = (-0.9).sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    displaySmall = TextStyle(
        fontFamily = BarlowCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.15.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    // Screen titles (32-40sp Barlow Condensed, bold uppercase ready)
    headlineLarge = TextStyle(
        fontFamily = BarlowCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.15.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    // Card titles (24-28sp Barlow Condensed)
    headlineMedium = TextStyle(
        fontFamily = BarlowCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.1.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    headlineSmall = TextStyle(
        fontFamily = BarlowCondensedFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    titleLarge = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    titleMedium = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = "tnum",
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = CrispLineHeightStyle
    ),
    bodyLarge = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    bodyMedium = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    bodySmall = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    labelLarge = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    ),
    labelMedium = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
        fontFeatureSettings = "tnum"
    ),
    // Tiny uppercase grey section labels (e.g. "LIVE INSIGHTS")
    labelSmall = TextStyle(
        fontFamily = InterTightFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.9.sp,
        fontFeatureSettings = "tnum"
    )
)
