package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.InkBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SunnyYellow

/**
 * One-time disclosure card shown before the user's very first AI scan or natural language parse.
 */
@Composable
fun BeforeFirstScanDisclosureDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("before_first_scan_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = SunnyYellow,
                contentColor = InkBlack,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PureWhite,
                        border = BorderStroke(1.8.dp, InkBlack),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Scan",
                                tint = InkBlack,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "BEFORE YOUR FIRST SCAN",
                        style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "The image, PDF or note you pick is sent to Gemini to be read. SnapSpend saves only the details it finds, not the file.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WhitePillChip(
                            label = "CANCEL",
                            selected = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            testTag = "cancel_first_scan_button"
                        )

                        Editorial3DLayerBox(
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            extrusionColor = InkBlack,
                            extrusionBorderColor = InkBlack,
                            depthX = 2.dp,
                            depthY = 3.dp
                        ) {
                            Surface(
                                onClick = onConfirm,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("got_it_first_scan_button"),
                                shape = CircleShape,
                                color = InkBlack,
                                contentColor = PureWhite,
                                border = BorderStroke(1.8.dp, InkBlack)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "GOT IT",
                                        style = CondensedCardTitleStyle.copy(fontSize = 15.sp),
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Just-in-time friendly rationale card shown right after the user saves their first budget,
 * explaining why budget alerts are helpful before the system notification prompt appears.
 */
@Composable
fun NotificationPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_rationale_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = EditorialAqua,
                contentColor = InkBlack,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PureWhite,
                        border = BorderStroke(1.8.dp, InkBlack),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Budget Alerts",
                                tint = InkBlack,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "BUDGET ALERTS",
                        style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "Get a heads-up when you are close to a category budget.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WhitePillChip(
                            label = "NOT NOW",
                            selected = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            testTag = "not_now_notification_button"
                        )

                        Editorial3DLayerBox(
                            modifier = Modifier.weight(1f),
                            shape = CircleShape,
                            extrusionColor = InkBlack,
                            extrusionBorderColor = InkBlack,
                            depthX = 2.dp,
                            depthY = 3.dp
                        ) {
                            Surface(
                                onClick = onContinue,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("continue_notification_button"),
                                shape = CircleShape,
                                color = InkBlack,
                                contentColor = PureWhite,
                                border = BorderStroke(1.8.dp, InkBlack)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "CONTINUE",
                                        style = CondensedCardTitleStyle.copy(fontSize = 15.sp),
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shown if notification permission was permanently blocked, providing a quick shortcut to App Settings.
 */
@Composable
fun AppSettingsRedirectDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Editorial3DLayerBox(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            extrusionColor = InkBlack,
            extrusionBorderColor = InkBlack,
            depthX = 4.dp,
            depthY = 6.dp
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("app_settings_redirect_dialog"),
                shape = RoundedCornerShape(28.dp),
                color = EditorialCoral,
                contentColor = InkBlack,
                border = BorderStroke(2.dp, InkBlack)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = PureWhite,
                        border = BorderStroke(1.8.dp, InkBlack),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "App Settings",
                                tint = InkBlack,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Text(
                        text = "NOTIFICATIONS BLOCKED",
                        style = CondensedCardTitleStyle.copy(fontSize = 22.sp),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    Text(
                        text = "To receive budget warnings when you hit 80% or 100%, please enable notifications in App Settings.",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = InkBlack,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WhitePillChip(
                            label = "NOT NOW",
                            selected = false,
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            testTag = "dismiss_settings_redirect_button"
                        )

                        Editorial3DLayerBox(
                            modifier = Modifier.weight(1.3f),
                            shape = CircleShape,
                            extrusionColor = InkBlack,
                            extrusionBorderColor = InkBlack,
                            depthX = 2.dp,
                            depthY = 3.dp
                        ) {
                            Surface(
                                onClick = onOpenSettings,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("open_app_settings_button"),
                                shape = CircleShape,
                                color = InkBlack,
                                contentColor = PureWhite,
                                border = BorderStroke(1.8.dp, InkBlack)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "OPEN SETTINGS",
                                        style = CondensedCardTitleStyle.copy(fontSize = 14.sp),
                                        color = PureWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
