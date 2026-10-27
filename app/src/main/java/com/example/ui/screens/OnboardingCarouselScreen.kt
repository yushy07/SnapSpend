package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
 * Signature Electric Cobalt Blue background from the reference design.
 */
private val OnboardingElectricBlue = Color(0xFF3858F8)

/**
 * High-octane, tactile 3D pop-art first-launch onboarding carousel matching the reference design:
 * - Electric Cobalt Blue background
 * - Top bar with "X / 5" and 3D white "SKIP" pill
 * - Huge bold white headline with yellow comic ray sparkles & curved yellow underline
 * - Chunky white 3D card with floating Indian payment badges (GPay, PhonePe, Paytm, CRED),
 *   rupee banknotes, 3D minted coins, cute smiling mascot characters, and comic action tick marks
 * - Interactive 3D touch/drag tilt + continuous organic floating bobbing
 * - Bottom bar with morphing yellow/black dot indicators and 3D Sunny Yellow "NEXT →" button
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

    BackHandler {
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
            .background(OnboardingElectricBlue)
            .testTag("onboarding_carousel_screen")
    ) {
        // Ambient background drifting particles
        AmbientFloatingParticles(reducedMotion = reducedMotion)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            // Top Bar: Step Counter (Left) & 3D Skip Pill (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / 5",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    ),
                    color = PureWhite,
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

            // Bottom Bar: Morphing Indicators (Left) & 3D Yellow Action Button (Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Morphing Dots: Active page is a Sunny Yellow pill; inactive pages are Black dots
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
                                .background(if (isSelected) SunnyYellow else InkBlack)
                                .border(1.2.dp, InkBlack, CircleShape)
                        )
                    }
                }

                // Next / Get Started Action Button
                val isLastSlide = pagerState.currentPage == 4
                OnboardingActionButton(
                    label = if (isLastSlide) "GET STARTED" else "NEXT",
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
private fun AmbientFloatingParticles(reducedMotion: Boolean) {
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

        particlePositions.forEachIndexed { i, (pos, rad) ->
            val offsetMultiplier = (i + 1) * 0.85f
            val floatY = sin(driftPhase + offsetMultiplier) * 12.dp.toPx()
            val floatX = sin(driftPhase * 0.7f + offsetMultiplier) * 8.dp.toPx()
            val center = Offset(pos.x + floatX, pos.y + floatY)

            if (i % 2 == 0) {
                // Golden 4-point star
                drawStar(center = center, radius = rad * 1.4f, color = SunnyYellow.copy(alpha = 0.55f))
            } else {
                // Soft white sparkle
                drawCircle(color = PureWhite.copy(alpha = 0.35f), radius = rad, center = center)
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

/**
 * 3 comic radiating tick marks (black whiskers) placed near corners & floating objects.
 */
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

/**
 * 3 yellow radiating sparkle rays that shoot above mascots and headlines.
 */
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

/**
 * Hand-drawn yellow curved accent underline flourish under the subtitle.
 */
@Composable
private fun CurvedYellowUnderline(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(0f, size.height * 0.75f)
            quadraticTo(
                size.width * 0.5f, size.height * 0.15f,
                size.width, size.height * 0.60f
            )
        }
        drawPath(
            path = path,
            color = SunnyYellow,
            style = Stroke(width = 4.2.dp.toPx(), cap = StrokeCap.Round)
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

// --- FLOATING 3D RUPEE BANKNOTE ---
@Composable
private fun FloatingRupeeBill(
    denomination: String,
    primaryColor: Color,
    watermarkColor: Color,
    modifier: Modifier = Modifier,
    tiltAngle: Float = 0f,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            rotationZ = tiltAngle
            translationY = bobOffset
        },
        shape = RoundedCornerShape(12.dp),
        depthX = 2.5.dp,
        depthY = 4.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(105.dp)
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            color = primaryColor,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(watermarkColor)
                )
                Text(
                    text = "₹$denomination",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    ),
                    color = InkBlack
                )
            }
        }
    }
}

