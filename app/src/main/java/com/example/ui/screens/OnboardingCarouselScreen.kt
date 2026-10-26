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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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
import com.example.ui.theme.PalePinkTint
import com.example.ui.theme.PaleYellowTint
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunnyYellow
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

private val SlideBackgrounds = listOf(
    PeriwinkleIndigo, // Slide 1
    SunnyYellow,      // Slide 2
    EditorialCoral,   // Slide 3
    EditorialAqua,    // Slide 4
    EditorialMint     // Slide 5
)

/**
 * Highly interactive, tactile 3D first-launch onboarding carousel with floating rupee bills,
 * 3D minted coins, ambient particles, authentic Indian payment app badges (GPay, PhonePe, Paytm, CRED),
 * and touch/drag reactive 3D physical tilt.
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

    // Back handling: go to previous slide, or exit/finish if on first slide
    BackHandler {
        if (pagerState.currentPage > 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        } else {
            onFinish()
        }
    }

    // Dynamic background color interpolated based on current page
    val currentBgColor = SlideBackgrounds[pagerState.currentPage.coerceIn(0, 4)]
    val animatedBgColor by animateColorAsState(
        targetValue = currentBgColor,
        animationSpec = Motion.normalTween(reducedMotion),
        label = "onboarding_bg_color"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBgColor)
            .testTag("onboarding_carousel_screen")
    ) {
        // Ambient background drifting particles across the entire screen
        AmbientFloatingParticles(
            page = pagerState.currentPage,
            reducedMotion = reducedMotion
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Top Bar: Step Counter & Skip Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isIndigo = pagerState.currentPage == 0
                val textColor = if (isIndigo) PureWhite else InkBlack

                Text(
                    text = "${pagerState.currentPage + 1} / 5",
                    style = CondensedCardTitleStyle.copy(fontSize = 15.sp, letterSpacing = 1.sp),
                    color = textColor,
                    modifier = Modifier.testTag("onboarding_step_counter")
                )

                AnimatedVisibility(
                    visible = pagerState.currentPage < 4,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OnboardingSkipPill(
                        onClick = {
                            Motion.performLightTapHaptic(view)
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

            // Bottom Bar: Dots Indicator & Next / Let's Go Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Morphing 3D Dots
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
                        Box(
                            modifier = Modifier
                                .height(10.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(InkBlack)
                                .border(1.dp, InkBlack, CircleShape)
                        )
                    }
                }

                // Next / Let's Go Button
                val isLastSlide = pagerState.currentPage == 4
                OnboardingActionButton(
                    label = if (isLastSlide) "LET'S GO" else "NEXT",
                    onClick = {
                        Motion.performLightTapHaptic(view)
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

// --- AMBIENT PARTICLES & SPARKLES CANVAS ---
@Composable
private fun AmbientFloatingParticles(
    page: Int,
    reducedMotion: Boolean
) {
    if (reducedMotion) return

    val infiniteTransition = rememberInfiniteTransition(label = "particles_drift")
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift_phase"
    )

    val particleColor = when (page) {
        0 -> PureWhite.copy(alpha = 0.35f)
        1 -> InkBlack.copy(alpha = 0.18f)
        2 -> PureWhite.copy(alpha = 0.32f)
        3 -> InkBlack.copy(alpha = 0.20f)
        else -> PureWhite.copy(alpha = 0.40f)
    }

    val goldAccent = SunnyYellow.copy(alpha = 0.45f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val particlePositions = listOf(
            Offset(w * 0.12f, h * 0.18f) to 6.dp.toPx(),
            Offset(w * 0.88f, h * 0.22f) to 9.dp.toPx(),
            Offset(w * 0.08f, h * 0.65f) to 8.dp.toPx(),
            Offset(w * 0.90f, h * 0.60f) to 7.dp.toPx(),
            Offset(w * 0.50f, h * 0.10f) to 5.dp.toPx(),
            Offset(w * 0.20f, h * 0.82f) to 6.dp.toPx(),
            Offset(w * 0.82f, h * 0.80f) to 8.dp.toPx()
        )

        particlePositions.forEachIndexed { i, (pos, rad) ->
            val offsetMultiplier = (i + 1) * 0.85f
            val floatY = sin(driftPhase + offsetMultiplier) * 14.dp.toPx()
            val floatX = sin(driftPhase * 0.7f + offsetMultiplier) * 8.dp.toPx()
            val center = Offset(pos.x + floatX, pos.y + floatY)

            if (i % 2 == 0) {
                // 4-point golden starburst
                drawStar(
                    center = center,
                    radius = rad * 1.5f,
                    color = goldAccent
                )
            } else {
                // Soft sparkling bokeh circle with border
                drawCircle(
                    color = particleColor,
                    radius = rad,
                    center = center
                )
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

// --- FLOATING 3D RUPEE BANKNOTE COMPONENT ---
@Composable
private fun FloatingRupeeBill(
    denomination: String,
    primaryColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    tiltAngle: Float = 0f,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            rotationZ = tiltAngle
            translationY = bobOffset
        },
        shape = RoundedCornerShape(10.dp),
        depthX = 3.dp,
        depthY = 4.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(115.dp)
                .height(62.dp),
            shape = RoundedCornerShape(10.dp),
            color = primaryColor,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(5.dp)
            ) {
                // Outer inner border representing guilloche
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                )

                // Watermark circle
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.35f))
                        .align(Alignment.CenterStart)
                        .offset(x = 6.dp)
                )

                // Rupee symbol and denomination
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "₹$denomination",
                        style = CondensedCardTitleStyle.copy(
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        ),
                        color = InkBlack
                    )
                    Text(
                        text = "RESERVE BANK",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 6.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = InkBlack.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// --- FLOATING 3D GOLD COIN COMPONENT ---
@Composable
private fun FloatingCoin3D(
    modifier: Modifier = Modifier,
    sizeDp: Int = 38,
    wobbleOffset: Float = 0f,
    wobbleRotation: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = wobbleOffset
            rotationZ = wobbleRotation
        },
        shape = CircleShape,
        depthX = 2.5.dp,
        depthY = 3.5.dp,
        extrusionColor = Color(0xFF996515),
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier.size(sizeDp.dp),
            shape = CircleShape,
            color = SunnyYellow,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Coin inner rim
                Box(
                    modifier = Modifier
                        .size((sizeDp - 8).dp)
                        .clip(CircleShape)
                        .border(1.2.dp, Color(0xFFD4AF37), CircleShape)
                )
                Text(
                    text = "₹",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = (sizeDp * 0.48f).sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = InkBlack
                )
            }
        }
    }
}

// --- AUTHENTIC UPI APP BADGES ---
@Composable
private fun FloatingGPayBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = -6f
        },
        shape = RoundedCornerShape(14.dp),
        depthX = 3.dp,
        depthY = 4.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(96.dp)
                .height(38.dp),
            shape = RoundedCornerShape(14.dp),
            color = PureWhite,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // 4-color Google GPay Pill Icon
                Canvas(modifier = Modifier.size(20.dp)) {
                    val strokeW = 3.5.dp.toPx()
                    // Blue arc
                    drawArc(
                        color = Color(0xFF4285F4),
                        startAngle = 180f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Red arc
                    drawArc(
                        color = Color(0xFFEA4335),
                        startAngle = 270f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Yellow arc
                    drawArc(
                        color = Color(0xFFFBBC05),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Green arc
                    drawArc(
                        color = Color(0xFF34A853),
                        startAngle = 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GPay",
                    style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black),
                    color = InkBlack
                )
            }
        }
    }
}

@Composable
private fun FloatingPhonePeBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    val phonePePurple = Color(0xFF5F259F)
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = 8f
        },
        shape = CircleShape,
        depthX = 3.dp,
        depthY = 4.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = phonePePurple,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "पे",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = PureWhite
                )
            }
        }
    }
}

@Composable
private fun FloatingPaytmBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = -7f
        },
        shape = RoundedCornerShape(12.dp),
        depthX = 3.dp,
        depthY = 4.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(88.dp)
                .height(36.dp),
            shape = RoundedCornerShape(12.dp),
            color = PureWhite,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00BAF2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(10.dp)
                    )
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Paytm",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = Color(0xFF002E6E)
                )
            }
        }
    }
}

@Composable
private fun FloatingCredBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = 6f
        },
        shape = RoundedCornerShape(12.dp),
        depthX = 3.dp,
        depthY = 4.dp,
        extrusionColor = Color(0xFF1E1E28),
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(92.dp)
                .height(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF121218),
            border = BorderStroke(1.8.dp, Color(0xFFD4AF37))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Gold Smart Chip
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD4AF37))
                )
                Text(
                    text = "CRED",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = PureWhite
                )
            }
        }
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
    val isIndigo = page == 0
    val titleColor = if (isIndigo) PureWhite else InkBlack
    val textColor = if (isIndigo) PureWhite.copy(alpha = 0.92f) else InkBlack.copy(alpha = 0.88f)

    val (title, bodyText) = when (page) {
        0 -> "SNAP IT. SORT IT." to "SnapSpend reads your payment screenshots and sorts every rupee into the right category."
        1 -> "ADD IN SECONDS" to "Tap + ADD. Scan with the camera, pick screenshots or a PDF, say a quick note, or type it in yourself."
        2 -> "YOU STAY IN CONTROL" to "The AI reads it, you check it, then you confirm. Fix a category once and SnapSpend remembers it. Duplicates are skipped."
        3 -> "SEE WHERE IT GOES" to "Charts, month-by-month comparisons, budgets and repeating payments. Swipe the category cards to explore."
        else -> "YOUR MONEY, YOUR PHONE" to "Your payments are saved on this phone. Only what you scan or type for the AI is sent to Gemini to be read. Export a backup file any time from Settings."
    }

    val parallaxTranslationX = if (reducedMotion) 0f else (pageOffset * 65f)
    val cardRotationZ = if (reducedMotion) 0f else (pageOffset * -4f).coerceIn(-6f, 6f)
    val scale = if (reducedMotion) 1f else (1f - (pageOffset.absoluteValue * 0.08f)).coerceIn(0.90f, 1f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Text Header Area
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = title,
                style = CondensedCardTitleStyle.copy(fontSize = 38.sp, lineHeight = 42.sp),
                color = titleColor,
                modifier = Modifier.testTag("slide_title_$page")
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 23.sp),
                color = textColor,
                modifier = Modifier.testTag("slide_text_$page")
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Center Chunky 3D Illustration Scene with Interactive Tilt
        Interactive3DTiltCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .graphicsLayer {
                    translationX = parallaxTranslationX
                    this.rotationZ = cardRotationZ
                    scaleX = scale
                    scaleY = scale
                },
            reducedMotion = reducedMotion
        ) {
            when (page) {
                0 -> Slide1RobotIllustration(reducedMotion = reducedMotion)
                1 -> Slide2AddMiniMock(reducedMotion = reducedMotion)
                2 -> Slide3ReviewMiniMock(reducedMotion = reducedMotion)
                3 -> Slide4ChartsMiniMock(reducedMotion = reducedMotion)
                else -> Slide5PrivacyMiniMock(reducedMotion = reducedMotion)
            }
        }
    }
}

// --- SLIDE 1 MINI MOCK: Robot Mascot + Floating ₹500 Bill + GPay + PhonePe + Coins ---
@Composable
private fun Slide1RobotIllustration(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide1_anims")
    val bob1 by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob1"
    )
    val bob2 by infiniteTransition.animateFloat(
        initialValue = 5f,
        targetValue = -5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob2"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main Chunky 3D Base Card
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide1_mock_card"),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.graphicsLayer {
                            translationY = if (reducedMotion) 0f else bob1
                        },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Friendly 3D Robot Mascot Head
                        Surface(
                            modifier = Modifier.size(96.dp),
                            shape = RoundedCornerShape(24.dp),
                            color = SunnyYellow,
                            border = BorderStroke(2.2.dp, InkBlack)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                // Antenna pill on top
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(EditorialCoral)
                                        .border(1.2.dp, InkBlack, CircleShape)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                    Box(modifier = Modifier.size(15.dp).clip(CircleShape).background(InkBlack))
                                    Box(modifier = Modifier.size(15.dp).clip(CircleShape).background(InkBlack))
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(38.dp)
                                        .height(7.dp)
                                        .clip(RoundedCornerShape(3.5.dp))
                                        .background(InkBlack)
                                )
                            }
                        }

                        // Floating Receipt Card in Mascot's Hands
                        Surface(
                            modifier = Modifier
                                .width(210.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = PaleLavenderTint,
                            border = BorderStroke(1.8.dp, InkBlack)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("SWIGGY INSTAMART", style = CondensedCardTitleStyle.copy(fontSize = 13.sp), color = InkBlack)
                                    Text("UPI Screenshot · 10:42 AM", style = MaterialTheme.typography.labelSmall, color = InkBlack.copy(alpha = 0.7f))
                                }
                                Text("₹420", style = CondensedCardTitleStyle.copy(fontSize = 17.sp), color = InkBlack)
                            }
                        }
                    }
                }
            }
        }

        // Floating ₹500 Rupee Banknote (Top Right)
        FloatingRupeeBill(
            denomination = "500",
            primaryColor = Color(0xFF4A9086),
            accentColor = Color(0xFF2E655D),
            tiltAngle = 12f,
            bobOffset = if (reducedMotion) 0f else bob2,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-12).dp)
        )

        // Floating Google Pay Badge (Top Left)
        FloatingGPayBadge(
            bobOffset = if (reducedMotion) 0f else -bob1,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-8).dp, y = (-6).dp)
        )

        // Floating PhonePe Badge (Bottom Left)
        FloatingPhonePeBadge(
            bobOffset = if (reducedMotion) 0f else bob2,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 10.dp)
        )

        // Floating 3D Gold Coin (Bottom Right)
        FloatingCoin3D(
            sizeDp = 40,
            wobbleOffset = if (reducedMotion) 0f else bob1,
            wobbleRotation = 10f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 12.dp)
        )
    }
}

// --- SLIDE 2 MINI MOCK: "+ ADD" Pill + Stacked Cards + Paytm + ₹200 Bill + Coins ---
@Composable
private fun Slide2AddMiniMock(reducedMotion: Boolean) {
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
            .height(310.dp),
        contentAlignment = Alignment.Center
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide2_mock_card"),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header 3D Mini Pill "+ ADD"
                    Surface(
                        shape = CircleShape,
                        color = SunnyYellow,
                        border = BorderStroke(2.dp, InkBlack)
                    ) {
                        Text(
                            text = "+ ADD PAYMENT",
                            style = CondensedCardTitleStyle.copy(fontSize = 15.sp),
                            color = InkBlack,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Stacked 3D Colored Action Options
                    Column(
                        modifier = Modifier.fillMaxWidth(0.92f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MiniMockOptionStrip(title = "QUICK SCAN CAMERA", color = PaleYellowTint, icon = Icons.Default.PhotoCamera)
                        MiniMockOptionStrip(title = "PICK SCREENSHOTS", color = EditorialAqua, icon = Icons.Default.ReceiptLong)
                        MiniMockOptionStrip(title = "SPEAK A NOTE", color = PaleLavenderTint, icon = Icons.Default.Mic)
                        MiniMockOptionStrip(title = "UPLOAD BANK PDF", color = PalePinkTint, icon = Icons.Default.UploadFile)
                    }
                }
            }
        }

        // Floating ₹200 Banknote (Top Right)
        FloatingRupeeBill(
            denomination = "200",
            primaryColor = Color(0xFFF38A3E),
            accentColor = Color(0xFFB55816),
            tiltAngle = -10f,
            bobOffset = if (reducedMotion) 0f else bob,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 14.dp, y = (-10).dp)
        )

        // Floating Paytm Badge (Bottom Right)
        FloatingPaytmBadge(
            bobOffset = if (reducedMotion) 0f else -bob,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 10.dp, y = 14.dp)
        )

        // Floating Camera Badge (Top Left)
        Editorial3DLayerBox(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-8).dp, y = (-8).dp)
                .graphicsLayer { translationY = if (reducedMotion) 0f else bob },
            shape = CircleShape,
            depthX = 2.5.dp,
            depthY = 3.5.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = PaleYellowTint,
                border = BorderStroke(1.8.dp, InkBlack)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.PhotoCamera, contentDescription = null, tint = InkBlack, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniMockOptionStrip(title: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = color,
        border = BorderStroke(1.5.dp, InkBlack)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
            Text(title, style = CondensedCardTitleStyle.copy(fontSize = 12.sp), color = InkBlack)
        }
    }
}

// --- SLIDE 3 MINI MOCK: Review Card with EXAMPLE Tag + CRED Badge + ₹100 Bill ---
@Composable
private fun Slide3ReviewMiniMock(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide3_anims")
    val bob by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_slide3"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.Center
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide3_mock_card"),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top row with merchant and EXAMPLE tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("BLUE TOKAI COFFEE", style = CondensedCardTitleStyle.copy(fontSize = 18.sp), color = InkBlack)
                            Text("Food & Drinks · Today, 10:15 AM", style = MaterialTheme.typography.bodySmall, color = InkBlack.copy(alpha = 0.7f))
                        }
                        // Mandatory small EXAMPLE tag
                        Surface(
                            shape = CircleShape,
                            color = SunnyYellow,
                            border = BorderStroke(1.4.dp, InkBlack)
                        ) {
                            Text(
                                text = "EXAMPLE",
                                style = CondensedCardTitleStyle.copy(fontSize = 10.sp),
                                color = InkBlack,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Middle: Big amount & verified check chip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("₹1,249.00", style = CondensedCardTitleStyle.copy(fontSize = 32.sp), color = InkBlack)
                        Surface(
                            shape = CircleShape,
                            color = EditorialMint,
                            border = BorderStroke(1.4.dp, InkBlack)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = InkBlack, modifier = Modifier.size(13.dp))
                                Text("Ready", style = CondensedCardTitleStyle.copy(fontSize = 11.sp), color = InkBlack)
                            }
                        }
                    }

                    // Bottom Black CONFIRM Button
                    Surface(
                        shape = CircleShape,
                        color = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "CONFIRM PAYMENT",
                            style = CondensedCardTitleStyle.copy(fontSize = 14.sp),
                            color = PureWhite,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // Floating CRED Luxe Card Badge (Top Right)
        FloatingCredBadge(
            bobOffset = if (reducedMotion) 0f else bob,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-12).dp)
        )

        // Floating ₹100 Banknote (Bottom Left)
        FloatingRupeeBill(
            denomination = "100",
            primaryColor = Color(0xFF6B7FB5),
            accentColor = Color(0xFF455584),
            tiltAngle = 10f,
            bobOffset = if (reducedMotion) 0f else -bob,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-12).dp, y = 12.dp)
        )

        // Floating Duplicate Skip Shield (Bottom Right)
        Editorial3DLayerBox(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 14.dp)
                .graphicsLayer { translationY = if (reducedMotion) 0f else bob },
            shape = CircleShape,
            depthX = 2.dp,
            depthY = 3.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = PaleYellowTint,
                border = BorderStroke(1.5.dp, InkBlack)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// --- SLIDE 4 MINI MOCK: 3D Weekly Pace Chart + Category Chips + Coins ---
@Composable
private fun Slide4ChartsMiniMock(reducedMotion: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide4_anims")
    val bob by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_slide4"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(310.dp),
        contentAlignment = Alignment.Center
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide4_mock_card"),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Background peeking card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(180.dp)
                            .offset(x = 10.dp, y = (-12).dp),
                        shape = RoundedCornerShape(20.dp),
                        color = SunnyYellow,
                        border = BorderStroke(1.5.dp, InkBlack)
                    ) {}

                    // Foreground Bar Chart Mock
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .height(185.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = EditorialCoral,
                        border = BorderStroke(1.8.dp, InkBlack)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("WEEKLY SPENDING", style = CondensedCardTitleStyle.copy(fontSize = 15.sp), color = InkBlack)
                                Surface(
                                    shape = CircleShape,
                                    color = PureWhite,
                                    border = BorderStroke(1.dp, InkBlack)
                                ) {
                                    Text(
                                        "₹8,450",
                                        style = CondensedCardTitleStyle.copy(fontSize = 11.sp),
                                        color = InkBlack,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // 6 mini 3D prism bars
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(95.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val heights = listOf(0.4f, 0.75f, 0.5f, 0.95f, 0.35f, 0.65f)
                                heights.forEachIndexed { idx, h ->
                                    val barColor = if (idx == 3) EditorialMint else PureWhite
                                    Box(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height((95 * h).dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(barColor)
                                            .border(1.2.dp, InkBlack, RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Category Chip: Food (Top Left)
        FloatingCategoryChip(
            label = "Food",
            icon = Icons.Default.Restaurant,
            color = PaleYellowTint,
            bobOffset = if (reducedMotion) 0f else bob,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-8).dp, y = (-8).dp)
        )

        // Floating Category Chip: Shopping (Top Right)
        FloatingCategoryChip(
            label = "Shopping",
            icon = Icons.Default.ShoppingBag,
            color = PalePinkTint,
            bobOffset = if (reducedMotion) 0f else -bob,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-10).dp)
        )

        // Floating Gold Coin (Bottom Left)
        FloatingCoin3D(
            sizeDp = 36,
            wobbleOffset = if (reducedMotion) 0f else bob,
            wobbleRotation = -12f,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-8).dp, y = 10.dp)
        )

        // Floating Category Chip: Fuel (Bottom Right)
        FloatingCategoryChip(
            label = "Fuel",
            icon = Icons.Default.LocalGasStation,
            color = PaleLavenderTint,
            bobOffset = if (reducedMotion) 0f else -bob,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 12.dp)
        )
    }
}

@Composable
private fun FloatingCategoryChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    bobOffset: Float,
    modifier: Modifier = Modifier
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer { translationY = bobOffset },
        shape = CircleShape,
        depthX = 2.dp,
        depthY = 3.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            shape = CircleShape,
            color = color,
            border = BorderStroke(1.4.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(13.dp))
                Text(label, style = CondensedCardTitleStyle.copy(fontSize = 11.sp), color = InkBlack)
            }
        }
    }
}

// --- SLIDE 5 MINI MOCK: 3D Safe Vault + Cloud Backup Badge + Key ---
@Composable
private fun Slide5PrivacyMiniMock(reducedMotion: Boolean) {
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
            .height(310.dp),
        contentAlignment = Alignment.Center
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide5_mock_card"),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 3D Phone & Brass Lock Badge
                    Box(
                        modifier = Modifier.size(95.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(82.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = EditorialMint,
                            border = BorderStroke(2.dp, InkBlack)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.PhoneAndroid, contentDescription = null, tint = InkBlack, modifier = Modifier.size(36.dp))
                            }
                        }
                        // 3D Padlock
                        Surface(
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.BottomEnd),
                            shape = CircleShape,
                            color = SunnyYellow,
                            border = BorderStroke(1.8.dp, InkBlack)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Text(
                        text = "100% PRIVATE TO THIS DEVICE",
                        style = CondensedCardTitleStyle.copy(fontSize = 17.sp),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    // Export Button Mock
                    Surface(
                        shape = CircleShape,
                        color = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack),
                        modifier = Modifier.fillMaxWidth(0.88f)
                    ) {
                        Text(
                            text = "EXPORT BACKUP (.JSON)",
                            style = CondensedCardTitleStyle.copy(fontSize = 14.sp),
                            color = PureWhite,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }

        // Floating Cloud Backup Badge (Top Right)
        Editorial3DLayerBox(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 12.dp, y = (-10).dp)
                .graphicsLayer { translationY = if (reducedMotion) 0f else bob },
            shape = CircleShape,
            depthX = 2.5.dp,
            depthY = 3.5.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = PaleYellowTint,
                border = BorderStroke(1.6.dp, InkBlack)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.CloudDone, contentDescription = null, tint = InkBlack, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Floating Security Key Badge (Bottom Left)
        Editorial3DLayerBox(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 12.dp)
                .graphicsLayer { translationY = if (reducedMotion) 0f else -bob },
            shape = CircleShape,
            depthX = 2.5.dp,
            depthY = 3.5.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = PaleLavenderTint,
                border = BorderStroke(1.6.dp, InkBlack)
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// --- SKIP PILL BUTTON ---
@Composable
private fun OnboardingSkipPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current

    val pressFraction by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "skip_press"
    )

    Box(
        modifier = modifier
            .padding(end = 2.dp, bottom = 3.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = "Skip onboarding"
            }
            .testTag("onboarding_skip_button")
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(
                    x = (2 * (1f - pressFraction)).dp,
                    y = (3 * (1f - pressFraction)).dp
                )
                .clip(CircleShape)
                .background(InkBlack)
        )
        Surface(
            modifier = Modifier.offset(
                x = (2 * pressFraction).dp,
                y = (3 * pressFraction).dp
            ),
            shape = CircleShape,
            color = PureWhite,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Text(
                text = "SKIP",
                style = CondensedCardTitleStyle.copy(fontSize = 13.sp),
                color = InkBlack,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
            )
        }
    }
}

// --- ACTION BUTTON (NEXT / LET'S GO) ---
@Composable
private fun OnboardingActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current

    val pressFraction by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = Motion.pressSpring(reducedMotion),
        label = "action_btn_press"
    )

    Box(
        modifier = modifier
            .padding(end = 3.dp, bottom = 4.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .testTag("onboarding_action_button")
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(
                    x = (3 * (1f - pressFraction)).dp,
                    y = (4 * (1f - pressFraction)).dp
                )
                .clip(CircleShape)
                .background(InkBlack)
        )
        Surface(
            modifier = Modifier.offset(
                x = (3 * pressFraction).dp,
                y = (4 * pressFraction).dp
            ),
            shape = CircleShape,
            color = SunnyYellow,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = CondensedCardTitleStyle.copy(fontSize = 17.sp),
                    color = InkBlack
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = InkBlack,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
