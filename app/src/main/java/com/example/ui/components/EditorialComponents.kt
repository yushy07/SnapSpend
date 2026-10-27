package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.data.local.CategoryEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.ui.theme.CandyLime
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.CondensedScreenTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialLavender
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.EditorialPink
import com.example.ui.theme.IncomeDarkGreen
import com.example.ui.theme.InkBlack
import com.example.ui.theme.InterTightFontFamily
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.NewTransactionHighlightTracker
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PaleTintCardPalette
import com.example.ui.theme.PaleYellowTint
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SecondaryGreyText
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.resolveCategoryDotAndPaleTint
import com.example.util.MoneyUtils
import kotlin.math.roundToInt

/**
 * Reusable 3D Extruded Container that renders a tactile 3D offset depth block behind the content.
 * Implements Requirement 3 (Press feedback):
 * On press, the element translates toward its black offset shadow (+depthX, +depthY) and the shadow
 * shrinks to nothing so it looks like a real chunky button being pushed in; on release it springs back.
 */
@Composable
fun Editorial3DLayerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    extrusionColor: Color = InkBlack,
    extrusionBorderColor: Color? = null,
    depthX: Dp = 4.dp,
    depthY: Dp = 6.dp,
    enableInteractiveTilt: Boolean = false,
    staticTiltX: Float = 0f,
    staticTiltY: Float = 0f,
    externalPressed: Boolean = false,
    content: @Composable () -> Unit
) {
    val densityObj = LocalDensity.current
    val density = densityObj.density
    val reducedMotion = LocalReducedMotion.current

    var touchTiltX by remember { mutableFloatStateOf(0f) }
    var touchTiltY by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }

    val animTiltX by animateFloatAsState(
        targetValue = if (reducedMotion) staticTiltX else staticTiltX + touchTiltX,
        animationSpec = Motion.physicalSpring(reducedMotion = reducedMotion),
        label = "3dTiltX"
    )
    val animTiltY by animateFloatAsState(
        targetValue = if (reducedMotion) staticTiltY else staticTiltY + touchTiltY,
        animationSpec = Motion.physicalSpring(reducedMotion = reducedMotion),
        label = "3dTiltY"
    )
    val pressFraction by animateFloatAsState(
        targetValue = if (isPressed || externalPressed) 1f else 0f,
        animationSpec = Motion.pressSpring(reducedMotion = reducedMotion),
        label = "3dPressFraction"
    )

    val gestureMod = Modifier.pointerInput(enableInteractiveTilt) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            isPressed = true
            val w = size.width.toFloat().coerceAtLeast(1f)
            val h = size.height.toFloat().coerceAtLeast(1f)
            fun update(pos: Offset) {
                if (enableInteractiveTilt) {
                    val nx = ((pos.x / w) - 0.5f) * 2f
                    val ny = ((pos.y / h) - 0.5f) * 2f
                    touchTiltY = (nx * 4.5f).coerceIn(-5.5f, 5.5f)
                    touchTiltX = (-ny * 4.5f).coerceIn(-5.5f, 5.5f)
                }
            }
            update(down.position)
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull()
                if (change != null && change.pressed) {
                    update(change.position)
                }
            } while (event.changes.any { it.pressed })
            isPressed = false
            touchTiltX = 0f
            touchTiltY = 0f
        }
    }

    val depthXPx = with(densityObj) { depthX.toPx() }
    val depthYPx = with(densityObj) { depthY.toPx() }
    val pushTranslateXPx = depthXPx * pressFraction
    val pushTranslateYPx = depthYPx * pressFraction

    Box(
        modifier = modifier
            .padding(end = depthX, bottom = depthY)
            .then(gestureMod)
    ) {
        val dynamicOffsetX = (depthX.value - animTiltY * 0.35f).roundToInt()
        val dynamicOffsetY = (depthY.value + animTiltX * 0.35f).roundToInt()

        // 3D Extruded Depth Base Block (stays anchored at the offset so as the foreground pushes into it, the visible shadow shrinks to 0)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(dynamicOffsetX.dp.roundToPx(), dynamicOffsetY.dp.roundToPx()) }
                .clip(shape)
                .background(extrusionColor)
                .then(
                    if (extrusionBorderColor != null) {
                        Modifier.border(1.2.dp, extrusionBorderColor, shape)
                    } else {
                        Modifier
                    }
                )
        )

        // Elevated Foreground Card Layer with 3D perspective & push-into-shadow translation
        Box(
            modifier = Modifier.graphicsLayer {
                translationX = pushTranslateXPx
                translationY = pushTranslateYPx
                rotationX = animTiltX
                rotationY = animTiltY
                cameraDistance = 14f * density
            }
        ) {
            content()
        }
    }
}

/**
 * Adaptive app background with 24dp dot-grid pattern (no radial vignette):
 * - Dark Mode: Deep Charcoal-Slate (#181A1F) with white dots at 8% opacity
 * - Light Mode: Warm Peach Cream (#F9E4D4) with subtle ink dots at 8% opacity
 */
@Composable
fun EditorialSquiggleBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = SnapSpendThemeTokens.colors
    val bgColor by animateColorAsState(targetValue = colors.appBackground, label = "appBg")
    val dotColor = if (colors.isDark) {
        Color(0xFFFFFFFF).copy(alpha = 0.08f)
    } else {
        InkBlack.copy(alpha = 0.08f)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .drawBehind {
                val stepPx = 24.dp.toPx()
                val dotRadiusPx = 1.5.dp.toPx()
                val startOffsetPx = 12.dp.toPx()
                var y = startOffsetPx
                while (y < size.height) {
                    var x = startOffsetPx
                    while (x < size.width) {
                        drawCircle(
                            color = dotColor,
                            radius = dotRadiusPx,
                            center = Offset(x, y)
                        )
                        x += stepPx
                    }
                    y += stepPx
                }
            }
    ) {
        content()
    }
}

/**
 * Bold Uppercase Condensed Screen Title (Barlow Condensed, 34-38sp, tight spacing)
 * with optional 3D leading round icon badge and trailing pill dropdown.
 */
