package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.ui.sound.LocalRetroSoundManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleLavenderTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunnyYellow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

/**
 * 5-Slide Onboarding Carousel faithfully matching the uploaded reference illustrations:
 * - Slide 1: "SNAP IT. SORT IT." — Electric Royal Blue, Swiggy Instamart receipt, GPay, ₹500 bill, Yellow Scanner Mascot
 * - Slide 2: "ADD IN, SECONDS." — Two-tone Cream & Electric Blue, 4 Action strips, PhonePe badge, ₹200 bill
 * - Slide 3: "YOU STAY IN CONTROL." — Sunset Ember Red to Midnight Indigo, Zomato order, Confirm & Save, Paytm, Winking Mascot with speech bubble
 * - Slide 4: "SEE WHERE, IT GOES." — Seafoam Green watercolor splash, Spend Smarter receipt, CRED, ₹2000 bill, Peeking Mascot, Monthly category bars
 * - Slide 5: "YOUR MONEY, YOUR PHONE." — Warm Canvas with Emerald & Coral curves, Phone hugging Mascot, Sticky Note, 3 Security checks, GET STARTED
 */
@Composable
fun OnboardingCarouselScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(pageCount = { 5 })
    val coroutineScope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val view = LocalView.current
    val soundManager = LocalRetroSoundManager.current

    DisposableEffect(Unit) {
        soundManager?.startCarouselBgm()
        onDispose {
            soundManager?.startMainBgm()
        }
    }

    var previousPage by remember { mutableStateOf(pagerState.currentPage) }
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != previousPage) {
            previousPage = pagerState.currentPage
            soundManager?.playSwoosh()
        }
    }

    BackHandler {
        soundManager?.playButtonBlip()
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onFinish()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("onboarding_carousel_screen")
    ) {
        // Dynamic Slide Background Layer
        SlideBackgroundRenderer(page = pagerState.currentPage)

        // Floating Ambient Particles (sparkles / bokeh)
        AmbientFloatingParticles(page = pagerState.currentPage, reducedMotion = reducedMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Top Bar: Step Counter, Capsule Dots & 3D Skip Pill
            val isDarkTextSlide = pagerState.currentPage == 1 || pagerState.currentPage == 3 || pagerState.currentPage == 4
            val topTextColor = if (isDarkTextSlide) InkBlack else PureWhite

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / 5",
                        style = CondensedCardTitleStyle.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = topTextColor,
                        modifier = Modifier.testTag("onboarding_step_counter")
                    )

                    // Capsule Dots Indicator in Top Bar (shown on Slides 2, 4, 5 as in reference images)
                    if (pagerState.currentPage != 0 && pagerState.currentPage != 2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(5) { idx ->
                                val isSelected = idx == pagerState.currentPage
                                val activeColor = when (pagerState.currentPage) {
                                    1 -> Color(0xFF3858F8)
                                    3 -> SunnyYellow
                                    4 -> Color(0xFFF56042)
                                    else -> SunnyYellow
                                }
                                Box(
                                    modifier = Modifier
                                        .height(7.dp)
                                        .width(if (isSelected) 18.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) activeColor else topTextColor.copy(alpha = 0.22f))
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = pagerState.currentPage < 4,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OnboardingSkipPill(
                        onClick = {
                            Motion.performLightTapHaptic(view)
                            soundManager?.playButtonBlip()
                            onFinish()
                        }
                    )
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("onboarding_pager")
            ) { page ->
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                OnboardingSlideContent(
                    page = page,
                    pageOffset = pageOffset,
                    reducedMotion = reducedMotion
                )
            }

            // Bottom Bar: Morphing Indicators (Left) & 3D Yellow Action Button (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bottom Dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { idx ->
                        val isSelected = idx == pagerState.currentPage
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 30.dp else 10.dp,
                            animationSpec = Motion.physicalSpring(reducedMotion),
                            label = "onboarding_dot_width"
                        )
                        val activeColor = when (pagerState.currentPage) {
                            4 -> Color(0xFFF56042)
                            else -> SunnyYellow
                        }
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(if (isSelected) activeColor else InkBlack.copy(alpha = 0.35f))
                                .border(1.dp, InkBlack, CircleShape)
                        )
                    }
                }

                // Next / Get Started Action Button
                val isLastSlide = pagerState.currentPage == 4
                OnboardingActionButton(
                    label = if (isLastSlide) "GET STARTED" else "NEXT",
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        soundManager?.playButtonBlip()
                        if (isLastSlide) {
                            onFinish()
                        } else {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    }
                )
            }
        }
    }
}

