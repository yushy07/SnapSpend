package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.CategoryType
import com.example.data.local.TransactionEntity
import com.example.ui.ScanUiState
import com.example.ui.SnapSpendViewModel
import com.example.ui.components.Editorial3DLayerBox
import com.example.ui.components.EditorialSquiggleBackground
import com.example.ui.components.WhitePillChip
import com.example.ui.components.YuEditorialTopBar
import com.example.ui.screens.AiNaturalLanguageNoteDialog
import com.example.ui.screens.AiReviewDeckOverlay
import com.example.ui.screens.CategoriesFolderScreen
import com.example.ui.screens.ChartsAndInsightsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.QuickAddSourceBottomSheet
import com.example.ui.screens.SettingsAndBackupScreen
import com.example.ui.screens.TransactionEditorDialog
import com.example.ui.screens.TransactionsScreen
import com.example.ui.theme.BrightCardBlack
import com.example.ui.theme.CondensedCardTitleStyle
import com.example.ui.theme.DarkCardSurface
import com.example.ui.theme.DarkSubtleBorder
import com.example.ui.theme.EditorialAqua
import com.example.ui.theme.EditorialBg
import com.example.ui.theme.InkBlack
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.example.ui.theme.LocalReducedMotion
import com.example.ui.theme.Motion
import com.example.ui.theme.NewTransactionHighlightTracker
import com.example.ui.theme.PeriwinkleIndigo
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SnapSpendTheme
import com.example.ui.theme.SnapSpendThemeTokens
import com.example.ui.theme.SunnyYellow
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.TrueBlackBg
import com.example.ui.theme.dialogScaleFadeEntrance
import com.example.util.MoneyUtils
import com.example.util.configureAppCheck
import android.provider.MediaStore
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.data.local.SnapSpendPreferences
import com.example.ui.screens.OnboardingCarouselScreen
import com.example.ui.components.BeforeFirstScanDisclosureDialog
import com.example.ui.components.NotificationPermissionRationaleDialog
import com.example.ui.components.AppSettingsRedirectDialog
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

enum class SnapSpendTab(val label: String, val icon: ImageVector, val testTag: String) {
    HOME("Home", Icons.Default.Home, "nav_tab_home"),
    CHARTS("Charts", Icons.Default.BarChart, "nav_tab_charts"),
    CATEGORIES("Categories", Icons.Default.Category, "nav_tab_categories"),
    TRANSACTIONS("Transactions", Icons.AutoMirrored.Filled.FormatListBulleted, "nav_tab_transactions"),
    SETTINGS("Settings", Icons.Default.Settings, "nav_tab_settings")
}

class MainActivity : ComponentActivity() {

    private val viewModel: SnapSpendViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }

        // Handle Share-to-App intent if launched from Gallery or Files
        if (savedInstanceState == null) {
            handleIncomingShareIntent(intent)
        }

        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val snapSpendPrefs = remember { SnapSpendPreferences(this@MainActivity) }
            val hasSeenOnboarding by snapSpendPrefs.hasSeenOnboardingFlow.collectAsStateWithLifecycle(initialValue = null)
            val coroutineScope = rememberCoroutineScope()

            SnapSpendTheme(themeMode = themeMode) {
                if (hasSeenOnboarding == false) {
                    OnboardingCarouselScreen(
                        onFinish = {
                            coroutineScope.launch {
                                snapSpendPrefs.setHasSeenOnboarding(true)
                            }
                        }
                    )
                } else if (hasSeenOnboarding == true) {
                    SnapSpendRootScreen(
                        viewModel = viewModel,
                        themeMode = themeMode,
                        activity = this@MainActivity,
                        snapSpendPrefs = snapSpendPrefs
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SnapSpendThemeTokens.colors.appBackground)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                val singleUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
                }
                if (singleUri != null) {
                    viewModel.processSelectedUris(listOf(singleUri))
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                @Suppress("DEPRECATION")
                val multipleUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                }
                if (!multipleUris.isNullOrEmpty()) {
                    viewModel.processSelectedUris(multipleUris)
                }
            }
        }
    }
}