@Composable
fun UppercaseScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    leadingIconBg: Color = PureWhite,
    leadingIconTint: Color = InkBlack,
    titleColor: Color = SnapSpendThemeTokens.colors.primaryText,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            if (leadingIcon != null) {
                Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .offset(x = 2.dp, y = 3.dp)
                            .clip(CircleShape)
                            .background(InkBlack)
                    )
                    Surface(
                        modifier = Modifier.size(42.dp),
                        shape = CircleShape,
                        color = leadingIconBg,
                        contentColor = leadingIconTint,
                        border = BorderStroke(1.6.dp, InkBlack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = leadingIcon,
                                contentDescription = null,
                                tint = leadingIconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Text(
                text = title.uppercase(),
                style = CondensedScreenTitleStyle,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailingContent()
        }
    }
}

/**
 * Tiny uppercase grey section label (like "LIVE INSIGHTS", "REPEATING PAYMENTS").
 */
@Composable
fun TinySectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = if (SnapSpendThemeTokens.colors.isDark) SecondaryGreyText else Color(0xFF5E5E5E)
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = InterTightFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 1.2.sp
        ),
        color = color
    )
}

/**
 * White 3D pill dropdown with a trailing chevron (like "This Week v", "All Categories v", "Today v").
 */
@Composable
fun WhitePillDropdown(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = PureWhite,
    textColor: Color = InkBlack,
    fillWidth: Boolean = false,
    testTag: String? = null
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    var toggleSpin by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (toggleSpin) 180f else 0f,
        animationSpec = Motion.physicalSpring(reducedMotion = reducedMotion),
        label = "dropdownChevronRotation"
    )
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    Editorial3DLayerBox(
        modifier = taggedModifier.minimumInteractiveComponentSize(),
        shape = CircleShape,
        extrusionColor = InkBlack,
        depthX = 3.dp,
        depthY = 4.dp
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                toggleSpin = !toggleSpin
                onClick()
            },
            modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            shape = CircleShape,
            color = backgroundColor,
            contentColor = textColor,
            border = BorderStroke(1.5.dp, InkBlack)
        ) {
            Row(
                modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier)
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (fillWidth) {
                    Arrangement.SpaceBetween
                } else {
                    Arrangement.spacedBy(6.dp)
                }
            ) {
                // Anchor scale + fade transition when the selected filter label changes
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (fadeIn(Motion.quickTween(reducedMotion)) +
                            scaleIn(initialScale = 0.92f, animationSpec = Motion.physicalSpring(reducedMotion)))
                            .togetherWith(
                                fadeOut(Motion.quickTween(reducedMotion)) +
                                    scaleOut(targetScale = 0.94f, animationSpec = Motion.quickTween(reducedMotion))
                            )
                    },
                    modifier = if (fillWidth) Modifier.weight(1f, fill = false) else Modifier,
                    label = "dropdownLabelAnim"
                ) { targetLabel ->
                    Text(
                        text = targetLabel,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier
                        .size(16.dp)
                        .graphicsLayer { rotationZ = chevronRotation }
                )
            }
        }
    }
}

/**
 * Round 3D arrow button:
 * - White circle (#FFFFFF) with black arrow (#0B0B0B) on black/dark cards
 * - Black circle (#0B0B0B) with white arrow (#FFFFFF) on colored cards
 * - Or custom circle color (e.g., SunnyYellow #FFD23F on "View details" footer)
 */
@Composable
fun RoundArrowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onColoredCard: Boolean = false,
    customCircleColor: Color? = null,
    customIconTint: Color? = null,
    size: Dp = 40.dp,
    contentDescription: String = "View details",
    testTag: String? = null
) {
    val view = LocalView.current
    val circleBg = customCircleColor ?: if (onColoredCard) InkBlack else PureWhite
    val iconTint = customIconTint ?: if (onColoredCard) PureWhite else InkBlack
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier

    Editorial3DLayerBox(
        modifier = tagged,
        shape = CircleShape,
        extrusionColor = if (circleBg == InkBlack) PureWhite.copy(alpha = 0.28f) else InkBlack,
        depthX = 2.dp,
        depthY = 3.dp
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = circleBg,
            contentColor = iconTint,
            border = BorderStroke(1.5.dp, InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(size * 0.46f)
                )
            }
        }
    }
}

/**
 * Little black pill with an up/down arrow and percent (like "↑ 12%", "0%") for changes.
 */
@Composable
fun BlackDeltaPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = InkBlack,
        contentColor = PureWhite,
        border = BorderStroke(1.dp, PureWhite.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                fontFeatureSettings = "tnum"
            ),
            color = PureWhite,
            maxLines = 1
        )
    }
}

/**
 * Small status pill with a colored dot (like "● High volume", "● Steady", "● Confirmed", "● Connected").
 */
