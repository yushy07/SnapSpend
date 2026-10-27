package com.example.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import com.example.ui.sound.LocalRetroSoundManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionSource
import com.example.data.local.TransactionStatus
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import com.example.data.rules.ResolutionMode
import com.example.ui.ScanUiState
import com.example.ui.components.BlackCircleIconButton
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.EditorialSquiggleBackground
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.WhitePillChip
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CandyLimeGreen
import com.example.ui.theme.CandyOrange
import com.example.ui.theme.CandySkyBlue
import com.example.ui.theme.CandySunshineYellow
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.CondensedScreenTitleStyle
import com.example.ui.theme.DarkCandyCardPalette
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCardPalette
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialLavender
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.EditorialPink
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LimeAccent
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.PastelBeige
import com.example.ui.theme.PastelPink
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.dialogScaleFadeEntrance
import com.example.ui.theme.staggeredItemReveal
import com.example.util.MoneyUtils
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSourceBottomSheet(
    onDismiss: () -> Unit,
    onCameraQuickScan: () -> Unit = {},
    onPickScreenshots: () -> Unit,
    onPickPdf: () -> Unit,
    onAiQuickNote: () -> Unit,
    onLogCashPayment: () -> Unit = {},
    onTrySampleAiReceipt: () -> Unit = {},
    onManualAdd: () -> Unit
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) tokens.background else PureWhite,
        contentColor = tokens.primaryText,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "QUICK SCAN & ADD",
                style = CondensedScreenTitleStyle,
                color = tokens.primaryText
            )
            Text(
                text = "Snap a receipt with Camera, upload a screenshot or PDF, or speak/type a quick note — AI automatically reads the amount, date & category.",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = tokens.secondaryText
            )

            // 0. Quick Scan with Camera (Sunny Yellow 3D Featured Card)
            val view = LocalView.current
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredItemReveal(index = 0, staggerMs = Motion.STAGGER_SHEET_MS),
                shape = RoundedCornerShape(28.dp),
                depthX = 3.dp,
                depthY = 5.dp,
                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                enableInteractiveTilt = false
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onDismiss()
                        onCameraQuickScan()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_camera_scan_button"),
                    shape = RoundedCornerShape(28.dp),
                    color = SunnyYellow,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrightCardBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = PureWhite
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "QUICK SCAN WITH CAMERA",
                                style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                color = BrightCardBlack
                            )
                            Text(
                                text = "Snap a receipt or payment screen. AI reads the amount, date and category.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack
                            )
                        }
                    }
                }
            }

            // 1. Scan Payment Screenshot(s) (Pink #FF9EC7 3D Card with Black Text)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredItemReveal(index = 1, staggerMs = Motion.STAGGER_SHEET_MS),
                shape = RoundedCornerShape(28.dp),
                depthX = 3.dp,
                depthY = 5.dp,
                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                enableInteractiveTilt = false
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onDismiss()
                        onPickScreenshots()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_pick_screenshot_button"),
                    shape = RoundedCornerShape(28.dp),
                    color = EditorialPink,
                    contentColor = BrightCardBlack,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrightCardBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = PureWhite
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPLOAD PAYMENT SCREENSHOT(S)",
                                style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                color = BrightCardBlack
                            )
                            Text(
                                text = "Pick one or many UPI, bank or receipt screenshots at once.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack
                            )
                        }
                    }
                }
            }

            // 2. Natural-Language Text or Voice Note to Expense Draft (Coral #FF6F59 3D Card)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredItemReveal(index = 2, staggerMs = Motion.STAGGER_SHEET_MS),
                shape = RoundedCornerShape(28.dp),
                depthX = 3.dp,
                depthY = 5.dp,
                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                enableInteractiveTilt = false
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onDismiss()
                        onAiQuickNote()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_ai_quick_note_button"),
                    shape = RoundedCornerShape(28.dp),
                    color = EditorialCoral,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrightCardBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = PureWhite
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "TYPE OR SPEAK PAYMENT (AI)",
                                style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                color = BrightCardBlack
                            )
                            Text(
                                text = "Say or paste a quick note, like a bank SMS.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack
                            )
                        }
                    }
                }
            }

            // 2.5 Log Cash Payment (Mint #9FE3C0 3D Featured Cash Card)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredItemReveal(index = 2, staggerMs = Motion.STAGGER_SHEET_MS),
                shape = RoundedCornerShape(28.dp),
                depthX = 3.dp,
                depthY = 5.dp,
                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                enableInteractiveTilt = false
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onDismiss()
                        onLogCashPayment()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_smart_cash_log_button"),
                    shape = RoundedCornerShape(28.dp),
                    color = EditorialMint,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrightCardBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = PureWhite
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "LOG CASH PAYMENT",
                                    style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                    color = BrightCardBlack
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SunnyYellow,
                                    border = BorderStroke(1.4.dp, BrightCardBlack)
                                ) {
                                    Text(
                                        text = "[CASH]",
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                        color = BrightCardBlack
                                    )
                                }
                            }
                            Text(
                                text = "Type or say '40 momos, 60 auto' — AI sorts merchant & category.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack
                            )
                        }
                    }
                }
            }

            // 3. Scan Payment PDF Statement (Aqua #8FE3F0 3D Card)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredItemReveal(index = 3, staggerMs = Motion.STAGGER_SHEET_MS),
                shape = RoundedCornerShape(28.dp),
                depthX = 3.dp,
                depthY = 5.dp,
                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                enableInteractiveTilt = false
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onDismiss()
                        onPickPdf()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_pick_pdf_button"),
                    shape = RoundedCornerShape(28.dp),
                    color = EditorialAqua,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrightCardBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = PureWhite
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPLOAD PAYMENT PDF",
                                style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                color = BrightCardBlack
                            )
                            Text(
                                text = "Reads every page of a statement.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack
                            )
                        }
                    }
                }
            }

            // 4. Add by Hand (Strong Lavender #C9A0FF 3D Card)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredItemReveal(index = 4, staggerMs = Motion.STAGGER_SHEET_MS),
                shape = RoundedCornerShape(28.dp),
                depthX = 3.dp,
                depthY = 5.dp,
                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                enableInteractiveTilt = false
            ) {
                Surface(
                    onClick = {
                        Motion.performLightTapHaptic(view)
                        onDismiss()
                        onManualAdd()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sheet_manual_add_button"),
                    shape = RoundedCornerShape(28.dp),
                    color = EditorialLavender,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(BrightCardBlack),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = PureWhite
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ADD PAYMENT BY HAND",
                                style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                                color = BrightCardBlack
                            )
                            Text(
                                text = "Type in the amount yourself.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

/**
 * Privacy-preserving Natural-Language Text or Voice Note to Expense Draft Dialog.
 * Only the single typed/spoken text string is sent to Gemini — never stored DB history.
 */
@Composable
fun AiNaturalLanguageNoteDialog(
    onDismiss: () -> Unit,
    onSubmitNote: (String) -> Unit
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val palette = if (isDark) DarkCandyCardPalette else EditorialCardPalette

    val context = LocalContext.current
    var noteText by remember { mutableStateOf("") }
    var voiceStatusHint by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                noteText = if (noteText.isBlank()) spoken else "$noteText $spoken"
                voiceStatusHint = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogScaleFadeEntrance(),
        containerColor = tokens.cardSurface,
        titleContentColor = tokens.primaryText,
        textContentColor = tokens.primaryText,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VOICE OR TEXT AI DRAFT",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = tokens.primaryText
                    )
                    Text(
                        text = "Only this single note is sent to Gemini",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.secondaryText
                    )
                }
                BlackCircleIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "Speak payment note",
                    size = 44.dp,
                    onClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(
                                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(
                                    RecognizerIntent.EXTRA_PROMPT,
                                    "Say your payment details"
                                )
                            }
                            if (intent.resolveActivity(context.packageManager) != null) {
                                speechLauncher.launch(intent)
                            } else {
                                voiceStatusHint = "Speech recognition isn't available on this device — type your note below."
                            }
                        } catch (_: Exception) {
                            voiceStatusHint = "Speech recognition isn't available on this device — type your note below."
                        }
                    },
                    testTag = "ai_note_mic_button"
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Describe payment or paste bank SMS", color = tokens.secondaryText) },
                    placeholder = {
                        Text(
                            "Enter payment amount, merchant, and app or paste a transaction message",
                            color = tokens.secondaryText.copy(alpha = 0.7f)
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.primaryText,
                        unfocusedBorderColor = tokens.subtleDivider,
                        focusedTextColor = tokens.primaryText,
                        unfocusedTextColor = tokens.primaryText,
                        cursorColor = tokens.primaryText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_note_text_input")
                )

                if (voiceStatusHint != null) {
                    Text(
                        text = voiceStatusHint!!,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.primaryText
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        onSubmitNote(noteText.trim())
                        onDismiss()
                    }
                },
                enabled = noteText.isNotBlank(),
                modifier = Modifier.testTag("ai_note_submit_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) CandyLimeGreen else LimeAccent,
                    contentColor = BrightCardBlack
                ),
                shape = CircleShape,
                border = BorderStroke(1.8.dp, BrightCardBlack)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrightCardBlack, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Extract with AI", fontWeight = FontWeight.ExtraBold, color = BrightCardBlack)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Pure helper for Smart Cash Log quick amount buttons (+₹10, +₹20, +₹50, +₹100, +₹500).
 * Inserts the rupee amount into the note text, or adds to the existing numeric amount in the active segment.
 */
fun applyQuickAmountToCashNote(currentNote: String, addRupees: Int): String {
    if (addRupees <= 0) return currentNote
    val trimmed = currentNote.trim()
    if (trimmed.isEmpty()) {
        return "$addRupees "
    }
    val lastCommaIdx = currentNote.lastIndexOf(',')
    val prefix = if (lastCommaIdx >= 0) currentNote.substring(0, lastCommaIdx + 1) else ""
    val activeSegment = if (lastCommaIdx >= 0) currentNote.substring(lastCommaIdx + 1) else currentNote

    val numberRegex = Regex("""\b(\d+(?:\.\d+)?)\b""")
    val matches = numberRegex.findAll(activeSegment).toList()
    if (matches.isNotEmpty()) {
        val lastMatch = matches.last()
        val existingVal = lastMatch.value.toDoubleOrNull()
        if (existingVal != null) {
            val updatedVal = existingVal + addRupees
            val formattedVal = if (updatedVal % 1.0 == 0.0) {
                updatedVal.toLong().toString()
            } else {
                String.format(Locale.US, "%.2f", updatedVal)
            }
            val updatedSegment = activeSegment.replaceRange(lastMatch.range, formattedVal)
            return prefix + updatedSegment
        }
    }
    val separator = if (activeSegment.endsWith(" ")) "" else " "
    return prefix + activeSegment + separator + addRupees
}

/**
 * Chunky 3D "Smart Cash Log" Dialog (AI Auto-Sort for one-line or multi-item cash notes).
 */
@Composable
fun SmartCashLogDialog(
    onDismiss: () -> Unit,
    onSubmitCashNote: (String, TransactionType) -> Unit
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val context = LocalContext.current
    val view = LocalView.current
    val soundManager = LocalRetroSoundManager.current

    var noteText by remember { mutableStateOf("") }
    var selectedCashType by remember { mutableStateOf(TransactionType.EXPENSE) }
    var voiceStatusHint by remember { mutableStateOf<String?>(null) }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                noteText = if (noteText.isBlank()) spoken else "$noteText, $spoken"
                voiceStatusHint = null
            }
        }
    }

    val launchSpeechRecognizer = {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(
                    RecognizerIntent.EXTRA_PROMPT,
                    "Say cash note like '40 momos, 60 auto'"
                )
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                speechLauncher.launch(intent)
            } else {
                voiceStatusHint = "Speech recognition isn't available on this device — type your note below."
            }
        } catch (_: Exception) {
            voiceStatusHint = "Speech recognition isn't available on this device — type your note below."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogScaleFadeEntrance(),
        containerColor = tokens.cardSurface,
        titleContentColor = tokens.primaryText,
        textContentColor = tokens.primaryText,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SMART CASH LOG",
                            style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                            color = tokens.primaryText
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SunnyYellow,
                            border = BorderStroke(1.5.dp, BrightCardBlack)
                        ) {
                            Text(
                                text = "[CASH]",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                                color = BrightCardBlack
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Type or say '40 momos, 60 auto' — AI sorts merchant & category.",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.secondaryText
                    )
                }
                BlackCircleIconButton(
                    icon = Icons.Default.Mic,
                    contentDescription = "Speak cash note",
                    size = 44.dp,
                    onClick = launchSpeechRecognizer,
                    testTag = "smart_cash_mic_button"
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Rough cash note (single or multi-item)", color = tokens.secondaryText) },
                    placeholder = {
                        Text(
                            "40 momos near station, or auto 60, samosa 30",
                            color = tokens.secondaryText.copy(alpha = 0.7f)
                        )
                    },
                    trailingIcon = {
                        Surface(
                            onClick = launchSpeechRecognizer,
                            shape = CircleShape,
                            color = SunnyYellow,
                            border = BorderStroke(1.4.dp, BrightCardBlack),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Speak cash note",
                                tint = BrightCardBlack,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(18.dp)
                            )
                        }
                    },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = tokens.primaryText,
                        unfocusedBorderColor = tokens.subtleDivider,
                        focusedTextColor = tokens.primaryText,
                        unfocusedTextColor = tokens.primaryText,
                        cursorColor = tokens.primaryText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("smart_cash_note_input")
                )

                // Quick amount chips (+₹10, +₹20, +₹50, +₹100, +₹500)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick amount (+ into note):",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = tokens.primaryText
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 20, 50, 100, 500).forEach { rupees ->
                            Surface(
                                onClick = {
                                    Motion.performLightTapHaptic(view)
                                    soundManager?.playButtonBlip()
                                    noteText = applyQuickAmountToCashNote(noteText, rupees)
                                },
                                shape = CircleShape,
                                color = SunnyYellow,
                                border = BorderStroke(1.6.dp, BrightCardBlack),
                                modifier = Modifier.testTag("smart_cash_quick_add_$rupees")
                            ) {
                                Text(
                                    text = "+₹$rupees",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                    color = BrightCardBlack
                                )
                            }
                        }
                    }
                }

                // 3-way Cash Flow Toggle
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Cash direction:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = tokens.primaryText
                    )
                    val cashOptions = listOf(
                        Triple(TransactionType.EXPENSE, "Spent Cash (Expense)", "smart_cash_type_expense"),
                        Triple(TransactionType.EARNING, "Received Cash (Earning)", "smart_cash_type_earning"),
                        Triple(TransactionType.TRANSFER, "ATM Withdrawal / Wallet Top-up (Transfer)", "smart_cash_type_transfer")
                    )
                    cashOptions.forEach { (typeOption, label, tag) ->
                        val isSelected = selectedCashType == typeOption
                        Surface(
                            onClick = {
                                Motion.performLightTapHaptic(view)
                                selectedCashType = typeOption
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(tag),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) SunnyYellow else tokens.cardSurface,
                            border = BorderStroke(if (isSelected) 2.dp else 1.4.dp, if (isSelected) BrightCardBlack else tokens.subtleDivider)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
                                    ),
                                    color = if (isSelected) BrightCardBlack else tokens.primaryText,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = BrightCardBlack,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (voiceStatusHint != null) {
                    Text(
                        text = voiceStatusHint!!,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = tokens.primaryText
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (noteText.isNotBlank()) {
                        soundManager?.playButtonBlip()
                        onSubmitCashNote(noteText.trim(), selectedCashType)
                        onDismiss()
                    }
                },
                enabled = noteText.isNotBlank(),
                modifier = Modifier.testTag("smart_cash_sort_ai_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SunnyYellow,
                    contentColor = BrightCardBlack
                ),
                shape = CircleShape,
                border = BorderStroke(2.dp, BrightCardBlack)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = BrightCardBlack, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SORT WITH AI", fontWeight = FontWeight.Black, color = BrightCardBlack)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Chunky 2-way "Online | Cash" segmented pill used on AI Review Cards, Draft Edit Dialog, and Manual Add/Edit Dialog.
 */
@Composable
fun OnlineCashSegmentedPill(
    paymentMode: String,
    onModeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = "payment_mode"
) {
    val view = LocalView.current
    val isCash = paymentMode.equals("CASH", ignoreCase = true)
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = PureWhite,
        border = BorderStroke(1.8.dp, BrightCardBlack)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                onClick = {
                    if (isCash) {
                        Motion.performLightTapHaptic(view)
                        onModeChange("ONLINE")
                    }
                },
                shape = CircleShape,
                color = if (!isCash) EditorialAqua else Color.Transparent,
                border = if (!isCash) BorderStroke(1.4.dp, BrightCardBlack) else null,
                modifier = Modifier.testTag("${tagPrefix}_online")
            ) {
                Text(
                    text = "Online",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (!isCash) FontWeight.Black else FontWeight.Bold
                    ),
                    color = BrightCardBlack
                )
            }
            Surface(
                onClick = {
                    if (!isCash) {
                        Motion.performLightTapHaptic(view)
                        onModeChange("CASH")
                    }
                },
                shape = CircleShape,
                color = if (isCash) SunnyYellow else Color.Transparent,
                border = if (isCash) BorderStroke(1.4.dp, BrightCardBlack) else null,
                modifier = Modifier.testTag("${tagPrefix}_cash")
            ) {
                Text(
                    text = "Cash",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isCash) FontWeight.Black else FontWeight.Bold
                    ),
                    color = BrightCardBlack
                )
            }
        }
    }
}