// --- FLOATING 3D RUPEE COIN ---
@Composable
private fun FloatingCoinComic(
    symbolColor: Color,
    coinColor: Color,
    modifier: Modifier = Modifier,
    sizeDp: Int = 48,
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
        depthY = 4.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier.size(sizeDp.dp),
            shape = CircleShape,
            color = coinColor,
            border = BorderStroke(2.2.dp, InkBlack)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "₹",
                    style = CondensedCardTitleStyle.copy(
                        fontSize = (sizeDp * 0.52f).sp,
                        fontWeight = FontWeight.Black
                    ),
                    color = symbolColor
                )
            }
        }
    }
}

// --- AUTHENTIC FLOATING PAYMENT APP BADGES ---

@Composable
private fun FloatingGPayPillBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = -6f
        },
        shape = RoundedCornerShape(20.dp),
        depthX = 2.5.dp,
        depthY = 3.5.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(102.dp)
                .height(42.dp),
            shape = RoundedCornerShape(20.dp),
            color = PureWhite,
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Canvas(modifier = Modifier.size(20.dp)) {
                    val strokeW = 3.5.dp.toPx()
                    drawArc(Color(0xFF4285F4), 180f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                    drawArc(Color(0xFFEA4335), 270f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                    drawArc(Color(0xFFFBBC05), 0f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                    drawArc(Color(0xFF34A853), 90f, 90f, false, style = Stroke(strokeW, cap = StrokeCap.Round))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "GPay",
                    style = CondensedCardTitleStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Black),
                    color = InkBlack
                )
            }
        }
    }
}

@Composable
private fun FloatingPhonePePillBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = -5f
        },
        shape = RoundedCornerShape(20.dp),
        depthX = 2.5.dp,
        depthY = 3.5.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(115.dp)
                .height(42.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF5F259F),
            border = BorderStroke(2.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("पे", style = CondensedCardTitleStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Black), color = PureWhite)
                Spacer(modifier = Modifier.width(6.dp))
                Text("PhonePe", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black), color = PureWhite)
            }
        }
    }
}

@Composable
private fun FloatingPaytmPillBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = 6f
        },
        shape = RoundedCornerShape(20.dp),
        depthX = 2.5.dp,
        depthY = 3.5.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(105.dp)
                .height(42.dp),
            shape = RoundedCornerShape(20.dp),
            color = PureWhite,
            border = BorderStroke(2.dp, InkBlack)
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
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00BAF2)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = PureWhite, modifier = Modifier.size(12.dp))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text("Paytm", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black), color = Color(0xFF002E6E))
            }
        }
    }
}

@Composable
private fun FloatingCredPillBadge(
    modifier: Modifier = Modifier,
    bobOffset: Float = 0f
) {
    Editorial3DLayerBox(
        modifier = modifier.graphicsLayer {
            translationY = bobOffset
            rotationZ = -6f
        },
        shape = RoundedCornerShape(20.dp),
        depthX = 2.5.dp,
        depthY = 3.5.dp,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack
    ) {
        Surface(
            modifier = Modifier
                .width(105.dp)
                .height(42.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14141E),
            border = BorderStroke(2.dp, Color(0xFFD4AF37))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(14.dp)
                        .height(11.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFFD4AF37))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("CRED", style = CondensedCardTitleStyle.copy(fontSize = 15.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp), color = PureWhite)
            }
        }
    }
}

// --- SMILING MASCOT CHARACTERS ---

/**
 * The yellow smiling mascot with 4 corner viewfinder brackets from the user reference image.
 */