// --- DYNAMIC SLIDE BACKGROUND RENDERER ---
@Composable
private fun SlideBackgroundRenderer(page: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (page) {
            0 -> {
                // Slide 1: Electric Royal Blue
                drawRect(Color(0xFF3858F8))
            }
            1 -> {
                // Slide 2: Two-tone organic split (Warm Cream top + Electric Blue bottom with yellow & blue organic shapes)
                drawRect(Color(0xFFFDFBF5))

                // Left pastel yellow arc
                val leftYellowArc = Path().apply {
                    moveTo(0f, h * 0.32f)
                    cubicTo(w * 0.22f, h * 0.34f, w * 0.24f, h * 0.46f, 0f, h * 0.54f)
                    close()
                }
                drawPath(leftYellowArc, Color(0xFFFFEBA6))

                // Top-right golden yellow sun
                drawCircle(
                    color = Color(0xFFFFE57F),
                    radius = w * 0.44f,
                    center = Offset(w * 0.95f, h * 0.36f)
                )

                // Middle organic cobalt blue wave and fill
                val blueWavePath = Path().apply {
                    moveTo(0f, h * 0.53f)
                    cubicTo(
                        w * 0.28f, h * 0.52f,
                        w * 0.38f, h * 0.33f,
                        w * 0.52f, h * 0.33f
                    )
                    cubicTo(
                        w * 0.66f, h * 0.33f,
                        w * 0.74f, h * 0.47f,
                        w, h * 0.47f
                    )
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(blueWavePath, Color(0xFF3858F8))
            }
            2 -> {
                // Slide 3: Sunset Ember Red to Midnight Indigo
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF573D),
                            Color(0xFFFF6947),
                            Color(0xFF2E1F52),
                            Color(0xFF1E143B)
                        )
                    )
                )
                // Yellow energetic scratch scribble on top right
                val path = Path().apply {
                    moveTo(w * 0.55f, h * 0.08f)
                    lineTo(w * 0.65f, h * 0.07f)
                    lineTo(w * 0.58f, h * 0.09f)
                    lineTo(w * 0.70f, h * 0.085f)
                }
                drawPath(path, SunnyYellow, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))

                // Purple wavy floor
                val purpleWave = Path().apply {
                    moveTo(0f, h * 0.82f)
                    cubicTo(w * 0.25f, h * 0.78f, w * 0.45f, h * 0.88f, w, h * 0.82f)
                }
                drawPath(purpleWave, Color(0xFF7C5CFC).copy(alpha = 0.5f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
            }
            3 -> {
                // Slide 4: Mint Seafoam watercolor splash with pastel watercolor clouds
                drawRect(Color(0xFFD3F8EE))
                // Violet watercolor blob (center)
                drawCircle(Color(0xFFC7B1FE).copy(alpha = 0.55f), w * 0.36f, Offset(w * 0.50f, h * 0.40f))
                // Coral watercolor blob (right)
                drawCircle(Color(0xFFFF9D80).copy(alpha = 0.45f), w * 0.32f, Offset(w * 0.95f, h * 0.60f))
                // Aqua splash (bottom left)
                drawCircle(Color(0xFF7CE4CE).copy(alpha = 0.50f), w * 0.40f, Offset(w * 0.05f, h * 0.75f))
            }
            else -> {
                // Slide 5: Warm Canvas with playful Emerald & Coral curves
                drawRect(Color(0xFFFAF5E9))
                // Emerald curve bottom left
                val emeraldPath = Path().apply {
                    moveTo(0f, h * 0.48f)
                    cubicTo(w * 0.15f, h * 0.52f, w * 0.20f, h * 0.80f, 0f, h * 0.85f)
                    close()
                }
                drawPath(emeraldPath, Color(0xFF0F9D75))

                // Coral curve bottom right
                val coralPath = Path().apply {
                    moveTo(w, h * 0.56f)
                    cubicTo(w * 0.85f, h * 0.65f, w * 0.65f, h * 0.82f, w * 0.72f, h)
                    lineTo(w, h)
                    close()
                }
                drawPath(coralPath, Color(0xFFF56042))

                // Yellow highlighter behind headline area
                drawRoundRect(
                    color = Color(0xFFFFE66D),
                    topLeft = Offset(w * 0.06f, h * 0.16f),
                    size = Size(w * 0.75f, 26.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                )
            }
        }
    }
}

// --- TOP SKIP PILL (3D WHITE BUTTON) ---
@Composable
private fun OnboardingSkipPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pillShape = RoundedCornerShape(20.dp)

    Editorial3DLayerBox(
        modifier = modifier
            .testTag("onboarding_skip_button")
            .offset(
                x = if (isPressed) 1.5.dp else 0.dp,
                y = if (isPressed) 2.5.dp else 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        shape = pillShape,
        depthX = 2.dp,
        depthY = if (isPressed) 1.dp else 3.5.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(82.dp)
                .height(40.dp),
            shape = pillShape,
            color = PureWhite,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SKIP",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = InkBlack
                )
            }
        }
    }
}