@Composable
fun SnapSpendRootScreen(
    viewModel: SnapSpendViewModel,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    activity: Activity,
    snapSpendPrefs: SnapSpendPreferences
) {
    val tokens = SnapSpendThemeTokens.colors
    val isDark = tokens.isDark
    val coroutineScope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
    val restoreOffer by viewModel.restoreOffer.collectAsStateWithLifecycle()
    val pendingConsentSender by viewModel.pendingConsentIntentSender.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val hasSeenAiScanNotice by snapSpendPrefs.hasSeenAiScanNoticeFlow.collectAsStateWithLifecycle(initialValue = false)
    val hasPromptedBudgetNotifications by snapSpendPrefs.hasPromptedBudgetNotificationsFlow.collectAsStateWithLifecycle(initialValue = false)

    var showOnboardingReplay by rememberSaveable { mutableStateOf(false) }
    var showBeforeFirstScanDialog by rememberSaveable { mutableStateOf(false) }
    var pendingScanAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showBudgetNotificationRationale by rememberSaveable { mutableStateOf(false) }
    var showBlockedNotificationSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var notificationPermissionStatus by remember { mutableStateOf("NOT_ASKED") }

    if (showOnboardingReplay) {
        OnboardingCarouselScreen(
            onFinish = { showOnboardingReplay = false }
        )
        return
    }

    // Lifecycle observer: refresh notification permission on ON_RESUME
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationPermissionStatus = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when {
                        ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> "ALLOWED"
                        ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS) -> "NOT_ASKED"
                        else -> "NOT_ASKED"
                    }
                } else {
                    "ALLOWED"
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", activity.packageName, null)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            viewModel.showToast("Could not open system settings.")
        }
    }

    fun performWithFirstScanNoticeCheck(action: () -> Unit) {
        if (!hasSeenAiScanNotice) {
            pendingScanAction = action
            showBeforeFirstScanDialog = true
        } else {
            action()
        }
    }

    val initialTab = remember {
        if (activity.intent?.getStringExtra("TARGET_TAB") == "CATEGORIES") {
            SnapSpendTab.CATEGORIES
        } else {
            SnapSpendTab.HOME
        }
    }
    var currentTab by rememberSaveable { mutableStateOf(initialTab) }
    var showQuickAddSheet by rememberSaveable { mutableStateOf(false) }
    var showAiNoteDialog by rememberSaveable { mutableStateOf(false) }
    var showBudgetSetupDialog by rememberSaveable { mutableStateOf(false) }
    var showTransactionEditor by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<TransactionEntity?>(null) }
    var importReplaceMode by rememberSaveable { mutableStateOf(false) }
    var samplePresetCounter by rememberSaveable { mutableIntStateOf(0) }

    // BackHandler on non-Home tabs to return cleanly to Home
    if (currentTab != SnapSpendTab.HOME && scanState is ScanUiState.Idle) {
        BackHandler {
            currentTab = SnapSpendTab.HOME
        }
    }

    // Photo Picker for Payment Screenshots (Zero storage permission required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.processSelectedUris(uris)
        }
    }

    // PDF Document Picker for Bank Statement & Receipt PDFs
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.processSelectedUris(listOf(uri))
        }
    }

    // Pending URI & File for full-resolution Camera capture via FileProvider
    var pendingCameraPhotoFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraCaptureUri by remember { mutableStateOf<Uri?>(null) }

    // Full-resolution Camera Capture Launcher -> sends captured image directly to Gemini
    val cameraTakePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val capturedUri = pendingCameraCaptureUri
        val capturedFile = pendingCameraPhotoFile
        if (success && capturedUri != null) {
            viewModel.processSelectedUris(listOf(capturedUri)) {
                // Delete temporary photo file right after scan finishes or fails
                capturedFile?.delete()
            }
        } else {
            // Cancelled: delete temporary photo file immediately
            capturedFile?.delete()
        }
        pendingCameraCaptureUri = null
        pendingCameraPhotoFile = null
    }

    fun launchCameraCaptureNow() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (takePictureIntent.resolveActivity(activity.packageManager) == null) {
            viewModel.showToast("No camera app found on this device — pick a screenshot or PDF instead.")
            showQuickAddSheet = true
            return
        }
        try {
            val captureDir = File(activity.cacheDir, "camera_captures").apply { mkdirs() }
            val photoFile = File(captureDir, "snapspend_scan_${System.currentTimeMillis()}.jpg")
            val authority = "${activity.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(activity, authority, photoFile)
            pendingCameraPhotoFile = photoFile
            pendingCameraCaptureUri = contentUri
            cameraTakePictureLauncher.launch(contentUri)
        } catch (_: Exception) {
            viewModel.showToast("Camera unavailable on this device — pick a screenshot or PDF instead.")
            showQuickAddSheet = true
        }
    }

    fun triggerQuickCameraScan() {
        performWithFirstScanNoticeCheck {
            launchCameraCaptureNow()
        }
    }

    // Export JSON Backup file launcher
    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportJsonToUri(uri)
        }
    }

    // Export CSV Spreadsheet launcher
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCsvToUri(uri)
        }
    }

    // Import JSON Backup file launcher
    val importJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importJsonFromUri(uri, replaceEverything = importReplaceMode)
        }
    }

    // Google Drive OAuth Consent Resolution Launcher
    val driveConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDriveConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    // Optional Android 13+ notification permission launcher for budget warnings
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        notificationPermissionStatus = if (isGranted) "ALLOWED" else "BLOCKED"
    }

    LaunchedEffect(pendingConsentSender) {
        val sender = pendingConsentSender
        if (sender != null) {
            driveConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(3600L)
            viewModel.clearToast()
        }
    }

    var isFabExpanded by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(currentTab) {
        isFabExpanded = true
    }

    // Track newly inserted transaction IDs so new items slide in from top and flash a warm highlight
    LaunchedEffect(uiState.allTransactions) {
        NewTransactionHighlightTracker.syncTransactions(uiState.allTransactions.map { it.id })
    }

    val fabScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -6f) {
                    isFabExpanded = false
                } else if (available.y > 6f) {
                    isFabExpanded = true
                }
                return Offset.Zero
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = activity.window
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isStatusBarContrastEnforced = false
                window.isNavigationBarContrastEnforced = false
            }
            val insetsController = WindowCompat.getInsetsController(window, view)
            val useDarkStatusIcons = !isDark && currentTab != SnapSpendTab.CATEGORIES
            insetsController.isAppearanceLightStatusBars = useDarkStatusIcons
            insetsController.isAppearanceLightNavigationBars = useDarkStatusIcons
        }
    }

    val density = LocalDensity.current
    val windowStatusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val safeDrawingTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val resourceStatusBarHeight = remember(view, density) {
        val resId = view.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) {
            with(density) { view.resources.getDimensionPixelSize(resId).toDp() }
        } else {
            28.dp
        }
    }
    val statusBarHeight = maxOf(windowStatusBarTop, safeDrawingTop, resourceStatusBarHeight, 28.dp)
    val statusBarScrimColor = when {
        currentTab == SnapSpendTab.CATEGORIES -> PeriwinkleIndigo
        isDark -> TrueBlackBg
        else -> EditorialBg
    }

    Box(modifier = Modifier.fillMaxSize()) {
        EditorialSquiggleBackground(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = statusBarHeight + 8.dp)
                        ) {
                            YuEditorialTopBar(
                                onToggleThemeClick = { viewModel.toggleDarkLightQuickly(currentlyDark = isDark) }
                            )
                        }
                    },
                    floatingActionButton = {
                        QuickAddFloatingPillButton(
                            isDark = isDark,
                            expanded = isFabExpanded,
                            onClick = { showQuickAddSheet = true }
                        )
                    },
                    bottomBar = {
                        EditorialBottomNavBar(
                            currentTab = currentTab,
                            onTabSelected = { currentTab = it }
                        )
                    }
                ) { innerPadding ->
                    val topContentPadding = innerPadding.calculateTopPadding() + 8.dp
                    val bottomContentPadding = maxOf(120.dp, innerPadding.calculateBottomPadding() + 72.dp)

                    val reducedMotion = LocalReducedMotion.current
                    val slideOffsetPx = with(density) { Motion.TAB_SLIDE_DISTANCE.roundToPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(fabScrollConnection)
                    ) {
                        AnimatedContent(
                            targetState = currentTab,
                            transitionSpec = {
                                if (reducedMotion) {
                                    fadeIn(Motion.quickTween(true)) togetherWith fadeOut(Motion.quickTween(true))
                                } else {
                                    val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                                    (slideInHorizontally(
                                        animationSpec = Motion.normalIntOffsetTween(false),
                                        initialOffsetX = { direction * slideOffsetPx }
                                    ) + fadeIn(
                                        animationSpec = Motion.normalTween(false)
                                    )) togetherWith fadeOut(
                                        animationSpec = Motion.fastExitTween(false)
                                    )
                                }
                            },
                            label = "tabScreenDirectionalSlide"
                        ) { targetTab ->
                            when (targetTab) {
                                SnapSpendTab.HOME -> {
                                    HomeScreen(
                                        uiState = uiState,
                                        onPickImagesClick = {
                                            performWithFirstScanNoticeCheck {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            }
                                        },
                                        onPickPdfClick = {
                                            performWithFirstScanNoticeCheck {
                                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                            }
                                        },
                                        onAiQuickNoteClick = {
                                            performWithFirstScanNoticeCheck {
                                                showAiNoteDialog = true
                                            }
                                        },
                                        onTrySampleScanClick = {
                                            performWithFirstScanNoticeCheck {
                                                viewModel.scanSampleUpiScreenshotWithGemini(samplePresetCounter++)
                                            }
                                        },
                                        onManualAddClick = {
                                            editingTransaction = null
                                            showTransactionEditor = true
                                        },
                                        onSeeAllTransactionsClick = {
                                            currentTab = SnapSpendTab.TRANSACTIONS
                                        },
                                        onSeeChartsClick = {
                                            currentTab = SnapSpendTab.CHARTS
                                        },
                                        onTransactionClick = { tx ->
                                            editingTransaction = tx
                                            showTransactionEditor = true
                                        },
                                        onRetryQueuedScansClick = viewModel::retryQueuedScans,
                                        onSetBudgetClick = {
                                            showBudgetSetupDialog = true
                                        },
                                        topContentPadding = topContentPadding,
                                        bottomContentPadding = bottomContentPadding
                                    )
                                }

                                SnapSpendTab.TRANSACTIONS -> {
                                    TransactionsScreen(
                                        uiState = uiState,
                                        onSearchQueryChange = viewModel::updateSearchQuery,
                                        onDateRangeChange = viewModel::updateDateRangeFilter,
                                        onCategoryFilterChange = viewModel::updateCategoryFilter,
                                        onTypeFilterChange = viewModel::updateTypeFilter,
                                        onPaymentAppFilterChange = viewModel::updatePaymentAppFilter,
                                        onClearFilters = viewModel::clearAllFilters,
                                        onTransactionClick = { tx ->
                                            editingTransaction = tx
                                            showTransactionEditor = true
                                        },
                                        topContentPadding = topContentPadding,
                                        bottomContentPadding = bottomContentPadding
                                    )
                                }

                                SnapSpendTab.CATEGORIES -> {
                                    CategoriesFolderScreen(
                                        uiState = uiState,
                                        onSaveBudget = { category, limitPaise ->
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                notificationPermissionStatus != "ALLOWED" &&
                                                !hasPromptedBudgetNotifications
                                            ) {
                                                showBudgetNotificationRationale = true
                                            }
                                            viewModel.saveBudget(category, limitPaise)
                                        },
                                        onTransactionClick = { tx ->
                                            editingTransaction = tx
                                            showTransactionEditor = true
                                        },
                                        topContentPadding = topContentPadding,
                                        bottomContentPadding = bottomContentPadding
                                    )
                                }

                                SnapSpendTab.CHARTS -> {
                                    ChartsAndInsightsScreen(
                                        uiState = uiState,
                                        onChartDateRangeChange = viewModel::updateChartDateRange,
                                        onLineGranularityChange = viewModel::updateLineGranularity,
                                        onSaveBudget = { category, limitPaise ->
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                notificationPermissionStatus != "ALLOWED" &&
                                                !hasPromptedBudgetNotifications
                                            ) {
                                                showBudgetNotificationRationale = true
                                            }
                                            viewModel.saveBudget(category, limitPaise)
                                        },
                                        onRepeatingStateChange = viewModel::updateRepeatingState,
                                        onSetBudgetClick = {
                                            showBudgetSetupDialog = true
                                        },
                                        topContentPadding = topContentPadding,
                                        bottomContentPadding = bottomContentPadding
                                    )
                                }

                                SnapSpendTab.SETTINGS -> {
                                    SettingsAndBackupScreen(
                                        uiState = uiState,
                                        backupState = backupState,
                                        isBackupBusy = isBackupBusy,
                                        themeMode = themeMode,
                                        onThemeModeChange = viewModel::setThemeMode,
                                        onConnectGoogleClick = { viewModel.connectGoogleAccount(activity) },
                                        onDisconnectGoogleClick = viewModel::disconnectGoogleAccount,
                                        onBackupNowClick = viewModel::triggerDriveBackupNow,
                                        onRestoreFromDriveClick = {
                                            viewModel.checkForDriveBackupToRestore(silentIfNone = false)
                                        },
                                        onExportJsonClick = {
                                            exportJsonLauncher.launch("snapspend_backup.json")
                                        },
                                        onExportCsvClick = {
                                            exportCsvLauncher.launch("snapspend_transactions.csv")
                                        },
                                        onImportJsonClick = { replaceAll ->
                                            importReplaceMode = replaceAll
                                            importJsonLauncher.launch(arrayOf("application/json", "*/*"))
                                        },
                                        onSaveCategory = { id, oldName, newName, type, colorHex, isDefault ->
                                            viewModel.saveCategory(
                                                id = id,
                                                oldName = oldName,
                                                newName = newName,
                                                type = type,
                                                colorHex = colorHex,
                                                iconName = "category",
                                                isDefault = isDefault
                                            )
                                        },
                                        onDeleteCategory = viewModel::deleteCustomCategory,
                                        onDeleteMerchantMemory = { normMerchant ->
                                            viewModel.showToast("Removed learned rule for $normMerchant")
                                        },
                                        onOpenOnboardingClick = {
                                            showOnboardingReplay = true
                                        },
                                        notificationPermissionStatus = notificationPermissionStatus,
                                        onRequestNotificationPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                if (notificationPermissionStatus == "BLOCKED") {
                                                    showBlockedNotificationSettingsDialog = true
                                                } else {
                                                    showBudgetNotificationRationale = true
                                                }
                                            }
                                        },
                                        onOpenAppSettings = {
                                            openAppSystemSettings()
                                        },
                                        topContentPadding = topContentPadding,
                                        bottomContentPadding = bottomContentPadding
                                    )
                                }
                            }
                        }
                    }
            }

            // Thin scrim behind the status bar only: matches the current screen's background
            // (cream in Light mode, deep ink in Dark mode, or indigo on Categories) with a tiny
            // bottom fade so scrolled content does not peek behind the status bar clock/battery.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight + 8.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            0.0f to statusBarScrimColor,
                            0.78f to statusBarScrimColor,
                            1.0f to Color.Transparent
                        )
                    )
            )
            }
        }

        // Full-Screen AI Scan & Review Overlay when active
        if (scanState !is ScanUiState.Idle) {
            AiReviewDeckOverlay(
                scanState = scanState,
                categories = uiState.categories,
                onDismiss = viewModel::dismissScanFlow,
                onRetry = viewModel::retryLastScan,
                onQueueFailedScan = viewModel::queueCurrentFailedScan,
                onPickAnotherImage = {
                    viewModel.dismissScanFlow()
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onUpdateDraft = viewModel::updateReviewDraft,
                onToggleSkip = viewModel::toggleDraftSkipped,
                onApplyEqualSplit = viewModel::applyDraftEqualSplit,
                onToggleLineItem = viewModel::toggleDraftLineItem,
                onConfirmAll = {
                    viewModel.confirmAllReviewDrafts()
                }
            )
        }

        // Editorial Toast Pill Banner at the top
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp, start = 20.dp, end = 20.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = SunnyYellow,
                contentColor = BrightCardBlack,
                border = BorderStroke(1.5.dp, BrightCardBlack),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = toastMessage.orEmpty(),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = BrightCardBlack
                )
            }
        }
    }

    // Quick Add Source Bottom Sheet
    if (showQuickAddSheet) {
        QuickAddSourceBottomSheet(
            onDismiss = { showQuickAddSheet = false },
            onCameraQuickScan = {
                triggerQuickCameraScan()
            },
            onPickScreenshots = {
                performWithFirstScanNoticeCheck {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            },
            onPickPdf = {
                performWithFirstScanNoticeCheck {
                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                }
            },
            onAiQuickNote = {
                performWithFirstScanNoticeCheck {
                    showAiNoteDialog = true
                }
            },
            onTrySampleAiReceipt = {
                performWithFirstScanNoticeCheck {
                    viewModel.scanSampleUpiScreenshotWithGemini(samplePresetCounter++)
                }
            },
            onManualAdd = {
                editingTransaction = null
                showTransactionEditor = true
            }
        )
    }

    // Natural-Language Voice / Text Note AI Dialog
    if (showAiNoteDialog) {
        AiNaturalLanguageNoteDialog(
            onDismiss = { showAiNoteDialog = false },
            onSubmitNote = { userNote ->
                viewModel.processNaturalLanguageNoteWithGemini(userNote)
            }
        )
    }

    // Manual Add / Edit Transaction Modal
    if (showTransactionEditor) {
        TransactionEditorDialog(
            initialTransaction = editingTransaction,
            categories = uiState.categories,
            onDismiss = { showTransactionEditor = false },
            onSave = { tx, learnMerchant ->
                viewModel.saveManualOrEditedTransaction(tx, learnMerchant)
            },
            onDelete = { id ->
                viewModel.deleteTransaction(id)
            }
        )
    }

    // Quick Budget Setup Modal (opened when tapping "Set a budget" on the Target pill)
    if (showBudgetSetupDialog) {
        val expenseCategoryNames = remember(uiState.categories) {
            uiState.categories
                .filter { it.type == CategoryType.EXPENSE }
                .map { it.name }
                .ifEmpty { listOf("Food & Dining", "Groceries", "Shopping", "Transport & Fuel", "Bills & Utilities") }
        }
        var selectedBudgetCategory by rememberSaveable(expenseCategoryNames) {
            mutableStateOf(expenseCategoryNames.first())
        }
        val existingLimitPaise = uiState.budgets
            .firstOrNull { it.categoryName.equals(selectedBudgetCategory, ignoreCase = true) }
            ?.monthlyLimitPaise
        var limitInputText by remember(selectedBudgetCategory, existingLimitPaise) {
            mutableStateOf(
                if (existingLimitPaise != null && existingLimitPaise > 0L) {
                    (existingLimitPaise / 100L).toString()
                } else {
                    ""
                }
            )
        }

        AlertDialog(
            onDismissRequest = { showBudgetSetupDialog = false },
            modifier = Modifier.dialogScaleFadeEntrance(),
            containerColor = tokens.cardSurface,
            titleContentColor = tokens.primaryText,
            textContentColor = tokens.primaryText,
            title = {
                Text(
                    text = "SET MONTHLY BUDGET",
                    style = CondensedCardTitleStyle.copy(fontSize = 24.sp),
                    color = tokens.primaryText
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Pick a category and enter your monthly budget in ₹ Rupees to see your daily target line on the chart.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.secondaryText
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        expenseCategoryNames.forEach { catName ->
                            WhitePillChip(
                                label = catName,
                                selected = catName == selectedBudgetCategory,
                                onClick = { selectedBudgetCategory = catName }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = limitInputText,
                        onValueChange = { limitInputText = it },
                        label = { Text("Budget for this category", color = tokens.secondaryText) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = tokens.primaryText,
                            unfocusedTextColor = tokens.primaryText,
                            focusedBorderColor = if (isDark) SunnyYellow else InkBlack,
                            unfocusedBorderColor = tokens.subtleDivider
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paise = MoneyUtils.parseRupeesToPaise(limitInputText)
                        if (paise != null && paise > 0L) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPromptedBudgetNotifications) {
                                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    showBudgetNotificationRationale = true
                                }
                            }
                            viewModel.saveBudget(selectedBudgetCategory, paise)
                            showBudgetSetupDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunnyYellow,
                        contentColor = InkBlack
                    ),
                    shape = CircleShape,
                    modifier = Modifier.testTag("save_quick_budget_button")
                ) {
                    Text("Save budget", fontWeight = FontWeight.ExtraBold, color = InkBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetSetupDialog = false }) {
                    Text("Cancel", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Reinstall Auto-Restore / Drive Restore Offer Dialog
    restoreOffer?.let { metadata ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestoreOffer,
            modifier = Modifier.dialogScaleFadeEntrance(),
            containerColor = tokens.cardSurface,
            titleContentColor = tokens.primaryText,
            textContentColor = tokens.primaryText,
            title = {
                Text(
                    text = "Found a backup from ${metadata.formattedDate}, restore it?",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = tokens.primaryText
                )
            },
            text = {
                Text(
                    text = "We found your hidden Google Drive backup file. Tap Restore to bring back all your transactions, categories, budgets, and learned merchants.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tokens.secondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.confirmRestoreFromDrive(metadata, replaceEverything = false)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SunnyYellow,
                        contentColor = BrightCardBlack
                    ),
                    shape = CircleShape
                ) {
                    Text("Restore Backup", fontWeight = FontWeight.ExtraBold, color = BrightCardBlack)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestoreOffer) {
                    Text("Not now", color = tokens.primaryText, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // One-time Before First Scan AI Notice Dialog
    if (showBeforeFirstScanDialog) {
        BeforeFirstScanDisclosureDialog(
            onDismiss = {
                showBeforeFirstScanDialog = false
                pendingScanAction = null
            },
            onConfirm = {
                showBeforeFirstScanDialog = false
                coroutineScope.launch {
                    snapSpendPrefs.setHasSeenAiScanNotice(true)
                }
                val action = pendingScanAction
                pendingScanAction = null
                action?.invoke()
            }
        )
    }

    // Just-in-time Budget Alerts Notification Permission Rationale Dialog
    if (showBudgetNotificationRationale) {
        NotificationPermissionRationaleDialog(
            onDismiss = {
                showBudgetNotificationRationale = false
                coroutineScope.launch {
                    snapSpendPrefs.setHasPromptedBudgetNotifications(true)
                }
            },
            onContinue = {
                showBudgetNotificationRationale = false
                coroutineScope.launch {
                    snapSpendPrefs.setHasPromptedBudgetNotifications(true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }

    // Redirect to System Settings when notifications are blocked
    if (showBlockedNotificationSettingsDialog) {
        AppSettingsRedirectDialog(
            onDismiss = { showBlockedNotificationSettingsDialog = false },
            onOpenSettings = {
                showBlockedNotificationSettingsDialog = false
                openAppSystemSettings()
            }
        )
    }
}

/**
 * Bottom Navigation Bar:
 * - A black rounded bar (#1D1D28) with 3D extruded shadow base
 * - The selected tab sits in an Aqua (#8FE3F0) 3D rounded pill with a black icon AND its text label
 * - Unselected tabs are plain white icons (#FFFFFF)
 */
@Composable
private fun EditorialBottomNavBar(
    currentTab: SnapSpendTab,
    onTabSelected: (SnapSpendTab) -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val reducedMotion = LocalReducedMotion.current
    val tabs = SnapSpendTab.entries
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    // Measure each tab item's X offset & width inside the row so the Aqua pill slides smoothly to the selected tab
    val tabOffsetsPx = remember { IntArray(tabs.size) }
    val tabWidthsPx = remember { IntArray(tabs.size) }
    var hasMeasuredTabs by remember { mutableStateOf(false) }

    val targetPillOffsetDp = with(density) { tabOffsetsPx[selectedIndex].toDp() }
    val targetPillWidthDp = with(density) { tabWidthsPx[selectedIndex].coerceAtLeast(1).toDp() }

    val animatedPillOffsetX by animateDpAsState(
        targetValue = targetPillOffsetDp,
        animationSpec = Motion.physicalSpring(reducedMotion),
        label = "bottomNavAquaPillOffsetX"
    )
    val animatedPillWidth by animateDpAsState(
        targetValue = targetPillWidthDp,
        animationSpec = Motion.physicalSpring(reducedMotion),
        label = "bottomNavAquaPillWidth"
    )

    val pillShape = RoundedCornerShape(16.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 10.dp),
        shape = RoundedCornerShape(28.dp),
        color = DarkCardSurface,
        border = BorderStroke(1.5.dp, DarkSubtleBorder),
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // Sliding 3D Aqua (#8FE3F0) selected-tab pill behind the active tab
            if (hasMeasuredTabs && tabWidthsPx[selectedIndex] > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = animatedPillOffsetX)
                        .width(animatedPillWidth)
                        .height(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Editorial3DLayerBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = pillShape,
                        depthX = 2.dp,
                        depthY = 3.dp,
                        extrusionColor = Color(0xFF050507),
                        enableInteractiveTilt = false
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(pillShape)
                                .background(EditorialAqua)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { idx, tab ->
                    val isSelected = currentTab == tab
                    val tabInteractionSource = remember { MutableInteractionSource() }
                    val isTabPressed by tabInteractionSource.collectIsPressedAsState()
                    val tabPressFraction by animateFloatAsState(
                        targetValue = if (isTabPressed) 1f else 0f,
                        animationSpec = Motion.pressSpring(reducedMotion),
                        label = "bottomNavTabPress_${tab.name}"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) InkBlack else PureWhite,
                        animationSpec = Motion.quickTween(reducedMotion),
                        label = "bottomNavIconTint_${tab.name}"
                    )

                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(46.dp)
                            .onGloballyPositioned { coords ->
                                val posX = coords.positionInParent().x.roundToInt()
                                val w = coords.size.width
                                if (tabOffsetsPx[idx] != posX || tabWidthsPx[idx] != w) {
                                    tabOffsetsPx[idx] = posX
                                    tabWidthsPx[idx] = w
                                    if (tabWidthsPx.any { it > 0 }) {
                                        hasMeasuredTabs = true
                                    }
                                }
                            }
                            .offset(
                                x = 2.dp * tabPressFraction,
                                y = 2.5.dp * tabPressFraction
                            )
                            .clickable(
                                interactionSource = tabInteractionSource,
                                indication = null,
                                role = Role.Tab
                            ) {
                                Motion.performLightTapHaptic(view)
                                onTabSelected(tab)
                            }
                            .testTag(tab.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        // Fallback Aqua pill on very first frame before onGloballyPositioned fires
                        if (isSelected && !hasMeasuredTabs) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clip(pillShape)
                                    .background(EditorialAqua)
                            )
                        }

                        Row(
                            modifier = Modifier
                                .height(42.dp)
                                .padding(horizontal = if (isSelected) 12.dp else 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label,
                                tint = iconTint,
                                modifier = Modifier.size(if (isSelected) 20.dp else 22.dp)
                            )
                            // Selected tab label grows out and shrinks away with a spring animation (never a sudden jump)
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandHorizontally(
                                    animationSpec = Motion.physicalIntSizeSpring(reducedMotion),
                                    expandFrom = Alignment.Start
                                ) + fadeIn(animationSpec = Motion.quickTween(reducedMotion)),
                                exit = shrinkHorizontally(
                                    animationSpec = Motion.physicalIntSizeSpring(reducedMotion),
                                    shrinkTowards = Alignment.Start
                                ) + fadeOut(animationSpec = Motion.quickTween(reducedMotion))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tab.label,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.sp
                                        ),
                                        color = InkBlack,
                                        maxLines = 1
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
 * Single 3D Extruded Sunny Yellow (+ ADD) Floating Action Button at the bottom-right:
 * - Tapping it opens the "Quick Scan & Add" bottom sheet (camera, screenshots, voice/text, PDF, manual entry).
 * - Shrinks to just the "+" icon when the user scrolls down, and shows "+ ADD" when scrolling up.
 */
@Composable
private fun QuickAddFloatingPillButton(
    isDark: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pillShape = CircleShape
    Editorial3DLayerBox(
        shape = pillShape,
        depthX = 3.5.dp,
        depthY = 5.dp,
        extrusionColor = if (isDark) Color(0xFF000000) else InkBlack,
        enableInteractiveTilt = false,
        externalPressed = isPressed
    ) {
        Surface(
            onClick = {
                Motion.performLightTapHaptic(view)
                onClick()
            },
            interactionSource = interactionSource,
            modifier = Modifier
                .height(52.dp)
                .testTag("fab_add_payment"),
            shape = pillShape,
            color = SunnyYellow,
            contentColor = BrightCardBlack,
            border = BorderStroke(2.2.dp, BrightCardBlack),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (expanded) 18.dp else 14.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add payment",
                    tint = BrightCardBlack,
                    modifier = Modifier.size(24.dp)
                )
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn(Motion.quickTween(reducedMotion)) + expandHorizontally(Motion.physicalIntSizeSpring(reducedMotion)),
                    exit = fadeOut(Motion.quickTween(reducedMotion)) + shrinkHorizontally(Motion.physicalIntSizeSpring(reducedMotion))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ADD",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            ),
                            color = BrightCardBlack,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