@Composable
private fun SmilingScannerMascot(
    modifier: Modifier = Modifier,
    mode: Int = 0 // 0: Scanner brackets, 1: Camera lens, 2: Shield, 3: Chart, 4: Safe lock
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Radiating yellow sparkle rays above mascot
        ComicYellowRays(
            modifier = Modifier
                .size(46.dp, 28.dp)
                .align(Alignment.TopCenter)
                .offset(y = (-24).dp)
        )

        Editorial3DLayerBox(
            modifier = Modifier.size(86.dp),
            shape = RoundedCornerShape(22.dp),
            depthX = 3.dp,
            depthY = 4.5.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
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

                    when (mode) {
                        0 -> {
                            // 4 Corner Viewfinder Brackets
                            drawLine(InkBlack, Offset(0f, 0f), Offset(bracketLen, 0f), strokeW, StrokeCap.Round)
                            drawLine(InkBlack, Offset(0f, 0f), Offset(0f, bracketLen), strokeW, StrokeCap.Round)

                            drawLine(InkBlack, Offset(w, 0f), Offset(w - bracketLen, 0f), strokeW, StrokeCap.Round)
                            drawLine(InkBlack, Offset(w, 0f), Offset(w, bracketLen), strokeW, StrokeCap.Round)

                            drawLine(InkBlack, Offset(0f, h), Offset(bracketLen, h), strokeW, StrokeCap.Round)
                            drawLine(InkBlack, Offset(0f, h), Offset(0f, h - bracketLen), strokeW, StrokeCap.Round)

                            drawLine(InkBlack, Offset(w, h), Offset(w - bracketLen, h), strokeW, StrokeCap.Round)
                            drawLine(InkBlack, Offset(w, h), Offset(w, h - bracketLen), strokeW, StrokeCap.Round)
                        }
                        1 -> {
                            // Camera flash circle on top-right
                            drawCircle(InkBlack, 3.5.dp.toPx(), Offset(w * 0.82f, h * 0.18f))
                        }
                        else -> {
                            // Subtle corner anchors
                            drawCircle(InkBlack, 2.5.dp.toPx(), Offset(4.dp.toPx(), 4.dp.toPx()))
                            drawCircle(InkBlack, 2.5.dp.toPx(), Offset(w - 4.dp.toPx(), 4.dp.toPx()))
                        }
                    }

                    // Two black dot eyes
                    val eyeRadius = 4.5.dp.toPx()
                    drawCircle(InkBlack, eyeRadius, Offset(w * 0.35f, h * 0.44f))
                    drawCircle(InkBlack, eyeRadius, Offset(w * 0.65f, h * 0.44f))

                    // Cute curved smile
                    val smilePath = Path().apply {
                        moveTo(w * 0.36f, h * 0.60f)
                        quadraticTo(w * 0.50f, h * 0.76f, w * 0.64f, h * 0.60f)
                    }
                    drawPath(
                        path = smilePath,
                        color = InkBlack,
                        style = Stroke(width = 3.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
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
    val (titleLines, bodyText) = when (page) {
        0 -> listOf("SNAP IT.,", "SORT IT.") to "SnapSpend reads your payment screenshots and sorts every rupee into the right category."
        1 -> listOf("ADD IN,", "SECONDS.") to "Tap + ADD. Scan with the camera, pick screenshots or a PDF, say a quick note, or type it in yourself."
        2 -> listOf("YOU STAY IN,", "CONTROL.") to "The AI reads it, you check it, then confirm. Fix a category once and SnapSpend remembers it forever."
        3 -> listOf("SEE WHERE,", "IT GOES.") to "Charts, month-by-month comparisons, budgets, and repeating payments at a single glance."
        else -> listOf("YOUR MONEY,,", "YOUR PHONE.") to "Your finances live strictly on your phone. Private on-device database with zero ads and encrypted backups."
    }

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
        // Hero Header Area with Yellow Sparkles & Curved Flourish
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    titleLines.forEach { line ->
                        Text(
                            text = line,
                            style = CondensedCardTitleStyle.copy(
                                fontSize = 42.sp,
                                lineHeight = 44.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-0.5).sp
                            ),
                            color = PureWhite,
                            modifier = Modifier.testTag("slide_title_$page")
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                // 3 Yellow rays beside headline
                ComicYellowRays(
                    modifier = Modifier
                        .size(38.dp, 32.dp)
                        .offset(y = (-8).dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = PureWhite.copy(alpha = 0.95f)
                ),
                modifier = Modifier.testTag("slide_text_$page")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Hand-drawn yellow underline flourish
            CurvedYellowUnderline(
                modifier = Modifier
                    .width(110.dp)
                    .height(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

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
// SLIDE 1: SNAP IT. SORT IT. (FAITHFUL TO USER REFERENCE IMAGE)
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
        // Main Chunky White Card with -2° tilt
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
                    // Inner Soft Lavender UPI Receipt
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFFF1EEFC),
                        border = BorderStroke(1.5.dp, Color(0xFFE2DCF7))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Top Row: Swiggy Instamart + UPI Timestamp
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
                                        Text(
                                            text = "SWIGGY INSTAMART",
                                            style = CondensedCardTitleStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Black),
                                            color = InkBlack
                                        )
                                        Text(
                                            text = "UPI Screenshot • 10:42 AM",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                            color = InkBlack.copy(alpha = 0.65f)
                                        )
                                    }
                                }

                                // UPI Flag Arrow
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

                            // Bottom Row: Amount & Category
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
        SmilingScannerMascot(
            mode = 0,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        )

        // Top Left: Floating GPay Pill Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 20.dp)
        ) {
            FloatingGPayPillBadge(
                bobOffset = if (reducedMotion) 0f else -bob1
            )
            ComicActionTicks(
                angleDegrees = 135f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-16).dp, y = (-12).dp)
            )
        }

        // Top Right: Floating ₹500 Rupee Bill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 24.dp)
        ) {
            FloatingRupeeBill(
                denomination = "500",
                primaryColor = Color(0xFF6EC68E),
                watermarkColor = Color(0xFF4FA870),
                tiltAngle = 10f,
                bobOffset = if (reducedMotion) 0f else bob2
            )
            ComicActionTicks(
                angleDegrees = 45f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
            )
        }

        // Bottom Left: Floating Purple Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-8).dp)
        ) {
            FloatingCoinComic(
                symbolColor = PureWhite,
                coinColor = Color(0xFF7C5CFC),
                sizeDp = 52,
                wobbleOffset = if (reducedMotion) 0f else bob1,
                wobbleRotation = -8f
            )
            ComicActionTicks(
                angleDegrees = 225f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-16).dp, y = 14.dp)
            )
        }

        // Bottom Right: Floating Gold Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-4).dp)
        ) {
            FloatingCoinComic(
                symbolColor = InkBlack,
                coinColor = SunnyYellow,
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else -bob2,
                wobbleRotation = 12f
            )
            ComicActionTicks(
                angleDegrees = 315f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 14.dp)
            )
        }
    }
}