// --- BOTTOM ACTION BUTTON (3D YELLOW PILL WITH ARROW) ---
@Composable
private fun OnboardingActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pillShape = RoundedCornerShape(26.dp)

    Editorial3DLayerBox(
        modifier = modifier
            .testTag("onboarding_action_button")
            .offset(
                x = if (isPressed) 2.dp else 0.dp,
                y = if (isPressed) 3.dp else 0.dp
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        shape = pillShape,
        depthX = 2.5.dp,
        depthY = if (isPressed) 1.5.dp else 4.5.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .height(52.dp)
                .padding(horizontal = 2.dp),
            shape = pillShape,
            color = SunnyYellow,
            border = BorderStroke(2.5.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    ),
                    color = InkBlack
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = InkBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- AMBIENT PARTICLES CANVAS ---
@Composable
private fun AmbientFloatingParticles(page: Int, reducedMotion: Boolean) {
    if (reducedMotion) return

    val infiniteTransition = rememberInfiniteTransition(label = "ambient_particles")
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ambient_drift"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val particlePositions = listOf(
            Offset(w * 0.10f, h * 0.16f) to 7.dp.toPx(),
            Offset(w * 0.90f, h * 0.20f) to 8.dp.toPx(),
            Offset(w * 0.06f, h * 0.70f) to 9.dp.toPx(),
            Offset(w * 0.92f, h * 0.65f) to 8.dp.toPx(),
            Offset(w * 0.50f, h * 0.08f) to 6.dp.toPx(),
            Offset(w * 0.18f, h * 0.88f) to 7.dp.toPx(),
            Offset(w * 0.84f, h * 0.86f) to 8.dp.toPx()
        )

        val starColor = when (page) {
            1, 3, 4 -> InkBlack.copy(alpha = 0.18f)
            else -> SunnyYellow.copy(alpha = 0.55f)
        }

        particlePositions.forEachIndexed { i, (pos, rad) ->
            val offsetMultiplier = (i + 1) * 0.85f
            val floatY = sin(driftPhase + offsetMultiplier) * 12.dp.toPx()
            val floatX = sin(driftPhase * 0.7f + offsetMultiplier) * 8.dp.toPx()
            val center = Offset(pos.x + floatX, pos.y + floatY)

            if (i % 2 == 0) {
                drawStar(center = center, radius = rad * 1.4f, color = starColor)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path = path, color = color)
}

// --- COMIC ACCENTS: ACTION TICKS & SPARKLE RAYS ---
@Composable
private fun ComicActionTicks(
    angleDegrees: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { rotationZ = angleDegrees }) {
        val strokeW = 3.2.dp.toPx()
        val color = InkBlack

        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.85f),
            end = Offset(size.width * 0.15f, size.height * 0.25f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.85f),
            end = Offset(size.width * 0.5f, size.height * 0.10f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.85f),
            end = Offset(size.width * 0.85f, size.height * 0.25f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ComicYellowRays(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 3.8.dp.toPx()
        val color = SunnyYellow

        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.90f),
            end = Offset(size.width * 0.05f, size.height * 0.20f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.85f),
            end = Offset(size.width * 0.50f, size.height * 0.05f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.90f),
            end = Offset(size.width * 0.95f, size.height * 0.20f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MascotRadiatingTicks(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeW = 3.6.dp.toPx()
        val color = InkBlack
        val len = 12.dp.toPx()

        // Left tick tilted outward
        drawLine(
            color = color,
            start = Offset(size.width * 0.26f, size.height * 0.95f),
            end = Offset(size.width * 0.10f, size.height * 0.95f - len),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // Center tick straight up
        drawLine(
            color = color,
            start = Offset(size.width * 0.50f, size.height * 0.90f),
            end = Offset(size.width * 0.50f, size.height * 0.90f - len * 1.15f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        // Right tick tilted outward
        drawLine(
            color = color,
            start = Offset(size.width * 0.74f, size.height * 0.95f),
            end = Offset(size.width * 0.90f, size.height * 0.95f - len),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}

// --- INTERACTIVE 3D TILT CONTAINER ---
@Composable
private fun Interactive3DTiltCard(
    modifier: Modifier = Modifier,
    reducedMotion: Boolean,
    content: @Composable () -> Unit
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }

    val animatedRotX by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else (-dragY * 0.16f).coerceIn(-12f, 12f),
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "tilt_rot_x"
    )
    val animatedRotY by animateFloatAsState(
        targetValue = if (reducedMotion) 0f else (dragX * 0.16f).coerceIn(-14f, 14f),
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "tilt_rot_y"
    )

    Box(
        modifier = modifier
            .pointerInput(reducedMotion) {
                if (!reducedMotion) {
                    detectDragGestures(
                        onDragEnd = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragCancel = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragX += dragAmount.x
                            dragY += dragAmount.y
                        }
                    )
                }
            }
            .graphicsLayer {
                rotationX = animatedRotX
                rotationY = animatedRotY
                cameraDistance = 16f * density
            }
    ) {
        content()
    }
}

// --- SLIDE CONTENT CONTAINER ---
@Composable
private fun OnboardingSlideContent(
    page: Int,
    pageOffset: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val parallaxTranslationX = if (reducedMotion) 0f else (pageOffset * 65f)
    val cardRotationZ = if (reducedMotion) 0f else (pageOffset * -3.5f).coerceIn(-6f, 6f)
    val scale = if (reducedMotion) 1f else (1f - (pageOffset.absoluteValue * 0.08f)).coerceIn(0.90f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Slide Specific Header
        when (page) {
            0 -> Slide1Header()
            1 -> Slide2Header()
            2 -> Slide3Header()
            3 -> Slide4Header()
            else -> Slide5Header()
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Center Chunky 3D Illustration Scene with Interactive Tilt
        Interactive3DTiltCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .graphicsLayer {
                    translationX = parallaxTranslationX
                    this.rotationZ = cardRotationZ
                    scaleX = scale
                    scaleY = scale
                },
            reducedMotion = reducedMotion
        ) {
            when (page) {
                0 -> Slide1SnapScene(reducedMotion = reducedMotion)
                1 -> Slide2AddScene(reducedMotion = reducedMotion)
                2 -> Slide3ControlScene(reducedMotion = reducedMotion)
                3 -> Slide4InsightsScene(reducedMotion = reducedMotion)
                else -> Slide5PrivacyScene(reducedMotion = reducedMotion)
            }
        }
    }
}

// ============================================================================
// HEADERS FOR EACH SLIDE (MATCHING REFERENCE IMAGES)
// ============================================================================

@Composable
private fun Slide1Header() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    text = "SNAP IT.",
                    style = CondensedCardTitleStyle.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black),
                    color = PureWhite
                )
                Text(
                    text = "SORT IT.",
                    style = CondensedCardTitleStyle.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black),
                    color = PureWhite
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            ComicYellowRays(modifier = Modifier.size(38.dp, 32.dp).offset(y = (-8).dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "SnapSpend reads your payment screenshots and sorts every rupee into the right category.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
            color = PureWhite.copy(alpha = 0.95f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Canvas(modifier = Modifier.width(110.dp).height(10.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.7f)
                quadraticTo(size.width * 0.5f, 0f, size.width, size.height * 0.5f)
            }
            drawPath(path, SunnyYellow, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun Slide2Header() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ADD IN,",
                    style = CondensedCardTitleStyle.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black),
                    color = InkBlack
                )
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Box {
                        // Yellow highlighter brush directly under SECONDS.
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .offset(x = (-2).dp, y = 2.dp)
                                .width(172.dp)
                                .height(13.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFFFE66D).copy(alpha = 0.92f))
                        )
                        Text(
                            text = "SECONDS.",
                            style = CondensedCardTitleStyle.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black),
                            color = Color(0xFF3858F8)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    ComicYellowRays(modifier = Modifier.size(28.dp, 26.dp).offset(y = (-6).dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    // 4-point sparkle star
                    Canvas(modifier = Modifier.size(14.dp).offset(y = (-14).dp)) {
                        drawStar(center = Offset(size.width / 2f, size.height / 2f), radius = 6.dp.toPx(), color = InkBlack)
                    }
                }
            }

            // Whimsical handwritten-style note on right with ample space (prevents vertical wrapping)
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .width(112.dp)
                    .graphicsLayer { rotationZ = -8f }
                    .offset(x = 4.dp, y = 6.dp)
            ) {
                Text(
                    text = "Receipts\nScreenshots\nPDFs\nVoice Notes",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp
                    ),
                    color = Color(0xFF8F9BBF),
                    textAlign = TextAlign.End,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Doodle loop-de-loop swirl arrow pointing down
                Canvas(modifier = Modifier.size(28.dp, 32.dp).offset(x = (-10).dp)) {
                    val path = Path().apply {
                        moveTo(size.width * 0.70f, 0f)
                        cubicTo(
                            size.width * 0.30f, size.height * 0.20f,
                            size.width * 0.10f, size.height * 0.60f,
                            size.width * 0.50f, size.height * 0.70f
                        )
                        cubicTo(
                            size.width * 0.90f, size.height * 0.80f,
                            size.width * 0.60f, size.height * 0.95f,
                            size.width * 0.20f, size.height
                        )
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF8F9BBF),
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap + Add. Scan with the camera, pick screenshots or a PDF, say a quick note, or type it in yourself.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
            color = Color(0xFF2E2E3E)
        )
    }
}

@Composable
private fun Slide3Header() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text(
                    text = "YOU STAY IN",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 42.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = PureWhite
                )
                Text(
                    text = "CONTROL.",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 42.sp,
                        lineHeight = 44.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = SunnyYellow
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            ComicYellowRays(modifier = Modifier.size(36.dp, 30.dp).offset(y = (-6).dp))
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "The AI reads it, you check it, then confirm. Fix a category once and SnapSpend remembers it forever.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
            color = PureWhite.copy(alpha = 0.95f)
        )
    }
}

