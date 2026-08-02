package com.splitsmith.app.ui.home

import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PhotoCamera
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
import com.splitsmith.app.ui.personal.PersonalExpensesScreen
import com.splitsmith.app.ui.split.SplitExpensesScreen
import com.splitsmith.app.ui.split.DirectSplitDetailBottomSheet
import com.splitsmith.app.ui.components.UserAvatar
import com.splitsmith.app.ui.components.dotGridBackground
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
    onSignOut: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    var showCreateGroupSheet by remember { mutableStateOf(false) }
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

    var hasDrivePermission by remember { mutableStateOf(com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context)) }
    var showDriveSnackbar by remember { mutableStateOf(false) }

    val homeDriveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val success = com.splitsmith.app.data.GoogleDriveManager.handleDrivePermissionResult(result.data, context)
        hasDrivePermission = success
        if (success) {
            showDriveSnackbar = false
            Toast.makeText(context, "Google Drive linked! Syncing...", Toast.LENGTH_SHORT).show()
            coroutineScope.launch {
                com.splitsmith.app.data.GoogleDriveManager.ensureRootFolderExists(context)
                com.splitsmith.app.data.PendingDriveUploadsManager.processPendingQueue(context)
                com.splitsmith.app.data.DriveSyncWorker.enqueue(context.applicationContext)
            }
        }
    }

    LaunchedEffect(userProfileState.value) {
        val wantsSync = userProfileState.value?.driveSyncEnabled == true
        val hasPerm = com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context)
        hasDrivePermission = hasPerm
        showDriveSnackbar = wantsSync && !hasPerm
    }

    // Process any pending Google Drive uploads on app startup if scope is granted
    LaunchedEffect(Unit) {
        com.splitsmith.app.migration.FolderMigration.runFolderMigration(context)
        if (com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context)) {
            com.splitsmith.app.data.GoogleDriveManager.ensureRootFolderExists(context)
            com.splitsmith.app.data.DriveSyncWorker.enqueue(context.applicationContext)
            com.splitsmith.app.data.PendingDriveUploadsManager.processPendingQueue(context)
        }
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
                            NavIconSlot(2, Icons.Default.AccountBalanceWallet, "Personal", pagerState.targetPage, colors.inkPrimary, colors.borderWhisper, colors.inkMuted, d) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvasChalk)
                .dotGridBackground(colors.dotColor)
                .padding(paddingValues)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> HomeDashboardView(
                        groups = groupsState.value,
                        userProfile = userProfileState.value,
                        onNavigateToGroup = onNavigateToGroup,
                        onSeeAllGroups = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        onCreateGroup = { showCreateGroupSheet = true },
                        onNavigateToQRCode = onNavigateToQRCode,
                        onNavigateToQuickSplit = onNavigateToQuickSplit,
                        onSplitClick = { selectedSplitForDetail = it },
                        onPersonalClick = { selectedPersonalForDetail = it },
                        onGroupExpenseClick = { selectedGroupExpenseForDetail = it },
                        onConnectUser = { connectedUserPopup = it }
                    )
                    1 -> SplitExpensesScreen(
                        onNavigateToGroup = onNavigateToGroup,
                        onNavigateToQuickSplit = onNavigateToQuickSplit,
                        onBack = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
                    )
                    2 -> PersonalExpensesScreen(
                        showAddPersonalInitially = showAddPersonalInitially,
                        initialSelectedExpenseId = targetPersonalExpenseId,
                        onNavigateToQuickSplit = onNavigateToQuickSplit,
                        onBack = { coroutineScope.launch { pagerState.animateScrollToPage(0) } }
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
                        // 4. Create Group
                        QuickActionRow(
                            title = "Create New Group",
                            subtitle = "Start a group for trips, flat, office…",
                            icon = Icons.Default.GroupAdd,
                            colors = colors, d = d
                        ) {
                            showQuickAddSheet = false
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
            if (showDriveSnackbar) {
                com.splitsmith.app.ui.PermissionSnackbar(
                    showSnackbar = true,
                    onDismiss = { showDriveSnackbar = false },
                    onGrantPermission = {
                        com.splitsmith.app.data.GoogleDriveManager.requestDrivePermission(homeDriveLauncher, context)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = d.navDockHeight + d.space24)
                        .padding(horizontal = d.space16)
                )
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
    groups: List<Group>,
    userProfile: com.splitsmith.app.data.UserProfile?,
    onNavigateToGroup: (groupId: String) -> Unit,
    onSeeAllGroups: () -> Unit,
    onCreateGroup: () -> Unit,
    onNavigateToQRCode: () -> Unit,
    onNavigateToQuickSplit: () -> Unit = {},
    onSplitClick: (DirectSplit) -> Unit = {},
    onPersonalClick: (com.splitsmith.app.data.PersonalExpense) -> Unit = {},
    onGroupExpenseClick: (com.splitsmith.app.data.GroupExpenseWithContext) -> Unit = {},
    onConnectUser: (UserProfile) -> Unit = {}
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploadingAvatar by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            isUploadingAvatar = true
            coroutineScope.launch {
                try {
                    val base64 = uriToBase64(context, uri)
                    if (base64 != null) {
                        FirebaseManager.updateAvatarUrl(base64)
                        Toast.makeText(context, "Avatar updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to process image", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingAvatar = false
                }
            }
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
                            // Try group first, then fallback to user
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

    val currentUserId = FirebaseManager.currentUserId ?: ""

    // Current month start timestamp for strict calendar-month budget tracking
    val currentMonthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Observe expenses, direct splits, and group expenses for real-time budget tracking on dashboard
    val personalExpensesFlow = remember { FirebaseManager.observePersonalExpenses() }
    val personalExpensesState = personalExpensesFlow.collectAsState(initial = emptyList())
    val directSplitsFlow = remember { FirebaseManager.observeDirectSplits() }
    val directSplitsState = directSplitsFlow.collectAsState(initial = emptyList())
    val groupExpensesFlow = remember { FirebaseManager.observeAllUserGroupExpenses() }
    val groupExpensesState = groupExpensesFlow.collectAsState(initial = emptyList())

    // Comprehensive Total Monthly Outflow: Solo Personal Spend + Direct Splits Share + Group Expense Share for current month
    val totalPersonalSpent = remember(personalExpensesState.value, directSplitsState.value, groupExpensesState.value, currentMonthStart, currentUserId) {
        val soloMonthly = personalExpensesState.value.filter { it.date >= currentMonthStart }.sumOf { it.amount }
        val directMonthly = directSplitsState.value.filter { it.date >= currentMonthStart }.sumOf { ds ->
            ds.myShare
        }
        val groupMonthly = groupExpensesState.value.filter { it.expense.date >= currentMonthStart }.sumOf { ge ->
            ge.expense.splits[currentUserId] ?: 0.0
        }
        soloMonthly + directMonthly + groupMonthly
    }
    val budgetLimit = (userProfile?.budget?.limit ?: 15000.0).let { if (it <= 0) 15000.0 else it }
    val budgetProgress = if (budgetLimit > 0) (totalPersonalSpent / budgetLimit).coerceIn(0.0, 1.0) else 0.0

    // Consolidated Shared Net Balance: Direct 1-on-1 Splits + Group Net Balances
    val totalDirectOwed = remember(directSplitsState.value, groupExpensesState.value, currentUserId) {
        val directNet = directSplitsState.value.filter { it.status == "PENDING" }.sumOf {
            if (it.paidBy == currentUserId) it.myShare else -it.myShare
        }
        val groupNet = groupExpensesState.value.sumOf { ge ->
            val exp = ge.expense
            val myShare = exp.splits[currentUserId] ?: 0.0
            if (exp.paidBy == currentUserId) {
                exp.amount - myShare
            } else {
                -myShare
            }
        }
        directNet + groupNet
    }

    val combinedRecentActivities = remember(directSplitsState.value, personalExpensesState.value, groupExpensesState.value, currentUserId) {
        val splits = directSplitsState.value.map { split ->
            RecentActivityItem(
                id = split.id,
                title = if (split.description.isNotEmpty()) split.description else "Split",
                subtitle = if (split.status == "SETTLED") "Settled" else if (split.status == "WAITING_APPROVAL") "Approval Pending" else if (split.paidBy == currentUserId) "You paid" else "You owe",
                amount = split.myShare,
                date = split.date,
                isPositive = split.paidBy == currentUserId,
                isSettled = split.status == "SETTLED",
                rawSplit = split
            )
        }
        val personal = personalExpensesState.value.map { pe ->
            RecentActivityItem(
                id = pe.id,
                title = if (pe.description.isNotEmpty()) pe.description else pe.category,
                subtitle = "Personal Expense • ${pe.category}",
                amount = pe.amount,
                date = pe.date,
                isPositive = false,
                isSettled = true,
                rawSplit = null,
                rawPersonal = pe
            )
        }
        val groupExps = groupExpensesState.value.map { ge ->
            val exp = ge.expense
            RecentActivityItem(
                id = exp.id,
                title = if (exp.description.isNotEmpty()) exp.description else exp.category,
                subtitle = "${ge.groupName} • ${exp.category}",
                amount = exp.amount,
                date = exp.date,
                isPositive = exp.paidBy == currentUserId,
                isSettled = false,
                rawSplit = null,
                rawGroupExpense = ge
            )
        }
        (splits + personal + groupExps).sortedByDescending { it.date }.take(12)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space24),
        verticalArrangement = Arrangement.spacedBy(d.space24)
    ) {
        item {
            // App Bar / Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SplitSmith",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textHeadlineLarge,
                        color = colors.inkPrimary,
                        letterSpacing = (-0.5).sp
                    )
                }

                var showProfileDialog by remember { mutableStateOf(false) }

                if (showProfileDialog && userProfile != null) {
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
                                        .clickable(enabled = !isUploadingAvatar) {
                                            photoPickerLauncher.launch("image/*")
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    UserAvatar(
                                        avatarUrl = userProfile.avatarUrl,
                                        displayName = userProfile.displayName,
                                        size = 80.dp
                                    )
                                    if (isUploadingAvatar) {
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
                                        text = userProfile.displayName,
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textTitleMedium,
                                        color = colors.inkPrimary
                                    )
                                    Text(
                                        text = userProfile.email,
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

                // Profile Avatar Indicator — with subtle grey ring border
                Box(
                    modifier = Modifier
                        .size(d.avatarMd + 4.dp)
                        .clip(CircleShape)
                        .background(colors.inkMuted)
                        .clickable { showProfileDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfile != null) {
                        UserAvatar(
                            avatarUrl = userProfile.avatarUrl,
                            displayName = userProfile.displayName,
                            size = d.avatarMd
                        )
                    }
                }
            }
        }

        // ── Summary strip (no cards — pure typography) ────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(d.radiusLG))
                    .background(colors.surfaceCard)
                    .padding(horizontal = d.space20, vertical = d.space16),
                verticalArrangement = Arrangement.spacedBy(d.space16)
            ) {
                // Net balance row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "NET BALANCE",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkMuted,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = if (totalDirectOwed >= 0) "₹${"%.0f".format(totalDirectOwed)}"
                                   else "-₹${"%.0f".format(-totalDirectOwed)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = if (totalDirectOwed >= 0) colors.inkPrimary else colors.alertRed
                        )
                    }
                    // Status badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (totalDirectOwed >= 0) colors.inkPrimary.copy(alpha = 0.08f)
                                else colors.alertRed.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (totalDirectOwed == 0.0) "Settled" else if (totalDirectOwed > 0) "Owed to you" else "You owe",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (totalDirectOwed >= 0) colors.inkPrimary else colors.alertRed
                        )
                    }
                }

                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                // Budget row with inline progress bar
                val animatedProgress by animateFloatAsState(
                    targetValue = budgetProgress.toFloat(),
                    animationSpec = tween(durationMillis = 900),
                    label = "budgetBar"
                )
                val barColor = when {
                    budgetProgress < 0.70 -> colors.inkPrimary
                    budgetProgress < 0.90 -> Color(0xFFE8A838)  // subtle warm amber
                    else -> Color(0xFFE05252)  // muted red
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MONTHLY BUDGET",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.inkMuted,
                            letterSpacing = 1.5.sp
                        )
                        val remainingBudget = budgetLimit - totalPersonalSpent
                        Text(
                            text = if (remainingBudget >= 0) "₹${"%.0f".format(remainingBudget)} left" else "Overspent: ₹${"%.0f".format(-remainingBudget)}",
                            fontFamily = OutfitFamily,
                            fontSize = 11.sp,
                            fontWeight = if (remainingBudget < 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (remainingBudget < 0) colors.alertRed else colors.inkMuted
                        )
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
                }
            }
        }

        // ── Recent Activity ───────────────────────────────────────────
        item {
            Text(
                text = "RECENT ACTIVITY",
                fontFamily = OutfitFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkMuted,
                letterSpacing = 1.5.sp
            )
        }

        if (combinedRecentActivities.isEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = d.space8),
                    horizontalArrangement = Arrangement.spacedBy(d.space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✓", fontSize = 18.sp, color = colors.inkPrimary)
                    Column {
                        Text(
                            text = "No recent activity",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textBodyLarge,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "Tap + to create a split or log an expense",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    }
                }
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        } else {
            items(combinedRecentActivities) { item ->
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (item.rawSplit != null) onSplitClick(item.rawSplit)
                                else if (item.rawPersonal != null) onPersonalClick(item.rawPersonal)
                                else if (item.rawGroupExpense != null) onGroupExpenseClick(item.rawGroupExpense)
                            }
                            .padding(vertical = d.space12),
                        horizontalArrangement = Arrangement.spacedBy(d.space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Direction indicator dot
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (item.isSettled) colors.inkMuted
                                    else if (item.isPositive) colors.inkPrimary
                                    else colors.alertRed
                                )
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = d.textBodyLarge,
                                color = if (item.isSettled) colors.inkMuted else colors.inkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.subtitle,
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelSmall,
                                color = colors.inkMuted
                            )
                        }
                        Text(
                            text = "₹${if (item.amount % 1.0 == 0.0) item.amount.toInt().toString() else String.format("%.2f", item.amount)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textBodyLarge,
                            color = if (item.isSettled) colors.inkMuted else if (item.isPositive) colors.inkPrimary else colors.alertRed
                        )
                    }
                    HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
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
    var showAvatarPickerSheet by remember { mutableStateOf(false) }
    val avatarPickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isCheckingForUpdate by remember { mutableStateOf(false) }
    var updateReleaseInfo by remember { mutableStateOf<com.splitsmith.app.util.AppReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
            budgetLimitInput = "15000" // Simulated or default
        }
    }

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
                    label = { Text("UPI ID / Phone Number", fontFamily = OutfitFamily, color = colors.inkMuted) },
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

    if (showEditBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showEditBudgetDialog = false },
            containerColor = colors.surfaceCard,
            shape = RoundedCornerShape(d.radiusLG),
            title = { Text("Edit Budget Limit", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary) },
            text = {
                OutlinedTextField(
                    value = budgetLimitInput,
                    onValueChange = { budgetLimitInput = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                    shape = RoundedCornerShape(d.radiusSM),
                    label = { Text("Monthly limit (₹ INR)", fontFamily = OutfitFamily, color = colors.inkMuted) },
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
                        showEditBudgetDialog = false
                        Toast.makeText(context, "Budget limit updated!", Toast.LENGTH_SHORT).show()
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

                // Render 8 cute presets in a grid-like structure (2 rows of 4)
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

                // Upload custom photo button
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

                // If signed in with Google, offer to revert to original photo
                val googlePhoto = FirebaseManager.currentUserPhotoUrl
                if (googlePhoto != null && googlePhoto.isNotEmpty()) {
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
        contentPadding = PaddingValues(start = d.space24, top = d.space24, end = d.space24, bottom = 120.dp)
    ) {
        item {
            Text("Settings", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textHeadlineLarge, color = colors.inkPrimary, letterSpacing = (-0.5).sp)
            Spacer(modifier = Modifier.height(d.space16))

            // Flat Top Profile Header (Cardless)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = d.space16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.clickable { showAvatarPickerSheet = true }
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
                        color = colors.inkPrimary
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
                Spacer(modifier = Modifier.width(d.space16))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile?.displayName ?: "SplitSmith User",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textTitleLarge,
                        color = colors.inkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = (profile?.upiId ?: "").ifEmpty { profile?.email ?: "Tap photo to change avatar" },
                        fontFamily = OutfitFamily,
                        fontSize = d.textLabelMedium,
                        color = colors.inkMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(d.space24))

            // ACCOUNT section
            Text("ACCOUNT", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(d.space8))
        }

        // Display Name Row
        item {
            ProfileSettingsRow(
                label = "Display Name",
                value = (profile?.displayName ?: "SplitSmith User").ifEmpty { "Not set" },
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { showEditNameDialog = true }
            )
        }

        // UPI ID Row
        item {
            Spacer(modifier = Modifier.height(d.space4))
            ProfileSettingsRow(
                label = "UPI ID",
                value = (profile?.upiId ?: "").ifEmpty { "Not set" },
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { showEditUpiDialog = true }
            )
        }

        // My QR Code row
        item {
            Spacer(modifier = Modifier.height(d.space4))
            ProfileSettingsRow(
                label = "My QR Code",
                trailingContent = {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = "QR Code",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                },
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { onNavigateToQRCode() }
            )
        }

        // My User Code row
        item {
            Spacer(modifier = Modifier.height(d.space4))
            ProfileSettingsRow(
                label = "My User Code",
                value = uid.take(6).uppercase(),
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("User Code", uid.take(6).uppercase())
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "User code copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // ANALYTICS & DATA section
        item {
            Spacer(modifier = Modifier.height(d.space24))
            Text("ANALYTICS & DATA", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(d.space8))
            
            ProfileSettingsRow(
                label = "Spend & Balance Reports",
                value = "View reports",
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { onNavigateToReports() }
            )
        }

        // PREFERENCES section
        item {
            Spacer(modifier = Modifier.height(d.space24))
            Text("PREFERENCES", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(d.space8))

            ProfileSettingsRow(
                label = "Budget Limit",
                value = "₹${(profile?.budget?.limit ?: 15000.0).toInt()}",
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { showEditBudgetDialog = true }
            )
        }



        // STORAGE & CLOUD BACKUP section
        item {
            Spacer(modifier = Modifier.height(d.space24))
            Text("STORAGE & CLOUD BACKUP", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(d.space8))

            val hasDrivePermission = remember(context) { com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context) }

            LaunchedEffect(hasDrivePermission) {
                if (hasDrivePermission) {
                    FirebaseManager.updateDriveSyncSetting(true)
                    com.splitsmith.app.data.DriveSyncWorker.enqueue(context.applicationContext)
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.splitsmith.app.data.GoogleDriveManager.ensureRootFolderExists(context.applicationContext)
                        com.splitsmith.app.data.PendingDriveUploadsManager.processPendingQueue(context.applicationContext)
                    }
                }
            }

            val driveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val success = com.splitsmith.app.data.GoogleDriveManager.handleDrivePermissionResult(result.data, context)
                coroutineScope.launch {
                    FirebaseManager.updateDriveSyncSetting(success)
                    if (success) {
                        Toast.makeText(context, "Google Drive linked! Syncing pending attachments...", Toast.LENGTH_SHORT).show()
                        com.splitsmith.app.data.DriveSyncWorker.enqueue(context.applicationContext)
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val rootId = com.splitsmith.app.data.GoogleDriveManager.ensureRootFolderExists(context.applicationContext)
                            if (rootId != null) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    Toast.makeText(context, "SplitSmith folder created in My Drive!", Toast.LENGTH_LONG).show()
                                }
                            }
                            com.splitsmith.app.data.PendingDriveUploadsManager.processPendingQueue(context.applicationContext)
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                ProfileSettingsRow(
                    label = "Google Drive Auto-Sync",
                    trailingContent = {
                        Switch(
                            checked = profile?.driveSyncEnabled ?: false,
                            onCheckedChange = { enabled ->
                                coroutineScope.launch {
                                    if (enabled && !com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context)) {
                                        com.splitsmith.app.data.GoogleDriveManager.requestDrivePermission(driveLauncher, context)
                                    } else {
                                        FirebaseManager.updateDriveSyncSetting(enabled)
                                        if (enabled) {
                                            com.splitsmith.app.data.DriveSyncWorker.enqueue(context.applicationContext)
                                            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                com.splitsmith.app.data.PendingDriveUploadsManager.processPendingQueue(context.applicationContext)
                                            }
                                        }
                                    }
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
                    d = d,
                    onClick = {
                        val current = profile?.driveSyncEnabled ?: false
                        val nextState = !current
                        coroutineScope.launch {
                            if (nextState && !com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context)) {
                                com.splitsmith.app.data.GoogleDriveManager.requestDrivePermission(driveLauncher, context)
                            } else {
                                FirebaseManager.updateDriveSyncSetting(nextState)
                                if (nextState) {
                                    com.splitsmith.app.data.DriveSyncWorker.enqueue(context.applicationContext)
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        com.splitsmith.app.data.PendingDriveUploadsManager.processPendingQueue(context.applicationContext)
                                    }
                                }
                            }
                        }
                    }
                )

                // Banner removed: Now handled globally by PermissionSnackbar at the Scaffold level
            }
        }

        // SECURITY section
        item {
            Spacer(modifier = Modifier.height(d.space24))
            Text("SECURITY", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(d.space8))

            val prefs = remember { context.getSharedPreferences("splitsmith_prefs", android.content.Context.MODE_PRIVATE) }
            var isBiometricEnabled by remember { mutableStateOf(prefs.getBoolean("key_biometric_enabled", false)) }

            ProfileSettingsRow(
                label = "App Biometric Lock",
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
                d = d,
                onClick = {
                    val newValue = !isBiometricEnabled
                    isBiometricEnabled = newValue
                    prefs.edit().putBoolean("key_biometric_enabled", newValue).apply()
                    Toast.makeText(context, if (newValue) "Biometric Lock enabled" else "Biometric Lock disabled", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // App Theme Switch with Sun / Moon Indicator Icon
        item {
            Spacer(modifier = Modifier.height(d.space4))
            val themeController = LocalThemeController.current
            ProfileSettingsRow(
                label = "App Theme",
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (themeController.isDark) Icons.Default.NightsStay else Icons.Default.WbSunny,
                            contentDescription = if (themeController.isDark) "Dark Mode" else "Light Mode",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
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
                d = d,
                onClick = { themeController.toggleTheme() }
            )
        }

        // ABOUT section
        item {
            Spacer(modifier = Modifier.height(d.space24))
            Text("ABOUT", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Spacer(modifier = Modifier.height(d.space8))

            val latestTag = updateReleaseInfo?.tagName ?: ""
            val isNewerAvailable = updateReleaseInfo?.isNewer == true

            ProfileSettingsRow(
                label = "Check for Updates",
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        Text(
                            text = when {
                                isCheckingForUpdate -> "Checking..."
                                latestTag.isNotEmpty() -> latestTag
                                else -> "v${com.splitsmith.app.BuildConfig.VERSION_NAME}"
                            },
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
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
                                        Toast.makeText(context, "You are already using the latest version (v${com.splitsmith.app.BuildConfig.VERSION_NAME})!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Could not check GitHub releases. Check network connection.", Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                isCheckingForUpdate = false
                                Toast.makeText(context, "Update check failed: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )

            ProfileSettingsRow(
                label = "App Version",
                value = "v${com.splitsmith.app.BuildConfig.VERSION_NAME}",
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { Toast.makeText(context, "SplitSmith v${com.splitsmith.app.BuildConfig.VERSION_NAME} is up to date!", Toast.LENGTH_SHORT).show() }
            )

            var showInfoDialog by remember { mutableStateOf(false) }

            ProfileSettingsRow(
                label = "About SplitSmith & Info",
                value = "Learn more",
                inkPrimary = colors.inkPrimary,
                inkMuted = colors.inkMuted,
                borderWhisper = colors.borderWhisper,
                d = d,
                onClick = { showInfoDialog = true }
            )

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
                        // Brand Logo & Header
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

                        // Feature Bento Grid
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

                        // Website & Close Action Buttons
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

        item {
            Spacer(modifier = Modifier.height(d.space32))
            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(d.space16))

            TextButton(
                onClick = {
                    FirebaseManager.signOut()
                    onSignOut()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = d.rowHeightSm)
            ) {
                Text(
                    text = "Sign Out",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = d.textBodyLarge,
                    color = colors.alertRed
                )
            }
        }
    }
}

@Composable
fun ProfileSettingsRow(
    label: String,
    value: String = "",
    trailingContent: (@Composable () -> Unit)? = null,
    inkPrimary: Color,
    inkMuted: Color,
    borderWhisper: Color,
    d: com.splitsmith.app.theme.Dimens,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = d.rowHeightSm)
            .padding(vertical = d.space12),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = OutfitFamily,
            fontSize = d.textBodyLarge,
            color = inkPrimary,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(d.space16))
        if (trailingContent != null) {
            trailingContent()
        } else if (value.isNotEmpty()) {
            Text(
                text = value,
                fontFamily = OutfitFamily,
                fontSize = d.textBodyMedium,
                color = inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    HorizontalDivider(color = borderWhisper, thickness = 0.5.dp)
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
                groupIdInput = com.splitsmith.app.util.QrPayloadParser.extractCleanCode(result.contents)
                Toast.makeText(context, "Scanned Group ID!", Toast.LENGTH_SHORT).show()
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
    onDismiss: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val exp = groupExpense.expense
    val isPayer = exp.paidBy == currentUserId

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
                        Text(if (isPayer) "You paid" else "Your share", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textLabelMedium)
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