// ============================================================================
// SLIDE 2: ADD IN SECONDS (CAMERA + SCREENSHOTS + VOICE + PDF)
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
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = 1.5f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide2_mock_card"),
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Slide2OptionPill(title = "CAMERA SCAN", icon = Icons.Default.PhotoCamera, color = EditorialAqua)
                        Slide2OptionPill(title = "PICK SCREENSHOTS", icon = Icons.Default.ReceiptLong, color = SunnyYellow)
                        Slide2OptionPill(title = "VOICE NOTE", icon = Icons.Default.Mic, color = PaleLavenderTint)
                        Slide2OptionPill(title = "BANK PDF STATEMENT", icon = Icons.Default.UploadFile, color = EditorialMint)
                    }
                }
            }
        }

        // Mascot (Camera mode)
        SmilingScannerMascot(
            mode = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        )

        // Top Left: PhonePe Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 20.dp)
        ) {
            FloatingPhonePePillBadge(bobOffset = if (reducedMotion) 0f else bob)
            ComicActionTicks(
                angleDegrees = 135f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-16).dp, y = (-12).dp)
            )
        }

        // Top Right: ₹200 Orange Rupee Bill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 24.dp)
        ) {
            FloatingRupeeBill(
                denomination = "200",
                primaryColor = Color(0xFFF38A3E),
                watermarkColor = Color(0xFFD96E25),
                tiltAngle = 10f,
                bobOffset = if (reducedMotion) 0f else -bob
            )
            ComicActionTicks(
                angleDegrees = 45f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
            )
        }

        // Bottom Left: Blue Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-8).dp)
        ) {
            FloatingCoinComic(
                symbolColor = PureWhite,
                coinColor = Color(0xFF3B82F6),
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else bob,
                wobbleRotation = -10f
            )
            ComicActionTicks(
                angleDegrees = 225f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-16).dp, y = 14.dp)
            )
        }

        // Bottom Right: Gold Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-4).dp)
        ) {
            FloatingCoinComic(
                symbolColor = InkBlack,
                coinColor = SunnyYellow,
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else -bob,
                wobbleRotation = 8f
            )
            ComicActionTicks(
                angleDegrees = 315f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 14.dp)
            )
        }
    }
}

@Composable
private fun Slide2OptionPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color,
        border = BorderStroke(1.8.dp, InkBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = CondensedCardTitleStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Black),
                    color = InkBlack
                )
            }
            Text("TAP →", style = CondensedCardTitleStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Black), color = InkBlack)
        }
    }
}