@Composable
private fun Slide4Header() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SEE WHERE,",
                    style = CondensedCardTitleStyle.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black),
                    color = Color(0xFF111936)
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "IT GOES.",
                        style = CondensedCardTitleStyle.copy(fontSize = 42.sp, lineHeight = 44.sp, fontWeight = FontWeight.Black),
                        color = Color(0xFFFF5B3B)
                    )
                    ComicYellowRays(modifier = Modifier.size(34.dp, 28.dp).offset(y = (-6).dp))
                }
            }

            // Tilted Spend Smarter Receipt Doodle (Right)
            Surface(
                modifier = Modifier
                    .width(76.dp)
                    .graphicsLayer { rotationZ = 12f }
                    .offset(x = (-4).dp, y = (-4).dp),
                shape = RoundedCornerShape(8.dp),
                color = PureWhite,
                border = BorderStroke(1.5.dp, InkBlack),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SPEND\nSMARTER",
                        style = CondensedCardTitleStyle.copy(fontSize = 8.sp, fontWeight = FontWeight.Black, lineHeight = 10.sp),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Box(modifier = Modifier.width(6.dp).height(18.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF6EC68E)))
                        Box(modifier = Modifier.width(6.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFE8629B)))
                        Box(modifier = Modifier.width(6.dp).height(14.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFF38A3E)))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Charts, month-by-month comparisons, budgets, and repeating payments at a single glance.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
            color = Color(0xFF1E2540)
        )
    }
}

@Composable
private fun Slide5Header() {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        // Yellow doodle scribble above title
        Canvas(modifier = Modifier.width(60.dp).height(12.dp).offset(x = 10.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.8f)
                cubicTo(size.width * 0.25f, 0f, size.width * 0.50f, size.height, size.width * 0.75f, 0f)
                cubicTo(size.width * 0.85f, size.height * 0.5f, size.width * 0.95f, size.height * 0.8f, size.width, size.height * 0.4f)
            }
            drawPath(path, SunnyYellow, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "YOUR MONEY,",
                    style = CondensedCardTitleStyle.copy(fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black),
                    color = Color(0xFF111936)
                )
                Text(
                    text = "YOUR PHONE.",
                    style = CondensedCardTitleStyle.copy(fontSize = 40.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black),
                    color = Color(0xFFF56042)
                )
            }

            // Lock doodle & "YOUR DATA YOUR RULES"
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .width(96.dp)
                    .offset(x = 2.dp, y = 2.dp)
            ) {
                Box {
                    // Lock rays
                    ComicActionTicks(angleDegrees = 0f, modifier = Modifier.size(24.dp).align(Alignment.TopCenter).offset(y = (-14).dp))
                    Surface(
                        modifier = Modifier.size(34.dp).align(Alignment.Center),
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF0F9D75),
                        border = BorderStroke(2.dp, InkBlack)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "YOUR DATA\nYOUR RULES",
                    style = CondensedCardTitleStyle.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Black, lineHeight = 11.5.sp),
                    color = Color(0xFF3B4060),
                    textAlign = TextAlign.End,
                    softWrap = false
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Your finances live strictly on your phone. Private on-device database with zero ads and encrypted backups.",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.5.sp, lineHeight = 22.sp),
            color = Color(0xFF22283A)
        )
    }
}

