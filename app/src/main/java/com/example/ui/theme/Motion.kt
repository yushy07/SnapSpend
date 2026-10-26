package com.example.ui.theme

import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.util.MoneyUtils
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * CompositionLocal indicating whether Android's system "Remove animations"
 * (Settings.Global.ANIMATOR_DURATION_SCALE == 0f) accessibility setting is active.
 */
val LocalReducedMotion = compositionLocalOf { false }

/**
 * Centralized motion tokens, durations, easing curves, spring specifications,
 * and reusable modifiers for the entire SnapSpend app.
 */
object Motion {
    // 0. Shared Durations (ms)
    const val QUICK_MS: Int = 120          // Quick (taps, presses, icon swaps): 100-150ms
    const val NORMAL_MS: Int = 300         // Normal (cards, sheets, tabs, theme cross-fade): 250-350ms
    const val FAST_EXIT_MS: Int = 180      // Fast exit/dismiss for dialogs & outgoing layers
    const val CHART_MS: Int = 600          // Charts & money counters: 500-700ms
    const val HIGHLIGHT_FLASH_MS: Int = 650

    // Stagger & Loop Timings (ms)
    const val STAGGER_ITEM_MS: Int = 50    // 50ms delay per card on first screen entrance
    const val MAX_STAGGER_ITEMS: Int = 8   // Only animate the first 8 visible items
    const val STAGGER_SHEET_MS: Int = 40   // 40ms delay per option card in Quick Add sheet
    const val STAGGER_BAR_MS: Int = 30     // 30ms delay per bar in bar charts
    const val BOB_CYCLE_MS: Int = 3000     // 3-second gentle vertical bobbing loop for empty states
    const val STATUS_CYCLE_MS: Long = 1200L // 1.2s per status message during AI scanning

    // Shared Distances
    val TAB_SLIDE_DISTANCE: Dp = 24.dp
    val ENTRANCE_SLIDE_DISTANCE: Dp = 24.dp
    val EMPTY_BOB_AMPLITUDE: Dp = 4.dp
    val BOB_AMPLITUDE: Dp = 4.dp

    // Shared Easing Curves
    val StandardEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseOutCounter: Easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
    val QuickEasing: Easing = FastOutSlowInEasing
    val FastExitEasing: Easing = FastOutLinearInEasing
    val GentleOutEasing: Easing = LinearOutSlowInEasing

    // Shared Spring Constants (matches Categories 3D card-deck spring feel)
    const val PHYSICAL_DAMPING: Float = Spring.DampingRatioMediumBouncy // 0.5f medium bounce
    const val PHYSICAL_STIFFNESS: Float = Spring.StiffnessMediumLow     // 300f smooth physical travel

    const val PRESS_DAMPING: Float = 0.65f
    const val PRESS_STIFFNESS: Float = 650f

    const val POP_DAMPING: Float = 0.52f
    const val POP_STIFFNESS: Float = 480f

    fun duration(baseMs: Int, reducedMotion: Boolean): Int =
        if (reducedMotion) 0 else baseMs