// ============================================================================
// SLIDE 3: YOU STAY IN CONTROL (CONFIRM, EDIT, NO DUPLICATES)
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
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = -1.5f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide3_mock_card"),
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Zomato Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFFF2F0),
                            border = BorderStroke(1.5.dp, Color(0xFFFFD5CE))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
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
                                Text("₹680", style = CondensedCardTitleStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.Black), color = InkBlack)
                            }
                        }

                        // Duplicate Skip pill
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = PaleLavenderTint,
                            border = BorderStroke(1.2.dp, InkBlack)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = InkBlack, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Duplicate reference skipped automatically", style = CondensedCardTitleStyle.copy(fontSize = 11.sp), color = InkBlack)
                            }
                        }

                        // Confirm Button 3D Pill
                        Editorial3DLayerBox(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            depthX = 2.dp,
                            depthY = 3.dp,
                            extrusionColor = InkBlack
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = EditorialMint,
                                border = BorderStroke(2.dp, InkBlack)
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = InkBlack, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("CONFIRM & SAVE", style = CondensedCardTitleStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Black), color = InkBlack)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Mascot
        SmilingScannerMascot(
            mode = 2,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        )

        // Top Left: Paytm Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 20.dp)
        ) {
            FloatingPaytmPillBadge(bobOffset = if (reducedMotion) 0f else bob)
            ComicActionTicks(
                angleDegrees = 135f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-16).dp, y = (-12).dp)
            )
        }

        // Top Right: ₹100 Blue Bill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 24.dp)
        ) {
            FloatingRupeeBill(
                denomination = "100",
                primaryColor = Color(0xFF6B93DC),
                watermarkColor = Color(0xFF4C75BF),
                tiltAngle = 10f,
                bobOffset = if (reducedMotion) 0f else -bob
            )
            ComicActionTicks(
                angleDegrees = 45f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
            )
        }

        // Bottom Left: Mint Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-8).dp)
        ) {
            FloatingCoinComic(
                symbolColor = InkBlack,
                coinColor = EditorialMint,
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else bob,
                wobbleRotation = -10f
            )
            ComicActionTicks(
                angleDegrees = 225f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-16).dp, y = 14.dp)
            )
        }

        // Bottom Right: Coral Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-4).dp)
        ) {
            FloatingCoinComic(
                symbolColor = PureWhite,
                coinColor = EditorialCoral,
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else -bob,
                wobbleRotation = 8f
            )
            ComicActionTicks(
                angleDegrees = 315f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 14.dp)
            )
        }
    }
}

// ============================================================================
// SLIDE 4: SEE WHERE IT GOES (INSIGHTS + BUDGETS)
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
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = 1.5f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide4_mock_card"),
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Monthly Total Spend Callout
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = SunnyYellow,
                            border = BorderStroke(1.8.dp, InkBlack)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("THIS MONTH SPENT", style = CondensedCardTitleStyle.copy(fontSize = 11.sp), color = InkBlack)
                                Text("₹18,450", style = CondensedCardTitleStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.Black), color = InkBlack)
                            }
                        }

                        // Category Bar: Food
                        Slide4CategoryProgress(name = "Food & Dining", spent = "₹8,200", fraction = 0.65f, barColor = EditorialCoral)
                        // Category Bar: Groceries
                        Slide4CategoryProgress(name = "Groceries", spent = "₹5,400", fraction = 0.45f, barColor = Color(0xFF7C5CFC))
                        // Category Bar: Shopping
                        Slide4CategoryProgress(name = "Shopping", spent = "₹4,850", fraction = 0.35f, barColor = EditorialAqua)
                    }
                }
            }
        }

        // Mascot
        SmilingScannerMascot(
            mode = 3,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        )

        // Top Left: CRED Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 20.dp)
        ) {
            FloatingCredPillBadge(bobOffset = if (reducedMotion) 0f else bob)
            ComicActionTicks(
                angleDegrees = 135f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-16).dp, y = (-12).dp)
            )
        }

        // Top Right: ₹2,000 Pink Rupee Bill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 24.dp)
        ) {
            FloatingRupeeBill(
                denomination = "2000",
                primaryColor = Color(0xFFE8629B),
                watermarkColor = Color(0xFFC7437C),
                tiltAngle = 10f,
                bobOffset = if (reducedMotion) 0f else -bob
            )
            ComicActionTicks(
                angleDegrees = 45f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
            )
        }

        // Bottom Left: Lavender Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-8).dp)
        ) {
            FloatingCoinComic(
                symbolColor = InkBlack,
                coinColor = PaleLavenderTint,
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else bob,
                wobbleRotation = -8f
            )
            ComicActionTicks(
                angleDegrees = 225f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-16).dp, y = 14.dp)
            )
        }

        // Bottom Right: Gold Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-4).dp)
        ) {
            FloatingCoinComic(
                symbolColor = InkBlack,
                coinColor = SunnyYellow,
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else -bob,
                wobbleRotation = 12f
            )
            ComicActionTicks(
                angleDegrees = 315f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 14.dp)
            )
        }
    }
}