// ============================================================================
// SLIDE 1 3D SCENE: SWIGGY INSTAMART + GPAY + ₹500 BILL + COINS
// ============================================================================
@Composable
private fun Slide1SnapScene(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide1_anims")
    val bob1 by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob1"
    )
    val bob2 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Chunky White Card
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = -2f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide1_mock_card"),
                shape = RoundedCornerShape(26.dp),
                color = PureWhite,
                border = BorderStroke(2.5.dp, InkBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp, start = 14.dp, end = 14.dp, bottom = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFF1EEFC),
                        border = BorderStroke(1.5.dp, Color(0xFFE2DCF7))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFFF5200)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("S", color = PureWhite, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("SWIGGY INSTAMART", style = CondensedCardTitleStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Black), color = InkBlack)
                                        Text("UPI Screenshot • 10:42 AM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = InkBlack.copy(alpha = 0.65f))
                                    }
                                }
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    val path = Path().apply {
                                        moveTo(0f, 0f)
                                        lineTo(size.width, size.height * 0.5f)
                                        lineTo(0f, size.height)
                                        close()
                                    }
                                    drawPath(path, Color(0xFF0F8A5F))
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE2DCF7)))
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Amount", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = InkBlack.copy(alpha = 0.6f))
                                    Text("₹420", style = CondensedCardTitleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Black), color = InkBlack)
                                }
                                Box(modifier = Modifier.width(1.dp).height(32.dp).background(Color(0xFFE2DCF7)))
                                Column {
                                    Text("Category", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = InkBlack.copy(alpha = 0.6f))
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFDDD5FB),
                                        border = BorderStroke(1.dp, Color(0xFFCABEF7))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color(0xFF5B3DE8), modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text("Groceries", style = CondensedCardTitleStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Black), color = Color(0xFF5B3DE8))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mascot sitting atop the main card
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        ) {
            ComicYellowRays(modifier = Modifier.size(46.dp, 28.dp).align(Alignment.TopCenter).offset(y = (-24).dp))
            Editorial3DLayerBox(
                modifier = Modifier.size(86.dp),
                shape = RoundedCornerShape(22.dp),
                depthX = 3.dp,
                depthY = 4.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    color = SunnyYellow,
                    border = BorderStroke(2.5.dp, InkBlack)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(11.dp)) {
                        val w = size.width
                        val h = size.height
                        val bracketLen = 14.dp.toPx()
                        val strokeW = 4.dp.toPx()

                        // Viewfinder brackets
                        drawLine(InkBlack, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(0f, 0f), Offset(0f, bracketLen), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(w, 0f), Offset(w, bracketLen), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(0f, h), Offset(bracketLen, h), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(0f, h), Offset(0f, h - bracketLen), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(w, h), Offset(w - bracketLen, h), strokeW, StrokeCap.Round)
                        drawLine(InkBlack, Offset(w, h), Offset(w, h - bracketLen), strokeW, StrokeCap.Round)

                        // Eyes + smile
                        val eyeR = 4.5.dp.toPx()
                        drawCircle(InkBlack, eyeR, Offset(w * 0.35f, h * 0.44f))
                        drawCircle(InkBlack, eyeR, Offset(w * 0.65f, h * 0.44f))
                        val smile = Path().apply {
                            moveTo(w * 0.36f, h * 0.60f)
                            quadraticTo(w * 0.50f, h * 0.76f, w * 0.64f, h * 0.60f)
                        }
                        drawPath(smile, InkBlack, style = Stroke(3.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }

        // Top Left: GPay Badge
        Box(modifier = Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 20.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else -bob1; rotationZ = -6f },
                shape = RoundedCornerShape(20.dp),
                depthX = 2.5.dp,
                depthY = 3.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(102.dp).height(42.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = PureWhite,
                    border = BorderStroke(2.dp, InkBlack)
                ) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val strokeW = 3.5.dp.toPx()
                            drawArc(Color(0xFF4285F4), 180f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                            drawArc(Color(0xFFEA4335), 270f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                            drawArc(Color(0xFFFBBC05), 0f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                            drawArc(Color(0xFF34A853), 90f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("GPay", style = CondensedCardTitleStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 135f, modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(x = (-16).dp, y = (-12).dp))
        }

        // Top Right: ₹500 Green Bill
        Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 24.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { rotationZ = 10f; translationY = if (reducedMotion) 0f else bob2 },
                shape = RoundedCornerShape(12.dp),
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(105.dp).height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6EC68E),
                    border = BorderStroke(2.dp, InkBlack)
                ) {
                    Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFF4FA870)))
                        Text("₹500", style = CondensedCardTitleStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 45f, modifier = Modifier.size(24.dp).align(Alignment.TopEnd).offset(x = 16.dp, y = (-12).dp))
        }

        // Bottom Left: Purple Coin
        Box(modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-8).dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else bob1; rotationZ = -8f },
                shape = CircleShape,
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = Color(0xFF7C5CFC), border = BorderStroke(2.2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Black), color = PureWhite)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 225f, modifier = Modifier.size(24.dp).align(Alignment.BottomStart).offset(x = (-16).dp, y = 14.dp))
        }

        // Bottom Right: Gold Coin
        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-10).dp, y = (-4).dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else -bob2; rotationZ = 12f },
                shape = CircleShape,
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = SunnyYellow, border = BorderStroke(2.2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 315f, modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).offset(x = 16.dp, y = 14.dp))
        }
    }
}

// ============================================================================
// SLIDE 2 3D SCENE: PHONEPE + 4 OPTION STRIPS + ₹200 BILL + STACKED COINS
// ============================================================================
@Composable
private fun Slide2AddScene(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide2_anims")
    val bob by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_slide2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Chunky White Card
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = -1.5f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().testTag("slide2_mock_card"),
                shape = RoundedCornerShape(26.dp),
                color = PureWhite,
                border = BorderStroke(2.5.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 36.dp, start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Slide2ActionStrip(
                        title = "CAMERA SCAN",
                        subtitle = "Scan receipts or bills",
                        icon = Icons.Default.PhotoCamera,
                        circleColor = Color(0xFF6B8EFF),
                        stripBg = Color(0xFFDDE5FE)
                    )
                    Slide2ActionStrip(
                        title = "PICK SCREENSHOTS",
                        subtitle = "Choose from your gallery",
                        icon = Icons.Default.ReceiptLong,
                        circleColor = Color(0xFFFFD43F),
                        stripBg = Color(0xFFFEEDAC)
                    )
                    Slide2ActionStrip(
                        title = "VOICE NOTE",
                        subtitle = "Just say it",
                        icon = Icons.Default.Mic,
                        circleColor = Color(0xFFFF8FA3),
                        stripBg = Color(0xFFFFD5DC)
                    )
                    Slide2ActionStrip(
                        title = "BANK PDF STATEMENT",
                        subtitle = "Import and auto-categorize",
                        icon = Icons.Default.UploadFile,
                        circleColor = Color(0xFF5CDCA5),
                        stripBg = Color(0xFFC6F4DC)
                    )
                }
            }
        }

        // Mascot with 3 radiating antenna ticks
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        ) {
            // 3 radiating black antenna tick marks on top
            MascotRadiatingTicks(
                modifier = Modifier.size(46.dp, 16.dp).align(Alignment.TopCenter).offset(y = (-18).dp)
            )
            Editorial3DLayerBox(
                modifier = Modifier.size(84.dp),
                shape = RoundedCornerShape(22.dp),
                depthX = 3.dp,
                depthY = 4.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    color = SunnyYellow,
                    border = BorderStroke(2.5.dp, InkBlack)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val w = size.width
                        val h = size.height
                        val eyeR = 4.5.dp.toPx()
                        drawCircle(InkBlack, eyeR, Offset(w * 0.35f, h * 0.44f))
                        drawCircle(InkBlack, eyeR, Offset(w * 0.65f, h * 0.44f))
                        val smile = Path().apply {
                            moveTo(w * 0.36f, h * 0.60f)
                            quadraticTo(w * 0.50f, h * 0.76f, w * 0.64f, h * 0.60f)
                        }
                        drawPath(smile, InkBlack, style = Stroke(3.6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }

        // Top Left: PhonePe Badge (Green border pill)
        Box(modifier = Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 20.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { rotationZ = -6f; translationY = if (reducedMotion) 0f else bob },
                shape = RoundedCornerShape(18.dp),
                depthX = 2.5.dp,
                depthY = 3.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(115.dp).height(44.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF00C06A),
                    border = BorderStroke(2.2.dp, InkBlack)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = Color(0xFF007541)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("पे", color = PureWhite, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PhonePe", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 135f, modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(x = (-16).dp, y = (-12).dp))
        }

        // Top Right: Coral ₹200 Bill
        Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 24.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { rotationZ = 8f; translationY = if (reducedMotion) 0f else -bob },
                shape = RoundedCornerShape(12.dp),
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(110.dp).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF97B6B),
                    border = BorderStroke(2.dp, InkBlack)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFD65848)))
                        Text("₹200", style = CondensedCardTitleStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 45f, modifier = Modifier.size(24.dp).align(Alignment.TopEnd).offset(x = 16.dp, y = (-12).dp))
        }

        // Bottom Left: Blue Coin
        Box(modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-8).dp)) {
            // 4-point sparkle star to the left
            Canvas(modifier = Modifier.size(16.dp).align(Alignment.CenterStart).offset(x = (-18).dp, y = (-12).dp)) {
                drawStar(center = Offset(size.width / 2f, size.height / 2f), radius = 7.dp.toPx(), color = InkBlack)
            }

            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else bob; rotationZ = -6f },
                shape = CircleShape,
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = Color(0xFF3858F8), border = BorderStroke(2.2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Black), color = PureWhite)
                    }
                }
            }

            // Action ticks and curved doodle arrow pointing up
            ComicActionTicks(angleDegrees = 225f, modifier = Modifier.size(24.dp).align(Alignment.BottomStart).offset(x = (-16).dp, y = 14.dp))
            Canvas(modifier = Modifier.size(28.dp, 24.dp).align(Alignment.BottomStart).offset(x = (-6).dp, y = 30.dp)) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.8f)
                    cubicTo(size.width * 0.4f, size.height, size.width * 0.8f, size.height * 0.6f, size.width, 0f)
                }
                drawPath(path, InkBlack, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
                // Arrowhead
                val arrowHead = Path().apply {
                    moveTo(size.width - 6.dp.toPx(), 4.dp.toPx())
                    lineTo(size.width, 0f)
                    lineTo(size.width - 2.dp.toPx(), 8.dp.toPx())
                }
                drawPath(arrowHead, InkBlack, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
            }
        }

        // Bottom Right: Double Stacked Gold Coins
        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-4).dp)) {
            // Lower coin in stack
            Editorial3DLayerBox(
                modifier = Modifier.size(46.dp).offset(y = 8.dp),
                shape = CircleShape,
                depthX = 2.dp,
                depthY = 3.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color(0xFFE5B520), border = BorderStroke(2.dp, InkBlack)) {}
            }
            // Upper coin in stack
            Editorial3DLayerBox(
                modifier = Modifier.size(48.dp).offset(x = 10.dp),
                shape = CircleShape,
                depthX = 2.dp,
                depthY = 3.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = SunnyYellow, border = BorderStroke(2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 315f, modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).offset(x = 24.dp, y = 14.dp))
        }
    }
}

