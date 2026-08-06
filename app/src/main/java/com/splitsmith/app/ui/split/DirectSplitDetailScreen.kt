package com.splitsmith.app.ui.split

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
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
import com.splitsmith.app.data.*
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.ui.components.UserAvatar
import com.splitsmith.app.ui.components.dotGridBackground
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectSplitDetailScreen(
    peerUid: String,
    onBack: () -> Unit,
    onNavigateToQuickSplit: (UserProfile) -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUserId = FirebaseManager.currentUserId ?: ""

    val splitsFlow = remember(currentUserId) { FirebaseManager.observeDirectSplits() }
    val connectionsFlow = remember { FirebaseManager.observeConnections() }
    val splitsState = splitsFlow.collectAsState(initial = emptyList())
    val connectionsState = connectionsFlow.collectAsState(initial = emptyList())

    var peerProfile by remember { mutableStateOf<UserProfile?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var selectedSplitForDetail by remember { mutableStateOf<DirectSplit?>(null) }

    LaunchedEffect(peerUid) {
        val fetched = FirebaseManager.getUserProfile(peerUid)
        if (fetched != null) {
            peerProfile = fetched
        }
    }

    val isConnected = remember(connectionsState.value, peerUid) {
        connectionsState.value.any { it.uid == peerUid }
    }

    val peerSplits = remember(splitsState.value, peerUid, currentUserId) {
        splitsState.value.filter {
            (it.paidBy == currentUserId && it.withUser == peerUid) ||
            (it.paidBy == peerUid && it.withUser == currentUserId)
        }.sortedByDescending { it.date }
    }

    val peerName = peerProfile?.displayName?.ifEmpty { peerProfile?.email?.substringBefore("@") } ?: "Friend"
    val peerAvatar = peerProfile?.avatarUrl ?: ""
    val peerUpi = peerProfile?.upiId ?: ""

    val peerGroup = remember(peerSplits, peerUid, peerName, peerAvatar, peerUpi) {
        val net = peerSplits.sumOf { s ->
            if (s.status == "SETTLED") 0.0
            else if (s.paidBy == currentUserId) s.myShare
            else -s.myShare
        }
        IndividualPeerGroup(
            peerUid = peerUid,
            peerName = peerName,
            peerAvatar = peerAvatar,
            peerUpi = peerUpi,
            netBalance = net,
            splits = peerSplits
        )
    }

    val pagerState = rememberPagerState(pageCount = { 2 })

    Scaffold(
        containerColor = colors.canvasChalk,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = {
            if (pagerState.currentPage == 0) {
                FloatingActionButton(
                    onClick = {
                        val profile = peerProfile ?: UserProfile(uid = peerUid, displayName = peerName, avatarUrl = peerAvatar, upiId = peerUpi)
                        onNavigateToQuickSplit(profile)
                    },
                    containerColor = colors.inkPrimary,
                    contentColor = colors.canvasChalk,
                    shape = CircleShape
                ) {
                    Text("+", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textHeadlineMedium, color = colors.canvasChalk)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.canvasChalk)
                .dotGridBackground(colors.dotColor.copy(alpha = 0.4f))
                .padding(paddingValues)
                .statusBarsPadding()
                .padding(top = d.space16)
        ) {
            // ── Top App Bar ──────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space16, vertical = d.space8),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.inkPrimary)
                    }
                    Spacer(modifier = Modifier.width(d.space8))
                    UserAvatar(avatarUrl = peerAvatar, displayName = peerName, size = d.avatarMd)
                    Spacer(modifier = Modifier.width(d.space12))
                    Column {
                        Text(
                            text = peerName,
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textTitleLarge,
                            color = colors.inkPrimary
                        )
                        if (peerUpi.isNotEmpty()) {
                            Text(
                                text = "UPI: $peerUpi",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = 11.sp,
                                color = colors.inkMuted
                            )
                        }
                    }
                }

                Box {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = colors.inkPrimary)
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        if (isConnected) {
                            DropdownMenuItem(
                                text = { Text("Disconnect Friend", fontFamily = OutfitFamily, color = colors.alertRed) },
                                onClick = {
                                    showOverflowMenu = false
                                    showDisconnectDialog = true
                                }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Reconnect Friend", fontFamily = OutfitFamily, color = colors.inkPrimary) },
                                onClick = {
                                    showOverflowMenu = false
                                    coroutineScope.launch {
                                        try {
                                            FirebaseManager.addConnection(peerUid)
                                            Toast.makeText(context, "Connected with $peerName!", Toast.LENGTH_SHORT).show()
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

            Spacer(modifier = Modifier.height(d.space8))

            // ── Net Position Banner ───────────────────────
            val net = peerGroup.netBalance
            val netText = when {
                net > 0.01  -> "Owes you \u20b9${"%.0f".format(net)}"
                net < -0.01 -> "You owe \u20b9${"%.0f".format(-net)}"
                else        -> "Settled up"
            }
            val netColor = when {
                net > 0.01  -> colors.positiveGreen
                net < -0.01 -> colors.alertRed
                else        -> colors.inkMuted
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space24),
                shape = RoundedCornerShape(d.radiusMD),
                color = colors.surfaceCard,
                border = BorderStroke(1.dp, colors.borderWhisper)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = d.space16, vertical = d.space12),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Standing", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                    Text(netText, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleMedium, color = netColor)
                }
            }

            Spacer(modifier = Modifier.height(d.space16))

            // ── Segmented Tab Header ──────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space24),
                horizontalArrangement = Arrangement.spacedBy(d.space8)
            ) {
                listOf("Expenses", "Balances").forEachIndexed { index, label ->
                    val isActive = pagerState.currentPage == index
                    Surface(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        shape = RoundedCornerShape(d.radiusFull),
                        color = if (isActive) colors.inkPrimary else colors.surfaceCard,
                        border = if (!isActive) BorderStroke(1.dp, colors.borderWhisper) else null,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space20)) {
                            Text(
                                text = label,
                                fontFamily = OutfitFamily,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                fontSize = d.textLabelLarge,
                                color = if (isActive) colors.canvasChalk else colors.inkMuted
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space12))

            // ── Tab Body Pager ───────────────────────────
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> {
                        // Expenses Tab
                        if (peerSplits.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No splits with $peerName yet.\nTap + to start a split!", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted, textAlign = TextAlign.Center)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space8),
                                verticalArrangement = Arrangement.spacedBy(d.space8)
                            ) {
                                items(peerSplits) { split ->
                                    DirectSplitListItem(
                                        split = split,
                                        peerName = peerName,
                                        peerAvatar = peerAvatar,
                                        peerUpi = peerUpi,
                                        currentUserId = currentUserId,
                                        colors = colors,
                                        d = d,
                                        onSplitClick = { selectedSplitForDetail = split }
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Balances Tab
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = d.space24, vertical = d.space16),
                            verticalArrangement = Arrangement.spacedBy(d.space16)
                        ) {
                            val unsettled = peerSplits.filter { it.status != "SETTLED" }
                            if (unsettled.isEmpty()) {
                                Text("All transactions settled ✓", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted)
                            } else {
                                Text("UNSETTLED TRANSACTIONS", fontFamily = OutfitFamily, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.inkMuted, letterSpacing = 1.5.sp)
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(unsettled) { split ->
                                        Surface(
                                            shape = RoundedCornerShape(d.radiusSM),
                                            color = colors.surfaceCard,
                                            border = BorderStroke(1.dp, colors.borderWhisper),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(d.space12),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(split.description.ifEmpty { "1-on-1 Split" }, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.inkPrimary)
                                                    Text("Share: \u20b9${"%.0f".format(split.myShare)}", fontFamily = JetBrainsMonoFamily, fontSize = 12.sp, color = colors.inkMuted)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            try {
                                                                FirebaseManager.settleDirectSplit(split.id)
                                                                Toast.makeText(context, "Settled!", Toast.LENGTH_SHORT).show()
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    shape = RoundedCornerShape(d.radiusFull),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Text("Settle", fontFamily = OutfitFamily, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            containerColor = colors.surfaceCard,
            title = { Text("Disconnect $peerName?", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary) },
            text = {
                Text("Disconnecting removes them from your connected friends. Your past split history will not be deleted.", fontFamily = OutfitFamily, fontSize = 14.sp, color = colors.inkMuted)
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                FirebaseManager.removeConnection(peerUid)
                                showDisconnectDialog = false
                                Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.alertRed)
                ) { Text("Disconnect", fontFamily = OutfitFamily, color = colors.canvasChalk) }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            }
        )
    }

    if (selectedSplitForDetail != null) {
        DirectSplitDetailBottomSheet(
            split = selectedSplitForDetail!!,
            peerName = peerName,
            peerAvatar = peerAvatar,
            peerUpi = peerUpi,
            currentUserId = currentUserId,
            onDismiss = { selectedSplitForDetail = null }
        )
    }
}
