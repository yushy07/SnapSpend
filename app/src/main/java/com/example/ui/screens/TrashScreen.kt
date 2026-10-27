package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.local.TransactionEntity
import com.example.data.local.TransactionType
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.categoryIconVector
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.EditorialPink
import com.example.ui.theme.IncomeDarkGreen
import com.example.ui.theme.InkBlack
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PalePinkTint
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.util.MoneyUtils

@Composable
fun TrashScreen(
    trashedTransactions: List<TransactionEntity>,
    onRestoreTransaction: (Long) -> Unit,
    onDeleteForeverTransaction: (Long) -> Unit,
    onEmptyTrash: () -> Unit,
    onBackClick: () -> Unit,
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackClick)

    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val colors = SnapSpendThemeTokens.colors
    var showEmptyTrashConfirmation by remember { mutableStateOf(false) }

    if (showEmptyTrashConfirmation) {
        EmptyTrashConfirmDialog(
            trashedCount = trashedTransactions.size,
            onConfirmEmpty = {
                showEmptyTrashConfirmation = false
                onEmptyTrash()
            },
            onDismiss = { showEmptyTrashConfirmation = false }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .animateContentSize(animationSpec = Motion.physicalIntSizeSpring(reducedMotion))
            .testTag("trash_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row: Back Button + Title + EMPTY TRASH button
        item(key = "trash_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Editorial3DLayerBox(
                        shape = CircleShape,
                        extrusionColor = if (colors.isDark) SunnyYellow else InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 2.5.dp,
                        depthY = 3.5.dp
                    ) {
                        Surface(
                            onClick = {
                                Motion.performLightTapHaptic(view)
                                onBackClick()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("trash_back_button"),
                            shape = CircleShape,
                            color = PureWhite,
                            contentColor = InkBlack,
                            border = BorderStroke(1.8.dp, InkBlack)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Settings",
                                    tint = InkBlack,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    Column {
                        Text(
                            text = "TRASH",
                            style = CondensedCardTitleStyle.copy(fontSize = 30.sp),
                            color = colors.primaryText
                        )
                        Text(
                            text = "Items older than 30 days are deleted for good on startup",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.secondaryText
                        )
                    }
                }

                if (trashedTransactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Editorial3DLayerBox(
                        shape = CircleShape,
                        extrusionColor = InkBlack,
                        extrusionBorderColor = InkBlack,
                        depthX = 3.dp,
                        depthY = 4.dp
                    ) {
                        Surface(
                            onClick = {
                                Motion.performLightTapHaptic(view)
                                showEmptyTrashConfirmation = true
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("empty_trash_button"),
                            shape = CircleShape,
                            color = EditorialCoral,
                            contentColor = InkBlack,
                            border = BorderStroke(1.8.dp, InkBlack)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteSweep,
                                    contentDescription = null,
                                    tint = InkBlack,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "EMPTY TRASH",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    color = InkBlack
                                )
                            }
                        }
                    }
                }
            }
        }

        if (trashedTransactions.isEmpty()) {
            item(key = "trash_empty_state") {
                Editorial3DLayerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trash_empty_card"),
                    shape = RoundedCornerShape(28.dp),
                    extrusionColor = InkBlack,
                    extrusionBorderColor = InkBlack,
                    depthX = 4.dp,
                    depthY = 6.dp
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        color = PaleMintHero,
                        contentColor = InkBlack,
                        border = BorderStroke(1.8.dp, InkBlack)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "TRASH IS EMPTY",
                                style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                                color = InkBlack
                            )
                            Text(
                                text = "Deleted payments stay here for 30 days so you can restore them anytime.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = InkBlack.copy(alpha = 0.84f)
                            )
                        }
                    }
                }
            }
        } else {
            items(
                items = trashedTransactions,
                key = { it.id }
            ) { tx ->
                TrashedPaymentCard(
                    transaction = tx,
                    onRestoreClick = {
                        Motion.performLightTapHaptic(view)
                        onRestoreTransaction(tx.id)
                    },
                    onDeleteForeverClick = {
                        Motion.performLightTapHaptic(view)
                        onDeleteForeverTransaction(tx.id)
                    }
                )
            }
        }
    }
}