@Composable
private fun Slide2ActionStrip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    circleColor: Color,
    stripBg: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = stripBg
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(circleColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, style = CondensedCardTitleStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp), color = InkBlack.copy(alpha = 0.68f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TAP", style = CondensedCardTitleStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Black), color = InkBlack)
                Spacer(modifier = Modifier.width(3.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = InkBlack, modifier = Modifier.size(12.dp))
            }
        }
    }
}

// ============================================================================
// SLIDE 3 3D SCENE: ZOMATO + CONFIRM & SAVE + MINT WINKING MASCOT + PAYTM
// ============================================================================
@Composable
private fun Slide3ControlScene(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide3_anims")
    val bob by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_slide3"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Chunky Zig-Zag Torn Receipt
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = -1.5f },
            shape = RoundedCornerShape(22.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().testTag("slide3_mock_card"),
                shape = RoundedCornerShape(22.dp),
                color = PureWhite,
                border = BorderStroke(2.5.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 34.dp, start = 14.dp, end = 14.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Top: Zomato Dining Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFE23744)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Restaurant, contentDescription = null, tint = PureWhite, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("ZOMATO DINING", style = CondensedCardTitleStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Black), color = InkBlack)
                                Text("Food & Dining", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = InkBlack.copy(alpha = 0.65f))
                            }
                        }
                        Text("₹680", style = CondensedCardTitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }

                    // Middle: Duplicate skipped strip
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFECE7FC)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Layers, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Duplicate reference skipped\nautomatically", style = CondensedCardTitleStyle.copy(fontSize = 10.5.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black), color = InkBlack)
                            }
                            Icon(Icons.Default.Info, contentDescription = null, tint = InkBlack.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                        }
                    }

                    // Bottom: Tactile Mint Green CONFIRM & SAVE button
                    Editorial3DLayerBox(
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(14.dp),
                        depthX = 2.dp,
                        depthY = 3.dp,
                        extrusionColor = InkBlack
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF68E5B0),
                            border = BorderStroke(2.dp, InkBlack)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(20.dp).clip(CircleShape).background(InkBlack),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF68E5B0), modifier = Modifier.size(13.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CONFIRM & SAVE", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black), color = InkBlack)
                            }
                        }
                    }
                }
            }
        }

        // Mascot: Mint Green Character Winking & Holding Card with Paws
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-16).dp)
        ) {
            // Violet Rays on top
            Canvas(modifier = Modifier.size(44.dp, 24.dp).align(Alignment.TopCenter).offset(y = (-20).dp)) {
                val strokeW = 3.5.dp.toPx()
                drawLine(Color(0xFF9B78FF), Offset(size.width * 0.2f, size.height), Offset(size.width * 0.05f, 0f), strokeW, StrokeCap.Round)
                drawLine(Color(0xFF9B78FF), Offset(size.width * 0.5f, size.height), Offset(size.width * 0.5f, 0f), strokeW, StrokeCap.Round)
                drawLine(Color(0xFF9B78FF), Offset(size.width * 0.8f, size.height), Offset(size.width * 0.95f, 0f), strokeW, StrokeCap.Round)
            }
            Editorial3DLayerBox(
                modifier = Modifier.size(86.dp),
                shape = RoundedCornerShape(22.dp),
                depthX = 3.dp,
                depthY = 4.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF68E5B0),
                    border = BorderStroke(2.5.dp, InkBlack)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val w = size.width
                        val h = size.height

                        // Left eye: Black dot
                        drawCircle(InkBlack, 5.dp.toPx(), Offset(w * 0.35f, h * 0.44f))

                        // Right eye: Wink line
                        val wink = Path().apply {
                            moveTo(w * 0.60f, h * 0.42f)
                            lineTo(w * 0.72f, h * 0.46f)
                        }
                        drawPath(wink, InkBlack, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))

                        // Smile
                        val smile = Path().apply {
                            moveTo(w * 0.36f, h * 0.60f)
                            quadraticTo(w * 0.50f, h * 0.74f, w * 0.64f, h * 0.60f)
                        }
                        drawPath(smile, InkBlack, style = Stroke(3.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }
        }

        // Speech Bubble: "You Check Confirm Done!" on top-right
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-8).dp),
            shape = RoundedCornerShape(18.dp),
            color = SunnyYellow,
            border = BorderStroke(2.dp, InkBlack),
            shadowElevation = 4.dp
        ) {
            Text(
                text = "You\nCheck\nConfirm\nDone!",
                style = CondensedCardTitleStyle.copy(fontSize = 11.5.sp, fontWeight = FontWeight.Black, lineHeight = 13.5.sp),
                color = InkBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // Top Left: Paytm Pill Badge
        Box(modifier = Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 20.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { rotationZ = -6f; translationY = if (reducedMotion) 0f else bob },
                shape = RoundedCornerShape(18.dp),
                depthX = 2.5.dp,
                depthY = 3.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(105.dp).height(42.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = PureWhite,
                    border = BorderStroke(2.dp, InkBlack)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(modifier = Modifier.size(18.dp), shape = RoundedCornerShape(4.dp), color = Color(0xFF00BAF2)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(12.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paytm", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black), color = Color(0xFF002E6E))
                    }
                }
            }
            ComicActionTicks(angleDegrees = 135f, modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(x = (-16).dp, y = (-12).dp))
        }

        // Bottom Right: Stacked Gold Coins
        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-8).dp, y = (-4).dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.size(46.dp).offset(y = 8.dp),
                shape = CircleShape,
                depthX = 2.dp,
                depthY = 3.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = Color(0xFFE5B520), border = BorderStroke(2.dp, InkBlack)) {}
            }
            Editorial3DLayerBox(
                modifier = Modifier.size(48.dp).offset(x = 10.dp),
                shape = CircleShape,
                depthX = 2.dp,
                depthY = 3.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.fillMaxSize(), shape = CircleShape, color = SunnyYellow, border = BorderStroke(2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 315f, modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).offset(x = 24.dp, y = 14.dp))
        }
    }
}