@Composable
private fun Slide4CategoryProgress(
    name: String,
    spent: String,
    fraction: Float,
    barColor: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(name, style = CondensedCardTitleStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.Black), color = InkBlack)
            Text(spent, style = CondensedCardTitleStyle.copy(fontSize = 11.sp), color = InkBlack)
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE5E5EA))
                .border(0.8.dp, InkBlack, RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(barColor)
            )
        }
    }
}

// ============================================================================
// SLIDE 5: YOUR MONEY, YOUR PHONE (PRIVACY + BACKUP)
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
            .height(350.dp),
        contentAlignment = Alignment.Center
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .height(280.dp)
                .graphicsLayer { rotationZ = -1.5f },
            shape = RoundedCornerShape(26.dp),
            depthX = 4.dp,
            depthY = 6.dp,
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("slide5_mock_card"),
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Slide5SecurityRow(title = "100% ON-DEVICE DATABASE", subtitle = "Stored locally on your phone", icon = Icons.Default.Lock, color = EditorialMint)
                        Slide5SecurityRow(title = "ENCRYPTED DRIVE BACKUP", subtitle = "Your own private Google Drive", icon = Icons.Default.CloudDone, color = EditorialAqua)
                        Slide5SecurityRow(title = "ZERO THIRD-PARTY ADS", subtitle = "No trackers or telemetry", icon = Icons.Default.Shield, color = PaleLavenderTint)
                    }
                }
            }
        }

        // Mascot
        SmilingScannerMascot(
            mode = 4,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-14).dp)
        )

        // Top Left: UPI Shield Badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 6.dp, y = 20.dp)
        ) {
            FloatingGPayPillBadge(bobOffset = if (reducedMotion) 0f else bob)
            ComicActionTicks(
                angleDegrees = 135f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopStart)
                    .offset(x = (-16).dp, y = (-12).dp)
            )
        }

        // Top Right: ₹500 Green Bill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-4).dp, y = 24.dp)
        ) {
            FloatingRupeeBill(
                denomination = "500",
                primaryColor = Color(0xFF6EC68E),
                watermarkColor = Color(0xFF4FA870),
                tiltAngle = 10f,
                bobOffset = if (reducedMotion) 0f else -bob
            )
            ComicActionTicks(
                angleDegrees = 45f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = (-12).dp)
            )
        }

        // Bottom Left: Emerald Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 10.dp, y = (-8).dp)
        ) {
            FloatingCoinComic(
                symbolColor = PureWhite,
                coinColor = Color(0xFF059669),
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else bob,
                wobbleRotation = -10f
            )
            ComicActionTicks(
                angleDegrees = 225f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomStart)
                    .offset(x = (-16).dp, y = 14.dp)
            )
        }

        // Bottom Right: Purple Rupee Coin
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-10).dp, y = (-4).dp)
        ) {
            FloatingCoinComic(
                symbolColor = PureWhite,
                coinColor = Color(0xFF7C5CFC),
                sizeDp = 50,
                wobbleOffset = if (reducedMotion) 0f else -bob,
                wobbleRotation = 10f
            )
            ComicActionTicks(
                angleDegrees = 315f,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 16.dp, y = 14.dp)
            )
        }
    }
}

@Composable
private fun Slide5SecurityRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = color,
        border = BorderStroke(1.8.dp, InkBlack)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(PureWhite)
                    .border(1.5.dp, InkBlack, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = InkBlack, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, style = CondensedCardTitleStyle.copy(fontSize = 12.sp, fontWeight = FontWeight.Black), color = InkBlack)
                Text(subtitle, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = InkBlack.copy(alpha = 0.7f))
            }
        }
    }
}
