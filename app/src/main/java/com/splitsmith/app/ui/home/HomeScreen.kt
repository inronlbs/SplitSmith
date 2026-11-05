package com.splitsmith.app.ui.home

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.splitsmith.app.util.formatCurrency
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.splitsmith.app.R
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import com.splitsmith.app.data.DirectSplit
import com.splitsmith.app.data.Group
import com.splitsmith.app.data.UserProfile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitsmith.app.ui.personal.PersonalExpensesScreen
import com.splitsmith.app.ui.split.SplitExpensesScreen
import com.splitsmith.app.ui.split.DirectSplitDetailBottomSheet
import com.splitsmith.app.ui.components.UserAvatar
import com.splitsmith.app.ui.components.dotGridBackground
import com.splitsmith.app.ui.components.meshGradientBackground
import com.splitsmith.app.theme.LocalSplitColors
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalThemeController
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.OutfitFamily
import kotlinx.coroutines.launch

// Helper: pick a consistent muted hue from a group/member ID
private val groupHues = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF06B6D4),
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444)
)
private fun hueForId(id: String) = groupHues[Math.abs(id.hashCode()) % groupHues.size]

private var savedHomeTab = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToGroup: (groupId: String) -> Unit,
    onNavigateToQuickSplit: () -> Unit,
    onNavigateToQRCode: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToAddExpense: (groupId: String, expenseId: String?) -> Unit,
    onNavigateToSlipImport: (imageUriStr: String) -> Unit = {},
    onNavigateToDirectSplit: (peerUid: String) -> Unit = {},
    onSignOut: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val homeViewModel: HomeViewModel = viewModel()
    val homeDashboardState by homeViewModel.uiState.collectAsStateWithLifecycle()
    var showCreateGroupSheet by remember { mutableStateOf(false) }
    var createGroupIsTracker by remember { mutableStateOf(false) }
    var showJoinGroupSheet by remember { mutableStateOf(false) }
    var showQuickAddSheet by remember { mutableStateOf(false) }
    var showAddPersonalInitially by remember { mutableStateOf(false) }
    var showSplitActionsSheet by remember { mutableStateOf(false) }
    var showSelectGroupForExpenseDialog by remember { mutableStateOf(false) }

    val quickAddSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val splitActionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectGroupSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val pagerState = rememberPagerState(initialPage = savedHomeTab) { 4 }

    var isDockVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isDockVisible = true
    }

    val dockOffset by animateDpAsState(
        targetValue = if (isDockVisible) 0.dp else 48.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "dockOffset"
    )
    val dockAlpha by animateFloatAsState(
        targetValue = if (isDockVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "dockAlpha"
    )

    val groupsFlow = remember { FirebaseManager.observeGroups() }
    val groupsState = groupsFlow.collectAsState(initial = emptyList())
    val userProfileFlow = remember { FirebaseManager.observeUserProfile() }
    val userProfileState = userProfileFlow.collectAsState(initial = null)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var joinConfirmGroup by remember { mutableStateOf<Group?>(null) }
    var connectedUserPopup by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(Unit) {
        com.splitsmith.app.migration.FolderMigration.runFolderMigration(context)
    }
    val pendingJoinCode = FirebaseManager.pendingGroupJoinCode
    LaunchedEffect(pendingJoinCode) {
        if (!pendingJoinCode.isNullOrEmpty()) {
            FirebaseManager.pendingGroupJoinCode = null // Clear it immediately to avoid loops
            coroutineScope.launch {
                try {
                    val group = FirebaseManager.getGroupOnce(pendingJoinCode)
                    if (group != null) {
                        val uid = FirebaseManager.currentUserId
                        if (group.members[uid] == true) {
                            Toast.makeText(context, "Already a member of ${group.name}", Toast.LENGTH_SHORT).show()
                            onNavigateToGroup(group.id)
                        } else {
                            joinConfirmGroup = group
                        }
                    } else {
                        Toast.makeText(context, "Group not found or expired", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error fetching group: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    var selectedSplitForDetail by remember { mutableStateOf<DirectSplit?>(null) }
    var selectedPersonalForDetail by remember { mutableStateOf<com.splitsmith.app.data.PersonalExpense?>(null) }
    var selectedGroupExpenseForDetail by remember { mutableStateOf<com.splitsmith.app.data.GroupExpenseWithContext?>(null) }
    var targetPersonalExpenseId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pagerState.currentPage) {
        savedHomeTab = pagerState.currentPage
        if (pagerState.currentPage != 2) {
            showAddPersonalInitially = false
            targetPersonalExpenseId = null
        }
        selectedPersonalForDetail = null
        selectedGroupExpenseForDetail = null
        selectedSplitForDetail = null
        FirebaseManager.pendingExpenseAttachmentUri = null
        FirebaseManager.pendingExpenseAmount = null
    }

    var showScannerOptionsSheet by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onNavigateToSlipImport(android.net.Uri.encode(uri.toString()))
        }
    }

    val cameraCaptureLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success && tempCameraUri != null) {
            onNavigateToSlipImport(android.net.Uri.encode(tempCameraUri.toString()))
        }
    }

    val isAnySheetOpen = showQuickAddSheet || showCreateGroupSheet || showJoinGroupSheet || showSplitActionsSheet || showScannerOptionsSheet || selectedSplitForDetail != null || selectedPersonalForDetail != null || selectedGroupExpenseForDetail != null

    // Smart System Back Navigation & Double-Back Exit Guard
    var lastBackPressTime by remember { mutableStateOf(0L) }
    androidx.activity.compose.BackHandler {
        if (isAnySheetOpen) {
            showQuickAddSheet = false
            showCreateGroupSheet = false
            showJoinGroupSheet = false
            showSplitActionsSheet = false
            showScannerOptionsSheet = false
            selectedSplitForDetail = null
            selectedPersonalForDetail = null
            selectedGroupExpenseForDetail = null
        } else if (pagerState.currentPage != 0) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(0)
            }
        } else {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < 2000) {
                (context as? android.app.Activity)?.finish()
            } else {
                lastBackPressTime = currentTime
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !isAnySheetOpen,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(animationSpec = tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                ) + fadeOut(animationSpec = tween(300))
            ) {
                // ── Icon-Only Floating Capsule Dock (Style B - Variant 1) ─────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = d.navDockMarginH, vertical = d.navDockMarginV)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(d.navDockHeight),
                        shape = RoundedCornerShape(d.radiusXL),
                        color = colors.surfaceCard.copy(alpha = 0.97f),
                        border = BorderStroke(1.dp, colors.borderWhisper),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Home (0) + Groups/Split (1)
                            NavIconSlot(0, Icons.Default.Home, "Home", pagerState.targetPage, colors.inkPrimary, colors.borderWhisper, colors.inkMuted, d) {
                                coroutineScope.launch { pagerState.animateScrollToPage(0) }
                            }
                            NavIconSlot(1, Icons.Default.Groups, "Split", pagerState.targetPage, colors.inkPrimary, colors.borderWhisper, colors.inkMuted, d) {
                                coroutineScope.launch { pagerState.animateScrollToPage(1) }
                            }

                            // Center FAB
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(d.navCenterFab)
                                        .clip(CircleShape)
                                        .background(colors.inkPrimary)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(bounded = false, radius = d.navCenterFab / 2)
                                        ) {
                                            showQuickAddSheet = true
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Quick Add",
                                        tint = colors.canvasChalk,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Right: Personal (2) + Settings (3)
                            NavIconSlot(2, Icons.Default.AccountBalanceWallet, "Expenses", pagerState.targetPage, colors.inkPrimary, colors.borderWhisper, colors.inkMuted, d) {
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            }
                            NavIconSlot(3, Icons.Default.Settings, "Settings", pagerState.targetPage, colors.inkPrimary, colors.borderWhisper, colors.inkMuted, d) {
                                coroutineScope.launch { pagerState.animateScrollToPage(3) }
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        val handleScrollToPage1 = remember(pagerState, coroutineScope) {
            { coroutineScope.launch { pagerState.animateScrollToPage(1) }; Unit }
        }
        val handleScrollToPage0 = remember(pagerState, coroutineScope) {
            { coroutineScope.launch { pagerState.animateScrollToPage(0) }; Unit }
        }
        val handleCreateGroupClick = remember { { showCreateGroupSheet = true } }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvasChalk)
                .meshGradientBackground(colors.inkPrimary)
                .dotGridBackground(colors.dotColor)
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> HomeDashboardView(
                        state = homeDashboardState,
                        onNavigateToGroup = onNavigateToGroup,
                        onSeeAllGroups = handleScrollToPage1,
                        onCreateGroup = handleCreateGroupClick,
                        onNavigateToQRCode = onNavigateToQRCode,
                        onNavigateToQuickSplit = onNavigateToQuickSplit,
                        onScanBill = { showScannerOptionsSheet = true },
                        onNavigateToExpenses = {
                            showAddPersonalInitially = true
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        },
                        onNavigateToSplitsTab = handleScrollToPage1,
                        onSplitClick = { selectedSplitForDetail = it },
                        onPersonalClick = { selectedPersonalForDetail = it },
                        onGroupExpenseClick = { selectedGroupExpenseForDetail = it },
                        onConnectUser = { connectedUserPopup = it },
                        onConfirmSettlement = { splitId ->
                            homeViewModel.confirmSettlement(splitId) { success, error ->
                                if (success) Toast.makeText(context, "Settlement confirmed!", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDeclineSettlement = { splitId ->
                            homeViewModel.declineSettlement(splitId) { success, error ->
                                if (success) Toast.makeText(context, "Settlement declined", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Error: $error", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onUploadAvatar = { uri ->
                            homeViewModel.uploadAvatar(context, uri) { success, error ->
                                if (success) Toast.makeText(context, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                                else Toast.makeText(context, "Upload failed: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    1 -> SplitExpensesScreen(
                        onNavigateToGroup = onNavigateToGroup,
                        onNavigateToQuickSplit = onNavigateToQuickSplit,
                        onNavigateToDirectSplit = onNavigateToDirectSplit,
                        onBack = handleScrollToPage0
                    )
                    2 -> PersonalExpensesScreen(
                        showAddPersonalInitially = showAddPersonalInitially,
                        initialSelectedExpenseId = targetPersonalExpenseId,
                        onNavigateToQuickSplit = onNavigateToQuickSplit,
                        onNavigateToGroup = onNavigateToGroup,
                        onBack = handleScrollToPage0
                    )
                    3 -> ProfileSettingsView(
                        onNavigateToQRCode = onNavigateToQRCode,
                        onNavigateToReports = onNavigateToReports,
                        onSignOut = {
                            coroutineScope.launch {
                                try {
                                    pagerState.scrollToPage(0)
                                } catch (e: Exception) {}
                                onSignOut()
                            }
                        }
                    )
                }
            }

            if (showCreateGroupSheet) {
                com.splitsmith.app.ui.split.CreateGroupBottomSheet(
                    isExpenseTracker = createGroupIsTracker,
                    onDismiss = { showCreateGroupSheet = false },
                    onGroupCreated = { groupId ->
                        showCreateGroupSheet = false
                        onNavigateToGroup(groupId)
                    }
                )
            }

            if (showJoinGroupSheet) {
                JoinGroupDialog(
                    onDismiss = { showJoinGroupSheet = false },
                    onGroupJoined = { groupId ->
                        showJoinGroupSheet = false
                        onNavigateToGroup(groupId)
                    }
                )
            }

            if (joinConfirmGroup != null) {
                val group = joinConfirmGroup!!
                AlertDialog(
                    onDismissRequest = { joinConfirmGroup = null },
                    title = { Text("Join Group?", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
                    text = { Text("Do you want to request to join group '${group.name}'? An admin will need to approve your request before you can start splitting expenses.", fontFamily = OutfitFamily) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                joinConfirmGroup = null
                                coroutineScope.launch {
                                    try {
                                        FirebaseManager.requestToJoinGroup(group.id)
                                        Toast.makeText(context, "Join request sent to admin!", Toast.LENGTH_SHORT).show()
                                        onNavigateToGroup(group.id)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to send request: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        ) {
                            Text("Request to Join", color = colors.inkPrimary, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { joinConfirmGroup = null }) {
                            Text("Cancel", color = colors.inkMuted, fontFamily = OutfitFamily)
                        }
                    },
                    shape = RoundedCornerShape(d.radiusLG),
                    containerColor = colors.surfaceCard
                )
            }

            if (selectedSplitForDetail != null) {
                val s = selectedSplitForDetail!!
                val currentUid = FirebaseManager.currentUserId ?: ""
                val peerId = if (s.paidBy == currentUid) s.withUser else s.paidBy
                var peerProfile by remember(peerId) { mutableStateOf<UserProfile?>(null) }
                LaunchedEffect(peerId) {
                    try {
                        peerProfile = FirebaseManager.getUserProfile(peerId)
                    } catch (e: Exception) {}
                }
                DirectSplitDetailBottomSheet(
                    split = s,
                    peerName = peerProfile?.displayName ?: "User",
                    peerAvatar = peerProfile?.avatarUrl ?: "",
                    peerUpi = peerProfile?.upiId ?: "",
                    currentUserId = currentUid,
                    onDismiss = { selectedSplitForDetail = null }
                )
            }

            if (selectedPersonalForDetail != null) {
                PersonalExpenseDetailBottomSheet(
                    expense = selectedPersonalForDetail!!,
                    onDismiss = { selectedPersonalForDetail = null },
                    onGoToPersonalExpenses = { expId ->
                        targetPersonalExpenseId = expId
                        selectedPersonalForDetail = null
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(2)
                        }
                    }
                )
            }

            if (selectedGroupExpenseForDetail != null) {
                GroupExpenseDetailBottomSheet(
                    groupExpense = selectedGroupExpenseForDetail!!,
                    currentUserId = FirebaseManager.currentUserId ?: "",
                    onNavigateToGroup = onNavigateToGroup,
                    onNavigateToAddExpense = onNavigateToAddExpense,
                    onDismiss = { selectedGroupExpenseForDetail = null }
                )
            }

            if (showScannerOptionsSheet) {
                ScannerOptionsBottomSheet(
                    onCameraClick = {
                        try {
                            com.splitsmith.app.MainActivity.isSystemPickerActive = true
                            val photoFile = java.io.File(context.cacheDir, "camera_slip_${System.currentTimeMillis()}.jpg")
                            val photoUri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                photoFile
                            )
                            tempCameraUri = photoUri
                            cameraCaptureLauncher.launch(photoUri)
                        } catch (e: Exception) {
                            com.splitsmith.app.MainActivity.isSystemPickerActive = false
                            Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onGalleryClick = {
                        com.splitsmith.app.MainActivity.isSystemPickerActive = true
                        galleryLauncher.launch("image/*")
                    },
                    onDismiss = { showScannerOptionsSheet = false }
                )
            }

            if (showQuickAddSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showQuickAddSheet = false },
                    sheetState = quickAddSheetState,
                    containerColor = colors.surfaceCard,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = d.space24)
                            .padding(top = d.space8, bottom = d.space24),
                        verticalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        Text(
                            text = "Add",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textTitleLarge,
                            color = colors.inkPrimary,
                            modifier = Modifier.padding(bottom = d.space12)
                        )
                        // 0. Universal Receipt & QR Scanner
                        QuickActionRow(
                            title = "Scan Receipt, Bill or QR Code",
                            subtitle = "Camera scan or upload slip photo from gallery",
                            icon = Icons.Default.QrCode,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
                            showScannerOptionsSheet = true
                        }
                        // 1. Personal Expense
                        QuickActionRow(
                            title = "Record Personal Expense",
                            subtitle = "Log a private spend for yourself",
                            icon = Icons.Default.AccountBalanceWallet,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
                            showAddPersonalInitially = true
                            coroutineScope.launch { pagerState.animateScrollToPage(2) }
                        }
                        // 2. Split with Person
                        QuickActionRow(
                            title = "Split with Person",
                            subtitle = "Quick 1-on-1 split via email or QR",
                            icon = Icons.Default.PersonAdd,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
                            onNavigateToQuickSplit()
                        }
                        // 3. Add to Group
                        QuickActionRow(
                            title = "Add Group Expense",
                            subtitle = "Log a spend inside a shared group",
                            icon = Icons.Default.Groups,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
                            showSelectGroupForExpenseDialog = true
                        }
                        // 4. Create Split Group
                        QuickActionRow(
                            title = "Create Split Group",
                            subtitle = "Split bills & track 'who owes who' debts",
                            icon = Icons.Default.GroupAdd,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
                            createGroupIsTracker = false
                            showCreateGroupSheet = true
                        }
                        // 5. Create Expense Tracker Group
                        QuickActionRow(
                            title = "Create Expense Tracker Group",
                            subtitle = "Track trips, projects, company spend without debts",
                            icon = Icons.Default.Groups,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
                            createGroupIsTracker = true
                            showCreateGroupSheet = true
                        }
                    }
                }
            }

            if (connectedUserPopup != null) {
                val user = connectedUserPopup!!
                ConnectedUserModal(
                    user = user,
                    onDismiss = { connectedUserPopup = null },
                    onStartQuickSplit = {
                        FirebaseManager.pendingQuickSplitUser = user
                        connectedUserPopup = null
                        onNavigateToQuickSplit()
                    },
                    onAddToGroup = {
                        connectedUserPopup = null
                        showSelectGroupForExpenseDialog = true
                    }
                )
            }

            if (showSelectGroupForExpenseDialog) {
                val activeGroups = groupsState.value
                ModalBottomSheet(
                    onDismissRequest = { showSelectGroupForExpenseDialog = false },
                    sheetState = selectGroupSheetState,
                    containerColor = colors.surfaceCard,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = d.space24, vertical = d.space16),
                        verticalArrangement = Arrangement.spacedBy(d.space12)
                    ) {
                        Text(
                            text = "Select Group",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textTitleLarge,
                            color = colors.inkPrimary,
                            modifier = Modifier.padding(bottom = d.space8)
                        )
                        if (activeGroups.isEmpty()) {
                            Text(
                                "No active groups yet. Create one to add expenses.",
                                fontFamily = OutfitFamily,
                                fontSize = d.textBodyLarge,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(vertical = d.space24)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp),
                                verticalArrangement = Arrangement.spacedBy(d.space8)
                            ) {
                                items(activeGroups) { group ->
                                    Surface(
                                        onClick = {
                                            showSelectGroupForExpenseDialog = false
                                            onNavigateToAddExpense(group.id, null)
                                        },
                                        shape = RoundedCornerShape(d.radiusSM),
                                        color = colors.canvasChalk,
                                        border = BorderStroke(1.dp, colors.borderWhisper),
                                        modifier = Modifier.fillMaxWidth().height(d.rowHeightSm)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = d.space16),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(d.space12)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Groups,
                                                    contentDescription = "Group",
                                                    tint = colors.inkPrimary,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Text(
                                                    text = group.name,
                                                    fontFamily = OutfitFamily,
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = d.textBodyLarge,
                                                    color = colors.inkPrimary
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Go",
                                                tint = colors.inkMuted,
                                                modifier = Modifier.size(20.dp)
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

@Composable
fun RowScope.NavIconSlot(
    index: Int,
    icon: ImageVector,
    label: String,
    selectedTab: Int,
    accentIndigo: Color,
    accentIndigoLight: Color,
    inkMuted: Color,
    d: com.splitsmith.app.theme.Dimens,
    onClick: () -> Unit
) {
    val isSelected = selectedTab == index
    val pillColor by animateColorAsState(
        targetValue = if (isSelected) accentIndigoLight else Color.Transparent,
        label = "navPill$index"
    )
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) accentIndigo else inkMuted,
        label = "navTint$index"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.1f else 1.0f,
        label = "navScale$index"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(d.navPillSize)
                .clip(CircleShape)
                .background(pillColor)
                .graphicsLayer(scaleX = scale, scaleY = scale),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(d.navIconSize)
            )
        }
    }
}

@Composable
fun QuickActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    colors: com.splitsmith.app.theme.SplitColors,
    d: com.splitsmith.app.theme.Dimens,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(d.radiusLG))
            .background(colors.canvasChalk.copy(alpha = 0.06f))
            .border(1.dp, colors.borderWhisper, RoundedCornerShape(d.radiusLG))
            .clickable { onClick() }
            .padding(horizontal = d.space16, vertical = d.space12),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.inkPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.canvasChalk,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(d.space16))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyLarge, color = colors.inkPrimary)
            Text(subtitle, fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}
data class RecentActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val date: Long,
    val isPositive: Boolean,
    val isSettled: Boolean = false,
    val rawSplit: DirectSplit? = null,
    val rawPersonal: com.splitsmith.app.data.PersonalExpense? = null,
    val rawGroupExpense: com.splitsmith.app.data.GroupExpenseWithContext? = null
)

// ─── 1. HOME DASHBOARD ───────────────────────────────────────
@Composable
fun HomeDashboardView(
    state: HomeDashboardUiState,
    onNavigateToGroup: (groupId: String) -> Unit,
    onSeeAllGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onNavigateToQRCode: () -> Unit,
    onNavigateToQuickSplit: () -> Unit = {},
    onScanBill: () -> Unit = {},
    onNavigateToExpenses: () -> Unit = {},
    onNavigateToSplitsTab: () -> Unit = {},
    onSplitClick: (DirectSplit) -> Unit = {},
    onPersonalClick: (com.splitsmith.app.data.PersonalExpense) -> Unit = {},
    onGroupExpenseClick: (com.splitsmith.app.data.GroupExpenseWithContext) -> Unit = {},
    onConnectUser: (UserProfile) -> Unit = {},
    onConfirmSettlement: (splitId: String) -> Unit = {},
    onDeclineSettlement: (splitId: String) -> Unit = {},
    onUploadAvatar: (android.net.Uri) -> Unit = {}
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var activeInsightTab by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            onUploadAvatar(uri)
        }
    }

    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = com.journeyapps.barcodescanner.ScanContract()
    ) { result ->
        if (result.contents != null) {
            val rawCode = result.contents.trim()
            coroutineScope.launch {
                try {
                    val parsed = com.splitsmith.app.util.QrPayloadParser.parse(rawCode)
                    when (parsed) {
                        is com.splitsmith.app.util.ParsedQrPayload.UserQr -> {
                            val user = FirebaseManager.searchUserByCode(rawCode)
                            if (user != null) {
                                FirebaseManager.addConnection(user.uid)
                                onConnectUser(user)
                            } else {
                                Toast.makeText(context, "User QR code not found", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.splitsmith.app.util.ParsedQrPayload.GroupQr -> {
                            val group = FirebaseManager.getGroupOnce(parsed.groupId)
                            if (group != null) {
                                val uid = FirebaseManager.currentUserId
                                if (group.members[uid] == true) {
                                    Toast.makeText(context, "Already a member of ${group.name}!", Toast.LENGTH_SHORT).show()
                                    onNavigateToGroup(group.id)
                                } else {
                                    FirebaseManager.requestToJoinGroup(parsed.groupId)
                                    Toast.makeText(context, "Join request sent to admin!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(context, "Group not found or expired", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.splitsmith.app.util.ParsedQrPayload.Unknown -> {
                            val group = FirebaseManager.getGroupOnce(rawCode)
                            if (group != null) {
                                val uid = FirebaseManager.currentUserId
                                if (group.members[uid] == true) {
                                    Toast.makeText(context, "Already a member of ${group.name}!", Toast.LENGTH_SHORT).show()
                                    onNavigateToGroup(group.id)
                                } else {
                                    FirebaseManager.requestToJoinGroup(group.id)
                                    Toast.makeText(context, "Join request sent to admin!", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                val user = FirebaseManager.searchUserByCode(rawCode)
                                if (user != null) {
                                    FirebaseManager.addConnection(user.uid)
                                    onConnectUser(user)
                                } else {
                                    Toast.makeText(context, "QR code not recognized (No matching group or user)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "QR code not recognized. Please scan a valid SplitSmith code.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val greetingTime = remember {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = d.space24, top = d.space24, end = d.space24, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(d.space20)
    ) {
        // ── 1. App Bar / Header ──────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SplitSmith",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textHeadlineLarge,
                    color = colors.inkPrimary,
                    letterSpacing = (-0.5).sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    // Alert Bell with Notification Dot
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard)
                            .border(BorderStroke(1.dp, colors.borderWhisper), CircleShape)
                            .clickable {
                                if (state.pendingSettlementApproval != null) {
                                    Toast.makeText(
                                        context,
                                        "Pending settlement: ₹${state.pendingSettlementApproval.myShare.formatCurrency()}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(context, "No pending notifications", Toast.LENGTH_SHORT).show()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        if (state.pendingSettlementApproval != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end = 5.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(colors.alertRed)
                            )
                        }
                    }

                    // QR Scanner Icon Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceCard)
                            .border(BorderStroke(1.dp, colors.borderWhisper), CircleShape)
                            .clickable {
                                val options = com.journeyapps.barcodescanner.ScanOptions()
                                options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                options.setPrompt("Scan a SplitSmith Group or User QR Code")
                                options.setCameraId(0)
                                options.setBeepEnabled(false)
                                options.setBarcodeImageEnabled(true)
                                options.setOrientationLocked(true)
                                qrScanLauncher.launch(options)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Profile Avatar Indicator
                    var showProfileDialog by remember { mutableStateOf(false) }

                    if (showProfileDialog && state.userProfile != null) {
                        AlertDialog(
                            onDismissRequest = { showProfileDialog = false },
                            containerColor = colors.surfaceCard,
                            shape = RoundedCornerShape(d.radiusLG),
                            title = {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Text(
                                        "Profile",
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textTitleLarge,
                                        color = colors.inkPrimary
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(d.space16)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = !state.isUploadingAvatar) {
                                                photoPickerLauncher.launch("image/*")
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        UserAvatar(
                                            avatarUrl = state.userProfile.avatarUrl,
                                            displayName = state.userProfile.displayName,
                                            size = 80.dp
                                        )
                                        if (state.isUploadingAvatar) {
                                            Box(
                                                modifier = Modifier
                                                    .matchParentSize()
                                                    .background(Color.Black.copy(alpha = 0.4f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    color = Color.White,
                                                    modifier = Modifier.size(24.dp),
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = state.userProfile.displayName,
                                            fontFamily = OutfitFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = d.textTitleMedium,
                                            color = colors.inkPrimary
                                        )
                                        Text(
                                            text = state.userProfile.email,
                                            fontFamily = OutfitFamily,
                                            fontSize = d.textLabelMedium,
                                            color = colors.inkMuted
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(d.space4))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                showProfileDialog = false
                                                val options = com.journeyapps.barcodescanner.ScanOptions()
                                                options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                                options.setPrompt("Scan a SplitSmith Group QR Code to Join")
                                                options.setCameraId(0)
                                                options.setBeepEnabled(false)
                                                options.setBarcodeImageEnabled(true)
                                                options.setOrientationLocked(true)
                                                qrScanLauncher.launch(options)
                                            },
                                            shape = RoundedCornerShape(d.radiusMD),
                                            border = BorderStroke(1.dp, colors.borderWhisper),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkPrimary),
                                            modifier = Modifier.weight(1f).height(d.buttonHeight)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.QrCodeScanner, contentDescription = "Scan QR", modifier = Modifier.size(18.dp))
                                                Text("Scan QR", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(d.space12))

                                        Button(
                                            onClick = {
                                                showProfileDialog = false
                                                onNavigateToQRCode()
                                            },
                                            shape = RoundedCornerShape(d.radiusMD),
                                            colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                                            modifier = Modifier.weight(1f).height(d.buttonHeight)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.QrCode, contentDescription = "Show QR", modifier = Modifier.size(18.dp), tint = colors.canvasChalk)
                                                Text("Show QR", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, color = colors.canvasChalk)
                                            }
                                        }
                                    }

                                    TextButton(
                                        onClick = { showProfileDialog = false },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Close", fontFamily = OutfitFamily, color = colors.inkMuted, fontWeight = FontWeight.Medium)
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = null
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(1.dp, colors.borderWhisper), CircleShape)
                            .clickable { showProfileDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.userProfile != null) {
                            UserAvatar(
                                avatarUrl = state.userProfile.avatarUrl,
                                displayName = state.userProfile.displayName,
                                size = 36.dp
                            )
                        }
                    }
                }
            }
        }

        // ── 2. Greeting Headline (Directly above Net Position Hero) ──
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val firstName = state.userProfile?.displayName?.trim()?.split(" ")?.firstOrNull()?.ifEmpty { "there" } ?: "there"
                Text(
                    text = "$greetingTime, $firstName",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 21.sp,
                    color = colors.inkPrimary,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Here is your financial overview",
                    fontFamily = OutfitFamily,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
            }
        }

        // ── 3. Unified Net Position Hero Card ─────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(d.radiusLG),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                border = BorderStroke(1.dp, colors.borderWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    // Net Position headline & badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "NET POSITION",
                                fontFamily = OutfitFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted,
                                letterSpacing = 1.2.sp
                            )
                            val netText = if (state.netPosition >= 0) "+₹${state.netPosition.formatCurrency()}" else "-₹${(-state.netPosition).formatCurrency()}"
                            Text(
                                text = netText,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp,
                                color = if (state.netPosition >= 0) colors.inkPrimary else colors.alertRed
                            )
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (state.netPosition >= 0) colors.inkPrimary.copy(alpha = 0.08f)
                                    else colors.alertRed.copy(alpha = 0.12f)
                                )
                                .border(
                                    BorderStroke(0.5.dp, colors.borderWhisper),
                                    RoundedCornerShape(999.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (state.netPosition == 0.0) "Settled" else if (state.netPosition > 0) "Owed to you" else "You owe",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (state.netPosition >= 0) colors.inkPrimary else colors.alertRed
                            )
                        }
                    }

                    // Receivable vs Payable Sub-row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Receivable:",
                                fontFamily = OutfitFamily,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                            Text(
                                text = "₹${state.totalDirectReceivable.formatCurrency()}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.inkPrimary
                            )
                        }

                        Text(text = "•", fontSize = 11.sp, color = colors.borderWhisper)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Payable:",
                                fontFamily = OutfitFamily,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                            Text(
                                text = "₹${state.totalDirectPayable.formatCurrency()}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.alertRed
                            )
                        }
                    }

                    HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                    // Monthly Budget with progress & daily pacing
                    val animatedProgress by animateFloatAsState(
                        targetValue = state.budgetProgress,
                        animationSpec = tween(durationMillis = 800),
                        label = "budgetBar"
                    )
                    val barColor = when {
                        state.budgetProgress < 0.70f -> colors.inkPrimary
                        state.budgetProgress < 0.90f -> Color(0xFFE8A838)
                        else -> Color(0xFFE05252)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MONTHLY BUDGET",
                                fontFamily = OutfitFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.inkMuted,
                                letterSpacing = 1.2.sp
                            )

                            if (state.remainingBudget >= 0) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "₹${state.remainingBudget.formatCurrency()} left",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "of ₹${state.budgetLimit.formatCurrency()}",
                                        fontFamily = OutfitFamily,
                                        fontSize = 11.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            } else {
                                Text(
                                    text = "Overspent: ₹${(-state.remainingBudget).formatCurrency()}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = colors.alertRed
                                )
                            }
                        }

                        // Slim progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(colors.borderWhisper)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(barColor)
                            )
                        }

                        Text(
                            text = "Daily allowance: ₹${state.dailyAllowance.toInt()}/day for next ${state.daysRemaining} days",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        }

        // ── 4. Option A Quick Actions Strip ──────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "QUICK ACTIONS",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    // Action 1: Scan Bill (Primary Solid)
                    Surface(
                        onClick = onScanBill,
                        shape = RoundedCornerShape(d.radiusMD),
                        color = colors.inkPrimary,
                        modifier = Modifier.weight(1f).height(68.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Scan Bill",
                                tint = colors.canvasChalk,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Scan Bill",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = colors.canvasChalk
                            )
                        }
                    }

                    // Action 2: Split Bill (Outlined)
                    Surface(
                        onClick = onNavigateToQuickSplit,
                        shape = RoundedCornerShape(d.radiusMD),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.borderWhisper),
                        modifier = Modifier.weight(1f).height(68.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Split Bill",
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Split Bill",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }

                    // Action 3: Log Expense (Outlined)
                    Surface(
                        onClick = onNavigateToExpenses,
                        shape = RoundedCornerShape(d.radiusMD),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.borderWhisper),
                        modifier = Modifier.weight(1f).height(68.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Log Expense",
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Log Expense",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }
            }
        }

        // ── 5. Action Required Banner (Conditional) ───────────────────
        if (state.pendingSettlementApproval != null) {
            item {
                Card(
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.alertRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d.space16, vertical = d.space12),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = d.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(d.space12)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.alertRed)
                            )
                            Column {
                                Text(
                                    text = "Settlement Confirmation",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.inkPrimary
                                )
                                Text(
                                    text = "Friend marked ₹${state.pendingSettlementApproval.myShare.formatCurrency()} as settled with you",
                                    fontFamily = OutfitFamily,
                                    fontSize = 10.sp,
                                    color = colors.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(d.space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { onConfirmSettlement(state.pendingSettlementApproval.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                                shape = RoundedCornerShape(d.radiusSM),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Confirm", fontFamily = OutfitFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.canvasChalk)
                            }

                            OutlinedButton(
                                onClick = { onDeclineSettlement(state.pendingSettlementApproval.id) },
                                shape = RoundedCornerShape(d.radiusSM),
                                border = BorderStroke(0.5.dp, colors.borderWhisper),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Decline", fontFamily = OutfitFamily, fontSize = 10.sp, fontWeight = FontWeight.Normal, color = colors.inkMuted)
                            }
                        }
                    }
                }
            }
        }

        // ── 6. Spending Insights Card (Options B + C Blended) ─────────
        item {
            Card(
                shape = RoundedCornerShape(d.radiusLG),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                border = BorderStroke(1.dp, colors.borderWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space12)
                ) {
                    // Header Row with Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPENDING INSIGHTS",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 1.2.sp
                        )

                        // 2-Pill Switcher
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(d.radiusMD))
                                .background(colors.canvasChalk)
                                .border(BorderStroke(0.5.dp, colors.borderWhisper), RoundedCornerShape(d.radiusMD))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(d.radiusSM))
                                    .background(if (activeInsightTab == 0) colors.inkPrimary else Color.Transparent)
                                    .clickable { activeInsightTab = 0 }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "7-Day Trend",
                                    fontFamily = OutfitFamily,
                                    fontWeight = if (activeInsightTab == 0) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = if (activeInsightTab == 0) colors.canvasChalk else colors.inkMuted
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(d.radiusSM))
                                    .background(if (activeInsightTab == 1) colors.inkPrimary else Color.Transparent)
                                    .clickable { activeInsightTab = 1 }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Categories",
                                    fontFamily = OutfitFamily,
                                    fontWeight = if (activeInsightTab == 1) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp,
                                    color = if (activeInsightTab == 1) colors.canvasChalk else colors.inkMuted
                                )
                            }
                        }
                    }

                    if (activeInsightTab == 0) {
                        // VIEW 1: 7-DAY TREND
                        Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "₹${state.weekTotalSpend.formatCurrency()}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "Total spent this week",
                                        fontFamily = OutfitFamily,
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }

                            // Minimalist Linear Bar Graph
                            val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(84.dp)
                                    .padding(vertical = 4.dp)
                            ) {
                                // Subtle dashed benchmark line across chart (minimalist, no text overlay)
                                val benchmarkFraction = (state.dailyBudgetBenchmark / state.maxSpend).toFloat().coerceIn(0.2f, 0.85f)
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                ) {
                                    val y = size.height * (1f - benchmarkFraction)
                                    drawLine(
                                        color = colors.borderWhisper.copy(alpha = 0.5f),
                                        start = androidx.compose.ui.geometry.Offset(0f, y),
                                        end = androidx.compose.ui.geometry.Offset(size.width, y),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                // 7 Vertical Linear Pillars
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = d.space8),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    state.currentWeekDaySpends.forEachIndexed { idx, spend ->
                                        val isToday = idx == state.currentDayOfWeekIdx
                                        val heightFraction = (spend / state.maxSpend).toFloat().coerceIn(if (spend > 0) 0.12f else 0.05f, 1f)
                                        val barColor = when {
                                            spend > state.dailyBudgetBenchmark * 1.25 -> colors.alertRed
                                            isToday -> colors.inkPrimary
                                            else -> colors.inkMuted.copy(alpha = 0.7f)
                                        }

                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            // Pillar container
                                            Box(
                                                modifier = Modifier
                                                    .width(6.dp)
                                                    .height(56.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(colors.borderWhisper.copy(alpha = 0.35f)),
                                                contentAlignment = Alignment.BottomCenter
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .fillMaxHeight(heightFraction)
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(barColor)
                                                )
                                            }

                                            // Day Letter + Active Dot
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = dayLetters[idx],
                                                    fontFamily = OutfitFamily,
                                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 9.sp,
                                                    color = if (isToday) colors.inkPrimary else colors.inkMuted
                                                )
                                                if (isToday) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(3.dp)
                                                            .clip(CircleShape)
                                                            .background(colors.inkPrimary)
                                                    )
                                                } else {
                                                    Spacer(modifier = Modifier.height(3.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // VIEW 2: CATEGORIES BREAKDOWN
                        Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                Column {
                                    Text(
                                        text = "₹${state.totalPersonalSpent.formatCurrency()}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = "Total outflow this month",
                                        fontFamily = OutfitFamily,
                                        fontSize = 10.sp,
                                        color = colors.inkMuted
                                    )
                                }

                                Text(
                                    text = "${state.categoryBreakdown.size} CATEGORIES",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 10.sp,
                                    color = colors.inkMuted
                                )
                            }

                            // Segmented proportion bar
                            if (state.categoryBreakdown.isEmpty() || state.totalPersonalSpent <= 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.borderWhisper)
                                )
                            } else {
                                val segmentColors = listOf(
                                    colors.inkPrimary,
                                    colors.inkMuted,
                                    colors.inkMuted.copy(alpha = 0.5f),
                                    colors.borderWhisper
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(colors.borderWhisper)
                                ) {
                                    val topSegments = state.categoryBreakdown.take(4)
                                    topSegments.forEachIndexed { index, pair ->
                                        val weight = (pair.second / state.totalPersonalSpent).toFloat().coerceIn(0.02f, 1f)
                                        Box(
                                            modifier = Modifier
                                                .weight(weight)
                                                .fillMaxHeight()
                                                .background(segmentColors.getOrElse(index) { colors.borderWhisper })
                                        )
                                    }
                                }
                            }

                            // Top 3 Category Breakdown rows
                            val topThree = state.categoryBreakdown.take(3)
                            val dotColors = listOf(
                                colors.inkPrimary,
                                colors.inkMuted,
                                colors.inkMuted.copy(alpha = 0.6f)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                                topThree.forEachIndexed { index, (catName, catAmount) ->
                                    val pct = if (state.totalPersonalSpent > 0) ((catAmount / state.totalPersonalSpent) * 100).toInt() else 0
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(d.space8)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(dotColors.getOrElse(index) { colors.inkMuted })
                                            )
                                            Text(
                                                text = catName,
                                                fontFamily = OutfitFamily,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                color = colors.inkPrimary
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(d.space12)
                                        ) {
                                            Text(
                                                text = "$pct%",
                                                fontFamily = OutfitFamily,
                                                fontSize = 10.sp,
                                                color = colors.inkMuted
                                            )
                                            Text(
                                                text = "₹${catAmount.formatCurrency()}",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = colors.inkPrimary
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

        // ── 7. Curated Recent Activity (3 Items Only) ─────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "LATEST TRANSACTIONS",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "All Splits →",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = colors.inkMuted,
                        modifier = Modifier.clickable { onNavigateToSplitsTab() }
                    )
                    Text(text = "•", fontSize = 10.sp, color = colors.borderWhisper)
                    Text(
                        text = "All Expenses →",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = colors.inkMuted,
                        modifier = Modifier.clickable { onNavigateToExpenses() }
                    )
                }
            }
        }

        if (state.curatedActivities.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = d.space24),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recent transactions",
                            fontFamily = OutfitFamily,
                            fontSize = 12.sp,
                            color = colors.inkMuted
                        )
                    }
                }
            }
        } else {
            item {
                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        state.curatedActivities.forEachIndexed { index, item ->
                            val (badgeText, badgeBg, badgeFg) = when {
                                item.rawSplit != null -> Triple("SPLIT", colors.inkPrimary.copy(alpha = 0.08f), colors.inkPrimary)
                                item.rawPersonal != null -> Triple("SOLO", colors.canvasChalk, colors.inkMuted)
                                else -> Triple("GROUP", colors.inkPrimary.copy(alpha = 0.08f), colors.inkPrimary)
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.rawSplit != null) onSplitClick(item.rawSplit)
                                        else if (item.rawPersonal != null) onPersonalClick(item.rawPersonal)
                                        else if (item.rawGroupExpense != null) onGroupExpenseClick(item.rawGroupExpense)
                                    }
                                    .padding(horizontal = d.space16, vertical = d.space12),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f).padding(end = d.space8),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(badgeBg)
                                            .border(BorderStroke(0.5.dp, colors.borderWhisper), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = badgeText,
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            color = badgeFg
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = item.title,
                                            fontFamily = OutfitFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = colors.inkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.subtitle,
                                            fontFamily = OutfitFamily,
                                            fontSize = 10.sp,
                                            color = colors.inkMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    val amountPrefix = if (item.rawPersonal != null) "" else if (item.isPositive) "+" else "-"
                                    val amountColor = if (item.rawPersonal != null) colors.inkMuted else if (item.isPositive) colors.inkPrimary else colors.alertRed
                                    Text(
                                        text = "${amountPrefix}₹${item.amount.formatCurrency()}",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = amountColor
                                    )
                                    Text(
                                        text = if (item.rawPersonal != null) "Logged" else if (item.isPositive) "You lent" else "You owe",
                                        fontFamily = OutfitFamily,
                                        fontSize = 9.sp,
                                        color = colors.inkMuted
                                    )
                                }
                            }

                            if (index < state.curatedActivities.lastIndex) {
                                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
// ─── 4. PROFILE VIEW (Style B) ───────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsView(
    onNavigateToQRCode: () -> Unit,
    onNavigateToReports: () -> Unit,
    onSignOut: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val uid = FirebaseManager.currentUserId ?: ""
    val userProfileFlow = remember { FirebaseManager.observeUserProfile() }
    val userProfileState = userProfileFlow.collectAsState(initial = null)
    val profile = userProfileState.value

    var upiIdInput by remember { mutableStateOf("") }
    var displayNameInput by remember { mutableStateOf("") }
    var budgetLimitInput by remember { mutableStateOf("") }

    var showEditUpiDialog by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showEditBudgetDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    val avatarPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isCheckingForUpdate by remember { mutableStateOf(false) }
    var updateReleaseInfo by remember { mutableStateOf<com.splitsmith.app.util.AppReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var cacheSizeText by remember { mutableStateOf(getReceiptCacheSize(context)) }

    val settingsPhotoPicker = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val base64 = uriToBase64(context, uri)
                    if (base64 != null) {
                        FirebaseManager.updateAvatarUrl(base64)
                        showAvatarPickerSheet = false
                        Toast.makeText(context, "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            val release = com.splitsmith.app.util.AppUpdateManager.checkForUpdates()
            if (release != null && release.isNewer) {
                updateReleaseInfo = release
                showUpdateDialog = true
            }
        } catch (e: Exception) {}
    }

    LaunchedEffect(profile) {
        if (profile != null) {
            upiIdInput = profile.upiId
            displayNameInput = profile.displayName
            val lim = profile.budget.limit.toInt()
            budgetLimitInput = if (lim > 0) lim.toString() else "15000"
        }
    }

    // ── Dialog: Edit UPI ──────────────────────────────────────────
    if (showEditUpiDialog) {
        AlertDialog(
            onDismissRequest = { showEditUpiDialog = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(d.radiusLG),
            title = { Text("Edit UPI ID", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary) },
            text = {
                OutlinedTextField(
                    value = upiIdInput,
                    onValueChange = { upiIdInput = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                    shape = RoundedCornerShape(d.radiusSM),
                    label = { Text("UPI ID / Phone Handle", fontFamily = OutfitFamily, color = colors.inkMuted) },
                    placeholder = { Text("name@okhdfcbank", fontFamily = OutfitFamily, color = colors.inkMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.borderWhisper,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary,
                        focusedContainerColor = colors.surfaceCard,
                        unfocusedContainerColor = colors.surfaceCard
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                FirebaseManager.updateUpiId(upiIdInput.trim())
                                showEditUpiDialog = false
                                Toast.makeText(context, "UPI ID updated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                    shape = RoundedCornerShape(d.radiusMD)
                ) {
                    Text("Save", fontFamily = OutfitFamily, color = colors.canvasChalk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditUpiDialog = false }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            }
        )
    }

    // ── Dialog: Edit Display Name ────────────────────────────────
    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(d.radiusLG),
            title = { Text("Edit Display Name", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary) },
            text = {
                OutlinedTextField(
                    value = displayNameInput,
                    onValueChange = { displayNameInput = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                    shape = RoundedCornerShape(d.radiusSM),
                    label = { Text("Display Name", fontFamily = OutfitFamily, color = colors.inkMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.borderWhisper,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary,
                        focusedContainerColor = colors.surfaceCard,
                        unfocusedContainerColor = colors.surfaceCard
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (displayNameInput.trim().isEmpty()) {
                            Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        coroutineScope.launch {
                            try {
                                FirebaseManager.updateDisplayName(displayNameInput.trim())
                                showEditNameDialog = false
                                Toast.makeText(context, "Display name updated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                    shape = RoundedCornerShape(d.radiusMD)
                ) {
                    Text("Save", fontFamily = OutfitFamily, color = colors.canvasChalk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            }
        )
    }

    // ── Dialog: Edit Budget Limit (Persistent to Firestore) ───────
    if (showEditBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showEditBudgetDialog = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(d.radiusLG),
            title = { Text("Edit Budget Limit", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                    OutlinedTextField(
                        value = budgetLimitInput,
                        onValueChange = { budgetLimitInput = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                        shape = RoundedCornerShape(d.radiusSM),
                        label = { Text("Monthly limit (INR)", fontFamily = OutfitFamily, color = colors.inkMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.inkPrimary,
                            unfocusedBorderColor = colors.borderWhisper,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary,
                            focusedContainerColor = colors.surfaceCard,
                            unfocusedContainerColor = colors.surfaceCard
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                    )
                    Text(
                        text = "Used for real-time daily budget pacing on the Home screen.",
                        fontFamily = OutfitFamily,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = budgetLimitInput.trim().toIntOrNull() ?: 15000
                        coroutineScope.launch {
                            try {
                                FirebaseManager.updateBudgetSettings(parsed)
                                showEditBudgetDialog = false
                                Toast.makeText(context, "Monthly budget updated to ₹$parsed!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                    shape = RoundedCornerShape(d.radiusMD)
                ) {
                    Text("Save", fontFamily = OutfitFamily, color = colors.canvasChalk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditBudgetDialog = false }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            }
        )
    }

    // ── Dialog: Sign Out Safety Guard ────────────────────────────
    if (showSignOutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirmDialog = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(d.radiusLG),
            title = {
                Text(
                    "Sign Out of SplitSmith?",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textTitleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Text(
                    "Your encrypted receipt cache and local data will remain securely preserved on this device.",
                    fontFamily = OutfitFamily,
                    fontSize = d.textBodyMedium,
                    color = colors.inkMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirmDialog = false
                        FirebaseManager.signOut()
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.alertRed),
                    shape = RoundedCornerShape(d.radiusMD)
                ) {
                    Text("Sign Out", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.canvasChalk)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirmDialog = false }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            }
        )
    }

    // ── Dialog: GitHub In-App Update ─────────────────────────────
    if (showUpdateDialog && updateReleaseInfo != null) {
        val info = updateReleaseInfo!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(d.radiusLG),
            title = {
                Text(
                    "New Update Available (${info.tagName})",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textTitleLarge,
                    color = colors.inkPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                    Text(
                        "A new version of SplitSmith is available on GitHub!",
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyMedium,
                        color = colors.inkMuted
                    )
                    if (info.releaseNotes.isNotEmpty()) {
                        Text(
                            "Release Notes:",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textBodyMedium,
                            color = colors.inkPrimary
                        )
                        Text(
                            info.releaseNotes,
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = colors.inkMuted
                        )
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                    OutlinedButton(
                        onClick = {
                            showUpdateDialog = false
                            com.splitsmith.app.util.AppUpdateManager.openInBrowser(context, "https://splitsmith.web.app/")
                        },
                        shape = RoundedCornerShape(d.radiusMD)
                    ) {
                        Text("Browser", fontFamily = OutfitFamily, color = colors.inkPrimary)
                    }
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            Toast.makeText(context, "Downloading update...", Toast.LENGTH_SHORT).show()
                            com.splitsmith.app.util.AppUpdateManager.downloadAndInstallApk(context, info.downloadUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                        shape = RoundedCornerShape(d.radiusMD)
                    ) {
                        Text("Download & Install", fontFamily = OutfitFamily, color = colors.canvasChalk)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("Later", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            }
        )
    }

    // ── Sheet: Edit Avatar / Preset Picker ───────────────────────
    if (showAvatarPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAvatarPickerSheet = false },
            sheetState = avatarPickerSheetState,
            containerColor = colors.surfaceCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = d.space24, vertical = d.space24),
                verticalArrangement = Arrangement.spacedBy(d.space16)
            ) {
                Text(
                    text = "Edit Profile Picture",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textTitleLarge,
                    color = colors.inkPrimary
                )
                Text(
                    text = "Select a character preset or upload your own:",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )

                Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                        (1..4).forEach { idx ->
                            val avatarName = "avatar_$idx"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .clickable {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.updateAvatarUrl(avatarName)
                                                showAvatarPickerSheet = false
                                                Toast.makeText(context, "Avatar applied!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                UserAvatar(avatarUrl = avatarName, displayName = profile?.displayName ?: "A", size = 56.dp)
                            }
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                        (5..8).forEach { idx ->
                            val avatarName = "avatar_$idx"
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .clickable {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.updateAvatarUrl(avatarName)
                                                showAvatarPickerSheet = false
                                                Toast.makeText(context, "Avatar applied!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                UserAvatar(avatarUrl = avatarName, displayName = profile?.displayName ?: "A", size = 56.dp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(d.space4))

                Surface(
                    onClick = { settingsPhotoPicker.launch("image/*") },
                    shape = RoundedCornerShape(d.radiusMD),
                    color = colors.inkPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = d.space12)) {
                        Text(
                            text = "Upload Custom Photo",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textLabelLarge,
                            color = colors.canvasChalk
                        )
                    }
                }

                val googlePhoto = FirebaseManager.currentUserPhotoUrl
                if (!googlePhoto.isNullOrEmpty()) {
                    Surface(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    FirebaseManager.updateAvatarUrl(googlePhoto)
                                    showAvatarPickerSheet = false
                                    Toast.makeText(context, "Google profile photo restored!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shape = RoundedCornerShape(d.radiusMD),
                        color = colors.canvasChalk,
                        border = BorderStroke(1.dp, colors.borderWhisper),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = d.space12)) {
                            Text(
                                text = "Use Google Profile Photo",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = d.textLabelLarge,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = d.space24, top = d.space24, end = d.space24, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(d.space16)
    ) {
        item {
            Column {
                Text(
                    text = "Settings",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textHeadlineLarge,
                    color = colors.inkPrimary,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Account, preferences & security",
                    fontFamily = OutfitFamily,
                    fontSize = 12.sp,
                    color = colors.inkMuted
                )
            }
        }

        // ── 1. EXECUTIVE IDENTITY HERO CARD ──────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(d.radiusLG),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                border = BorderStroke(1.dp, colors.borderWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(d.space16)
                    ) {
                        // Avatar with Camera Edit Badge
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clickable { showAvatarPickerSheet = true }
                        ) {
                            UserAvatar(
                                avatarUrl = profile?.avatarUrl ?: "",
                                displayName = profile?.displayName ?: "A",
                                size = 64.dp
                            )
                            Surface(
                                modifier = Modifier
                                    .size(22.dp)
                                    .align(Alignment.BottomEnd),
                                shape = CircleShape,
                                color = colors.inkPrimary,
                                border = BorderStroke(2.dp, colors.surfaceCard)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PhotoCamera,
                                        contentDescription = "Edit Avatar",
                                        tint = colors.canvasChalk,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        // Name & Identity Info
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = profile?.displayName ?: "SplitSmith User",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = colors.inkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = colors.inkPrimary.copy(alpha = 0.08f),
                                    border = BorderStroke(0.5.dp, colors.borderWhisper)
                                ) {
                                    Text(
                                        text = "VERIFIED",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = colors.inkPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Text(
                                text = (profile?.upiId ?: "").ifEmpty { "No UPI linked" },
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 12.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = profile?.email ?: "",
                                fontFamily = OutfitFamily,
                                fontSize = 11.sp,
                                color = colors.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                    // Dual Action Pill Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        Surface(
                            onClick = { onNavigateToQRCode() },
                            shape = RoundedCornerShape(d.radiusMD),
                            color = colors.canvasChalk,
                            border = BorderStroke(1.dp, colors.borderWhisper),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode,
                                    contentDescription = "My QR Code",
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "My QR Code",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("User Code", uid.take(6).uppercase())
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "User code #${uid.take(6).uppercase()} copied!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(d.radiusMD),
                            color = colors.canvasChalk,
                            border = BorderStroke(1.dp, colors.borderWhisper),
                            modifier = Modifier.weight(1f).height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy User Code",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "#${uid.take(6).uppercase()}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = colors.inkPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── 2. ACCOUNT & PAYMENTS BENTO CARD ─────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "ACCOUNT & PAYMENTS",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileSettingsRow(
                            label = "Display Name",
                            value = (profile?.displayName ?: "SplitSmith User").ifEmpty { "Not set" },
                            subtitle = "Your public name visible to friends",
                            icon = Icons.Default.Person,
                            showChevron = true,
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = { showEditNameDialog = true }
                        )

                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                        ProfileSettingsRow(
                            label = "Default UPI ID",
                            value = (profile?.upiId ?: "").ifEmpty { "Not set" },
                            subtitle = "Primary handle to receive payments",
                            icon = Icons.Default.AccountBalance,
                            showChevron = true,
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = { showEditUpiDialog = true }
                        )
                    }
                }
            }
        }

        // ── 3. FINANCIAL PREFERENCES BENTO CARD ──────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "FINANCIAL PREFERENCES",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileSettingsRow(
                            label = "Monthly Budget Limit",
                            value = "₹${(profile?.budget?.limit ?: 15000.0).toInt()}",
                            subtitle = "Powers real-time daily budget pacing",
                            icon = Icons.Default.AccountBalanceWallet,
                            showChevron = true,
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = { showEditBudgetDialog = true }
                        )

                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                        ProfileSettingsRow(
                            label = "Spend & Balance Reports",
                            value = "View analytics",
                            subtitle = "Export CSV, trends & debt summaries",
                            icon = Icons.Default.Analytics,
                            showChevron = true,
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = { onNavigateToReports() }
                        )
                    }
                }
            }
        }

        // ── 4. SECURITY & PRIVACY BENTO CARD ─────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "SECURITY & PRIVACY",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                val prefs = remember { context.getSharedPreferences("splitsmith_prefs", android.content.Context.MODE_PRIVATE) }
                var isBiometricEnabled by remember { mutableStateOf(prefs.getBoolean("key_biometric_enabled", false)) }
                val themeController = LocalThemeController.current

                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileSettingsRow(
                            label = "Biometric App Lock",
                            subtitle = "Fingerprint or Face Unlock on open",
                            icon = Icons.Default.Fingerprint,
                            trailingContent = {
                                Switch(
                                    checked = isBiometricEnabled,
                                    onCheckedChange = { checked ->
                                        isBiometricEnabled = checked
                                        prefs.edit().putBoolean("key_biometric_enabled", checked).apply()
                                        Toast.makeText(context, if (checked) "Biometric Lock enabled" else "Biometric Lock disabled", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.canvasChalk,
                                        checkedTrackColor = colors.inkPrimary,
                                        uncheckedThumbColor = colors.inkMuted,
                                        uncheckedTrackColor = colors.borderWhisper
                                    )
                                )
                            },
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = {
                                val newValue = !isBiometricEnabled
                                isBiometricEnabled = newValue
                                prefs.edit().putBoolean("key_biometric_enabled", newValue).apply()
                                Toast.makeText(context, if (newValue) "Biometric Lock enabled" else "Biometric Lock disabled", Toast.LENGTH_SHORT).show()
                            }
                        )

                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                        ProfileSettingsRow(
                            label = "App Theme",
                            subtitle = if (themeController.isDark) "OLED Dark Mode enabled" else "Clean Light Canvas enabled",
                            icon = Icons.Default.Palette,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (themeController.isDark) Icons.Default.NightsStay else Icons.Default.WbSunny,
                                        contentDescription = if (themeController.isDark) "Dark Mode" else "Light Mode",
                                        tint = colors.inkPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Switch(
                                        checked = themeController.isDark,
                                        onCheckedChange = { themeController.toggleTheme() },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = colors.canvasChalk,
                                            checkedTrackColor = colors.inkPrimary,
                                            uncheckedThumbColor = colors.inkMuted,
                                            uncheckedTrackColor = colors.borderWhisper
                                        )
                                    )
                                }
                            },
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = { themeController.toggleTheme() }
                        )
                    }
                }
            }
        }

        // ── 5. STORAGE & SYSTEM DATA BENTO CARD ──────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "STORAGE & DATA",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileSettingsRow(
                            label = "Cloud Backup for Receipts",
                            subtitle = "Encrypted sync to Cloudinary storage",
                            icon = Icons.Default.CloudDone,
                            trailingContent = {
                                Switch(
                                    checked = profile?.cloudBackupReceipts ?: true,
                                    onCheckedChange = { enabled ->
                                        coroutineScope.launch {
                                            FirebaseManager.updateCloudBackupReceiptsSetting(enabled)
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.canvasChalk,
                                        checkedTrackColor = colors.inkPrimary,
                                        uncheckedThumbColor = colors.inkMuted,
                                        uncheckedTrackColor = colors.borderWhisper
                                    )
                                )
                            },
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = {
                                val current = profile?.cloudBackupReceipts ?: true
                                coroutineScope.launch {
                                    FirebaseManager.updateCloudBackupReceiptsSetting(!current)
                                }
                            }
                        )

                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                        ProfileSettingsRow(
                            label = "Receipt Image Cache",
                            subtitle = "$cacheSizeText stored on device",
                            icon = Icons.Default.DeleteSweep,
                            trailingContent = {
                                Surface(
                                    onClick = {
                                        clearReceiptCache(context)
                                        cacheSizeText = getReceiptCacheSize(context)
                                        Toast.makeText(context, "Receipt cache cleared!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(d.radiusSM),
                                    color = colors.alertRed.copy(alpha = 0.1f),
                                    border = BorderStroke(0.5.dp, colors.alertRed.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "Clear",
                                        fontFamily = OutfitFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.alertRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            },
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = {
                                clearReceiptCache(context)
                                cacheSizeText = getReceiptCacheSize(context)
                                Toast.makeText(context, "Receipt cache cleared!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        // ── 6. SYSTEM & UPDATES BENTO CARD ───────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "SYSTEM & UPDATES",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.2.sp
                )

                val latestTag = updateReleaseInfo?.tagName ?: ""
                val isNewerAvailable = updateReleaseInfo?.isNewer == true
                var showInfoDialog by remember { mutableStateOf(false) }

                Card(
                    shape = RoundedCornerShape(d.radiusLG),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ProfileSettingsRow(
                            label = "Check for Updates",
                            subtitle = "v${com.splitsmith.app.BuildConfig.VERSION_NAME} • Release build",
                            icon = Icons.Default.SystemUpdate,
                            trailingContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                                ) {
                                    Text(
                                        text = when {
                                            isCheckingForUpdate -> "Checking..."
                                            latestTag.isNotEmpty() -> latestTag
                                            else -> "Up to date"
                                        },
                                        fontFamily = OutfitFamily,
                                        fontSize = 12.sp,
                                        color = colors.inkMuted
                                    )
                                    if (isNewerAvailable) {
                                        Surface(
                                            shape = RoundedCornerShape(d.radiusSM),
                                            color = colors.inkPrimary,
                                            modifier = Modifier.clickable {
                                                if (updateReleaseInfo != null) showUpdateDialog = true
                                            }
                                        ) {
                                            Text(
                                                text = "UPDATE",
                                                fontFamily = OutfitFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = d.textLabelSmall,
                                                color = colors.canvasChalk,
                                                modifier = Modifier.padding(horizontal = d.space8, vertical = d.space4)
                                            )
                                        }
                                    }
                                }
                            },
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = {
                                if (isNewerAvailable && updateReleaseInfo != null) {
                                    showUpdateDialog = true
                                } else if (!isCheckingForUpdate) {
                                    isCheckingForUpdate = true
                                    coroutineScope.launch {
                                        try {
                                            val release = com.splitsmith.app.util.AppUpdateManager.checkForUpdates()
                                            isCheckingForUpdate = false
                                            if (release != null) {
                                                if (release.isNewer) {
                                                    updateReleaseInfo = release
                                                    showUpdateDialog = true
                                                } else {
                                                    Toast.makeText(context, "SplitSmith v${com.splitsmith.app.BuildConfig.VERSION_NAME} is the latest version!", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                Toast.makeText(context, "Could not check GitHub releases.", Toast.LENGTH_LONG).show()
                                            }
                                        } catch (e: Exception) {
                                            isCheckingForUpdate = false
                                            Toast.makeText(context, "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )

                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                        ProfileSettingsRow(
                            label = "About SplitSmith",
                            subtitle = "Infrastructure, terms & open-source",
                            icon = Icons.Default.Info,
                            showChevron = true,
                            inkPrimary = colors.inkPrimary,
                            inkMuted = colors.inkMuted,
                            borderWhisper = colors.borderWhisper,
                            surfaceCard = colors.surfaceCard,
                            d = d,
                            onClick = { showInfoDialog = true }
                        )
                    }
                }

                if (showInfoDialog) {
                    ModalBottomSheet(
                        onDismissRequest = { showInfoDialog = false },
                        containerColor = colors.canvasChalk,
                        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(d.space24)
                                .navigationBarsPadding(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(d.space16)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.app_logo_brand),
                                contentDescription = "SplitSmith Brand Logo",
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "SplitSmith",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = colors.inkPrimary
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = colors.surfaceCard,
                                    border = BorderStroke(0.5.dp, colors.borderWhisper)
                                ) {
                                    Text(
                                        text = "v${com.splitsmith.app.BuildConfig.VERSION_NAME} • Release Build",
                                        fontFamily = JetBrainsMonoFamily,
                                        fontSize = d.textLabelSmall,
                                        color = colors.inkMuted,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Smart, effortless group expense splitting, real-time balances, and personal ledger accounting.",
                                fontFamily = OutfitFamily,
                                fontSize = d.textBodyLarge,
                                color = colors.inkMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = d.space16)
                            )

                            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "KEY FEATURES & INFRASTRUCTURE",
                                    fontFamily = OutfitFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.inkMuted,
                                    letterSpacing = 1.5.sp
                                )

                                val features = listOf(
                                    "Real-Time Ledger Accounting",
                                    "Group & Personal Monthly Budgets",
                                    "Connections & Zero-Overhead Search",
                                    "QR Code Instant Invites & Connect",
                                    "OLED Dark UI & High Performance"
                                )
                                features.forEach { feat ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = "•  $feat",
                                            fontFamily = OutfitFamily,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = d.textBodyMedium,
                                            color = colors.inkPrimary
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(d.space12)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        com.splitsmith.app.util.AppUpdateManager.openInBrowser(context, "https://splitsmith.web.app/")
                                    },
                                    modifier = Modifier.weight(1f).height(d.buttonHeight),
                                    shape = RoundedCornerShape(d.radiusMD),
                                    border = BorderStroke(1.dp, colors.borderWhisper)
                                ) {
                                    Text("Visit Web", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, color = colors.inkPrimary)
                                }

                                Button(
                                    onClick = { showInfoDialog = false },
                                    modifier = Modifier.weight(1f).height(d.buttonHeight),
                                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                                    shape = RoundedCornerShape(d.radiusMD)
                                ) {
                                    Text("Done", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.canvasChalk)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── 7. DANGER ZONE (SIGN OUT GUARD) ──────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(d.radiusLG),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceCard),
                border = BorderStroke(1.dp, colors.alertRed.copy(alpha = 0.3f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSignOutConfirmDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = d.space16),
                    horizontalArrangement = Arrangement.spacedBy(d.space8, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Sign Out",
                        tint = colors.alertRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Sign Out",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textBodyLarge,
                        color = colors.alertRed
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSettingsRow(
    label: String,
    value: String = "",
    subtitle: String? = null,
    icon: ImageVector? = null,
    showChevron: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    inkPrimary: Color,
    inkMuted: Color,
    borderWhisper: Color,
    surfaceCard: Color,
    d: com.splitsmith.app.theme.Dimens,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = d.space16, vertical = d.space12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).padding(end = d.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.space12)
        ) {
            if (icon != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = inkPrimary.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, borderWhisper),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = inkPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = label,
                    fontFamily = OutfitFamily,
                    fontSize = d.textBodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = inkPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = OutfitFamily,
                        fontSize = 11.sp,
                        color = inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(d.space8)
        ) {
            if (trailingContent != null) {
                trailingContent()
            } else if (value.isNotEmpty()) {
                Text(
                    text = value,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = inkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (showChevron) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = inkMuted.copy(alpha = 0.6f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

fun getReceiptCacheSize(context: android.content.Context): String {
    return try {
        val totalBytes = context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        if (totalBytes < 1024 * 1024) {
            "${totalBytes / 1024} KB"
        } else {
            String.format(java.util.Locale.US, "%.1f MB", totalBytes.toDouble() / (1024 * 1024))
        }
    } catch (e: Exception) {
        "0.0 KB"
    }
}

fun clearReceiptCache(context: android.content.Context) {
    try {
        context.cacheDir.listFiles()?.forEach { file ->
            try { file.deleteRecursively() } catch (e: Exception) {}
        }
    } catch (e: Exception) {}
}
// ─── CREATE GROUP DIALOG ──────────────────────────────────────
@Composable
fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    var groupName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Trip") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(d.radiusLG),
        title = {
            Text(
                "New Group",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = d.textTitleLarge,
                color = colors.inkPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name", fontFamily = OutfitFamily, color = colors.inkMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(d.radiusSM),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.borderWhisper,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary,
                        focusedContainerColor = colors.surfaceCard,
                        unfocusedContainerColor = colors.surfaceCard
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                )
                Text("Type Category", fontFamily = OutfitFamily, fontWeight = FontWeight.Normal, fontSize = d.textLabelSmall, color = colors.inkMuted)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                    listOf("Trip", "Home", "Office", "Other").forEach { type ->
                        val isSelected = selectedType == type
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = type },
                            label = { Text(type, fontFamily = OutfitFamily, color = if (isSelected) colors.canvasChalk else colors.inkMuted) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colors.inkPrimary,
                                containerColor = colors.surfaceCard
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.trim().isEmpty()) return@Button
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val id = FirebaseManager.createGroup(groupName.trim(), "GroupsOutlined", selectedType)
                            onGroupCreated(id)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally { isLoading = false }
                    }
                },
                enabled = !isLoading && groupName.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                shape = RoundedCornerShape(d.radiusMD)
            ) {
                Text("Create", fontFamily = OutfitFamily, color = colors.canvasChalk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
            }
        }
    )
}

// ─── JOIN GROUP DIALOG ────────────────────────────────────────
@Composable
fun JoinGroupDialog(
    onDismiss: () -> Unit,
    onGroupJoined: (groupId: String) -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    var groupIdInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = com.journeyapps.barcodescanner.ScanContract(),
        onResult = { result ->
            if (result.contents != null) {
                val contents = result.contents
                val parsed = com.splitsmith.app.util.QrPayloadParser.parse(contents)
                coroutineScope.launch {
                    isLoading = true
                    // First try to resolve it as a user code/QR globally
                    val user = FirebaseManager.searchUserByCode(contents)
                        ?: if (contents.contains("@")) FirebaseManager.searchUserByEmail(contents) else null
                    isLoading = false
                    
                    if (user != null) {
                        try { FirebaseManager.addConnection(user.uid) } catch (e: Exception) { }
                        Toast.makeText(context, "Connected with ${user.getResolvedName()}!", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } else {
                        // If not a user, treat as group code
                        when (parsed) {
                            is com.splitsmith.app.util.ParsedQrPayload.GroupQr -> {
                                groupIdInput = parsed.groupId
                                Toast.makeText(context, "Scanned Group Code!", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                val cleanGroupId = com.splitsmith.app.util.QrPayloadParser.extractCleanCode(contents)
                                groupIdInput = cleanGroupId
                                Toast.makeText(context, "Scanned Group Code!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
        }
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        shape = RoundedCornerShape(d.radiusLG),
        title = {
            Text(
                "Join Group",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = d.textTitleLarge,
                color = colors.inkPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                OutlinedTextField(
                    value = groupIdInput,
                    onValueChange = { groupIdInput = it },
                    label = { Text("Enter Group ID", fontFamily = OutfitFamily, color = colors.inkMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(d.radiusSM),
                    trailingIcon = {
                        Text(
                            text = "Scan",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textLabelMedium,
                            color = colors.inkPrimary,
                            modifier = Modifier
                                .clickable {
                                    val options = com.journeyapps.barcodescanner.ScanOptions()
                                    options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                    options.setPrompt("Scan a SplitSmith Group QR Code to Join")
                                    options.setCameraId(0)
                                    options.setBeepEnabled(false)
                                    options.setBarcodeImageEnabled(true)
                                    options.setOrientationLocked(true)
                                    qrScanLauncher.launch(options)
                                }
                                .padding(horizontal = d.space12)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.borderWhisper,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary,
                        focusedContainerColor = colors.surfaceCard,
                        unfocusedContainerColor = colors.surfaceCard
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupIdInput.trim().isEmpty()) return@Button
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val code = com.splitsmith.app.util.QrPayloadParser.extractCleanCode(groupIdInput)
                            val group = FirebaseManager.getGroupOnce(code)
                            if (group != null) {
                                val uid = FirebaseManager.currentUserId
                                if (group.members[uid] == true) {
                                    Toast.makeText(context, "Already a member!", Toast.LENGTH_SHORT).show()
                                    onGroupJoined(group.id)
                                } else {
                                    FirebaseManager.requestToJoinGroup(group.id)
                                    Toast.makeText(context, "Request sent!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            } else {
                                Toast.makeText(context, "Group not found", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not join group. Please check the ID.", Toast.LENGTH_LONG).show()
                        } finally { isLoading = false }
                    }
                },
                enabled = !isLoading && groupIdInput.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                shape = RoundedCornerShape(d.radiusMD)
            ) {
                Text("Join", fontFamily = OutfitFamily, color = colors.canvasChalk)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
            }
        }
    )
}

// ─── CONNECTED USER POPUP MODAL ──────────────────────────────
@Composable
fun ConnectedUserModal(
    user: UserProfile,
    onDismiss: () -> Unit,
    onStartQuickSplit: () -> Unit,
    onAddToGroup: () -> Unit
) {
    val colors = LocalSplitColors.current
    val d = LocalDimens.current

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceCard,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Success Badge
                Surface(
                    shape = CircleShape,
                    color = colors.canvasChalk,
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Connected",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Connected Successfully!",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textTitleLarge,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "Mutual connection established",
                        fontFamily = OutfitFamily,
                        fontSize = 12.sp,
                        color = colors.inkMuted
                    )
                }

                // User Info Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.canvasChalk,
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        UserAvatar(avatarUrl = user.avatarUrl, displayName = user.displayName, size = 48.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.displayName,
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = colors.inkPrimary
                            )
                            if (user.shortCode.isNotEmpty()) {
                                Text(
                                    text = "Code: ${user.shortCode.uppercase()}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkMuted
                                )
                            }
                            if (user.email.isNotEmpty()) {
                                Text(
                                    text = user.email,
                                    fontFamily = OutfitFamily,
                                    fontSize = 12.sp,
                                    color = colors.inkMuted
                                )
                            }
                        }
                    }
                }

                // Action Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onStartQuickSplit,
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Start 1-on-1 Split", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = onAddToGroup,
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("Add to Group", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Done", fontFamily = OutfitFamily, color = colors.inkMuted)
                    }
                }
            }
        }
    }
}

fun uriToBase64(context: android.content.Context, uri: android.net.Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val originalBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        if (originalBitmap == null) return null

        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, 120, 120, true)
        val outputStream = java.io.ByteArrayOutputStream()
        scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.WEBP, 70, outputStream)
        val bytes = outputStream.toByteArray()
        "data:image/webp;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupExpenseDetailBottomSheet(
    groupExpense: com.splitsmith.app.data.GroupExpenseWithContext,
    currentUserId: String,
    onNavigateToGroup: (groupId: String) -> Unit,
    onNavigateToAddExpense: (groupId: String, expenseId: String?) -> Unit = { _, _ -> },
    onDismiss: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val exp = groupExpense.expense
    val isPayer = exp.paidBy == currentUserId

    var showMenu by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<com.splitsmith.app.data.Expense?>(null) }
    val canManageExpense = currentUserId.isNotEmpty() && (
        exp.createdBy == currentUserId ||
        exp.paidBy == currentUserId ||
        exp.createdBy.isBlank()
    )

    if (expenseToDelete != null) {
        val targetExp = expenseToDelete!!
        com.splitsmith.app.ui.components.DeleteExpenseDialog(
            hasAttachments = targetExp.receiptUrls.isNotEmpty(),
            onDismiss = { expenseToDelete = null },
            onConfirmDelete = { _ ->
                coroutineScope.launch {
                    try {
                        FirebaseManager.deleteExpense(groupExpense.groupId, targetExp.id, targetExp.getEffectiveReceiptUrls())
                        android.widget.Toast.makeText(context, "Expense deleted", android.widget.Toast.LENGTH_SHORT).show()
                        onDismiss()
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                expenseToDelete = null
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.canvasChalk,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = d.space24, vertical = d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space16)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Groups,
                        contentDescription = "Group Expense",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(d.space12))
                    Column {
                        Text(
                            text = exp.description.ifEmpty { exp.category },
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textHeadlineMedium,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "${groupExpense.groupName} • ${exp.category}",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = colors.inkMuted
                        )
                    }
                }

                if (canManageExpense) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.inkPrimary)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(colors.surfaceCard)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Expense", fontFamily = OutfitFamily, color = colors.inkPrimary) },
                                onClick = {
                                    showMenu = false
                                    onDismiss()
                                    onNavigateToAddExpense(groupExpense.groupId, exp.id)
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Expense", fontFamily = OutfitFamily, color = colors.alertRed) },
                                onClick = {
                                    showMenu = false
                                    expenseToDelete = exp
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.alertRed) }
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(d.radiusMD),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderWhisper)
            ) {
                Column(
                    modifier = Modifier.padding(d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total Amount", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                        Text("₹${exp.amount}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary)
                    }
                    val myShare = exp.splits[currentUserId] ?: 0.0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isPayer) "You lent" else "Your share", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                        Text("₹${if (isPayer) exp.amount - myShare else myShare}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleMedium, color = if (isPayer) colors.inkPrimary else colors.alertRed)
                    }

                    val attachmentUrls = remember(exp) { exp.getEffectiveReceiptUrls() }
                    if (attachmentUrls.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
                        Text("ATTACHED RECEIPTS & INVOICES", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.2.sp)
                        val displayAttachments = attachmentUrls.map { url ->
                            val name = url.substringAfterLast("/").substringBefore("?").ifBlank { "Receipt Document" }
                            val isPdf = url.contains(".pdf", ignoreCase = true)
                            com.splitsmith.app.ui.components.DisplayAttachment(url = url, name = name, isPdf = isPdf)
                        }
                        com.splitsmith.app.ui.components.AttachmentChipsView(
                            attachments = displayAttachments,
                            isEditable = false
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onDismiss()
                    onNavigateToGroup(groupExpense.groupId)
                },
                modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                shape = RoundedCornerShape(d.radiusMD),
                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open Group")
                    Text("Open Group (${groupExpense.groupName})", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalExpenseDetailBottomSheet(
    expense: com.splitsmith.app.data.PersonalExpense,
    onDismiss: () -> Unit,
    onGoToPersonalExpenses: (String) -> Unit = {}
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.canvasChalk,
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = d.space24, vertical = d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space16)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = "Personal Expense",
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(d.space12))
                Column {
                    Text(
                        text = expense.description.ifEmpty { expense.category },
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textHeadlineMedium,
                        color = colors.inkPrimary
                    )
                    Text(
                        text = "Personal Spend • ${expense.category}",
                        fontFamily = OutfitFamily,
                        fontSize = d.textLabelMedium,
                        color = colors.inkMuted
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(d.radiusMD),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderWhisper)
            ) {
                Column(
                    modifier = Modifier.padding(d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Amount Spent", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                        Text("₹${expense.amount}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary)
                    }
                    if (expense.note.isNotBlank()) {
                        val noteText = expense.note.trim()
                        val paidTo = if (noteText.contains("Paid to ")) noteText.substringAfter("Paid to ").substringBefore(".").trim() else ""
                        val viaApp = if (noteText.contains("Via ")) noteText.substringAfter("Via ").substringBefore(".").trim() else ""
                        val txnId = if (noteText.contains("Txn ID: ")) noteText.substringAfter("Txn ID: ").substringBefore(".").trim() else ""
                        val cleanRemarks = noteText
                            .replace(Regex("Paid to [^.]+\\."), "")
                            .replace(Regex("Via [^.]+\\."), "")
                            .replace(Regex("Txn ID: [^.]+"), "")
                            .trim()

                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
                        if (paidTo.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Paid To", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                                Text(paidTo, fontFamily = OutfitFamily, fontWeight = FontWeight.Medium, color = colors.inkPrimary, fontSize = d.textLabelMedium)
                            }
                        }
                        if (viaApp.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Payment Via", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                                Text(viaApp, fontFamily = OutfitFamily, fontWeight = FontWeight.Medium, color = colors.inkPrimary, fontSize = d.textLabelMedium)
                            }
                        }
                        if (txnId.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Txn ID", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                                Text(txnId, fontFamily = OutfitFamily, fontWeight = FontWeight.Medium, color = colors.inkPrimary, fontSize = d.textLabelMedium)
                            }
                        }
                        if (cleanRemarks.isNotBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Note / Remarks", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
                                Text(cleanRemarks, fontFamily = OutfitFamily, color = colors.inkPrimary, fontSize = d.textLabelMedium)
                            }
                        }
                    }
                    val attachmentUrls = remember(expense) { expense.receiptUrls }
                    if (attachmentUrls.isNotEmpty()) {
                        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
                        Text("ATTACHED RECEIPTS & INVOICES", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.2.sp)
                        val displayAttachments = attachmentUrls.map { url ->
                            val name = url.substringAfterLast("/").substringBefore("?").ifBlank { "Receipt Document" }
                            val isPdf = url.contains(".pdf", ignoreCase = true)
                            com.splitsmith.app.ui.components.DisplayAttachment(url = url, name = name, isPdf = isPdf)
                        }
                        com.splitsmith.app.ui.components.AttachmentChipsView(
                            attachments = displayAttachments,
                            isEditable = false
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onGoToPersonalExpenses(expense.id)
                },
                modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                shape = RoundedCornerShape(d.radiusMD),
                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Text("Go to Personal Expenses", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                    Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Go", tint = colors.canvasChalk)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerOptionsBottomSheet(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = d.space24)
                .padding(top = d.space8, bottom = d.space24),
            verticalArrangement = Arrangement.spacedBy(d.space16)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Scan & Import",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textHeadlineMedium,
                    color = colors.inkPrimary
                )
                Text(
                    text = "AI-powered receipt & QR code scanner",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
            }

            // 1. Scan with Camera Hero Card
            Surface(
                onClick = {
                    onDismiss()
                    onCameraClick()
                },
                shape = RoundedCornerShape(d.radiusLG),
                color = colors.canvasChalk,
                border = BorderStroke(1.dp, colors.borderWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(d.space16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.inkPrimary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Camera Scan",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Scan with Camera",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textTitleMedium,
                                color = colors.inkPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.inkPrimary.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Instant", fontFamily = OutfitFamily, fontSize = 10.sp, color = colors.inkPrimary)
                            }
                        }
                        Text(
                            text = "Point camera at paper bill or QR code",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            // 2. Upload from Gallery Hero Card
            Surface(
                onClick = {
                    onDismiss()
                    onGalleryClick()
                },
                shape = RoundedCornerShape(d.radiusLG),
                color = colors.canvasChalk,
                border = BorderStroke(1.dp, colors.borderWhisper),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(d.space16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(colors.inkPrimary.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Gallery Upload",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Upload from Gallery",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textTitleMedium,
                                color = colors.inkPrimary
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(colors.inkPrimary.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Photo Picker", fontFamily = OutfitFamily, fontSize = 10.sp, color = colors.inkPrimary)
                            }
                        }
                        Text(
                            text = "Import screenshot from GPay, PhonePe, Paytm…",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            Text(
                text = "✓ Auto-detects Receipts, Payment Slips, and Group/User QR Codes",
                fontFamily = OutfitFamily,
                fontSize = 11.sp,
                color = colors.inkMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
    }
}





