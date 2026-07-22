package com.splitsmith.app.ui.split

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import com.splitsmith.app.ui.components.GroupIconView
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.DirectSplit
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.data.Group
import com.splitsmith.app.data.UserProfile
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.ui.components.UserAvatar
import com.splitsmith.app.ui.components.dotGridBackground
import kotlinx.coroutines.launch

private val groupColors = listOf(
    Color(0xFF6A6A66), Color(0xFF555552), Color(0xFF40403E),
    Color(0xFF7A7A76), Color(0xFF8F8F8A), Color(0xFF333331)
)
private fun groupColor(id: String) = groupColors[Math.abs(id.hashCode()) % groupColors.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitExpensesScreen(
    onNavigateToGroup: (groupId: String) -> Unit,
    onNavigateToQuickSplit: () -> Unit,
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, GROUPS, INDIVIDUAL
    var showCreateGroup by remember { mutableStateOf(false) }
    var fabExpanded by remember { mutableStateOf(false) }
    var selectedSplitForDetail by remember { mutableStateOf<DirectSplit?>(null) }

    val currentUserId = FirebaseManager.currentUserId ?: ""

    // Use collectAsState with remember(currentUserId) to ensure flows re-subscribe properly when user changes
    val groupsFlow = remember(currentUserId) { FirebaseManager.observeGroups() }
    val directSplitsFlow = remember(currentUserId) { FirebaseManager.observeDirectSplits() }

    val groupsState = groupsFlow.collectAsState(initial = null)
    val directSplitsState = directSplitsFlow.collectAsState(initial = null)

    val isLoaded = groupsState.value != null && directSplitsState.value != null
    val groups = groupsState.value ?: emptyList()
    val directSplits = directSplitsState.value ?: emptyList()

    // Resolve direct splits contact names with instant memory-cache pre-population
    var contactNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var contactUpis by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var contactAvatars by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(directSplits) {
        val uids = (directSplits.map { it.paidBy } + directSplits.map { it.withUser })
            .filter { it.isNotEmpty() && it != currentUserId }
            .toSet()
        val names = contactNames.toMutableMap()
        val upis = contactUpis.toMutableMap()
        val avs = contactAvatars.toMutableMap()
        
        // 1. Immediately load any profiles already cached in memory
        for (uid in uids) {
            val cached = FirebaseManager.getCachedUserProfile(uid)
            if (cached != null) {
                names[uid] = cached.displayName
                upis[uid] = cached.upiId
                avs[uid] = cached.avatarUrl
            }
        }
        contactNames = names; contactUpis = upis; contactAvatars = avs

        // 2. Concurrently fetch missing profiles over network
        for (uid in uids) {
            if (!names.containsKey(uid) || names[uid].isNullOrEmpty()) {
                val p = FirebaseManager.getUserProfile(uid)
                if (p != null) {
                    contactNames = contactNames + (uid to p.displayName)
                    contactUpis = contactUpis + (uid to p.upiId)
                    contactAvatars = contactAvatars + (uid to p.avatarUrl)
                }
            }
        }
    }

    val filteredGroups = remember(groups, searchQuery) {
        groups.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }
    val filteredDirectSplits = remember(directSplits, searchQuery, contactNames) {
        directSplits.filter { split ->
            val peerId = if (split.paidBy == currentUserId) split.withUser else split.paidBy
            val peerName = contactNames[peerId] ?: ""
            split.description.contains(searchQuery, ignoreCase = true) || peerName.contains(searchQuery, ignoreCase = true)
        }
    }

    // Bottom sheet for group creation
    if (showCreateGroup) {
        CreateGroupBottomSheet(
            onDismiss = { showCreateGroup = false },
            onGroupCreated = { groupId ->
                showCreateGroup = false
                onNavigateToGroup(groupId)
            }
        )
    }

    if (selectedSplitForDetail != null) {
        val split = selectedSplitForDetail!!
        val peerId = if (split.paidBy == currentUserId) split.withUser else split.paidBy
        DirectSplitDetailBottomSheet(
            split = split,
            peerName = contactNames[peerId] ?: "Unknown",
            peerAvatar = contactAvatars[peerId] ?: "",
            peerUpi = contactUpis[peerId] ?: "",
            currentUserId = currentUserId,
            onDismiss = { selectedSplitForDetail = null }
        )
    }

    Scaffold(
        containerColor = colors.surfaceCard,
        modifier = Modifier.dotGridBackground(colors.dotColor),
        contentWindowInsets = WindowInsets(0)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = d.space24)
                .padding(top = d.space24, bottom = paddingValues.calculateBottomPadding() + d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space24)
        ) {
            // Header row - split page header spacing aligned exactly with dashboard
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Split Expenses",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textHeadlineLarge,
                    color = colors.inkPrimary,
                    letterSpacing = (-0.5).sp
                )
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                shape = RoundedCornerShape(d.radiusSM),
                placeholder = { Text("Search groups or people...", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.inkMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.inkPrimary,
                    unfocusedBorderColor = colors.borderWhisper,
                    focusedContainerColor = colors.surfaceCard,
                    unfocusedContainerColor = colors.surfaceCard,
                    focusedTextColor = colors.inkPrimary,
                    unfocusedTextColor = colors.inkPrimary
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
            )

            // Filter Chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(d.space8),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf("ALL" to "All", "GROUPS" to "Groups", "DIRECT" to "Individual").forEach { (filterVal, label) ->
                    val isSelected = selectedFilter == filterVal
                    Surface(
                        onClick = { selectedFilter = filterVal },
                        shape = RoundedCornerShape(d.radiusFull),
                        color = if (isSelected) colors.inkPrimary else colors.surfaceCard,
                        border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space16)) {
                            Text(
                                text = label,
                                fontFamily = OutfitFamily,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = d.textLabelLarge,
                                color = if (isSelected) colors.canvasChalk else colors.inkMuted
                            )
                        }
                    }
                }
            }

            // List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(d.space12),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                if (!isLoaded) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = colors.inkMuted,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else {
                    if (selectedFilter == "ALL" || selectedFilter == "GROUPS") {
                        if (filteredGroups.isNotEmpty()) {
                            item {
                                Text(
                                    text = "GROUPS",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelSmall,
                                    color = colors.inkMuted,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(vertical = d.space4)
                                )
                            }
                            items(filteredGroups) { group ->
                                GroupListItem(group, onNavigateToGroup, colors, d)
                            }
                        }
                    }

                    if (selectedFilter == "ALL" || selectedFilter == "DIRECT") {
                        if (filteredDirectSplits.isNotEmpty()) {
                            item {
                                Spacer(modifier = Modifier.height(d.space4))
                                Text(
                                    text = "INDIVIDUAL",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelSmall,
                                    color = colors.inkMuted,
                                    letterSpacing = 1.5.sp,
                                    modifier = Modifier.padding(vertical = d.space4)
                                )
                            }
                            items(filteredDirectSplits) { split ->
                                DirectSplitListItem(
                                    split = split,
                                    peerName = contactNames[if (split.paidBy == currentUserId) split.withUser else split.paidBy] ?: "Unknown",
                                    peerAvatar = contactAvatars[if (split.paidBy == currentUserId) split.withUser else split.paidBy] ?: "",
                                    peerUpi = contactUpis[if (split.paidBy == currentUserId) split.withUser else split.paidBy] ?: "",
                                    currentUserId = currentUserId,
                                    colors = colors,
                                    d = d,
                                    onSplitClick = { selectedSplitForDetail = split }
                                )
                            }
                        }
                    }

                    if (filteredGroups.isEmpty() && filteredDirectSplits.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(d.space8)
                            ) {
                                Text("No groups yet", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = colors.inkPrimary)
                                Text("Tap + to create a group or split with someone.", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GroupListItem(
    group: Group,
    onNavigateToGroup: (String) -> Unit,
    colors: com.splitsmith.app.theme.SplitColors,
    d: com.splitsmith.app.theme.Dimens
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToGroup(group.id) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = d.space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GroupIconView(
                iconName = group.iconName,
                size = d.groupIconSize
            )
            Spacer(modifier = Modifier.width(d.space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = d.textTitleMedium,
                    color = colors.inkPrimary
                )
                Text(
                    text = "${group.members.size} members",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
            }
            val currentUserId = FirebaseManager.currentUserId ?: ""
            val isPendingApproval = group.members[currentUserId] != true

            if (isPendingApproval) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderWhisper)
                ) {
                    Text(
                        text = "Pending",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            } else {
                Text(
                    text = "Active",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelSmall,
                    color = colors.inkMuted,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
    }
}

@Composable
fun DirectSplitListItem(
    split: DirectSplit,
    peerName: String,
    peerAvatar: String,
    peerUpi: String,
    currentUserId: String,
    colors: com.splitsmith.app.theme.SplitColors,
    d: com.splitsmith.app.theme.Dimens,
    onSplitClick: () -> Unit
) {
    val isSettled = split.status == "SETTLED"
    val isWaitingApproval = split.status == "WAITING_APPROVAL"
    val paidByMe = split.paidBy == currentUserId

    val owedText = when {
        isSettled -> if (paidByMe) "Owed by $peerName" else "Owed to $peerName"
        isWaitingApproval -> if (paidByMe) "$peerName marked as paid" else "Waiting receiver approval"
        else -> if (paidByMe) "You are owed" else "You owe"
    }

    val balanceColor = when {
        isSettled -> colors.inkMuted
        isWaitingApproval -> Color(0xFFE65100)
        paidByMe -> colors.inkPrimary
        else -> colors.alertRed
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSplitClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = d.space12),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UserAvatar(avatarUrl = peerAvatar, displayName = peerName, size = d.avatarMd)
            Spacer(modifier = Modifier.width(d.space12))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = split.description.ifEmpty { "P2P Split" },
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = d.textTitleMedium,
                    color = if (isSettled) colors.inkMuted else colors.inkPrimary
                )
                Text(
                    text = owedText,
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "\u20b9${split.myShare}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textMonoLarge,
                    color = balanceColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isSettled) {
                    Surface(
                        shape = RoundedCornerShape(d.radiusXS),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.borderWhisper)
                    ) {
                        Text(
                            text = "✓ Settled",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                } else if (isWaitingApproval) {
                    Surface(
                        shape = RoundedCornerShape(d.radiusXS),
                        color = Color(0xFFFFF3E0),
                        border = BorderStroke(1.dp, Color(0xFFFFB74D))
                    ) {
                        Text(
                            text = "Approval Pending",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textLabelSmall,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectSplitDetailBottomSheet(
    split: DirectSplit,
    peerName: String,
    peerAvatar: String,
    peerUpi: String,
    currentUserId: String,
    onDismiss: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var showMenu by remember { mutableStateOf(false) }

    val paidByMe = split.paidBy == currentUserId
    val isSettled = split.status == "SETTLED"
    val isWaitingApproval = split.status == "WAITING_APPROVAL"
    val isDebtor = !paidByMe

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
                .padding(horizontal = d.space24, vertical = d.space16)
        ) {
            // Header with Title & 3-Dot Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "Split Icon",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(d.space12))
                    Column {
                        Text(
                            text = split.description.ifEmpty { "P2P Split" },
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textHeadlineMedium,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = split.category,
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = colors.inkMuted
                        )
                    }
                }

                if (!isSettled) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = colors.inkPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Delete Split", fontFamily = OutfitFamily, color = colors.alertRed) },
                                onClick = {
                                    showMenu = false
                                    coroutineScope.launch {
                                        try {
                                            FirebaseManager.deleteDirectSplit(split.id)
                                            Toast.makeText(context, "Split deleted", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space24))

            // Pure text amount section (no inner nested card!)
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (paidByMe) "YOU ARE OWED" else "YOU OWE",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelSmall,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(d.space4))
                Text(
                    text = "\u20b9${split.myShare}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = colors.inkPrimary
                )
                Spacer(modifier = Modifier.height(d.space4))
                Text(
                    text = "Total Bill: \u20b9${split.amount}",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
            }

            Spacer(modifier = Modifier.height(d.space24))
            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(d.space16))

            // Participant / Status Details (Clean List)
            Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paid By", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted)
                    Text(if (paidByMe) "You" else peerName, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Split With", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted)
                    Text(if (paidByMe) peerName else "You", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Date", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted)
                    val dateStr = if (split.date > 0) java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(split.date)) else "Recently"
                    Text(dateStr, fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Status", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted)
                    val statusText = when {
                        isSettled -> "Settled \u2713"
                        isWaitingApproval -> "Approval Pending"
                        else -> "Pending"
                    }
                    Text(statusText, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                }
            }

            Spacer(modifier = Modifier.height(d.space24))

            // Action Buttons
            when {
                isSettled -> {
                    Text(
                        text = "This transaction has been fully settled \u2713",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = d.textBodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = d.space12)
                    )
                }

                // If user owes money & payment is pending
                isDebtor && !isWaitingApproval -> {
                    Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                        if (peerUpi.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val uri = Uri.parse("upi://pay").buildUpon()
                                        .appendQueryParameter("pa", peerUpi)
                                        .appendQueryParameter("pn", peerName)
                                        .appendQueryParameter("am", split.myShare.toString())
                                        .appendQueryParameter("cu", "INR")
                                        .appendQueryParameter("tn", "Settling: ${split.description}")
                                        .build()
                                    val intent = Intent(Intent.ACTION_VIEW, uri)
                                    try {
                                        context.startActivity(intent)
                                        coroutineScope.launch {
                                            FirebaseManager.markDirectSplitPaid(split.id, "UPI")
                                            Toast.makeText(context, "Marked as paid via UPI!", Toast.LENGTH_SHORT).show()
                                            onDismiss()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No UPI app found. Copying UPI ID...", Toast.LENGTH_SHORT).show()
                                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UPI ID", peerUpi))
                                        coroutineScope.launch {
                                            FirebaseManager.markDirectSplitPaid(split.id, "UPI")
                                            onDismiss()
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                                shape = RoundedCornerShape(d.radiusMD),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                            ) {
                                Text("Pay via UPI App", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    FirebaseManager.markDirectSplitPaid(split.id, "CASH")
                                    Toast.makeText(context, "Marked as paid via Cash!", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                            shape = RoundedCornerShape(d.radiusMD),
                            border = BorderStroke(1.dp, colors.borderWhisper)
                        ) {
                            Text("Pay in Cash", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = colors.inkPrimary)
                        }

                        if (peerUpi.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("UPI ID", peerUpi))
                                    Toast.makeText(context, "UPI ID copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Copy UPI ID", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                            }
                        }
                    }
                }

                // If user is receiver & status is WAITING_APPROVAL
                paidByMe && isWaitingApproval -> {
                    Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                        Text(
                            text = "$peerName marked this split as paid (${split.method}). Please confirm receipt.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = colors.inkPrimary,
                            textAlign = TextAlign.Center
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        FirebaseManager.declineDirectSplitSettlement(split.id)
                                        Toast.makeText(context, "Declined settlement", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(d.buttonHeight),
                                shape = RoundedCornerShape(d.radiusMD),
                                border = BorderStroke(1.dp, colors.borderWhisper)
                            ) {
                                Text("Decline ✗", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = colors.inkMuted)
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        FirebaseManager.confirmDirectSplitSettlement(split.id)
                                        Toast.makeText(context, "Confirmed settlement!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(d.buttonHeight),
                                shape = RoundedCornerShape(d.radiusMD),
                                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                            ) {
                                Text("Confirm Received ✓", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                            }
                        }
                    }
                }

                // Payer waiting for receiver approval
                !paidByMe && isWaitingApproval -> {
                    Text(
                        text = "You marked this as paid (${split.method}). Waiting for $peerName to confirm.",
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = d.space12)
                    )
                }

                // Creditor waiting for debtor to pay
                paidByMe && !isSettled -> {
                    Text(
                        text = "Waiting for $peerName to pay their share.",
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyMedium,
                        color = colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = d.space12)
                    )
                }
            }
        }
    }
}

// ─── CREATE GROUP BOTTOM SHEET ────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupBottomSheet(
    onDismiss: () -> Unit,
    onGroupCreated: (groupId: String) -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var groupName by remember { mutableStateOf("") }
    var selectedIconName by remember { mutableStateOf("Other") }
    var memberInput by remember { mutableStateOf("") }
    var addedMembers by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    var recentContacts by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            recentContacts = FirebaseManager.getRecentDirectContacts()
        } catch (e: Exception) {
            android.util.Log.e("SplitSmithDebug", "Failed to fetch direct contacts: ${e.message}")
        }
    }

    val suggestions = remember(memberInput, recentContacts) {
        if (memberInput.trim().isEmpty()) emptyList()
        else recentContacts.filter {
            it.displayName.contains(memberInput, ignoreCase = true) ||
            it.email.contains(memberInput, ignoreCase = true)
        }
    }

    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = com.journeyapps.barcodescanner.ScanContract(),
        onResult = { result ->
            if (result.contents != null) {
                val scannedCode = result.contents.trim()
                isSearching = true
                coroutineScope.launch {
                    try {
                        val resolved = if (scannedCode.contains("@")) {
                            FirebaseManager.searchUserByEmail(scannedCode)
                        } else {
                            FirebaseManager.searchUserByCode(scannedCode)
                        }
                        if (resolved != null) {
                            if (addedMembers.none { it.uid == resolved.uid }) {
                                addedMembers = addedMembers + resolved
                                Toast.makeText(context, "Added ${resolved.displayName}!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Already added", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    } finally {
                        isSearching = false
                    }
                }
            }
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceCard,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.borderWhisper)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = d.space24, vertical = d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space20)
        ) {
            // Title
            Text(
                text = "New Group",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = d.textTitleLarge,
                color = colors.inkPrimary
            )

            // Group name field
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Group name",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                    shape = RoundedCornerShape(d.radiusSM),
                    placeholder = {
                        Text("e.g. Goa Trip, Flat Expenses...", fontFamily = OutfitFamily, color = colors.inkMuted)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.borderWhisper,
                        focusedContainerColor = colors.surfaceCard,
                        unfocusedContainerColor = colors.surfaceCard,
                        focusedTextColor = colors.inkPrimary,
                        unfocusedTextColor = colors.inkPrimary
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyLarge,
                        color = colors.inkPrimary
                    )
                )
            }

            // Group Icon Picker
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Group Icon",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(listOf("Home", "Trip", "Work", "Event", "Food", "Payment", "Shopping", "Dining", "Drinks", "Pets", "Education", "Tech", "Other")) { iconName ->
                        val isSelected = selectedIconName == iconName
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .let {
                                    if (isSelected) {
                                        it.border(2.dp, colors.inkPrimary, CircleShape).padding(2.dp)
                                    } else {
                                        it.alpha(0.5f)
                                    }
                                }
                                .clickable { selectedIconName = iconName }
                        ) {
                            GroupIconView(
                                iconName = iconName,
                                size = 36.dp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }

            // Add members section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Add Members",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.space8),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = memberInput,
                        onValueChange = { memberInput = it },
                        modifier = Modifier.weight(1f).heightIn(min = d.inputHeight),
                        shape = RoundedCornerShape(d.radiusSM),
                        placeholder = {
                            Text("Email or short code", fontFamily = OutfitFamily, color = colors.inkMuted)
                        },
                        singleLine = true,
                        trailingIcon = {
                            Text(
                                text = "Scan",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textLabelMedium,
                                color = colors.inkPrimary,
                                modifier = Modifier
                                    .clickable {
                                        val options = com.journeyapps.barcodescanner.ScanOptions().apply {
                                            setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                            setPrompt("Scan a member QR code")
                                            setBeepEnabled(false)
                                            setOrientationLocked(false)
                                        }
                                        qrScanLauncher.launch(options)
                                    }
                                    .padding(horizontal = d.space12)
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.inkPrimary,
                            unfocusedBorderColor = colors.borderWhisper,
                            focusedContainerColor = colors.surfaceCard,
                            unfocusedContainerColor = colors.surfaceCard,
                            focusedTextColor = colors.inkPrimary,
                            unfocusedTextColor = colors.inkPrimary
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyLarge,
                            color = colors.inkPrimary
                        )
                    )

                    Button(
                        onClick = {
                            if (memberInput.trim().isEmpty()) return@Button
                            isSearching = true
                            coroutineScope.launch {
                                try {
                                    val resolved = if (memberInput.contains("@")) {
                                        FirebaseManager.searchUserByEmail(memberInput.trim())
                                    } else {
                                        FirebaseManager.searchUserByCode(memberInput.trim())
                                    }
                                    if (resolved != null) {
                                        if (addedMembers.none { it.uid == resolved.uid }) {
                                            addedMembers = addedMembers + resolved
                                            memberInput = ""
                                        } else {
                                            Toast.makeText(context, "Already added", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isSearching = false
                                }
                            }
                        },
                        modifier = Modifier.height(d.inputHeight),
                        shape = RoundedCornerShape(d.radiusSM),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                        enabled = !isSearching && memberInput.trim().isNotEmpty()
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.canvasChalk, strokeWidth = 2.dp)
                        } else {
                            Text("Add", fontFamily = OutfitFamily, color = colors.canvasChalk)
                        }
                    }
                }

                // Dynamic suggestions for connected users
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.canvasChalk, RoundedCornerShape(d.radiusSM))
                            .padding(vertical = d.space4),
                        verticalArrangement = Arrangement.spacedBy(d.space4)
                    ) {
                        suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (addedMembers.none { it.uid == suggestion.uid }) {
                                            addedMembers = addedMembers + suggestion
                                        }
                                        memberInput = ""
                                    }
                                    .padding(horizontal = d.space12, vertical = d.space8),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(d.space8)
                            ) {
                                UserAvatar(avatarUrl = suggestion.avatarUrl, displayName = suggestion.displayName, size = d.avatarSm)
                                Column {
                                    Text(suggestion.displayName, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                                    Text(suggestion.email, fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                                }
                            }
                        }
                    }
                }

                // Render added members chips
                if (addedMembers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        items(addedMembers) { member ->
                            Surface(
                                shape = RoundedCornerShape(d.radiusFull),
                                color = colors.canvasChalk,
                                border = BorderStroke(1.dp, colors.borderWhisper)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = member.displayName,
                                        fontFamily = OutfitFamily,
                                        fontSize = d.textLabelMedium,
                                        color = colors.inkPrimary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove",
                                        tint = colors.inkMuted,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { addedMembers = addedMembers.filter { it.uid != member.uid } }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space8))

            // Create button
            Button(
                onClick = {
                    if (groupName.trim().isEmpty()) return@Button
                    isLoading = true
                    coroutineScope.launch {
                        try {
                            val id = FirebaseManager.createGroup(
                                name = groupName.trim(),
                                iconName = selectedIconName,
                                type = selectedIconName,
                                memberUids = addedMembers.map { it.uid }
                            )
                            onGroupCreated(id)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                enabled = !isLoading && groupName.trim().isNotEmpty(),
                shape = RoundedCornerShape(d.radiusMD),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    disabledContainerColor = colors.borderWhisper
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.canvasChalk, strokeWidth = 2.dp)
                } else {
                    Text("Create Group", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = colors.canvasChalk)
                }
            }
        }
    }
}