@Composable
private fun TrashedPaymentCard(
    transaction: TransactionEntity,
    onRestoreClick: () -> Unit,
    onDeleteForeverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deletedAtMillis = transaction.deletedAtMillis ?: transaction.timestamp
    val deletedDateLabel = "Deleted ${MoneyUtils.formatShortDateTime(deletedAtMillis, hasExplicitTime = true)}"
    val paymentDateLabel = MoneyUtils.formatShortDateTime(transaction.timestamp, transaction.hasExplicitTime)
    val prefix = when (transaction.type) {
        TransactionType.EXPENSE -> "-"
        TransactionType.EARNING -> "+"
        TransactionType.TRANSFER -> "⇄ "
    }
    val formattedAmount = prefix + MoneyUtils.formatPaise(transaction.amountPaise)
    val amountColor = if (transaction.type == TransactionType.EARNING) IncomeDarkGreen else InkBlack
    val shape = RoundedCornerShape(28.dp)

    Editorial3DLayerBox(
        modifier = modifier
            .fillMaxWidth()
            .testTag("trash_item_${transaction.id}"),
        shape = shape,
        extrusionColor = InkBlack,
        extrusionBorderColor = InkBlack,
        depthX = 4.dp,
        depthY = 6.dp
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            color = PalePinkTint,
            contentColor = InkBlack,
            border = BorderStroke(1.8.dp, InkBlack)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
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
                                style = CondensedCardTitleStyle.copy(fontSize = 20.sp),
                                color = InkBlack,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = transaction.category,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = InkBlack.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (transaction.paymentMode.equals("CASH", ignoreCase = true)) {
                                    com.example.ui.components.CashModeBadge()
                                }
                                Text(
                                    text = "· $paymentDateLabel",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = InkBlack.copy(alpha = 0.82f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = deletedDateLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack.copy(alpha = 0.72f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = formattedAmount,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            fontFeatureSettings = "tnum"
                        ),
                        color = amountColor,
                        maxLines = 1
                    )
                }

                // Action Row: RESTORE and DELETE FOREVER buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = onRestoreClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("trash_restore_${transaction.id}"),
                        shape = CircleShape,
                        color = EditorialMint,
                        contentColor = InkBlack,
                        border = BorderStroke(1.6.dp, InkBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestoreFromTrash,
                                contentDescription = null,
                                tint = InkBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RESTORE",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack
                            )
                        }
                    }

                    Surface(
                        onClick = onDeleteForeverClick,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("trash_delete_forever_${transaction.id}"),
                        shape = CircleShape,
                        color = PureWhite,
                        contentColor = InkBlack,
                        border = BorderStroke(1.6.dp, InkBlack)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = InkBlack,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "DELETE FOREVER",
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                color = InkBlack
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyTrashConfirmDialog(
    trashedCount: Int,
    onConfirmEmpty: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("empty_trash_confirm_dialog"),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 5.dp,
            depthY = 7.dp
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = PureWhite,
                contentColor = InkBlack,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "EMPTY TRASH?",
                        style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                        color = InkBlack
                    )
                    Text(
                        text = "Permanently delete $trashedCount trashed payment${if (trashedCount == 1) "" else "s"}? This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = InkBlack
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("cancel_empty_trash_button"),
                            shape = CircleShape,
                            color = EditorialAqua,
                            contentColor = InkBlack,
                            border = BorderStroke(1.6.dp, InkBlack)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "CANCEL",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = InkBlack
                                )
                            }
                        }

                        Surface(
                            onClick = onConfirmEmpty,
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .testTag("confirm_empty_trash_button"),
                            shape = CircleShape,
                            color = EditorialCoral,
                            contentColor = InkBlack,
                            border = BorderStroke(1.6.dp, InkBlack)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "EMPTY TRASH",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                                    color = InkBlack
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