/**
 * Full-screen overlay for AI Reading, Error/Unreadable states, and the Review & Confirm Deck.
 * Includes Cleaned UPI Merchant badges, interactive Itemized Receipt & Bill-Split Calculator,
 * and Queued Scan fallback.
 */
@Composable
fun AiReviewDeckOverlay(
    scanState: ScanUiState,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onQueueFailedScan: () -> Unit = {},
    onPickAnotherImage: () -> Unit,
    onUpdateDraft: (DraftTransaction, Boolean) -> Unit,
    onToggleSkip: (String) -> Unit,
    onApplyEqualSplit: (String, Int) -> Unit,
    onToggleLineItem: (String, Int) -> Unit,
    onConfirmAll: () -> Unit
) {
    BackHandler { onDismiss() }

    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val palette = if (isDark) DarkCandyCardPalette else EditorialCardPalette
    var editingDraft by remember { mutableStateOf<DraftTransaction?>(null) }

    EditorialSquiggleBackground(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // Top Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BlackCircleIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close Review",
                    onClick = onDismiss,
                    testTag = "review_back_button"
                )
                Text(
                    text = "AI Review",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.primaryText
                )
                Box(modifier = Modifier.size(48.dp))
            }

            when (scanState) {
                is ScanUiState.Idle -> Unit

                is ScanUiState.Reading -> {
                    val reducedMotion = LocalReducedMotion.current
                    val cyclingMessages = remember {
                        listOf("Reading amount…", "Finding the date…", "Picking a category…")
                    }
                    var statusIdx by remember { mutableIntStateOf(0) }
                    LaunchedEffect(scanState) {
                        while (true) {
                            delay(Motion.STATUS_CYCLE_MS)
                            statusIdx = (statusIdx + 1) % cyclingMessages.size
                        }
                    }

                    val infiniteTransition = rememberInfiniteTransition(label = "ai_scan_shimmer_and_laser")
                    val shimmerProgress by infiniteTransition.animateFloat(
                        initialValue = -0.5f,
                        targetValue = 1.5f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = if (reducedMotion) 1 else 1400, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "shimmer_sweep"
                    )
                    val laserYFraction by infiniteTransition.animateFloat(
                        initialValue = 0.08f,
                        targetValue = 0.92f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = if (reducedMotion) 1 else 1250, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "laser_scan_line"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Editorial3DLayerBox(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(32.dp),
                            depthX = 4.dp,
                            depthY = 6.dp,
                            extrusionColor = InkBlack
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(32.dp),
                                color = if (isDark) CandyLimeGreen else LimeAccent,
                                border = BorderStroke(2.dp, BrightCardBlack)
                            ) {
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    // Soft diagonal light shimmer sweep across the reading card
                                    if (!reducedMotion) {
                                        Canvas(modifier = Modifier.matchParentSize()) {
                                            val startX = size.width * shimmerProgress
                                            drawRect(
                                                brush = Brush.linearGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        PureWhite.copy(alpha = 0.36f),
                                                        Color.Transparent
                                                    ),
                                                    start = Offset(startX - size.width * 0.35f, 0f),
                                                    end = Offset(startX + size.width * 0.35f, size.height)
                                                )
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.padding(28.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        // Receipt preview box with horizontal laser scanning line moving up and down
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(110.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(PureWhite)
                                                .border(1.8.dp, BrightCardBlack, RoundedCornerShape(20.dp))
                                        ) {
                                            Canvas(modifier = Modifier.fillMaxSize()) {
                                                val padX = 20.dp.toPx()
                                                val lineH = 8.dp.toPx()
                                                // Skeleton receipt placeholder lines
                                                drawRoundRect(
                                                    color = InkBlack.copy(alpha = 0.14f),
                                                    topLeft = Offset(padX, size.height * 0.22f),
                                                    size = Size(size.width * 0.46f, lineH),
                                                    cornerRadius = CornerRadius(6f, 6f)
                                                )
                                                drawRoundRect(
                                                    color = InkBlack.copy(alpha = 0.10f),
                                                    topLeft = Offset(padX, size.height * 0.46f),
                                                    size = Size(size.width * 0.72f, lineH),
                                                    cornerRadius = CornerRadius(6f, 6f)
                                                )
                                                drawRoundRect(
                                                    color = InkBlack.copy(alpha = 0.14f),
                                                    topLeft = Offset(padX, size.height * 0.70f),
                                                    size = Size(size.width * 0.34f, lineH),
                                                    cornerRadius = CornerRadius(6f, 6f)
                                                )

                                                // Moving horizontal scanning line
                                                val scanY = size.height * (if (reducedMotion) 0.5f else laserYFraction)
                                                drawRect(
                                                    brush = Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            EditorialCoral.copy(alpha = 0.28f),
                                                            Color.Transparent
                                                        ),
                                                        startY = scanY - 14.dp.toPx(),
                                                        endY = scanY + 14.dp.toPx()
                                                    ),
                                                    topLeft = Offset(0f, (scanY - 14.dp.toPx()).coerceAtLeast(0f)),
                                                    size = Size(size.width, 28.dp.toPx())
                                                )
                                                drawLine(
                                                    color = EditorialCoral,
                                                    start = Offset(8.dp.toPx(), scanY),
                                                    end = Offset(size.width - 8.dp.toPx(), scanY),
                                                    strokeWidth = 3.dp.toPx()
                                                )
                                            }
                                        }

                                        CircularProgressIndicator(
                                            modifier = Modifier.size(44.dp),
                                            color = BrightCardBlack,
                                            strokeWidth = 4.5.dp
                                        )

                                        // Cycling status message ("Reading amount…" -> "Finding the date…" -> "Picking a category…")
                                        AnimatedContent(
                                            targetState = cyclingMessages[statusIdx],
                                            transitionSpec = {
                                                (fadeIn(Motion.quickTween(reducedMotion)) +
                                                    slideInVertically(Motion.quickIntOffsetTween(reducedMotion)) { it / 2 })
                                                    .togetherWith(
                                                        fadeOut(Motion.quickTween(reducedMotion)) +
                                                            slideOutVertically(Motion.quickIntOffsetTween(reducedMotion)) { -it / 2 }
                                                    )
                                            },
                                            label = "ai_scan_status_cycle"
                                        ) { cyclingLabel ->
                                            Text(
                                                text = cyclingLabel,
                                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                                color = BrightCardBlack,
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        Text(
                                            text = scanState.statusText,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = BrightCardBlack.copy(alpha = 0.78f),
                                            textAlign = TextAlign.Center
                                        )

                                        StatusPillBadge(
                                            text = "Step ${scanState.currentStep} of ${scanState.totalSteps} · Parallel AI Scan",
                                            backgroundColor = PureWhite
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is ScanUiState.Unreadable -> {
                    val reducedMotion = LocalReducedMotion.current
                    val shakeOffset = remember { Animatable(0f) }
                    LaunchedEffect(scanState) {
                        if (!reducedMotion) {
                            shakeOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 360
                                    0f at 0
                                    (-12f) at 50
                                    12f at 110
                                    (-8f) at 170
                                    8f at 230
                                    (-4f) at 290
                                    0f at 360
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = shakeOffset.value.dp.toPx()
                                },
                            shape = RoundedCornerShape(32.dp),
                            color = if (isDark) CandyOrange else PastelPink,
                            border = BorderStroke(2.dp, BrightCardBlack)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Couldn't read this",
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                                    color = BrightCardBlack
                                )
                                Text(
                                    text = scanState.reason,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = BrightCardBlack
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    WhitePillChip(
                                        label = "Pick another file",
                                        selected = true,
                                        onClick = onPickAnotherImage
                                    )
                                    WhitePillChip(
                                        label = "Try again",
                                        selected = false,
                                        leadingIcon = Icons.Default.Refresh,
                                        onClick = onRetry
                                    )
                                }
                            }
                        }
                    }
                }

                is ScanUiState.Error -> {
                    val reducedMotion = LocalReducedMotion.current
                    val shakeOffset = remember { Animatable(0f) }
                    LaunchedEffect(scanState) {
                        if (!reducedMotion) {
                            shakeOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = keyframes {
                                    durationMillis = 360
                                    0f at 0
                                    (-12f) at 50
                                    12f at 110
                                    (-8f) at 170
                                    8f at 230
                                    (-4f) at 290
                                    0f at 360
                                }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    translationX = shakeOffset.value.dp.toPx()
                                },
                            shape = RoundedCornerShape(32.dp),
                            color = if (isDark) CandySunshineYellow else WarningAmber,
                            border = BorderStroke(2.dp, BrightCardBlack)
                        ) {
                            Column(
                                modifier = Modifier.padding(28.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Connection hiccup",
                                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Black),
                                    color = BrightCardBlack
                                )
                                Text(
                                    text = scanState.message,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = BrightCardBlack
                                )
                                Text(
                                    text = "Your picked file is still safe — tap Try Again now, or Queue for Later to keep working.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = BrightCardBlack
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    WhitePillChip(
                                        label = "Try again",
                                        selected = true,
                                        leadingIcon = Icons.Default.Refresh,
                                        onClick = onRetry,
                                        testTag = "review_retry_button"
                                    )
                                    WhitePillChip(
                                        label = "Queue for later",
                                        selected = false,
                                        leadingIcon = Icons.Default.Schedule,
                                        onClick = onQueueFailedScan,
                                        testTag = "review_queue_button"
                                    )
                                    WhitePillChip(
                                        label = "Cancel",
                                        selected = false,
                                        onClick = onDismiss
                                    )
                                }
                            }
                        }
                    }
                }

                is ScanUiState.ReviewReady -> {
                    val drafts = scanState.drafts
                    val activeCount = drafts.count { !it.isSkipped }
                    val reducedMotion = LocalReducedMotion.current
                    val view = LocalView.current
                    val coroutineScope = rememberCoroutineScope()
                    val confirmPopScale = remember { Animatable(1f) }
                    var isConfirmAnimating by remember { mutableStateOf(false) }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column {
                                Text(
                                    text = "Check & confirm",
                                    style = if (isDark) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                                    color = tokens.primaryText
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap any card to edit details, or split bills and itemized receipts right on the card.",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = tokens.secondaryText
                                )
                            }
                        }

                        itemsIndexed(drafts, key = { _, d -> d.draftId }) { idx, draft ->
                            val baseColor = when {
                                draft.isLowConfidence -> if (isDark) CandySunshineYellow else WarningAmber
                                draft.resolutionMode == ResolutionMode.UPDATE_PENDING_TO_FINAL -> if (isDark) CandyLimeGreen else LimeAccent
                                draft.isSkipped -> if (isDark) CandySkyBlue else PastelBeige
                                else -> palette[idx % palette.size]
                            }

                            Editorial3DLayerBox(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .staggeredItemReveal(index = idx, staggerMs = Motion.STAGGER_ITEM_MS)
                                    .alpha(if (draft.isSkipped) 0.65f else 1f),
                                shape = RoundedCornerShape(28.dp),
                                depthX = 4.dp,
                                depthY = 6.dp,
                                extrusionColor = if (isDark) BrightCardBlack else InkBlack,
                                enableInteractiveTilt = false
                            ) {
                                Surface(
                                    onClick = { editingDraft = draft },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("review_draft_card_$idx"),
                                    shape = RoundedCornerShape(28.dp),
                                    color = baseColor,
                                    contentColor = BrightCardBlack,
                                    border = BorderStroke(1.8.dp, BrightCardBlack)
                                ) {
                                    Column(modifier = Modifier.padding(20.dp)) {
                                        // Top Badges Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                StatusPillBadge(
                                                    text = "Gemini Parsed · ${draft.source.name}",
                                                    backgroundColor = PureWhite
                                                )
                                                if (draft.paymentMode.equals("CASH", ignoreCase = true)) {
                                                    StatusPillBadge(
                                                        text = "[CASH]",
                                                        backgroundColor = SunnyYellow
                                                    )
                                                }
                                                if (draft.isLowConfidence) {
                                                    StatusPillBadge(
                                                        text = "Low confidence · Check me",
                                                        backgroundColor = PureWhite
                                                    )
                                                }
                                                if (!draft.rawMerchantText.isNullOrBlank() &&
                                                    !draft.rawMerchantText.equals(draft.merchant, ignoreCase = true)
                                                ) {
                                                    StatusPillBadge(
                                                        text = "Cleaned UPI name",
                                                        backgroundColor = PureWhite
                                                    )
                                                }
                                                when (draft.resolutionMode) {
                                                    ResolutionMode.UPDATE_PENDING_TO_FINAL -> {
                                                        StatusPillBadge(
                                                            text = "Updates Pending → ${draft.status.name}",
                                                            backgroundColor = PureWhite
                                                        )
                                                    }
                                                    ResolutionMode.SKIP_DUPLICATE -> {
                                                        StatusPillBadge(
                                                            text = if (draft.isSkipped) "Duplicate · Skipped" else "Duplicate · Included",
                                                            backgroundColor = PureWhite
                                                        )
                                                    }
                                                    ResolutionMode.INSERT_NEW -> {
                                                        if (draft.appliedMerchantMemory) {
                                                            StatusPillBadge(
                                                                text = "Learned Category",
                                                                backgroundColor = PureWhite
                                                            )
                                                        }
                                                    }
                                                }
                                                if (draft.status != TransactionStatus.SUCCESS) {
                                                    StatusPillBadge(
                                                        text = "${draft.status.name} (Not counted in totals)",
                                                        backgroundColor = PureWhite
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            WhitePillChip(
                                                label = if (draft.isSkipped) "Include" else "Skip",
                                                selected = !draft.isSkipped,
                                                onClick = { onToggleSkip(draft.draftId) }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Main Merchant + Amount Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = draft.merchant,
                                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                                                    color = BrightCardBlack
                                                )
                                                if (!draft.rawMerchantText.isNullOrBlank() &&
                                                    !draft.rawMerchantText.equals(draft.merchant, ignoreCase = true)
                                                ) {
                                                    Text(
                                                        text = "Raw: ${draft.rawMerchantText}",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                        color = BrightCardBlack.copy(alpha = 0.72f)
                                                    )
                                                }
                                                Text(
                                                    text = "${draft.category} · ${draft.type.name} · ${draft.paymentApp}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = BrightCardBlack
                                                )
                                                Text(
                                                    text = buildString {
                                                        append(MoneyUtils.formatShortDateTime(draft.timestamp, draft.hasExplicitTime))
                                                        if (!draft.referenceNumber.isNullOrBlank()) {
                                                            append(" · Ref: ${draft.referenceNumber}")
                                                        }
                                                    },
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                                    color = BrightCardBlack.copy(alpha = 0.8f)
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    text = MoneyUtils.formatPaise(draft.amountPaise),
                                                    style = MaterialTheme.typography.headlineLarge.copy(
                                                        fontWeight = FontWeight.Black,
                                                        fontFeatureSettings = "tnum"
                                                    ),
                                                    color = BrightCardBlack
                                                )
                                                if (draft.originalAmountPaise > 0L && draft.amountPaise != draft.originalAmountPaise) {
                                                    Text(
                                                        text = "of ${MoneyUtils.formatPaise(draft.originalAmountPaise)}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        color = BrightCardBlack.copy(alpha = 0.78f)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Gemini Auto-Parsed Amount, Date & Category Highlight Strip
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            color = BrightCardBlack.copy(alpha = 0.08f),
                                            border = BorderStroke(1.2.dp, BrightCardBlack.copy(alpha = 0.25f))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "AMOUNT",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = BrightCardBlack.copy(alpha = 0.65f)
                                                    )
                                                    Text(
                                                        text = MoneyUtils.formatPaise(draft.amountPaise),
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontFeatureSettings = "tnum"
                                                        ),
                                                        color = BrightCardBlack
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1.1f)) {
                                                    Text(
                                                        text = "DATE",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = BrightCardBlack.copy(alpha = 0.65f)
                                                    )
                                                    Text(
                                                        text = MoneyUtils.formatDayHeader(draft.timestamp),
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = BrightCardBlack
                                                    )
                                                }
                                                Column(
                                                    modifier = Modifier.weight(1.1f),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    Text(
                                                        text = "CATEGORY",
                                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = BrightCardBlack.copy(alpha = 0.65f)
                                                    )
                                                    Text(
                                                        text = draft.category,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                        color = BrightCardBlack
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Online | Cash Segmented Pill on each AI Review Card
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Payment mode:",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                                color = BrightCardBlack
                                            )
                                            OnlineCashSegmentedPill(
                                                paymentMode = draft.paymentMode,
                                                onModeChange = { newMode ->
                                                    val updatedApp = if (newMode == "CASH") {
                                                        if (draft.paymentApp.isBlank() || draft.paymentApp.equals("Unknown", ignoreCase = true)) "Cash" else draft.paymentApp
                                                    } else {
                                                        if (draft.paymentApp.equals("Cash", ignoreCase = true)) "GPay" else draft.paymentApp
                                                    }
                                                    onUpdateDraft(
                                                        draft.copy(
                                                            paymentMode = newMode,
                                                            paymentApp = updatedApp
                                                        ),
                                                        false
                                                    )
                                                },
                                                tagPrefix = "review_card_${idx}_mode"
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Interactive Bill-Split & Itemized Receipt Calculator (Long Paise Math)
                                        Surface(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(20.dp),
                                            color = PureWhite.copy(alpha = 0.92f),
                                            border = BorderStroke(1.5.dp, BrightCardBlack)
                                        ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Bill split",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                                    color = BrightCardBlack
                                                )
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf(1 to "Full", 2 to "÷ 2", 3 to "÷ 3", 4 to "÷ 4").forEach { (ways, label) ->
                                                        WhitePillChip(
                                                            label = label,
                                                            selected = draft.splitWays == ways,
                                                            onClick = { onApplyEqualSplit(draft.draftId, ways) },
                                                            testTag = "split_${ways}_chip_$idx"
                                                        )
                                                    }
                                                }
                                            }

                                            // Itemized Receipt Lines if extracted by Gemini
                                            if (draft.lineItems.isNotEmpty()) {
                                                Text(
                                                    text = "Receipt items (tap to include/exclude your share):",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = BrightCardBlack.copy(alpha = 0.78f)
                                                )
                                                draft.lineItems.forEachIndexed { itemIdx, lineItem ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(
                                                                if (lineItem.isSelectedForSplit) CandyLimeGreen.copy(alpha = 0.45f)
                                                                else PastelBeige.copy(alpha = 0.5f)
                                                            )
                                                            .clickable {
                                                                onToggleLineItem(draft.draftId, itemIdx)
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier.weight(1f)
                                                        ) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(20.dp)
                                                                    .clip(CircleShape)
                                                                    .background(if (lineItem.isSelectedForSplit) BrightCardBlack else PureWhite),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                if (lineItem.isSelectedForSplit) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Check,
                                                                        contentDescription = null,
                                                                        tint = PureWhite,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                }
                                                            }
                                                            Text(
                                                                text = if (lineItem.quantity > 1) "${lineItem.quantity}x ${lineItem.itemName}" else lineItem.itemName,
                                                                style = MaterialTheme.typography.bodySmall.copy(
                                                                    fontWeight = FontWeight.Bold,
                                                                    textDecoration = if (lineItem.isSelectedForSplit) TextDecoration.None else TextDecoration.LineThrough
                                                                ),
                                                                color = BrightCardBlack
                                                            )
                                                        }
                                                        Text(
                                                            text = MoneyUtils.formatPaise(lineItem.pricePaise),
                                                            style = MaterialTheme.typography.labelMedium.copy(
                                                                fontWeight = FontWeight.Black,
                                                                fontFeatureSettings = "tnum"
                                                            ),
                                                            color = BrightCardBlack
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Quick 1-Tap Category Switcher Strip on Review Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val relevantCats = categories.filter {
                                            if (draft.type == TransactionType.EARNING) it.type == CategoryType.EARNING
                                            else it.type == CategoryType.EXPENSE
                                        }
                                        relevantCats.forEach { cat ->
                                            WhitePillChip(
                                                label = cat.name,
                                                selected = draft.category == cat.name,
                                                onClick = {
                                                    onUpdateDraft(
                                                        draft.copy(category = cat.name),
                                                        true
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        }
                    }

                    // Bottom Confirm All Bar
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = tokens.cardSurface,
                        border = BorderStroke(if (isDark) 1.dp else 2.dp, if (isDark) tokens.subtleDivider else InkBlack)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "$activeCount of ${drafts.size} selected",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = tokens.primaryText
                                )
                                Text(
                                    text = "Duplicates are skipped automatically",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.secondaryText
                                )
                            }

                            Button(
                                onClick = {
                                    if (!isConfirmAnimating) {
                                        Motion.performLightTapHaptic(view)
                                        if (reducedMotion) {
                                            onConfirmAll()
                                        } else {
                                            isConfirmAnimating = true
                                            coroutineScope.launch {
                                                confirmPopScale.animateTo(
                                                    targetValue = 1.22f,
                                                    animationSpec = tween(durationMillis = 100, easing = FastOutSlowInEasing)
                                                )
                                                confirmPopScale.animateTo(
                                                    targetValue = 1f,
                                                    animationSpec = Motion.popSpring(reducedMotion)
                                                )
                                                isConfirmAnimating = false
                                                onConfirmAll()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = confirmPopScale.value
                                        scaleY = confirmPopScale.value
                                    }
                                    .testTag("confirm_all_drafts_button"),
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) CandyLimeGreen else LimeAccent,
                                    contentColor = BrightCardBlack
                                ),
                                border = BorderStroke(2.dp, BrightCardBlack),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = BrightCardBlack,
                                    modifier = Modifier.graphicsLayer {
                                        scaleX = confirmPopScale.value
                                        scaleY = confirmPopScale.value
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Confirm all",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = BrightCardBlack
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Draft Detail Edit Dialog
    editingDraft?.let { draft ->
        DraftEditDialog(
            draft = draft,
            categories = categories,
            onDismiss = { editingDraft = null },
            onSave = { updated, catChanged ->
                onUpdateDraft(updated, catChanged)
                editingDraft = null
            }
        )
    }
}

@Composable
private fun DraftEditDialog(
    draft: DraftTransaction,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (DraftTransaction, Boolean) -> Unit
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark

    var merchantInput by remember(draft) { mutableStateOf(draft.merchant) }
    var amountInput by remember(draft) { mutableStateOf(MoneyUtils.paiseToEditableRupeeString(draft.amountPaise)) }
    var selectedType by remember(draft) { mutableStateOf(draft.type) }
    var selectedCategory by remember(draft) { mutableStateOf(draft.category) }
    var selectedStatus by remember(draft) { mutableStateOf(draft.status) }
    var dateInput by remember(draft) { mutableStateOf(MoneyUtils.formatIsoDate(draft.timestamp)) }
    var timeInput by remember(draft) {
        mutableStateOf(if (draft.hasExplicitTime) MoneyUtils.formatTime24h(draft.timestamp) else "")
    }
    var paymentAppInput by remember(draft) { mutableStateOf(draft.paymentApp) }
    var selectedPaymentMode by remember(draft) { mutableStateOf(draft.paymentMode) }
    var refInput by remember(draft) { mutableStateOf(draft.referenceNumber.orEmpty()) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = tokens.primaryText,
        unfocusedBorderColor = tokens.subtleDivider,
        focusedTextColor = tokens.primaryText,
        unfocusedTextColor = tokens.primaryText,
        cursorColor = tokens.primaryText
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogScaleFadeEntrance(),
        containerColor = tokens.cardSurface,
        titleContentColor = tokens.primaryText,
        textContentColor = tokens.primaryText,
        title = {
            Text(
                text = "Edit scanned payment",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = tokens.primaryText
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount in ₹ (e.g. 150.50)", color = tokens.secondaryText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = merchantInput,
                    onValueChange = { merchantInput = it },
                    label = { Text("Merchant / Person", color = tokens.secondaryText) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mode:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                    OnlineCashSegmentedPill(
                        paymentMode = selectedPaymentMode,
                        onModeChange = { newMode ->
                            selectedPaymentMode = newMode
                            if (newMode == "CASH" && (paymentAppInput.isBlank() || paymentAppInput.equals("Unknown", ignoreCase = true))) {
                                paymentAppInput = "Cash"
                            }
                        },
                        tagPrefix = "draft_edit_mode"
                    )
                }

                Text("Type:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionType.entries.forEach { t ->
                        WhitePillChip(
                            label = t.name,
                            selected = selectedType == t,
                            onClick = { selectedType = t }
                        )
                    }
                }

                Text("Category:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        WhitePillChip(
                            label = cat.name,
                            selected = selectedCategory == cat.name,
                            onClick = { selectedCategory = cat.name }
                        )
                    }
                }

                Text("Status:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionStatus.entries.forEach { st ->
                        WhitePillChip(
                            label = st.name,
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Date (YYYY-MM-DD)", color = tokens.secondaryText) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("Time (HH:mm)", color = tokens.secondaryText) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = paymentAppInput,
                    onValueChange = { paymentAppInput = it },
                    label = { Text("Payment App / Bank", color = tokens.secondaryText) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = refInput,
                    onValueChange = { refInput = it },
                    label = { Text("Reference / UTR Number", color = tokens.secondaryText) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paise = MoneyUtils.parseRupeesToPaise(amountInput) ?: draft.amountPaise
                    val (ts, hasTime) = MoneyUtils.parseDateAndTime(
                        dateInput,
                        timeInput.ifBlank { "unknown" }
                    )
                    val catChanged = selectedCategory != draft.category
                    onSave(
                        draft.copy(
                            merchant = merchantInput.trim().ifEmpty { "Unknown" },
                            amountPaise = paise,
                            type = selectedType,
                            category = selectedCategory,
                            status = selectedStatus,
                            timestamp = ts,
                            hasExplicitTime = hasTime,
                            paymentApp = paymentAppInput.trim().ifEmpty { if (selectedPaymentMode == "CASH") "Cash" else "Unknown" },
                            referenceNumber = refInput.trim().ifEmpty { null },
                            paymentMode = selectedPaymentMode
                        ),
                        catChanged
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) CandyLimeGreen else LimeAccent,
                    contentColor = BrightCardBlack
                ),
                shape = CircleShape
            ) {
                Text("Apply", fontWeight = FontWeight.ExtraBold, color = BrightCardBlack)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Dialog for manually adding a new payment or viewing/editing/deleting a saved payment.
 * Shows full payment metadata (App, UTR/Reference, Source, Date/Time, Note) on tap so cards stay clean.
 */
@Composable
fun TransactionEditorDialog(
    initialTransaction: TransactionEntity?,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (TransactionEntity, Boolean) -> Unit,
    onDelete: ((Long) -> Unit)? = null
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val isEditing = initialTransaction != null && initialTransaction.id != 0L
    val now = System.currentTimeMillis()

    var amountInput by remember(initialTransaction) {
        mutableStateOf(
            if (initialTransaction != null && initialTransaction.amountPaise > 0L) {
                MoneyUtils.paiseToEditableRupeeString(initialTransaction.amountPaise)
            } else {
                ""
            }
        )
    }
    var merchantInput by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.merchant.orEmpty())
    }
    var selectedType by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.type ?: TransactionType.EXPENSE)
    }
    var selectedCategory by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.category ?: "Food & Dining")
    }
    var selectedStatus by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.status ?: TransactionStatus.SUCCESS)
    }
    var paymentAppInput by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.paymentApp ?: "GPay")
    }
    var selectedPaymentMode by remember(initialTransaction) {
        mutableStateOf(
            if (initialTransaction?.paymentMode.equals("CASH", ignoreCase = true)) "CASH" else "ONLINE"
        )
    }
    var refInput by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.referenceNumber.orEmpty())
    }
    var noteInput by remember(initialTransaction) {
        mutableStateOf(initialTransaction?.note.orEmpty())
    }
    var dateInput by remember(initialTransaction) {
        mutableStateOf(MoneyUtils.formatIsoDate(initialTransaction?.timestamp ?: now))
    }
    var timeInput by remember(initialTransaction) {
        mutableStateOf(
            if (initialTransaction?.hasExplicitTime != false) {
                MoneyUtils.formatTime24h(initialTransaction?.timestamp ?: now)
            } else {
                ""
            }
        )
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = tokens.primaryText,
        unfocusedBorderColor = tokens.subtleDivider,
        focusedTextColor = tokens.primaryText,
        unfocusedTextColor = tokens.primaryText,
        cursorColor = tokens.primaryText
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.dialogScaleFadeEntrance(),
        containerColor = tokens.cardSurface,
        titleContentColor = tokens.primaryText,
        textContentColor = tokens.primaryText,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Payment details" else "Add payment",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.primaryText
                )
                if (isEditing && onDelete != null) {
                    BlackCircleIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = "Delete payment",
                        size = 40.dp,
                        onClick = {
                            onDelete(initialTransaction!!.id)
                            onDismiss()
                        },
                        testTag = "delete_transaction_button"
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Full Metadata Summary Card when inspecting an existing payment
                if (isEditing && initialTransaction != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = if (isDark) CandySunshineYellow else PastelBeige,
                        border = BorderStroke(1.5.dp, BrightCardBlack)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${initialTransaction.paymentApp} · ${initialTransaction.source.name}",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                color = BrightCardBlack
                            )
                            if (!initialTransaction.referenceNumber.isNullOrBlank()) {
                                Text(
                                    text = "UTR / Ref: ${initialTransaction.referenceNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = BrightCardBlack
                                )
                            }
                            if (initialTransaction.note.isNotBlank()) {
                                Text(
                                    text = "Note: ${initialTransaction.note}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = BrightCardBlack
                                )
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = BrightCardBlack,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) CandyOrange else PastelPink)
                            .padding(10.dp)
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = {
                        amountInput = it
                        errorMessage = null
                    },
                    label = { Text("Amount in ₹ (e.g. 150.50)", color = tokens.secondaryText) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_amount_input")
                )

                OutlinedTextField(
                    value = merchantInput,
                    onValueChange = {
                        merchantInput = it
                        errorMessage = null
                    },
                    label = { Text("Merchant or Person", color = tokens.secondaryText) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_merchant_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Mode:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                    OnlineCashSegmentedPill(
                        paymentMode = selectedPaymentMode,
                        onModeChange = { newMode ->
                            selectedPaymentMode = newMode
                            if (newMode == "CASH" && (paymentAppInput.equals("GPay", ignoreCase = true) || paymentAppInput.equals("Unknown", ignoreCase = true))) {
                                paymentAppInput = "Cash"
                            } else if (newMode == "ONLINE" && paymentAppInput.equals("Cash", ignoreCase = true)) {
                                paymentAppInput = "GPay"
                            }
                        },
                        tagPrefix = "manual_editor_mode"
                    )
                }

                Text("Type:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    WhitePillChip(
                        label = "Expense (Out)",
                        selected = selectedType == TransactionType.EXPENSE,
                        onClick = {
                            selectedType = TransactionType.EXPENSE
                            val firstExp = categories.firstOrNull { it.type == CategoryType.EXPENSE }?.name ?: "Food & Dining"
                            selectedCategory = firstExp
                        }
                    )
                    WhitePillChip(
                        label = "Earning (In)",
                        selected = selectedType == TransactionType.EARNING,
                        onClick = {
                            selectedType = TransactionType.EARNING
                            val firstEarn = categories.firstOrNull { it.type == CategoryType.EARNING }?.name ?: "Salary"
                            selectedCategory = firstEarn
                        }
                    )
                    WhitePillChip(
                        label = "Transfer",
                        selected = selectedType == TransactionType.TRANSFER,
                        onClick = {
                            selectedType = TransactionType.TRANSFER
                            selectedCategory = "Other"
                        }
                    )
                }

                if (selectedType != TransactionType.TRANSFER) {
                    Text("Category:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                    val filteredCats = categories.filter {
                        if (selectedType == TransactionType.EARNING) it.type == CategoryType.EARNING
                        else it.type == CategoryType.EXPENSE
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredCats.forEach { cat ->
                            WhitePillChip(
                                label = cat.name,
                                selected = selectedCategory == cat.name,
                                onClick = { selectedCategory = cat.name }
                            )
                        }
                    }
                }

                Text("Payment App:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("GPay", "PhonePe", "Paytm", "HDFC Bank", "SBI", "Cash").forEach { app ->
                        WhitePillChip(
                            label = app,
                            selected = paymentAppInput.equals(app, ignoreCase = true),
                            onClick = {
                                paymentAppInput = app
                                if (app.equals("Cash", ignoreCase = true)) {
                                    selectedPaymentMode = "CASH"
                                }
                            }
                        )
                    }
                }

                Text("Status:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionStatus.entries.forEach { st ->
                        WhitePillChip(
                            label = st.name,
                            selected = selectedStatus == st,
                            onClick = { selectedStatus = st }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Date (YYYY-MM-DD)", color = tokens.secondaryText) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it },
                        label = { Text("Time (HH:mm)", color = tokens.secondaryText) },
                        singleLine = true,
                        colors = fieldColors,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = refInput,
                    onValueChange = { refInput = it },
                    label = { Text("Reference / UTR (optional)", color = tokens.secondaryText) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Note (optional)", color = tokens.secondaryText) },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val paise = MoneyUtils.parseRupeesToPaise(amountInput)
                    if (paise == null || paise <= 0L) {
                        errorMessage = "Please enter a valid rupee amount (e.g. 150.50)."
                        return@Button
                    }
                    val trimmedMerchant = merchantInput.trim()
                    if (trimmedMerchant.isEmpty()) {
                        errorMessage = "Please enter a merchant or person name."
                        return@Button
                    }
                    val (ts, hasTime) = MoneyUtils.parseDateAndTime(
                        dateInput,
                        timeInput.ifBlank { "unknown" }
                    )
                    val cleanRef = refInput.trim().ifEmpty { null }
                    val dupKey = MoneyUtils.buildDuplicateKey(
                        referenceNumber = cleanRef,
                        timestampMillis = ts,
                        hasExplicitTime = hasTime,
                        amountPaise = paise,
                        merchant = trimmedMerchant
                    )
                    val categoryChanged = initialTransaction == null || initialTransaction.category != selectedCategory
                    onSave(
                        TransactionEntity(
                            id = initialTransaction?.id ?: 0L,
                            timestamp = ts,
                            hasExplicitTime = hasTime,
                            amountPaise = paise,
                            type = selectedType,
                            category = selectedCategory,
                            merchant = trimmedMerchant,
                            paymentApp = paymentAppInput.trim().ifEmpty { "Unknown" },
                            referenceNumber = cleanRef,
                            status = selectedStatus,
                            note = noteInput.trim(),
                            source = initialTransaction?.source ?: TransactionSource.MANUAL,
                            duplicateKey = dupKey,
                            paymentMode = selectedPaymentMode
                        ),
                        categoryChanged
                    )
                    onDismiss()
                },
                modifier = Modifier.testTag("save_manual_transaction_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) CandyLimeGreen else LimeAccent,
                    contentColor = BrightCardBlack
                ),
                shape = CircleShape
            ) {
                Text(if (isEditing) "Update" else "Save", fontWeight = FontWeight.ExtraBold, color = BrightCardBlack)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
            }
        }
    )
}