// ============================================================================
// SLIDE 4 3D SCENE: CRED + ₹2000 BILL + MONTHLY BREAKDOWN BARS
// ============================================================================
@Composable
private fun Slide4InsightsScene(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide4_anims")
    val bob by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_slide4"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(350.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Chunky Card
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = -1.5f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().testTag("slide4_mock_card"),
                shape = RoundedCornerShape(26.dp),
                color = PureWhite,
                border = BorderStroke(2.5.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 34.dp, start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Yellow Callout Header: THIS MONTH SPENT ₹18,450
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFFEEDAC)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("THIS MONTH SPENT", style = CondensedCardTitleStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Black), color = InkBlack)
                            Text("₹18,450", style = CondensedCardTitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Black), color = InkBlack)
                        }
                    }

                    // Category 1: Food & Dining
                    Slide4CategoryRow(
                        name = "Food & Dining",
                        percent = "39% of spending",
                        amount = "₹7,200",
                        barFraction = 0.55f,
                        icon = Icons.Default.Restaurant,
                        iconColor = Color(0xFFFF5B3B),
                        iconBg = Color(0xFFFFD5CE),
                        barColor = Color(0xFFFF5B3B)
                    )

                    // Category 2: Groceries
                    Slide4CategoryRow(
                        name = "Groceries",
                        percent = "29% of spending",
                        amount = "₹5,400",
                        barFraction = 0.40f,
                        icon = Icons.Default.ShoppingBag,
                        iconColor = Color(0xFF0F9D75),
                        iconBg = Color(0xFFD1F5E8),
                        barColor = Color(0xFF7C5CFC)
                    )

                    // Category 3: Shopping
                    Slide4CategoryRow(
                        name = "Shopping",
                        percent = "32% of spending",
                        amount = "₹5,850",
                        barFraction = 0.45f,
                        icon = Icons.Default.ShoppingBag,
                        iconColor = Color(0xFF3858F8),
                        iconBg = Color(0xFFDDE5FE),
                        barColor = Color(0xFF3858F8)
                    )
                }
            }
        }

        // Mascot: Yellow Peeking Character with Paws
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        ) {
            ComicActionTicks(angleDegrees = 0f, modifier = Modifier.size(32.dp, 20.dp).align(Alignment.TopCenter).offset(y = (-16).dp))
            Editorial3DLayerBox(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                depthX = 3.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    color = SunnyYellow,
                    border = BorderStroke(2.5.dp, InkBlack)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val w = size.width
                        val h = size.height
                        val eyeR = 4.dp.toPx()
                        drawCircle(InkBlack, eyeR, Offset(w * 0.35f, h * 0.44f))
                        drawCircle(InkBlack, eyeR, Offset(w * 0.65f, h * 0.44f))
                        val smile = Path().apply {
                            moveTo(w * 0.40f, h * 0.62f)
                            quadraticTo(w * 0.50f, h * 0.82f, w * 0.60f, h * 0.62f)
                            close()
                        }
                        drawPath(smile, InkBlack)
                    }
                }
            }
        }

        // Top Left: CRED Badge
        Box(modifier = Modifier.align(Alignment.TopStart).offset(x = 6.dp, y = 20.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { rotationZ = -6f; translationY = if (reducedMotion) 0f else bob },
                shape = RoundedCornerShape(18.dp),
                depthX = 2.5.dp,
                depthY = 3.5.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(108.dp).height(42.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF14141E),
                    border = BorderStroke(2.dp, Color(0xFFD4AF37))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFD4AF37), RoundedCornerShape(2.dp)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CRED", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp), color = PureWhite)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 135f, modifier = Modifier.size(24.dp).align(Alignment.TopStart).offset(x = (-16).dp, y = (-12).dp))
        }

        // Top Right: ₹2000 Pink Bill
        Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 24.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { rotationZ = 8f; translationY = if (reducedMotion) 0f else -bob },
                shape = RoundedCornerShape(12.dp),
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.width(110.dp).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFF97B6B),
                    border = BorderStroke(2.dp, InkBlack)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color(0xFFD65848)))
                        Text("₹2000", style = CondensedCardTitleStyle.copy(fontSize = 18.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 45f, modifier = Modifier.size(24.dp).align(Alignment.TopEnd).offset(x = 16.dp, y = (-12).dp))
        }

        // Bottom Left: Gold Coin
        Box(modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = (-8).dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else bob; rotationZ = -6f },
                shape = CircleShape,
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = SunnyYellow, border = BorderStroke(2.2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 225f, modifier = Modifier.size(24.dp).align(Alignment.BottomStart).offset(x = (-16).dp, y = 14.dp))
        }

        // Bottom Right: Purple Coin
        Box(modifier = Modifier.align(Alignment.BottomEnd).offset(x = (-10).dp, y = (-4).dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else -bob; rotationZ = 10f },
                shape = CircleShape,
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.size(50.dp), shape = CircleShape, color = Color(0xFF8E68F0), border = BorderStroke(2.2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Black), color = PureWhite)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 315f, modifier = Modifier.size(24.dp).align(Alignment.BottomEnd).offset(x = 16.dp, y = 14.dp))
        }
    }
}