    fun <T> physicalSpring(
        reducedMotion: Boolean = false,
        visibilityThreshold: T? = null
    ): SpringSpec<T> = if (reducedMotion) {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
            visibilityThreshold = visibilityThreshold
        )
    } else {
        spring(
            dampingRatio = PHYSICAL_DAMPING,
            stiffness = PHYSICAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )
    }

    fun <T> pressSpring(
        reducedMotion: Boolean = false,
        visibilityThreshold: T? = null
    ): SpringSpec<T> = if (reducedMotion) {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
            visibilityThreshold = visibilityThreshold
        )
    } else {
        spring(
            dampingRatio = PRESS_DAMPING,
            stiffness = PRESS_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )
    }

    fun <T> popSpring(
        reducedMotion: Boolean = false,
        visibilityThreshold: T? = null
    ): SpringSpec<T> = if (reducedMotion) {
        spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessHigh,
            visibilityThreshold = visibilityThreshold
        )
    } else {
        spring(
            dampingRatio = POP_DAMPING,
            stiffness = POP_STIFFNESS,
            visibilityThreshold = visibilityThreshold
        )
    }

    fun <T> normalTween(
        reducedMotion: Boolean = false,
        delayMillis: Int = 0,
        easing: Easing = StandardEasing
    ): TweenSpec<T> = tween(
        durationMillis = if (reducedMotion) 0 else NORMAL_MS,
        delayMillis = if (reducedMotion) 0 else delayMillis,
        easing = easing
    )

    fun <T> quickTween(
        reducedMotion: Boolean = false,
        delayMillis: Int = 0,
        easing: Easing = QuickEasing
    ): TweenSpec<T> = tween(
        durationMillis = if (reducedMotion) 0 else QUICK_MS,
        delayMillis = if (reducedMotion) 0 else delayMillis,
        easing = easing
    )

    fun <T> chartTween(
        reducedMotion: Boolean = false,
        delayMillis: Int = 0,
        easing: Easing = EaseOutCounter
    ): TweenSpec<T> = tween(
        durationMillis = if (reducedMotion) 0 else CHART_MS,
        delayMillis = if (reducedMotion) 0 else delayMillis,
        easing = easing
    )

    fun <T> fastExitTween(
        reducedMotion: Boolean = false,
        delayMillis: Int = 0,
        easing: Easing = FastExitEasing
    ): TweenSpec<T> = tween(
        durationMillis = if (reducedMotion) 0 else FAST_EXIT_MS,
        delayMillis = if (reducedMotion) 0 else delayMillis,
        easing = easing
    )

    fun normalIntOffsetTween(
        reducedMotion: Boolean = false,
        delayMillis: Int = 0,
        easing: Easing = StandardEasing
    ): TweenSpec<IntOffset> = tween(
        durationMillis = if (reducedMotion) 0 else NORMAL_MS,
        delayMillis = if (reducedMotion) 0 else delayMillis,
        easing = easing
    )

    fun quickIntOffsetTween(
        reducedMotion: Boolean = false,
        delayMillis: Int = 0,
        easing: Easing = QuickEasing
    ): TweenSpec<IntOffset> = tween(
        durationMillis = if (reducedMotion) 0 else QUICK_MS,
        delayMillis = if (reducedMotion) 0 else delayMillis,
        easing = easing
    )

    fun physicalIntSizeSpring(
        reducedMotion: Boolean = false
    ): SpringSpec<IntSize> = physicalSpring(
        reducedMotion = reducedMotion,
        visibilityThreshold = IntSize(1, 1)
    )

    fun physicalIntOffsetSpring(
        reducedMotion: Boolean = false
    ): SpringSpec<IntOffset> = physicalSpring(
        reducedMotion = reducedMotion,
        visibilityThreshold = IntOffset(1, 1)
    )

    /**
     * Light tactile haptic tick for main buttons, tabs, segmented toggles, and filter chips.
     */
    fun performLightTapHaptic(view: View) {
        runCatching {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }
}

/**
 * Reads Android's global ANIMATOR_DURATION_SCALE to respect the system "Remove animations" setting.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        }.getOrDefault(false)
    }
}

/**
 * Tracks which screens have already played their initial staggered entrance in this session,
 * so cards only animate the first time a screen opens and never on subsequent scrolls.
 */
object FirstOpenEntranceTracker {
    private val openedScreens = mutableSetOf<String>()

    fun hasPlayed(screenKey: String): Boolean = synchronized(openedScreens) {
        openedScreens.contains(screenKey)
    }

    fun markPlayed(screenKey: String) {
        synchronized(openedScreens) {
            openedScreens.add(screenKey)
        }
    }
}

/**
 * Marks a screen's first-open entrance as completed once the initial composition settles
 * so subsequent scrolls or recompositions never re-trigger the entrance animation.
 */
@Composable
fun MarkScreenEntrancePlayedEffect(screenKey: String) {
    val reducedMotion = LocalReducedMotion.current
    LaunchedEffect(screenKey, reducedMotion) {
        if (reducedMotion) {
            FirstOpenEntranceTracker.markPlayed(screenKey)
        } else if (!FirstOpenEntranceTracker.hasPlayed(screenKey)) {
            delay((Motion.NORMAL_MS + Motion.STAGGER_ITEM_MS * Motion.MAX_STAGGER_ITEMS).toLong())
            FirstOpenEntranceTracker.markPlayed(screenKey)
        }
    }
}

/**
 * 2. SCREEN ENTRANCE (only the first time a screen opens, not on every scroll):
 * Cards fade in and slide up 24.dp one after another with a 50ms delay each.
 * Only animates the first 8 visible items (`index < 8`) so long lists never lag.
 */
fun Modifier.staggeredScreenEntrance(
    screenKey: String,
    index: Int
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val alreadyPlayed = remember(screenKey) {
        FirstOpenEntranceTracker.hasPlayed(screenKey)
    }
    val shouldAnimate = !reducedMotion && !alreadyPlayed && index in 0 until Motion.MAX_STAGGER_ITEMS
    val slidePx = with(LocalDensity.current) { Motion.ENTRANCE_SLIDE_DISTANCE.toPx() }

    val progress = remember(screenKey, index) {
        Animatable(if (shouldAnimate) 0f else 1f)
    }

    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate && progress.value < 1f) {
            delay((index * Motion.STAGGER_ITEM_MS).toLong())
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = Motion.NORMAL_MS,
                    easing = Motion.StandardEasing
                )
            )
            if (index == 0) {
                // Ensure the screen is marked after the first wave completes
                delay((Motion.STAGGER_ITEM_MS * (Motion.MAX_STAGGER_ITEMS - 1)).toLong())
                FirstOpenEntranceTracker.markPlayed(screenKey)
            }
        } else {
            progress.snapTo(1f)
        }
    }

    this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * slidePx
    }
}

