package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Fast, tactile Neo-Brutalist 3D Brand Reveal Overlay & WhatsApp-Style Exit Transition.
 * Displays for ~350ms while initial Room database & DataStore preferences load,
 * then smoothly scales up (0.96f -> 1.04f) and fades out (1f -> 0f over 220ms) to reveal Home.
 */
@Composable
fun SnapSpendSplashOverlay(
    isReady: Boolean,
    onDismissed: () -> Unit
) {
    var animateExit by remember { mutableStateOf(false) }

    LaunchedEffect(isReady) {
        if (isReady) {
            animateExit = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (animateExit) 0f else 1f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "splash_alpha",
        finishedListener = {
            if (animateExit) {
                onDismissed()
            }
        }
    )

    val scale by animateFloatAsState(
        targetValue = if (animateExit) 1.05f else 0.98f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )

    if (alpha <= 0f && animateExit) {
        return
    }

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF1C1917) else Color(0xFFF7EBE1)
    val textColor = if (isDark) Color(0xFFFAFAF9) else Color(0xFF111111)
    val pillBg = if (isDark) Color(0xFF292524) else Color(0xFFFFFFFF)
    val pillBorder = if (isDark) Color(0xFF44403C) else Color(0xFF111111)

    val customFontFamily = remember {
        try {
            FontFamily(Font(R.font.barlow_condensed_800))
        } catch (_: Exception) {
            FontFamily.SansSerif
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            }
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Tactile Neo-Brutalist 3D Logo Card (88.dp rounded square, Sunny Yellow #FFD23F)
            Box(
                modifier = Modifier.size(88.dp)
            ) {
                // Hard 5.dp Offset Shadow
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = 5.dp)
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF111111))
                )

                // Sunny Yellow Card Surface with 2.5.dp solid black border
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFFFFD23F))
                        .border(2.5.dp, Color(0xFF111111), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.ic_snapspend_splash_logo),
                        contentDescription = "SnapSpend Logo",
                        modifier = Modifier.size(68.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bold Geometric Typography "SNAPSPEND"
            Text(
                text = "SNAPSPEND",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = customFontFamily,
                color = textColor,
                letterSpacing = 2.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "FAST · LOCAL · PRIVATE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color(0xFFA8A29E) else Color(0xFF57534E),
                letterSpacing = 1.5.sp
            )
        }

        // Bottom Pill Badge with Mint #9FE3C0 Dot
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 44.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(pillBg)
                .border(1.5.dp, pillBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF9FE3C0))
                        .border(1.dp, Color(0xFF111111), CircleShape)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "Saved on your phone",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}