@Composable
private fun Slide4CategoryRow(
    name: String,
    percent: String,
    amount: String,
    barFraction: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    barColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(iconBg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = CondensedCardTitleStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Black), color = InkBlack)
                Text(percent, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = InkBlack.copy(alpha = 0.65f))
                Spacer(modifier = Modifier.height(3.dp))
                Box(modifier = Modifier.fillMaxWidth(0.9f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFE8E8EE))) {
                    Box(modifier = Modifier.fillMaxWidth(barFraction).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor))
                }
            }
        }
        Text(amount, style = CondensedCardTitleStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Black), color = InkBlack)
    }
}

// ============================================================================
// SLIDE 5 3D SCENE: PHONE HUGGING MASCOT + STICKY NOTE + 3 SECURITY ROWS
// ============================================================================
@Composable
private fun Slide5PrivacyScene(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide5_anims")
    val bob by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_slide5"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Chunky Card
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(260.dp)
                .offset(y = 20.dp),
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.fillMaxSize().testTag("slide5_mock_card"),
                shape = RoundedCornerShape(26.dp),
                color = PureWhite,
                border = BorderStroke(2.5.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Slide5SecurityStrip(
                        title = "100% ON-DEVICE DATABASE",
                        subtitle = "Stored locally on your phone.",
                        icon = Icons.Default.PhoneAndroid,
                        boxColor = Color(0xFFD1F5E8)
                    )
                    Slide5SecurityStrip(
                        title = "ENCRYPTED DRIVE BACKUP",
                        subtitle = "Your own private Google Drive.",
                        icon = Icons.Default.CloudDone,
                        boxColor = Color(0xFFFDE4DC)
                    )
                    Slide5SecurityStrip(
                        title = "ZERO THIRD-PARTY ADS",
                        subtitle = "No trackers or telemetry.",
                        icon = Icons.Default.Shield,
                        boxColor = Color(0xFFEAE5FC)
                    )
                }
            }
        }

        // Pinned Sticky Note on Top Left: "FINANCE STAYS ON YOUR PHONE"
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = (-8).dp)
                .graphicsLayer { rotationZ = -10f },
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFFFE66D),
            border = BorderStroke(1.8.dp, InkBlack),
            shadowElevation = 4.dp
        ) {
            Text(
                text = "FINANCE\nSTAYS\nON YOUR\nPHONE",
                style = CondensedCardTitleStyle.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Black, lineHeight = 12.sp),
                color = InkBlack,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }

        // Center Mascot Hugging Phone with Shield Checkmark
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(x = 30.dp, y = (-26).dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Yellow Character
            Editorial3DLayerBox(
                modifier = Modifier.size(76.dp),
                shape = RoundedCornerShape(20.dp),
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(20.dp),
                    color = SunnyYellow,
                    border = BorderStroke(2.2.dp, InkBlack)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                        val w = size.width
                        val h = size.height
                        // Blushing cheeks
                        drawCircle(Color(0xFFFF9D80), 4.dp.toPx(), Offset(w * 0.22f, h * 0.46f))
                        drawCircle(Color(0xFFFF9D80), 4.dp.toPx(), Offset(w * 0.78f, h * 0.46f))
                        // Eyes
                        drawCircle(InkBlack, 4.dp.toPx(), Offset(w * 0.35f, h * 0.40f))
                        drawCircle(InkBlack, 4.dp.toPx(), Offset(w * 0.65f, h * 0.40f))
                        // Cute smile
                        val smile = Path().apply {
                            moveTo(w * 0.36f, h * 0.56f)
                            quadraticTo(w * 0.50f, h * 0.70f, w * 0.64f, h * 0.56f)
                        }
                        drawPath(smile, InkBlack, style = Stroke(3.2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Phone with Green Shield Checkmark
            Editorial3DLayerBox(
                modifier = Modifier
                    .width(66.dp)
                    .height(98.dp)
                    .graphicsLayer { rotationZ = 8f },
                shape = RoundedCornerShape(16.dp),
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF6B58F8),
                    border = BorderStroke(2.2.dp, InkBlack)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            color = Color(0xFF00C06A),
                            border = BorderStroke(1.5.dp, PureWhite)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }

        // Bottom Left: Gold Coin
        Box(modifier = Modifier.align(Alignment.BottomStart).offset(x = 10.dp, y = 14.dp)) {
            Editorial3DLayerBox(
                modifier = Modifier.graphicsLayer { translationY = if (reducedMotion) 0f else bob; rotationZ = -6f },
                shape = CircleShape,
                depthX = 2.5.dp,
                depthY = 4.dp,
                extrusionColor = InkBlack
            ) {
                Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = SunnyYellow, border = BorderStroke(2.2.dp, InkBlack)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("₹", style = CondensedCardTitleStyle.copy(fontSize = 28.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    }
                }
            }
            ComicActionTicks(angleDegrees = 225f, modifier = Modifier.size(24.dp).align(Alignment.BottomStart).offset(x = (-16).dp, y = 14.dp))
        }
    }
}

@Composable
private fun Slide5SecurityStrip(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    boxColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(14.dp),
        color = boxColor
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(title, style = CondensedCardTitleStyle.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Black), color = InkBlack)
                    Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = InkBlack.copy(alpha = 0.70f))
                }
            }
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(0xFF0F9D75)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(16.dp))
            }
        }
    }
}