@Composable
fun StatusDotPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
    backgroundColor: Color = PureWhite.copy(alpha = 0.90f),
    textColor: Color = InkBlack,
    onClick: (() -> Unit)? = null,
    testTag: String? = null
) {
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier
    val clickableModifier = if (onClick != null) {
        taggedModifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        taggedModifier
    }
    Surface(
        modifier = clickableModifier,
        shape = CircleShape,
        color = backgroundColor,
        contentColor = textColor,
        border = BorderStroke(1.2.dp, InkBlack.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(1.dp, InkBlack.copy(alpha = 0.4f), CircleShape)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Colored 3D Live Insight & Cashflow Card (same bold palette in Light & Dark themes):
 * - Uses a strong palette color (e.g., EditorialPink, EditorialAqua, EditorialLavender)
 * - Black text (#0B0B0B), black outline (#0B0B0B), and black 3D offset shadow in both themes.
 */
@Composable
fun DarkLiveInsightCard(
    title: String,
    bodyText: String,
    iconBadgeColor: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cardColor: Color = EditorialPink,
    showViewDetailsFooter: Boolean = true,
    testTag: String? = null
) {
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier
    val shape = RoundedCornerShape(28.dp)
    val effectiveBadgeColor = if (iconBadgeColor == cardColor) PureWhite else iconBadgeColor

    Editorial3DLayerBox(
        modifier = tagged.fillMaxWidth(),
        shape = shape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp,
        enableInteractiveTilt = true
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = cardColor,
            contentColor = InkBlack,
            border = BorderStroke(width = 1.8.dp, color = InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 3D Colored round icon on the left with black icon
                        Box(modifier = Modifier.padding(end = 2.dp, bottom = 3.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .offset(x = 2.dp, y = 3.dp)
                                    .clip(CircleShape)
                                    .background(InkBlack)
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(effectiveBadgeColor)
                                    .border(1.5.dp, InkBlack, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = InkBlack,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = title.uppercase(),
                                style = CondensedCardTitleStyle.copy(
                                    fontSize = 23.sp,
                                    lineHeight = 25.sp
                                ),
                                color = InkBlack,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = bodyText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = InkBlack.copy(alpha = 0.86f),
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Black circle arrow with white icon on colored card
                    RoundArrowButton(
                        onClick = onClick,
                        onColoredCard = true,
                        size = 38.dp
                    )
                }

                if (showViewDetailsFooter) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (cardColor == SunnyYellow) PureWhite else SunnyYellow,
                        contentColor = InkBlack,
                        border = BorderStroke(1.4.dp, InkBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                contentDescription = null,
                                tint = InkBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "View details",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 14.sp
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

/**
 * Pale Mint (#A8D5C0) 3D rounded hero area with a fun hand-drawn style cartoon illustration
 * (a cartoon receipt/register machine with a face, steam clouds, and a happy coin/piggy companion),
 * used on empty screens and at the top of Reports/Payments.
 */
@Composable
fun PaleMintIllustratedHeroCard(
    modifier: Modifier = Modifier,
    captionText: String? = null
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val bobAmplitudePx = with(density) { Motion.EMPTY_BOB_AMPLITUDE.toPx() }
    val bobOffsetPx = if (reducedMotion) {
        0f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "emptyHeroBob")
        val animBob by infiniteTransition.animateFloat(
            initialValue = -bobAmplitudePx,
            targetValue = bobAmplitudePx,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = Motion.BOB_CYCLE_MS / 2, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "emptyHeroBobOffset"
        )
        animBob
    }

    val shape = RoundedCornerShape(28.dp)
    Editorial3DLayerBox(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        extrusionColor = if (SnapSpendThemeTokens.colors.isDark) EditorialMint.copy(alpha = 0.55f) else InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp,
        enableInteractiveTilt = true
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = PaleMintHero, // #A8D5C0
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .graphicsLayer {
                            translationY = bobOffsetPx
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val thinStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

                    // Soft decorative 3D ground shadow
                    drawOval(
                        color = InkBlack.copy(alpha = 0.20f),
                        topLeft = Offset(w * 0.14f, h * 0.83f),
                        size = Size(w * 0.72f, h * 0.11f)
                    )

                    // Playful Coral & Black Steam Puffs above the cartoon receipt machine
                    drawCircle(
                        color = InkBlack,
                        radius = h * 0.12f,
                        center = Offset(w * 0.30f + 4.dp.toPx(), h * 0.18f + 4.dp.toPx())
                    )
                    drawCircle(
                        color = EditorialCoral,
                        radius = h * 0.12f,
                        center = Offset(w * 0.30f, h * 0.18f)
                    )
                    drawCircle(
                        color = InkBlack,
                        radius = h * 0.12f,
                        center = Offset(w * 0.30f, h * 0.18f),
                        style = stroke
                    )
                    drawCircle(
                        color = InkBlack,
                        radius = h * 0.08f,
                        center = Offset(w * 0.22f, h * 0.21f)
                    )
                    drawCircle(
                        color = EditorialCoral,
                        radius = h * 0.10f,
                        center = Offset(w * 0.56f, h * 0.16f)
                    )
                    drawCircle(
                        color = InkBlack,
                        radius = h * 0.10f,
                        center = Offset(w * 0.56f, h * 0.16f),
                        style = stroke
                    )

                    // Left Cartoon Receipt/Register Box (Cream/Yellow body with 3D side block, face & receipt tape)
                    val boxLeft = w * 0.23f
                    val boxTop = h * 0.30f
                    val boxW = w * 0.36f
                    val boxH = h * 0.48f

                    // Side receipt ticket sticking out on the left
                    drawRoundRect(
                        color = SunnyYellow,
                        topLeft = Offset(w * 0.14f, h * 0.38f),
                        size = Size(w * 0.10f, h * 0.30f),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = InkBlack,
                        topLeft = Offset(w * 0.14f, h * 0.38f),
                        size = Size(w * 0.10f, h * 0.30f),
                        cornerRadius = CornerRadius(10f, 10f),
                        style = stroke
                    )

                    // 3D Extruded shadow block behind cartoon register
                    drawRoundRect(
                        color = InkBlack,
                        topLeft = Offset(boxLeft + 6.dp.toPx(), boxTop + 6.dp.toPx()),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(24f, 24f)
                    )

                    // Outer body of cartoon register
                    drawRoundRect(
                        color = Color(0xFFFFF5D6),
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(24f, 24f)
                    )
                    drawRoundRect(
                        color = InkBlack,
                        topLeft = Offset(boxLeft, boxTop),
                        size = Size(boxW, boxH),
                        cornerRadius = CornerRadius(24f, 24f),
                        style = stroke
                    )

                    // Inner Aqua screen with cartoon face
                    val scrLeft = boxLeft + boxW * 0.12f
                    val scrTop = boxTop + boxH * 0.14f
                    val scrW = boxW * 0.76f
                    val scrH = boxH * 0.62f
                    drawRoundRect(
                        color = EditorialAqua,
                        topLeft = Offset(scrLeft, scrTop),
                        size = Size(scrW, scrH),
                        cornerRadius = CornerRadius(16f, 16f)
                    )
                    drawRoundRect(
                        color = InkBlack,
                        topLeft = Offset(scrLeft, scrTop),
                        size = Size(scrW, scrH),
                        cornerRadius = CornerRadius(16f, 16f),
                        style = stroke
                    )

                    // Cartoon eyes & smile inside the screen
                    drawCircle(
                        color = InkBlack,
                        radius = 5.dp.toPx(),
                        center = Offset(scrLeft + scrW * 0.34f, scrTop + scrH * 0.42f)
                    )
                    drawCircle(
                        color = InkBlack,
                        radius = 5.dp.toPx(),
                        center = Offset(scrLeft + scrW * 0.66f, scrTop + scrH * 0.42f)
                    )
                    drawArc(
                        color = InkBlack,
                        startAngle = 20f,
                        sweepAngle = 140f,
                        useCenter = false,
                        topLeft = Offset(scrLeft + scrW * 0.36f, scrTop + scrH * 0.48f),
                        size = Size(scrW * 0.28f, scrH * 0.26f),
                        style = thinStroke
                    )

                    // Cartoon legs under the register
                    drawLine(
                        color = InkBlack,
                        start = Offset(boxLeft + boxW * 0.32f, boxTop + boxH),
                        end = Offset(boxLeft + boxW * 0.30f, h * 0.87f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = InkBlack,
                        start = Offset(boxLeft + boxW * 0.68f, boxTop + boxH),
                        end = Offset(boxLeft + boxW * 0.70f, h * 0.87f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    // Right Cartoon Sunny Yellow Coin / Mascot Character with Coral sash & telescope
                    val coinCenter = Offset(w * 0.69f, h * 0.46f)
                    val coinRadius = h * 0.17f

                    // Coral cape/sash body below the yellow coin head
                    val bodyPath = Path().apply {
                        moveTo(coinCenter.x - coinRadius * 0.7f, coinCenter.y + coinRadius * 0.6f)
                        lineTo(coinCenter.x + coinRadius * 0.85f, coinCenter.y + coinRadius * 0.5f)
                        lineTo(coinCenter.x + coinRadius * 1.05f, h * 0.86f)
                        lineTo(coinCenter.x - coinRadius * 0.85f, h * 0.86f)
                        close()
                    }
                    drawPath(path = bodyPath, color = EditorialCoral)
                    drawPath(path = bodyPath, color = InkBlack, style = stroke)

                    // 3D Coin rim shadow
                    drawCircle(
                        color = InkBlack,
                        radius = coinRadius,
                        center = Offset(coinCenter.x + 4.dp.toPx(), coinCenter.y + 4.dp.toPx())
                    )

                    // Sunny Yellow round coin head
                    drawCircle(
                        color = SunnyYellow,
                        radius = coinRadius,
                        center = coinCenter
                    )
                    drawCircle(
                        color = InkBlack,
                        radius = coinRadius,
                        center = coinCenter,
                        style = stroke
                    )

                    // Happy eyes on the yellow mascot
                    drawCircle(
                        color = InkBlack,
                        radius = 4.dp.toPx(),
                        center = Offset(coinCenter.x - coinRadius * 0.28f, coinCenter.y - coinRadius * 0.1f)
                    )
                    drawCircle(
                        color = InkBlack,
                        radius = 4.dp.toPx(),
                        center = Offset(coinCenter.x + coinRadius * 0.22f, coinCenter.y - coinRadius * 0.1f)
                    )

                    // Little Coral spyglass/horn held by the mascot
                    drawRoundRect(
                        color = EditorialCoral,
                        topLeft = Offset(coinCenter.x + coinRadius * 0.35f, coinCenter.y - 6.dp.toPx()),
                        size = Size(w * 0.14f, 12.dp.toPx()),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    drawRoundRect(
                        color = InkBlack,
                        topLeft = Offset(coinCenter.x + coinRadius * 0.35f, coinCenter.y - 6.dp.toPx()),
                        size = Size(w * 0.14f, 12.dp.toPx()),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = thinStroke
                    )
                }

                if (!captionText.isNullOrBlank()) {
                    Text(
                        text = captionText,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = InkBlack
                    )
                }
            }
        }
    }
}

/**
 * Playful 3D Top Bar optimized for Dark Mode and Light Mode:
 * - Floating 3D-extruded pill container (#1C1C1E in Dark Mode with Periwinkle/Aqua 3D lip, or PureWhite in Light Mode)
 * - Playful 3D cartoon mascot avatar on the left with a floating ₹ coin badge
 * - Punchy SNAPSPEND condensed header + live status dot badge ("● PRIVATE LEDGER")
 * - Three tactile 3D-extruded candy-colored circle action buttons on the right:
 *   1. Sunny Yellow (#FFD23F) Theme Toggle button
 *   2. Coral (#FF6F59) Quick AI Scan bolt button
 *   3. Aqua (#8FE3F0) Settings & Backup button
 */
@Composable
fun YuEditorialTopBar(
    onScanClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onToggleThemeClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = SnapSpendThemeTokens.colors
    val isDark = colors.isDark
    val pillShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 0.dp)
    ) {
        Editorial3DLayerBox(
            modifier = Modifier.fillMaxWidth(),
            shape = pillShape,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 3.dp,
            depthY = 5.dp
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = pillShape,
                color = PureWhite,
                contentColor = InkBlack,
                border = BorderStroke(width = 1.8.dp, color = InkBlack)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        YuProfileAvatarCircle(size = 46.dp)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "SNAPSPEND",
                                    style = CondensedCardTitleStyle.copy(
                                        fontSize = 23.sp,
                                        lineHeight = 24.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = InkBlack,
                                    maxLines = 1
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = SunnyYellow,
                                    border = BorderStroke(1.dp, InkBlack)
                                ) {
                                    Text(
                                        text = "INR",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        ),
                                        color = InkBlack
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(EditorialMint)
                                )
                                Text(
                                    text = "Saved on your phone",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = InkBlack.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onToggleThemeClick != null) {
                            Playful3DCircleActionButton(
                                icon = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                                containerColor = SunnyYellow,
                                iconTint = InkBlack,
                                onClick = onToggleThemeClick,
                                testTag = "top_theme_toggle_button"
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Playful 3D-extruded candy circle button used in the Top Bar.
 * Includes push-into-shadow press feedback, light haptic tick, and rotating/scaling icon swap (Moon/Sun).
 */
@Composable
fun Playful3DCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    iconTint: Color = InkBlack,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    testTag: String? = null
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val tagged = if (testTag != null) Modifier.testTag(testTag) else Modifier

    Editorial3DLayerBox(
        modifier = tagged.minimumInteractiveComponentSize(),
        shape = CircleShape,
        extrusionColor = InkBlack,
        depthX = 2.dp,
        depthY = 3.dp
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = containerColor,
            contentColor = iconTint,
            border = BorderStroke(1.6.dp, InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // 10. THEME TOGGLE: Moon/Sun icon rotates and swaps with a quick scale
                val spinAngle by animateFloatAsState(
                    targetValue = if (icon == Icons.Default.LightMode) 0f else 180f,
                    animationSpec = Motion.physicalSpring(reducedMotion = reducedMotion),
                    label = "topBarIconSpin"
                )
                AnimatedContent(
                    targetState = icon,
                    transitionSpec = {
                        (fadeIn(Motion.quickTween(reducedMotion)) +
                            scaleIn(initialScale = 0.55f, animationSpec = Motion.physicalSpring(reducedMotion)))
                            .togetherWith(
                                fadeOut(Motion.quickTween(reducedMotion)) +
                                    scaleOut(targetScale = 0.55f, animationSpec = Motion.quickTween(reducedMotion))
                            )
                    },
                    label = "topBarIconSwap"
                ) { targetIcon ->
                    Icon(
                        imageVector = targetIcon,
                        contentDescription = contentDescription,
                        tint = iconTint,
                        modifier = Modifier
                            .size(19.dp)
                            .graphicsLayer {
                                rotationZ = if (targetIcon == Icons.Default.LightMode) spinAngle else spinAngle - 180f
                            }
                    )
                }
            }
        }
    }
}

/**
 * Custom 3D SnapSpend Brand Logo Emblem used in the Top Bar:
 * - Deep Ink (#12121A) rounded-square badge with a solid #0B0B0B 3D offset shadow
 * - Tilted Coral (#FF6F59) & Periwinkle Indigo (#5B5CF6) stacked backing cards
 * - Front Sunny Yellow (#FFD23F) zig-zag receipt card with Mint (#9FE3C0) header and bold black ₹ symbol
 * - Floating Aqua (#8FE3F0) 4-point AI scan star in the top-right corner
 */
/**
 * Custom 3D SnapSpend Brand Logo Emblem used in the Top Bar:
 * - Displays the official SnapSpend app logo from logo.svg
 */
@Composable
fun YuProfileAvatarCircle(size: Dp = 46.dp) {
    val badgeShape = RoundedCornerShape(13.dp)
    Editorial3DLayerBox(
        modifier = Modifier.size(size),
        shape = badgeShape,
        depthX = 2.dp,
        depthY = 3.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.snapspend_logo),
            contentDescription = "SnapSpend Logo",
            modifier = Modifier
                .fillMaxSize()
                .clip(badgeShape)
                .border(1.5.dp, InkBlack, badgeShape)
        )
    }
}

/**
 * Round 3D circle icon button:
 * - On dark screens: White circle (#FFFFFF) with black (#0B0B0B) icon and 3D base.
 * - When `forceInkBlack = true`: Black circle (#0B0B0B) with white (#FFFFFF) icon.
 */
@Composable
fun BlackCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    forceInkBlack: Boolean = false,
    testTag: String? = null
) {
    val view = LocalView.current
    val colors = SnapSpendThemeTokens.colors
    val useWhiteCircle = colors.isDark && !forceInkBlack
    val containerColor = if (useWhiteCircle) PureWhite else InkBlack
    val iconTint = if (useWhiteCircle) InkBlack else PureWhite
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Editorial3DLayerBox(
        modifier = taggedModifier.minimumInteractiveComponentSize(),
        shape = CircleShape,
        extrusionColor = if (useWhiteCircle) EditorialAqua else InkBlack.copy(alpha = 0.45f),
        depthX = 2.dp,
        depthY = 3.dp
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            modifier = Modifier.size(size),
            shape = CircleShape,
            color = containerColor,
            contentColor = iconTint,
            border = BorderStroke(1.5.dp, InkBlack)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Small 40dp 3D circle icon button in the top-right corner of colored cards:
 * - Black circle (#0B0B0B) with white icon (#FFFFFF) on colored cards.
 */
@Composable
fun BrightCardCornerIconButton(
    cardColor: Color = CandyLime,
    parentCardColor: Color = cardColor,
    icon: ImageVector = Icons.AutoMirrored.Filled.ArrowForward,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val view = LocalView.current
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Editorial3DLayerBox(
        modifier = taggedModifier,
        shape = CircleShape,
        extrusionColor = PureWhite.copy(alpha = 0.45f),
        depthX = 2.dp,
        depthY = 2.dp
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            modifier = Modifier.size(40.dp),
            shape = CircleShape,
            color = InkBlack,
            contentColor = PureWhite
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = PureWhite,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

/**
 * Wide 3D secondary button (e.g. "+ Add payment by hand"):
 * - Strong Lavender (#C9A0FF) surface with black text (#0B0B0B) and black outline (#0B0B0B) in both themes.
 */
@Composable
fun WideSecondaryPillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    containerColor: Color = EditorialLavender,
    testTag: String? = null
) {
    val view = LocalView.current
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier

    Editorial3DLayerBox(
        modifier = tagged
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
        shape = CircleShape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 3.dp,
        depthY = 5.dp
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = containerColor,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = InkBlack,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = label.uppercase(),
                    style = CondensedCardTitleStyle.copy(fontSize = 19.sp),
                    color = InkBlack
                )
            }
        }
    }
}

/**
 * 6. SEGMENTED CONTROLS:
 * Segmented toggle with 3D extruded track (white track with sliding Aqua #8FE3F0 selected pill & black text in both themes).
 * The selected Aqua pill slides smoothly from one option to the next with a spring, and the label color cross-fades.
 */
@Composable
fun SegmentedPillToggle(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segmented_option"
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val safeSelectedIndex = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))

    Editorial3DLayerBox(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        extrusionColor = InkBlack,
        depthX = 3.dp,
        depthY = 4.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = PureWhite,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val count = options.size.coerceAtLeast(1)
                val gap = 4.dp
                val totalGap = gap * (count - 1)
                val pillWidth = ((maxWidth - totalGap) / count).coerceAtLeast(0.dp)
                val targetOffsetX = (pillWidth + gap) * safeSelectedIndex
                val animatedOffsetX by animateDpAsState(
                    targetValue = targetOffsetX,
                    animationSpec = Motion.physicalSpring(reducedMotion = reducedMotion),
                    label = "segSlidingPillOffset"
                )

                // Sliding Aqua (#8FE3F0) Selected Pill Indicator
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffsetX)
                        .width(pillWidth)
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(EditorialAqua)
                        .border(1.2.dp, InkBlack, CircleShape)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    options.forEachIndexed { index, label ->
                        val isSelected = index == safeSelectedIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) InkBlack else InkBlack.copy(alpha = 0.72f),
                            animationSpec = Motion.quickTween(reducedMotion = reducedMotion),
                            label = "segPillTextColor"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(role = Role.Tab) {
                                    Motion.performLightTapHaptic(view)
                                    onOptionSelected(index)
                                }
                                .padding(horizontal = 8.dp)
                                .testTag("${testTagPrefix}_$index"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> SegmentedPillToggle(
    options: List<T>,
    selectedOption: T,
    labelProvider: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTagProvider: ((T) -> String)? = null
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val selectedIndex = options.indexOf(selectedOption).coerceAtLeast(0)

    Editorial3DLayerBox(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        extrusionColor = InkBlack,
        depthX = 3.dp,
        depthY = 4.dp
    ) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = CircleShape,
            color = PureWhite,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val count = options.size.coerceAtLeast(1)
                val gap = 4.dp
                val totalGap = gap * (count - 1)
                val pillWidth = ((maxWidth - totalGap) / count).coerceAtLeast(0.dp)
                val targetOffsetX = (pillWidth + gap) * selectedIndex
                val animatedOffsetX by animateDpAsState(
                    targetValue = targetOffsetX,
                    animationSpec = Motion.physicalSpring(reducedMotion = reducedMotion),
                    label = "segGenericSlidingPillOffset"
                )

                // Sliding Aqua (#8FE3F0) Selected Pill Indicator
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffsetX)
                        .width(pillWidth)
                        .height(38.dp)
                        .clip(CircleShape)
                        .background(EditorialAqua)
                        .border(1.2.dp, InkBlack, CircleShape)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    options.forEachIndexed { index, option ->
                        val isSelected = option == selectedOption
                        val textColor by animateColorAsState(
                            targetValue = if (isSelected) InkBlack else InkBlack.copy(alpha = 0.72f),
                            animationSpec = Motion.quickTween(reducedMotion = reducedMotion),
                            label = "segGenericTextColor"
                        )
                        val tag = optionTestTagProvider?.invoke(option) ?: "segmented_option_$index"

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .minimumInteractiveComponentSize()
                                .clip(CircleShape)
                                .clickable(role = Role.Tab) {
                                    Motion.performLightTapHaptic(view)
                                    onOptionSelected(option)
                                }
                                .padding(horizontal = 8.dp)
                                .testTag(tag),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labelProvider(option),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                ),
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean bold sans-serif with tabular numerals (40sp+) for stat cards.
 */
@Composable
fun ResponsiveTabularNumberText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 46.sp,
    minFontSize: TextUnit = 24.sp,
    maxFontSizeSp: Float = maxFontSize.value,
    minFontSizeSp: Float = minFontSize.value,
    color: Color = InkBlack,
    fontWeight: FontWeight = FontWeight.ExtraBold
) {
    val len = text.length
    val computedSp = when {
        len <= 7 -> maxFontSizeSp
        len <= 9 -> (maxFontSizeSp * 0.86f).coerceAtLeast(minFontSizeSp)
        len <= 11 -> (maxFontSizeSp * 0.74f).coerceAtLeast(minFontSizeSp)
        len <= 13 -> (maxFontSizeSp * 0.64f).coerceAtLeast(minFontSizeSp)
        else -> minFontSizeSp
    }

    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.displayMedium.copy(
            fontFamily = InterTightFontFamily,
            fontWeight = fontWeight,
            fontSize = computedSp.sp,
            lineHeight = (computedSp * 1.10f).sp,
            letterSpacing = (-0.9).sp,
            fontFeatureSettings = "tnum"
        ),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}

/**
 * White 3D pill chip / filter button with push-into-shadow press feedback and light haptic tick.
 */
@Composable
fun WhitePillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onBrightCard: Boolean = false,
    enabled: Boolean = true,
    testTag: String? = null
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val colors = SnapSpendThemeTokens.colors
    val targetBg = when {
        !enabled -> PureWhite.copy(alpha = 0.55f)
        onBrightCard && selected -> InkBlack
        onBrightCard && !selected -> PureWhite
        selected -> EditorialAqua
        colors.isDark -> PureWhite
        else -> PureWhite
    }
    val contentColor = when {
        !enabled -> InkBlack.copy(alpha = 0.45f)
        onBrightCard && selected -> PureWhite
        else -> InkBlack
    }
    val bgColor by animateColorAsState(
        targetValue = targetBg,
        animationSpec = Motion.quickTween(reducedMotion = reducedMotion),
        label = "chipBg"
    )
    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Editorial3DLayerBox(
        modifier = taggedModifier.minimumInteractiveComponentSize(),
        shape = CircleShape,
        extrusionColor = if (enabled) InkBlack else InkBlack.copy(alpha = 0.35f),
        depthX = 2.dp,
        depthY = 3.dp
    ) {
        Surface(
            onClick = {
                if (enabled) {
                    Motion.performLightTapHaptic(view)
                }
                onClick()
            },
            enabled = enabled,
            shape = CircleShape,
            color = bgColor,
            contentColor = contentColor,
            border = BorderStroke(1.4.dp, if (enabled) InkBlack else InkBlack.copy(alpha = 0.40f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Underlined text link for secondary actions like "See all" or "Reset filters".
 */
@Composable
fun UnderlinedActionLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onBrightCard: Boolean = false,
    testTag: String? = null
) {
    val colors = SnapSpendThemeTokens.colors
    val textColor = if (onBrightCard) InkBlack else if (colors.isDark) SunnyYellow else InkBlack
    val tagged = if (testTag != null) modifier.testTag(testTag) else modifier
    Box(
        modifier = tagged
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                textDecoration = TextDecoration.Underline
            ),
            color = textColor
        )
    }
}

/**
 * 3D Stacked Transaction Deck (Home screen Recent Activity):
 * - Follows the Transactions rule in both Light and Dark themes:
 *   each transaction row uses the pale tint of its category's color (or PaleYellowTint if no color),
 *   a black outline, a solid category-color dot, black text, income in #0F7A3E, and expenses in black.
 */
@Composable
fun StackedPastelTransactionDeck(
    transactions: List<TransactionEntity>,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<CategoryEntity> = emptyList(),
    highlightTopCardInLime: Boolean = true
) {
    val reducedMotion = LocalReducedMotion.current
    val categoryMap = remember(categories) {
        categories.associateBy { it.name.lowercase() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.physicalIntSizeSpring(reducedMotion)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var previousCardTint: Color? = null
        transactions.forEachIndexed { index, tx ->
            val catEntity = categoryMap[tx.category.lowercase()]
            val (dotColor, basePaleTint) = resolveCategoryDotAndPaleTint(tx.category, catEntity?.colorHex)
            val resolvedPaleTint = if (basePaleTint == previousCardTint) {
                val curIdx = PaleTintCardPalette.indexOf(basePaleTint).coerceAtLeast(0)
                PaleTintCardPalette[(curIdx + 1) % PaleTintCardPalette.size]
            } else {
                basePaleTint
            }
            previousCardTint = resolvedPaleTint

            key(tx.id) {
                EditorialTransactionCard(
                    transaction = tx,
                    cardColor = resolvedPaleTint,
                    categoryDotColor = dotColor,
                    accentExtrusionColor = InkBlack,
                    isHeroCard = highlightTopCardInLime && index == 0,
                    onColoredCard = true,
                    avatarBadgeColor = dotColor,
                    staticTiltX = 0f,
                    onClick = { onTransactionClick(tx) },
                    modifier = Modifier.zIndex((transactions.size - index).toFloat())
                )
            }
        }
    }
}

/**
 * 3D Separated Transaction List used on the Transactions screen:
 * - Each transaction row uses the pale tint of its category's color (or PaleYellowTint if no color)
 *   with a black outline, a solid category-color dot, black text, income in #0F7A3E, and expenses in black.
 * - Same in Light and Dark themes.
 */
@Composable
fun SeparatedPastelTransactionList(
    transactions: List<TransactionEntity>,
    onTransactionClick: (TransactionEntity) -> Unit,
    modifier: Modifier = Modifier,
    categories: List<CategoryEntity> = emptyList(),
    highlightFirstCardInLime: Boolean = false
) {
    val reducedMotion = LocalReducedMotion.current
    val categoryMap = remember(categories) {
        categories.associateBy { it.name.lowercase() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = Motion.physicalIntSizeSpring(reducedMotion)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        var previousCardTint: Color? = null
        transactions.forEachIndexed { index, tx ->
            val catEntity = categoryMap[tx.category.lowercase()]
            val (dotColor, basePaleTint) = resolveCategoryDotAndPaleTint(tx.category, catEntity?.colorHex)
            val resolvedPaleTint = if (basePaleTint == previousCardTint) {
                val curIdx = PaleTintCardPalette.indexOf(basePaleTint).coerceAtLeast(0)
                PaleTintCardPalette[(curIdx + 1) % PaleTintCardPalette.size]
            } else {
                basePaleTint
            }
            previousCardTint = resolvedPaleTint

            key(tx.id) {
                EditorialTransactionCard(
                    transaction = tx,
                    cardColor = resolvedPaleTint,
                    categoryDotColor = dotColor,
                    accentExtrusionColor = InkBlack,
                    isHeroCard = highlightFirstCardInLime && index == 0,
                    onColoredCard = true,
                    avatarBadgeColor = dotColor,
                    onClick = { onTransactionClick(tx) }
                )
            }
        }
    }
}

@Composable
fun EditorialTransactionCard(
    transaction: TransactionEntity,
    cardColor: Color,
    isHeroCard: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    categoryDotColor: Color = SunnyYellow,
    accentExtrusionColor: Color = InkBlack,
    onColoredCard: Boolean = true,
    avatarBadgeColor: Color = categoryDotColor,
    staticTiltX: Float = 0f
) {
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val isNewlyAdded = remember(transaction.id) {
        NewTransactionHighlightTracker.consumeFlashIfNew(transaction.id)
    }
    val slideInProgress = remember(transaction.id) {
        Animatable(if (isNewlyAdded && !reducedMotion) 0f else 1f)
    }
    val flashIntensity = remember(transaction.id) {
        Animatable(if (isNewlyAdded && !reducedMotion) 1f else 0f)
    }

    LaunchedEffect(isNewlyAdded, reducedMotion) {
        if (isNewlyAdded && !reducedMotion) {
            slideInProgress.animateTo(
                targetValue = 1f,
                animationSpec = Motion.physicalSpring(reducedMotion = false)
            )
            flashIntensity.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = Motion.HIGHLIGHT_FLASH_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    val slideDistancePx = with(density) { 18.dp.toPx() }
    val effectiveCardColor = if (flashIntensity.value > 0.01f) {
        lerp(cardColor, SunnyYellow, flashIntensity.value * 0.65f)
    } else {
        cardColor
    }

    val prefix = when (transaction.type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.EARNING -> "+"
        TransactionType.TRANSFER -> "⇄ "
    }
    val formattedAmount = prefix + MoneyUtils.formatPaise(transaction.amountPaise)
    val isSplitBill = transaction.note.contains("[Split", ignoreCase = true)
    val amountTextColor = when (transaction.type) {
        TransactionType.EARNING -> IncomeDarkGreen // #0F7A3E darker green for income
        TransactionType.EXPENSE -> InkBlack        // #0B0B0B black for expenses
        TransactionType.TRANSFER -> InkBlack
    }
    val shape = RoundedCornerShape(28.dp)

    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = slideInProgress.value.coerceIn(0f, 1.1f)
                translationY = -(1f - p) * slideDistancePx
                alpha = p.coerceIn(0f, 1f)
            },
        shape = shape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp,
        enableInteractiveTilt = isHeroCard,
        staticTiltX = staticTiltX
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_card_${transaction.id}"),
            shape = shape,
            color = effectiveCardColor,
            contentColor = InkBlack,
            border = BorderStroke(if (flashIntensity.value > 0.1f) 2.6.dp else 1.8.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = if (isHeroCard) 18.dp else 15.dp
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isHeroCard) 44.dp else 42.dp)
                            .clip(CircleShape)
                            .background(PureWhite)
                            .border(1.5.dp, InkBlack, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = categoryIconVector(transaction.category, transaction.type),
                            contentDescription = transaction.category,
                            tint = InkBlack,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.merchant.uppercase(),
                            style = CondensedCardTitleStyle.copy(
                                fontSize = if (isHeroCard) 22.sp else 20.sp,
                                lineHeight = 23.sp
                            ),
                            color = InkBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Solid category-color dot with black outline
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(categoryDotColor)
                                    .border(1.2.dp, InkBlack, CircleShape)
                            )
                            Text(
                                text = transaction.category,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = InkBlack.copy(alpha = 0.84f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (transaction.paymentMode.equals("CASH", ignoreCase = true)) {
                                CashModeBadge(modifier = Modifier.testTag("cash_badge_${transaction.id}"))
                            }
                            Text(
                                text = "· ${MoneyUtils.formatShortDateTime(transaction.timestamp, transaction.hasExplicitTime)}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = InkBlack.copy(alpha = 0.84f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = amountTextColor,
                        maxLines = 1
                    )
                    if (transaction.status != TransactionStatus.SUCCESS) {
                        Spacer(modifier = Modifier.height(3.dp))
                        StatusPillBadge(
                            text = transaction.status.name,
                            backgroundColor = if (transaction.status == TransactionStatus.PENDING) SunnyYellow else PureWhite
                        )
                    } else if (isSplitBill) {
                        Spacer(modifier = Modifier.height(3.dp))
                        StatusPillBadge(text = "Split bill", backgroundColor = PureWhite)
                    } else if (transaction.type == TransactionType.TRANSFER) {
                        Spacer(modifier = Modifier.height(3.dp))
                        StatusPillBadge(text = "Transfer", backgroundColor = PureWhite)
                    }
                }
            }
        }
    }
}

/**
 * Chunky Sunny Yellow (#FFD23F) [CASH] pill badge with black border and black text
 * displayed next to the category name on cash transactions.
 */
@Composable
fun CashModeBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = SunnyYellow,
        border = BorderStroke(1.3.dp, InkBlack)
    ) {
        Text(
            text = "CASH",
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            ),
            color = InkBlack,
            maxLines = 1
        )
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    backgroundColor: Color = PureWhite,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = backgroundColor,
        border = BorderStroke(1.2.dp, InkBlack)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = InkBlack,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun categoryIconVector(category: String, type: TransactionType = TransactionType.EXPENSE): ImageVector {
    if (type == TransactionType.TRANSFER) return Icons.AutoMirrored.Filled.CompareArrows
    return when (category.lowercase()) {
        "food & dining", "restaurant" -> Icons.Default.Restaurant
        "groceries", "shopping_basket" -> Icons.Default.ShoppingBasket
        "transport & fuel", "directions_car" -> Icons.Default.DirectionsCar
        "shopping", "shopping_bag" -> Icons.Default.ShoppingBag
        "bills & utilities", "bolt" -> Icons.Default.Bolt
        "rent", "home" -> Icons.Default.Home
        "entertainment", "movie" -> Icons.Default.Movie
        "health", "medical_services" -> Icons.Default.MedicalServices
        "education", "school" -> Icons.Default.School
        "travel", "flight" -> Icons.Default.Flight
        "subscriptions", "autorenew" -> Icons.Default.Autorenew
        "sent to people", "person" -> Icons.Default.Person
        "salary", "payments" -> Icons.Default.Payments
        "freelance/side income", "work" -> Icons.Default.Work
        "pocket money/gifts", "card_giftcard" -> Icons.Default.CardGiftcard
        "refunds & cashback", "replay" -> Icons.Default.Replay
        "other income", "savings" -> Icons.Default.Savings
        else -> Icons.Default.Storefront
    }
}

/**
 * 6-second Editorial Aqua (#8FE3F0) UNDO bar displayed above the bottom navigation bar
 * whenever a payment is soft-deleted ("Payment deleted · UNDO") or added via Quick Repeat ("Added Chai ₹20 · UNDO").
 */
@Composable
fun EditorialAquaUndoBar(
    message: String,
    actionToken: Long,
    onUndoClick: () -> Unit,
    onAutoDismiss: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    LaunchedEffect(actionToken) {
        kotlinx.coroutines.delay(6000L)
        onAutoDismiss(actionToken)
    }

    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("aqua_undo_bar"),
        shape = CircleShape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 3.dp,
        depthY = 4.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = EditorialAqua,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = null,
                        tint = InkBlack,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "$message · UNDO",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = InkBlack,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("aqua_undo_bar_text")
                    )
                }

                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onUndoClick()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("aqua_undo_button"),
                    shape = CircleShape,
                    color = InkBlack,
                    contentColor = PureWhite
                ) {
                    Text(
                        text = "UNDO",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.6.sp
                        ),
                        color = PureWhite
                    )
                }
            }
        }
    }
}