/**
 * Staggered entrance modifier for transient overlays such as QuickAddSourceBottomSheet option cards
 * or AI review draft cards (always staggers when the sheet/overlay opens).
 */
fun Modifier.staggeredItemReveal(
    index: Int,
    staggerMs: Int = Motion.STAGGER_SHEET_MS,
    maxAnimatedItems: Int = Motion.MAX_STAGGER_ITEMS,
    slideDistance: Dp = 20.dp
): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val shouldAnimate = !reducedMotion && index in 0 until maxAnimatedItems
    val slidePx = with(LocalDensity.current) { slideDistance.toPx() }
    val progress = remember(index) { Animatable(if (shouldAnimate) 0f else 1f) }

    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            delay((index * staggerMs).toLong())
            progress.animateTo(
                targetValue = 1f,
                animationSpec = Motion.physicalSpring(reducedMotion = false)
            )
        } else {
            progress.snapTo(1f)
        }
    }

    this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1.15f)
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * slidePx
    }
}

/**
 * 7. DIALOG SCALE & FADE:
 * Scales dialog content from 90% to 100% and fades in over ~250-300ms.
 */
fun Modifier.dialogScaleFadeEntrance(): Modifier = composed {
    val reducedMotion = LocalReducedMotion.current
    val progress = remember { Animatable(if (reducedMotion) 1f else 0f) }

    LaunchedEffect(reducedMotion) {
        if (!reducedMotion) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 260,
                    easing = Motion.StandardEasing
                )
            )
        } else {
            progress.snapTo(1f)
        }
    }

    this.graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        val scale = 0.90f + 0.10f * p
        scaleX = scale
        scaleY = scale
        alpha = p
    }
}

/**
 * 4. NUMBERS (Money totals count-up):
 * Smoothly counts from the previous paise amount to [targetPaise] over 600ms ease-out.
 * Guarantees that the final value shown at rest is the exact real amount in paise.
 */
@Composable
fun rememberAnimatedPaise(
    targetPaise: Long,
    includeSignForNegative: Boolean = false
): String {
    val reducedMotion = LocalReducedMotion.current
    var previousTarget by remember { mutableLongStateOf(0L) }
    var displayedPaise by remember { mutableLongStateOf(if (reducedMotion) targetPaise else 0L) }
    var isSettled by remember { mutableStateOf(reducedMotion) }

    LaunchedEffect(targetPaise, reducedMotion) {
        if (reducedMotion || previousTarget == targetPaise && isSettled) {
            displayedPaise = targetPaise
            previousTarget = targetPaise
            isSettled = true
            return@LaunchedEffect
        }

        val startValue = if (isSettled) previousTarget else displayedPaise
        previousTarget = targetPaise
        if (startValue == targetPaise) {
            displayedPaise = targetPaise
            isSettled = true
            return@LaunchedEffect
        }

        isSettled = false
        val anim = Animatable(0f)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = Motion.CHART_MS,
                easing = Motion.EaseOutCounter
            )
        ) {
            val f = value.coerceIn(0f, 1f)
            displayedPaise = startValue + ((targetPaise - startValue) * f).roundToLong()
        }
        displayedPaise = targetPaise
        isSettled = true
    }

    val exactOrAnimated = if (isSettled || reducedMotion) targetPaise else displayedPaise
    val prefix = if (includeSignForNegative && exactOrAnimated < 0L) "-" else ""
    return prefix + MoneyUtils.formatPaise(abs(exactOrAnimated))
}

/**
 * Tracks transaction IDs that were already visible when the session started so only newly
 * added transactions slide in from the top and flash a warm highlight.
 */
object NewTransactionHighlightTracker {
    private var initialized = false
    private val knownTransactionIds = mutableSetOf<Long>()
    private val recentlyAddedIds = mutableSetOf<Long>()

    fun syncTransactions(ids: List<Long>) {
        synchronized(knownTransactionIds) {
            if (!initialized) {
                knownTransactionIds.addAll(ids)
                initialized = true
            } else {
                ids.forEach { id ->
                    if (id != 0L && !knownTransactionIds.contains(id)) {
                        knownTransactionIds.add(id)
                        recentlyAddedIds.add(id)
                    }
                }
            }
        }
    }

    fun consumeFlashIfNew(id: Long): Boolean = synchronized(knownTransactionIds) {
        recentlyAddedIds.remove(id)
    }
}
