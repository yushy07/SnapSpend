package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.BackupStateSnapshot
import com.example.data.local.CategoryEntity
import com.example.data.local.CategoryType
import com.example.ui.SnapSpendUiState
import com.example.ui.components.BrightCardCornerIconButton
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.SegmentedPillToggle
import com.example.ui.components.StatusDotPill
import com.example.ui.components.StatusPillBadge
import com.example.ui.components.TinySectionLabel
import com.example.ui.components.UppercaseScreenHeader
import com.example.ui.components.WhitePillChip
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSubtleBorder
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialCoral
import com.example.ui.theme.EditorialMint
import com.example.ui.theme.InkBlack
import com.example.ui.theme.MarkScreenEntrancePlayedEffect
import com.example.ui.theme.Motion
import com.example.ui.theme.PaleLavenderTint
import com.example.ui.theme.PaleMintHero
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.dialogScaleFadeEntrance
import com.example.ui.theme.parseEditorialColor
import com.example.ui.theme.staggeredScreenEntrance
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsAndBackupScreen(
    uiState: SnapSpendUiState,
    backupState: BackupStateSnapshot,
    isBackupBusy: Boolean,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onConnectGoogleClick: () -> Unit,
    onDisconnectGoogleClick: () -> Unit,
    onBackupNowClick: () -> Unit,
    onRestoreFromDriveClick: () -> Unit,
    onExportJsonClick: () -> Unit,
    onExportCsvClick: () -> Unit,
    onImportJsonClick: (replaceEverything: Boolean) -> Unit,
    onSaveCategory: (id: Long, oldName: String?, newName: String, type: CategoryType, colorHex: String, isDefault: Boolean) -> Unit,
    onDeleteCategory: (CategoryEntity) -> Unit,
    onDeleteMerchantMemory: (normalizedMerchant: String) -> Unit,
    topContentPadding: Dp = 8.dp,
    bottomContentPadding: Dp = 120.dp,
    modifier: Modifier = Modifier
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark

    var showImportModeDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var catNameInput by remember { mutableStateOf("") }
    var catTypeInput by remember { mutableStateOf(CategoryType.EXPENSE) }
    var catColorInput by remember { mutableStateOf("#FFD23F") }

    val lastBackupFormatted = if (backupState.lastSuccessMillis > 0L) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(backupState.lastSuccessMillis))
    } else {
        "Never backed up yet"
    }

    MarkScreenEntrancePlayedEffect(screenKey = "settings")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_list"),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = topContentPadding,
            bottom = bottomContentPadding
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Bold Uppercase Condensed Screen Title ("SETTINGS")
        item(key = "settings_header") {
            UppercaseScreenHeader(
                title = "SETTINGS",
                leadingIcon = Icons.Default.Layers,
                leadingIconBg = PureWhite,
                leadingIconTint = InkBlack,
                titleColor = tokens.primaryText,
                modifier = Modifier.staggeredScreenEntrance(screenKey = "settings", index = 0)
            )
        }

        // 2. Theme Selector Card (System / Light / Dark) on Pale Lavender (#E3D1FF) with Black Text & Outline
        item(key = "settings_theme_card") {
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance(screenKey = "settings", index = 1)
                    .testTag("theme_selector_card"),
                shape = RoundedCornerShape(28.dp),
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = PaleLavenderTint,
                    contentColor = InkBlack,
                    border = BorderStroke(
                        width = 1.8.dp,
                        color = InkBlack
                    )
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        TinySectionLabel(
                            text = "DISPLAY APPEARANCE",
                            color = InkBlack.copy(alpha = 0.72f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "THEME MODE",
                            style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                            color = InkBlack
                        )
                        Text(
                            text = "Follows your phone's system setting by default",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = InkBlack.copy(alpha = 0.80f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        SegmentedPillToggle(
                            options = ThemeMode.entries,
                            selectedOption = themeMode,
                            labelProvider = { it.label },
                            onOptionSelected = onThemeModeChange,
                            optionTestTagProvider = { "theme_option_${it.name.lowercase()}" }
                        )
                    }
                }
            }
        }

        // 3. Google Drive Hidden Folder Backup Card (Coral #FF6F59 with StatusDotPill "● Connected" / "● Not connected")
        item(key = "settings_drive_backup_card") {
            val driveShape = RoundedCornerShape(28.dp)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance(screenKey = "settings", index = 2)
                    .testTag("drive_backup_card"),
                shape = driveShape,
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = driveShape,
                    color = EditorialCoral,
                    contentColor = BrightCardBlack,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                Column(modifier = Modifier.padding(22.dp)) {
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BrightCardBlack),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDone,
                                    contentDescription = "Drive Backup",
                                    tint = PureWhite,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "GOOGLE DRIVE BACKUP",
                                        style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                                        color = BrightCardBlack
                                    )
                                    StatusPillBadge(
                                        text = "Coming in Phase 6",
                                        backgroundColor = PureWhite.copy(alpha = 0.88f)
                                    )
                                }
                                StatusDotPill(
                                    text = if (backupState.connectedEmail != null) {
                                        "Connected · ${backupState.connectedEmail}"
                                    } else {
                                        "Not connected · Saved on this phone only"
                                    },
                                    dotColor = if (backupState.connectedEmail != null) EditorialMint else InkBlack,
                                    backgroundColor = PureWhite.copy(alpha = 0.85f),
                                    textColor = BrightCardBlack
                                )
                            }
                        }

                        if (isBackupBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(26.dp),
                                color = BrightCardBlack,
                                strokeWidth = 3.dp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = PureWhite,
                        border = BorderStroke(1.5.dp, BrightCardBlack)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (backupState.lastSuccessMillis > 0L) {
                                    "Last backup: $lastBackupFormatted"
                                } else {
                                    "Last backup: none yet"
                                },
                                style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                color = BrightCardBlack
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Weekly auto-backup to a private folder in your Google Drive. A new backup is saved before the old one is replaced.",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack.copy(alpha = 0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val isDriveConnected = backupState.connectedEmail != null
                        if (!isDriveConnected) {
                            WhitePillChip(
                                label = "Connect Google Drive",
                                selected = false,
                                leadingIcon = Icons.Default.CloudUpload,
                                onClick = onConnectGoogleClick,
                                testTag = "connect_google_button"
                            )
                        }
                        WhitePillChip(
                            label = "Backup now",
                            selected = false,
                            leadingIcon = Icons.Default.CloudUpload,
                            onClick = onBackupNowClick,
                            enabled = isDriveConnected,
                            testTag = "backup_now_button"
                        )
                        WhitePillChip(
                            label = "Restore from Drive",
                            selected = false,
                            leadingIcon = Icons.Default.Restore,
                            onClick = onRestoreFromDriveClick,
                            enabled = isDriveConnected,
                            testTag = "restore_drive_button"
                        )
                        if (isDriveConnected) {
                            WhitePillChip(
                                label = "Disconnect",
                                selected = false,
                                onClick = onDisconnectGoogleClick,
                                testTag = "disconnect_google_button"
                            )
                        }
                    }
                }
                }
            }
        }

        // 4. Manual Export & Import Card (Aqua #8FE3F0 with Black Text)
        item(key = "settings_export_card") {
            val exportShape = RoundedCornerShape(28.dp)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance(screenKey = "settings", index = 3),
                shape = exportShape,
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = exportShape,
                    color = EditorialAqua,
                    contentColor = BrightCardBlack,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(
                            text = "EXPORT & IMPORT FILES",
                            style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                            color = BrightCardBlack
                        )
                        Text(
                            text = "Save a local JSON backup or Excel CSV spreadsheet anytime",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = BrightCardBlack.copy(alpha = 0.82f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FullWidthSettingsActionPillButton(
                                label = "Export backup (.json)",
                                leadingIcon = Icons.Default.Download,
                                onClick = onExportJsonClick,
                                testTag = "export_json_button"
                            )
                            FullWidthSettingsActionPillButton(
                                label = "Export spreadsheet (.csv)",
                                leadingIcon = Icons.Default.Download,
                                onClick = onExportCsvClick,
                                testTag = "export_csv_button"
                            )
                            FullWidthSettingsActionPillButton(
                                label = "Import from file",
                                leadingIcon = Icons.Default.UploadFile,
                                onClick = { showImportModeDialog = true },
                                testTag = "import_json_button"
                            )
                        }
                    }
                }
            }
        }

        // 5. Manage Categories (Sunny Yellow #FFD23F with Black Text)
        item(key = "settings_categories_card") {
            val catShape = RoundedCornerShape(28.dp)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance(screenKey = "settings", index = 4),
                shape = catShape,
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = catShape,
                    color = SunnyYellow,
                    contentColor = BrightCardBlack,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "CATEGORIES (${uiState.categories.size})",
                                style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                                color = BrightCardBlack
                            )
                            Text(
                                text = "The AI only picks from this list",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = BrightCardBlack.copy(alpha = 0.8f)
                            )
                        }
                        BrightCardCornerIconButton(
                            icon = Icons.Default.Add,
                            contentDescription = "Add category",
                            parentCardColor = SunnyYellow,
                            onClick = {
                                editingCategory = null
                                catNameInput = ""
                                catTypeInput = CategoryType.EXPENSE
                                catColorInput = "#FFD23F"
                                showCategoryDialog = true
                            },
                            testTag = "add_category_button"
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.categories.forEach { cat ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                color = PureWhite,
                                border = BorderStroke(1.5.dp, BrightCardBlack)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(parseEditorialColor(cat.colorHex))
                                                .border(1.5.dp, BrightCardBlack, CircleShape)
                                        )
                                        Column {
                                            Text(
                                                text = cat.name.uppercase(),
                                                style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                                color = BrightCardBlack
                                            )
                                            Text(
                                                text = if (cat.type == CategoryType.EXPENSE) "Expense" else "Earning",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = BrightCardBlack.copy(alpha = 0.7f)
                                            )
                                        }
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingCategory = cat
                                                catNameInput = cat.name
                                                catTypeInput = cat.type
                                                catColorInput = cat.colorHex
                                                showCategoryDialog = true
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Rename ${cat.name}",
                                                tint = BrightCardBlack
                                            )
                                        }
                                        if (!cat.isDefault) {
                                            IconButton(onClick = { onDeleteCategory(cat) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete ${cat.name}",
                                                    tint = BrightCardBlack
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
            }
        }

        // 6. Learned Merchant Memory Card (Pale Mint #A8D5C0 with Black Text)
        item(key = "settings_memories_card") {
            val memShape = RoundedCornerShape(28.dp)
            Editorial3DLayerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .staggeredScreenEntrance(screenKey = "settings", index = 5),
                shape = memShape,
                extrusionColor = InkBlack,
                extrusionBorderColor = InkBlack,
                depthX = 4.dp,
                depthY = 6.dp
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = memShape,
                    color = PaleMintHero,
                    contentColor = BrightCardBlack,
                    border = BorderStroke(1.8.dp, BrightCardBlack)
                ) {
                    Column(modifier = Modifier.padding(22.dp)) {
                        Text(
                            text = "LEARNED MERCHANTS (${uiState.merchantMemories.size})",
                            style = CondensedCardTitleStyle.copy(fontSize = 26.sp),
                            color = BrightCardBlack
                        )
                        Text(
                            text = "Corrections you make on review or edit are remembered automatically",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = BrightCardBlack.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        if (uiState.merchantMemories.isEmpty()) {
                            Text(
                                text = "No learned rules yet. Change any merchant's category once and SnapSpend will remember it next time.",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = BrightCardBlack
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                uiState.merchantMemories.forEach { mem ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(18.dp),
                                        color = PureWhite,
                                        border = BorderStroke(1.5.dp, BrightCardBlack)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = mem.displayMerchant.uppercase(),
                                                    style = CondensedCardTitleStyle.copy(fontSize = 18.sp),
                                                    color = BrightCardBlack
                                                )
                                                Text(
                                                    text = "Always categorized as → ${mem.categoryName}",
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                    color = BrightCardBlack
                                                )
                                            }
                                            IconButton(onClick = { onDeleteMerchantMemory(mem.normalizedMerchant) }) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Forget rule",
                                                    tint = BrightCardBlack
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
        }
    }

    // Import Mode Dialog: Replace Everything vs Merge
    if (showImportModeDialog) {
        AlertDialog(
            onDismissRequest = { showImportModeDialog = false },
            modifier = Modifier.dialogScaleFadeEntrance(),
            containerColor = tokens.cardSurface,
            titleContentColor = tokens.primaryText,
            textContentColor = tokens.primaryText,
            title = {
                Text(
                    text = "HOW SHOULD WE IMPORT?",
                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                    color = tokens.primaryText
                )
            },
            text = {
                Text(
                    text = "• Merge (Recommended): Adds only missing payments using the duplicate check and updates Pending → Success entries.\n\n• Replace everything: Wipes current data and loads the backup file cleanly.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportModeDialog = false
                        onImportJsonClick(false) // Merge
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunnyYellow,
                        contentColor = BrightCardBlack
                    ),
                    shape = CircleShape
                ) {
                    Text("Merge Missing", fontWeight = FontWeight.ExtraBold, color = BrightCardBlack)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showImportModeDialog = false
                        onImportJsonClick(true) // Replace everything
                    }
                ) {
                    Text("Replace Everything", color = tokens.primaryText, fontWeight = FontWeight.ExtraBold)
                }
            }
        )
    }

    // Add / Rename Category Dialog
    if (showCategoryDialog) {
        val colorOptions = listOf("#FFD23F", "#FF6F59", "#8FE3F0", "#9FE3C0", "#5B5CF6")
        AlertDialog(
            onDismissRequest = { showCategoryDialog = false },
            modifier = Modifier.dialogScaleFadeEntrance(),
            containerColor = tokens.cardSurface,
            titleContentColor = tokens.primaryText,
            textContentColor = tokens.primaryText,
            title = {
                Text(
                    text = if (editingCategory == null) "NEW CATEGORY" else "EDIT CATEGORY",
                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                    color = tokens.primaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = catNameInput,
                        onValueChange = { catNameInput = it },
                        label = { Text("Category Name", color = tokens.secondaryText) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = tokens.primaryText,
                            unfocusedBorderColor = tokens.subtleDivider,
                            focusedTextColor = tokens.primaryText,
                            unfocusedTextColor = tokens.primaryText,
                            cursorColor = tokens.primaryText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhitePillChip(
                            label = "Expense",
                            selected = catTypeInput == CategoryType.EXPENSE,
                            onClick = { catTypeInput = CategoryType.EXPENSE }
                        )
                        WhitePillChip(
                            label = "Earning",
                            selected = catTypeInput == CategoryType.EARNING,
                            onClick = { catTypeInput = CategoryType.EARNING }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Card color:", style = MaterialTheme.typography.labelLarge, color = tokens.primaryText)
                        colorOptions.forEach { hex ->
                            val isChosen = catColorInput.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(if (isChosen) 34.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(parseEditorialColor(hex))
                                    .border(if (isChosen) 3.dp else 1.5.dp, tokens.primaryText, CircleShape)
                                    .clickable { catColorInput = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (catNameInput.isNotBlank()) {
                            onSaveCategory(
                                editingCategory?.id ?: 0L,
                                editingCategory?.name,
                                catNameInput,
                                catTypeInput,
                                catColorInput,
                                editingCategory?.isDefault ?: false
                            )
                            showCategoryDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunnyYellow,
                        contentColor = BrightCardBlack
                    ),
                    shape = CircleShape
                ) {
                    Text("Save", fontWeight = FontWeight.ExtraBold, color = BrightCardBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCategoryDialog = false }) {
                    Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun FullWidthSettingsActionPillButton(
    label: String,
    leadingIcon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    val view = LocalView.current
    Editorial3DLayerBox(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = CircleShape,
        extrusionColor = InkBlack,
        depthX = 3.dp,
        depthY = 4.dp,
        enableInteractiveTilt = false
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = CircleShape,
            color = PureWhite,
            contentColor = InkBlack,
            border = BorderStroke(1.5.dp, InkBlack)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = InkBlack,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    ),
                    color = InkBlack
                )
            }
        }
    }
}
