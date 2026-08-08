package com.splitsmith.app.ui.group

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import com.splitsmith.app.ui.components.UserAvatar
import com.splitsmith.app.ui.components.GroupIconView
import com.splitsmith.app.ui.components.dotGridBackground
import com.splitsmith.app.util.UpiPaymentHelper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.*
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.data.Settlement
import com.splitsmith.app.data.UserProfile
import com.splitsmith.app.util.formatCurrency
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.content.FileProvider

private val memberHues = listOf(
    Color(0xFF6366F1), Color(0xFF8B5CF6), Color(0xFF06B6D4),
    Color(0xFF10B981), Color(0xFFF59E0B), Color(0xFFEF4444)
)
private fun hueForMember(id: String) = memberHues[Math.abs(id.hashCode()) % memberHues.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: String,
    onBack: () -> Unit,
    onNavigateToAddExpense: (groupId: String, expenseId: String?) -> Unit,
    onNavigateToReports: ((String) -> Unit)? = null
) {
    val d = LocalDimens.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    var selectedDebtForSettlement by remember { mutableStateOf<Debt?>(null) }
    var userNamesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberProfilesMap by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var pendingConfirmUpiSettlement by remember { mutableStateOf<Debt?>(null) }

    var selectedSettlementProofUri by remember { mutableStateOf<Uri?>(null) }
    var isUploadingSettlementProof by remember { mutableStateOf(false) }
    var selectedReceiptUrlForPreview by remember { mutableStateOf<String?>(null) }
    var targetPendingSettlementForProof by remember { mutableStateOf<Settlement?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val settlementProofPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        selectedSettlementProofUri = uri
    }

    val pendingProofPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val s = targetPendingSettlementForProof
        if (uri != null && s != null) {
            coroutineScope.launch {
                try {
                    isUploadingSettlementProof = true
                    val uploadResult = CloudinaryManager.uploadReceipt(context, uri, FirebaseManager.currentUserId ?: "user", "settlements")
                    val uploadedUrl = uploadResult.getOrThrow()
                    FirebaseManager.attachSettlementReceipt(groupId, s.id, uploadedUrl)
                    Toast.makeText(context, "Payment proof attached!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error uploading proof: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isUploadingSettlementProof = false
                    targetPendingSettlementForProof = null
                }
            }
        }
    }

    val groupFlow = remember(groupId) { FirebaseManager.observeGroup(groupId) }
    val expensesFlow = remember(groupId) { FirebaseManager.observeExpenses(groupId) }
    val settlementsFlow = remember(groupId) { FirebaseManager.observeSettlements(groupId) }

    val groupState       = groupFlow.collectAsState(initial = null)
    val expensesState    = expensesFlow.collectAsState(initial = emptyList())
    val settlementsState = settlementsFlow.collectAsState(initial = emptyList())

    val currentGroup = groupState.value
    val expenses     = expensesState.value
    val settlements  = settlementsState.value

    val currentUserId = FirebaseManager.currentUserId
    val isMember = currentGroup?.members?.get(currentUserId ?: "") == true
    val isPending = currentGroup?.joinRequests?.get(currentUserId ?: "") == true
    val isInvited = currentGroup?.pendingMembers?.get(currentUserId ?: "") == true

    LaunchedEffect(currentGroup) {
        if (currentGroup == null) return@LaunchedEffect
        val keysToLoad = currentGroup.members.keys + currentGroup.joinRequests.keys
        val tempNames = mutableMapOf<String, String>()
        val tempProfiles = mutableMapOf<String, UserProfile>()
        for (uid in keysToLoad) {
            val profile = FirebaseManager.getUserProfile(uid)
            if (profile != null) {
                tempNames[uid] = profile.displayName
                tempProfiles[uid] = profile
            }
        }
        userNamesMap = tempNames
        memberProfilesMap = tempProfiles
    }

    val netBalances = remember(currentGroup, expenses, settlements) {
        val membersList = currentGroup?.members?.keys?.toList() ?: emptyList()
        DebtSolver.calculateNetBalances(membersList, expenses, settlements)
    }
    val debts = remember(expenses, settlements) { DebtSolver.calculatePeerDebts(expenses, settlements) }

    val colors = LocalSplitColors.current
    val canvasChalk   = colors.canvasChalk
    val accentIndigo  = colors.inkPrimary
    val inkPrimary    = colors.inkPrimary
    val inkMuted      = colors.inkMuted
    val borderWhisper = colors.borderWhisper
    val positiveGreen = colors.positiveGreen
    val alertRed      = colors.alertRed

    if (expenseToDelete != null) {
        val exp = expenseToDelete!!
        com.splitsmith.app.ui.components.DeleteExpenseDialog(
            hasAttachments = exp.receiptUrls.isNotEmpty(),
            onDismiss = { expenseToDelete = null },
            onConfirmDelete = { _ ->
                coroutineScope.launch {
                    try {
                        FirebaseManager.deleteExpense(groupId, exp.id, exp.getEffectiveReceiptUrls())
                        Toast.makeText(context, "Expense deleted", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                expenseToDelete = null
            }
        )
    }

    Scaffold(
        containerColor = canvasChalk,
        contentWindowInsets = WindowInsets(0), // Ignore default window insets to lay out correctly
        floatingActionButton = {
            if (pagerState.currentPage == 0 && isMember) {
                FloatingActionButton(
                    onClick = { onNavigateToAddExpense(groupId, null) },
                    containerColor = inkPrimary,
                    contentColor = canvasChalk,
                    shape = CircleShape
                ) {
                    Text("+", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textHeadlineMedium, color = canvasChalk)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(canvasChalk)
                .dotGridBackground(colors.dotColor)
                .padding(paddingValues)
                .statusBarsPadding()
                .padding(top = d.space24),
            verticalArrangement = Arrangement.spacedBy(d.space16)
        ) {


            if (currentGroup == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = inkPrimary)
                }
            } else if (!isMember) {
                // Non-member / Pending request layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = d.space24, vertical = d.space32),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    GroupIconView(
                        iconName = currentGroup.iconName,
                        size = 80.dp
                    )
                    Spacer(modifier = Modifier.height(d.space24))
                    Text(
                        text = currentGroup.name,
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textHeadlineLarge,
                        color = inkPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(d.space8))
                    if (isInvited) {
                        Text(
                            text = "Group Invitation",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textTitleMedium,
                            color = positiveGreen,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(d.space12))
                        Text(
                            text = "You have been invited to join ${currentGroup.name}. Accept the invitation to view expenses and participate in splits.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = inkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(d.space32))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(d.space12)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            FirebaseManager.declineGroupInvitation(groupId)
                                            Toast.makeText(context, "Invitation declined", Toast.LENGTH_SHORT).show()
                                            onBack()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(d.radiusMD),
                                border = BorderStroke(1.dp, borderWhisper),
                                modifier = Modifier.weight(1f).height(d.buttonHeight)
                            ) {
                                Text("Decline", fontFamily = OutfitFamily, color = alertRed)
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        try {
                                            FirebaseManager.acceptGroupInvitation(groupId)
                                            Toast.makeText(context, "Joined ${currentGroup.name}!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = inkPrimary),
                                shape = RoundedCornerShape(d.radiusMD),
                                modifier = Modifier.weight(1f).height(d.buttonHeight)
                            ) {
                                Text("Accept & Join", fontFamily = OutfitFamily, color = colors.canvasChalk)
                            }
                        }
                    } else if (isPending) {
                        Text(
                            text = "Pending Admin Approval",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textTitleMedium,
                            color = colors.inkPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(d.space12))
                        Text(
                            text = "Your request to join this group is pending admin approval. You will be able to see group expenses once the admin approves your request.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = inkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(d.space32))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        FirebaseManager.declineJoinRequest(groupId, currentUserId ?: "")
                                        Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = alertRed),
                            shape = RoundedCornerShape(d.radiusMD),
                            modifier = Modifier.fillMaxWidth().height(d.buttonHeight)
                        ) {
                            Text("Cancel Request", fontFamily = OutfitFamily, color = colors.canvasChalk)
                        }
                    } else {
                        Text(
                            text = "You are not a member of this group.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = inkMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(d.space32))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        FirebaseManager.requestToJoinGroup(groupId)
                                        Toast.makeText(context, "Join request sent to admin!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to send request: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = inkPrimary),
                            shape = RoundedCornerShape(d.radiusMD),
                            modifier = Modifier.fillMaxWidth().height(d.buttonHeight)
                        ) {
                            Text("Request to Join", fontFamily = OutfitFamily, color = colors.canvasChalk)
                        }
                    }
                    Spacer(modifier = Modifier.height(d.space12))
                    TextButton(onClick = onBack) {
                        Text("Go Back", fontFamily = OutfitFamily, color = inkMuted)
                    }
                }
            } else {
                // ── Compact Header ────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space24),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(d.space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GroupIconView(
                            iconName = currentGroup.iconName,
                            size = 44.dp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = currentGroup.name,
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textHeadlineLarge,
                                color = inkPrimary,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Code: ${groupId.take(6).uppercase()}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = d.textMonoSmall,
                                color = inkMuted
                            )
                        }
                    }
                    IconButton(
                        onClick = { showSettingsSheet = true },
                        modifier = Modifier.size(d.iconSizeMd + 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Group Settings",
                            tint = inkMuted,
                            modifier = Modifier.size(d.iconSizeMd)
                        )
                    }
                }

                // ── Member chips row with profile image support ───
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space24),
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    val approvedMemberUids = memberProfilesMap.values.distinctBy { it.email.ifEmpty { it.displayName } }.map { it.uid }.ifEmpty { currentGroup.members.keys.toList() }
                    items(approvedMemberUids) { uid ->
                        val profile = memberProfilesMap[uid]
                        val displayName = userNamesMap[uid] ?: "User"
                        UserAvatar(
                            avatarUrl = profile?.avatarUrl ?: "",
                            displayName = displayName,
                            size = d.avatarMd
                        )
                    }
                }

                // ── Minimal Group & User Spend Header Line ───
                val groupBudgetLimit = currentGroup.budget.limit
                val totalGroupSpend = remember(expenses) { expenses.sumOf { it.amount } }
                val myUid = FirebaseManager.currentUserId
                val currentUserSpend = remember(expenses, myUid) { expenses.sumOf { it.splits[myUid ?: ""] ?: 0.0 } }

                val myNetBalance = remember(netBalances) { netBalances[myUid] ?: 0.0 }
                val netText = when {
                    myNetBalance > 0.01  -> "You are owed \u20b9${myNetBalance.formatCurrency()}"
                    myNetBalance < -0.01 -> "You owe \u20b9${(-myNetBalance).formatCurrency()}"
                    else                 -> "Settled up"
                }
                val netColor = when {
                    myNetBalance > 0.01  -> positiveGreen
                    myNetBalance < -0.01 -> alertRed
                    else                 -> inkMuted
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space24),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Group Total:",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelMedium,
                                color = inkMuted
                            )
                            Text(
                                text = "\u20b9${totalGroupSpend.formatCurrency()}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = d.textLabelMedium,
                                fontWeight = FontWeight.Bold,
                                color = inkPrimary
                            )
                        }
                        Text(
                            text = netText,
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            fontWeight = FontWeight.Bold,
                            color = netColor
                        )
                    }

                    if (groupBudgetLimit > 0.0) {
                        val groupBudgetProgress = (totalGroupSpend / groupBudgetLimit).coerceIn(0.0, 1.0)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Budget Limit",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelSmall,
                                color = inkMuted
                            )
                            val remainingGroupBudget = groupBudgetLimit - totalGroupSpend
                            Text(
                                text = if (remainingGroupBudget >= 0) "₹${remainingGroupBudget.formatCurrency()} left" else "Overspent: ₹${(-remainingGroupBudget).formatCurrency()}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = d.textLabelSmall,
                                fontWeight = if (remainingGroupBudget < 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (remainingGroupBudget < 0) alertRed else inkMuted
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(borderWhisper)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(groupBudgetProgress.toFloat())
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        when {
                                            groupBudgetProgress < 0.8 -> inkPrimary
                                            groupBudgetProgress < 1.0 -> Color(0xFFF59E0B)
                                            else -> alertRed
                                        }
                                    )
                            )
                        }
                    }
                }

                val currentUserIsAdmin = currentGroup.adminId == myUid || currentGroup.admins[myUid] == true
                val pendingApplicants = currentGroup.joinRequests.keys.toList()
                android.util.Log.d("SplitSmith_JR", "GroupDetail: myUid=$myUid isAdmin=$currentUserIsAdmin groupAdminId=${currentGroup.adminId} adminsMap=${currentGroup.admins} pendingApplicants=$pendingApplicants")


                if (currentUserIsAdmin && pendingApplicants.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(d.space8))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = d.space24)
                            .clickable { showSettingsSheet = true },
                        shape = RoundedCornerShape(d.radiusSM),
                        color = colors.surfaceCard,
                        border = BorderStroke(1.dp, colors.borderWhisper)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = d.space12, vertical = d.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Pending Requests",
                                    tint = colors.inkPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${pendingApplicants.size} pending join requests",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = d.textLabelSmall,
                                    color = colors.inkPrimary
                                )
                            }
                            Text(
                                text = "Review →",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textLabelSmall,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }

                // ── Custom pill tab bar (compact 2-tab layout) ────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = (-2).dp)
                        .padding(horizontal = d.space24),
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    listOf("Expenses", "Balances").forEachIndexed { index, label ->
                        val isActive = pagerState.currentPage == index
                        val bgColor by animateColorAsState(
                            targetValue = if (isActive) accentIndigo else Color.Transparent,
                            label = "tabBg$index"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) canvasChalk else inkMuted,
                            label = "tabText$index"
                        )
                        Surface(
                            onClick = {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                            shape = RoundedCornerShape(d.radiusFull),
                            color = bgColor,
                            modifier = Modifier.height(d.space32 + d.space4)
                        ) {
                            Text(
                                text = label,
                                fontFamily = OutfitFamily,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = d.textLabelLarge,
                                color = textColor,
                                modifier = Modifier.padding(horizontal = d.space16, vertical = d.space8)
                            )
                        }
                    }
                }

                HorizontalDivider(color = borderWhisper)

                // ── Swipeable Tab content ───────────────────────────────────
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { page ->
                    when (page) {
                        0 -> StyledExpensesTab(
                            expenses = expenses,
                            userNames = userNamesMap,
                            groupId = groupId,
                            onNavigateToAddExpense = onNavigateToAddExpense,
                            onDeleteExpense = { expenseToDelete = it },
                            d = d,
                            inkPrimary = inkPrimary,
                            inkMuted = inkMuted,
                            borderWhisper = borderWhisper,
                            alertRed = alertRed
                        )
                        1 -> StyledBalancesTab(
                            debts = debts,
                            settlements = settlements,
                            userNames = userNamesMap,
                            memberProfilesMap = memberProfilesMap,
                            groupId = groupId,
                            d = d,
                            inkPrimary = inkPrimary,
                            inkMuted = inkMuted,
                            borderWhisper = borderWhisper,
                            alertRed = alertRed,
                            accentIndigo = accentIndigo,
                            onSettleClick = { debt -> selectedDebtForSettlement = debt }
                        )
                    }
                }
            }
        }

        // ── Settings Bottom Sheet ─────────────────────────────
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = canvasChalk,
                dragHandle = { BottomSheetDefaults.DragHandle(color = borderWhisper) }
            ) {
                StyledSettingsTab(
                    group = currentGroup,
                    userNamesMap = userNamesMap,
                    memberProfilesMap = memberProfilesMap,
                    onBack = {
                        showSettingsSheet = false
                        onBack()
                    },
                    onNavigateToReports = { gId ->
                        showSettingsSheet = false
                        onNavigateToReports?.invoke(gId)
                    }
                )
            }
        }

        // ── Settlement Bottom Sheet ───────────────────────────
        if (selectedDebtForSettlement != null) {
            val debt = selectedDebtForSettlement!!
            val fromName = userNamesMap[debt.fromUser] ?: "User"
            val toName   = userNamesMap[debt.toUser]   ?: "User"

            ModalBottomSheet(onDismissRequest = {
                selectedDebtForSettlement = null
                selectedSettlementProofUri = null
            }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(d.space24)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    Text("Settle Balance", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary)
                    Text("$fromName owes $toName \u20b9${debt.amount.formatCurrency()}", fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkMuted)

                    // Optional Payment Proof Attachment Card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(d.radiusMD))
                            .background(colors.borderWhisper.copy(alpha = 0.2f))
                            .padding(d.space12)
                    ) {
                        Text("Payment Proof / Receipt (Optional)", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelMedium, color = colors.inkPrimary)
                        Spacer(modifier = Modifier.height(d.space8))
                        if (selectedSettlementProofUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                                    Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = colors.inkPrimary, modifier = Modifier.size(20.dp))
                                    Text("Screenshot Selected", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                                }
                                IconButton(onClick = { selectedSettlementProofUri = null }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Remove", tint = colors.alertRed)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settlementProofPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(d.radiusMD)
                            ) {
                                Text("📷 Attach Payment Proof / Screenshot", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkPrimary)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val receiverUpi = FirebaseManager.getReceiverUpiId(debt.toUser)
                                    UpiPaymentHelper.launchUpiPayment(
                                        context = context,
                                        receiverUpi = receiverUpi,
                                        receiverName = toName,
                                        amount = debt.amount,
                                        note = "SplitSmith Group Settlement",
                                        onPaymentInitiated = {
                                            pendingConfirmUpiSettlement = debt
                                            selectedDebtForSettlement = null
                                        }
                                    )
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                    ) { Text("Pay via UPI App", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge) }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    isUploadingSettlementProof = true
                                    var proofUrl = ""
                                    selectedSettlementProofUri?.let { uri ->
                                        val uploadRes = CloudinaryManager.uploadReceipt(context, uri, currentUserId ?: "user", "settlements")
                                        proofUrl = uploadRes.getOrDefault("")
                                    }
                                    FirebaseManager.addSettlement(groupId, debt.toUser, debt.amount, "CASH", receiptUrl = proofUrl)
                                    Toast.makeText(context, "Cash settlement request sent. Awaiting creditor confirmation.", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isUploadingSettlementProof = false
                                    selectedSettlementProofUri = null
                                    selectedDebtForSettlement = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD),
                        enabled = !isUploadingSettlementProof,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkPrimary)
                    ) {
                        if (isUploadingSettlementProof) {
                            CircularProgressIndicator(color = colors.inkPrimary, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Mark Paid in Cash", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                        }
                    }
                }
            }
        }

        if (pendingConfirmUpiSettlement != null) {
            val debt = pendingConfirmUpiSettlement!!
            val toName = userNamesMap[debt.toUser] ?: "User"
            AlertDialog(
                onDismissRequest = {
                    pendingConfirmUpiSettlement = null
                    selectedSettlementProofUri = null
                },
                containerColor = colors.surfaceCard,
                title = { Text("Confirm UPI Payment", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                        Text("Did your payment of \u20b9${debt.amount.formatCurrency()} to $toName complete successfully in your UPI app?", fontFamily = OutfitFamily, color = colors.inkMuted)

                        if (selectedSettlementProofUri != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = d.space4),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("✓ Proof attached", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelMedium, color = colors.inkPrimary)
                                TextButton(onClick = { selectedSettlementProofUri = null }) {
                                    Text("Remove", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.alertRed)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    settlementProofPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = d.space4),
                                shape = RoundedCornerShape(d.radiusMD)
                            ) {
                                Text("📷 Attach GPay / PhonePe Screenshot", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    isUploadingSettlementProof = true
                                    var proofUrl = ""
                                    selectedSettlementProofUri?.let { uri ->
                                        val uploadRes = CloudinaryManager.uploadReceipt(context, uri, currentUserId ?: "user", "settlements")
                                        proofUrl = uploadRes.getOrDefault("")
                                    }
                                    FirebaseManager.addSettlement(groupId, debt.toUser, debt.amount, "UPI", "UPI_REF_AUTO", receiptUrl = proofUrl)
                                    Toast.makeText(context, "UPI payment recorded. Pending creditor confirmation.", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isUploadingSettlementProof = false
                                    selectedSettlementProofUri = null
                                    pendingConfirmUpiSettlement = null
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk),
                        enabled = !isUploadingSettlementProof
                    ) {
                        Text(if (isUploadingSettlementProof) "Saving..." else "Yes, Submit Request", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingConfirmUpiSettlement = null
                        selectedSettlementProofUri = null
                    }) {
                        Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                    }
                }
            )
        }
    }
}

// ── Expenses Tab ─────────────────────────────────────────────
@Composable
private fun StyledExpensesTab(
    expenses: List<Expense>,
    userNames: Map<String, String>,
    groupId: String,
    onNavigateToAddExpense: (groupId: String, expenseId: String?) -> Unit,
    onDeleteExpense: (Expense) -> Unit,
    d: com.splitsmith.app.theme.Dimens,
    inkPrimary: Color,
    inkMuted: Color,
    borderWhisper: Color,
    alertRed: Color
) {
    val coroutineScope = rememberCoroutineScope()
    var selectedExpenseForDetail by remember { mutableStateOf<Expense?>(null) }

    if (expenses.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No expenses yet. Tap + to log one.", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = inkMuted, textAlign = TextAlign.Center)
        }
    } else {
        val sortedExpenses = remember(expenses) { expenses.sortedByDescending { it.date } }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space8),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(sortedExpenses) { expense ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedExpenseForDetail = expense }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = d.rowHeightLg)
                            .padding(vertical = d.space12),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(expense.description, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = inkPrimary)
                            val formattedDate = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(expense.date))
                            Text(
                                text = "Paid by ${userNames[expense.paidBy] ?: "Payer"} · $formattedDate · ${expense.category}",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelMedium,
                                color = inkMuted
                            )
                        }
                        Text(
                            text = "₹${if (expense.amount % 1.0 == 0.0) expense.amount.toInt().toString() else String.format("%.2f", expense.amount)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textMonoLarge,
                            color = inkPrimary
                        )
                    }
                    HorizontalDivider(color = borderWhisper, thickness = 0.5.dp)
                }
            }
        }
    }

    if (selectedExpenseForDetail != null) {
        val exp = selectedExpenseForDetail!!
        GroupExpenseDetailBottomSheet(
            expense = exp,
            groupId = groupId,
            userNames = userNames,
            onDismiss = { selectedExpenseForDetail = null },
            onEdit = {
                selectedExpenseForDetail = null
                onNavigateToAddExpense(groupId, exp.id)
            },
            onDelete = {
                selectedExpenseForDetail = null
                onDeleteExpense(exp)
            }
        )
    }
}

// ── Balances Tab ─────────────────────────────────────────────
@Composable
private fun StyledBalancesTab(
    debts: List<Debt>,
    settlements: List<Settlement>,
    userNames: Map<String, String>,
    memberProfilesMap: Map<String, UserProfile>,
    groupId: String,
    d: com.splitsmith.app.theme.Dimens,
    inkPrimary: Color,
    inkMuted: Color,
    borderWhisper: Color,
    alertRed: Color,
    accentIndigo: Color,
    onSettleClick: (Debt) -> Unit
) {
    val colors = LocalSplitColors.current
    val currentUserId = FirebaseManager.currentUserId
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showFullLedger by remember { mutableStateOf(false) }
    var selectedReceiptUrlForPreview by remember { mutableStateOf<String?>(null) }
    var targetPendingSettlementForProof by remember { mutableStateOf<Settlement?>(null) }

    val pendingProofPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val s = targetPendingSettlementForProof
        if (uri != null && s != null) {
            coroutineScope.launch {
                try {
                    val uploadResult = CloudinaryManager.uploadReceipt(context, uri, currentUserId ?: "user", "settlements")
                    val uploadedUrl = uploadResult.getOrThrow()
                    FirebaseManager.attachSettlementReceipt(groupId, s.id, uploadedUrl)
                    Toast.makeText(context, "Payment proof attached!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error uploading proof: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    targetPendingSettlementForProof = null
                }
            }
        }
    }

    val displayDebts = remember(debts, showFullLedger, currentUserId) {
        if (showFullLedger) debts else debts.filter { it.fromUser == currentUserId || it.toUser == currentUserId }
    }

    val pendingRequests = remember(settlements, currentUserId) {
        settlements.filter { it.status == "PENDING" && (it.toUser == currentUserId || it.fromUser == currentUserId) }
    }
    val confirmedSettlements = remember(settlements) {
        settlements.filter { it.status == "CONFIRMED" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = d.space24, top = d.space16, end = d.space24, bottom = 160.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Pending confirmations
            if (pendingRequests.isNotEmpty()) {
                item {
                    Text("Pending Confirmations", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = d.textLabelLarge, color = inkPrimary)
                    Spacer(modifier = Modifier.height(d.space8))
                }
                items(pendingRequests) { req ->
                    val isCreditor = req.toUser == currentUserId
                    val otherUid = if (isCreditor) req.fromUser else req.toUser
                    val otherName = userNames[otherUid] ?: "Member"
                    val otherProfile = memberProfilesMap[otherUid]
                    val methodLabel = if (req.method == "UPI") "UPI" else if (req.method == "DIRECT") "Direct" else "Cash"
                    val subtitleText = if (isCreditor) {
                        "$otherName says they paid via $methodLabel"
                    } else {
                        "You requested settlement ($methodLabel). Waiting for $otherName to confirm."
                    }
                    val effectiveProof = req.receiptUrl.ifBlank { req.receiptUrls.firstOrNull() ?: "" }

                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = d.space8),
                        shape = RoundedCornerShape(d.radiusLG),
                        color = borderWhisper.copy(alpha = 0.3f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(d.space16)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                                    UserAvatar(avatarUrl = otherProfile?.avatarUrl ?: "", displayName = otherName, size = d.avatarSm)
                                    Column {
                                        Text(subtitleText, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyLarge, color = inkPrimary)
                                        Text("\u20b9${req.amount.formatCurrency()}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = inkMuted)
                                    }
                                }

                                if (isCreditor) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(d.space4)) {
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    FirebaseManager.approveSettlement(groupId, req.id)
                                                    Toast.makeText(context, "Confirmed!", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                            }
                                        }) { Icon(Icons.Default.Check, contentDescription = "Approve", tint = inkPrimary) }
                                        IconButton(onClick = {
                                            coroutineScope.launch {
                                                try {
                                                    FirebaseManager.declineSettlement(groupId, req.id)
                                                    Toast.makeText(context, "Declined", Toast.LENGTH_SHORT).show()
                                                } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                            }
                                        }) { Icon(Icons.Default.Clear, contentDescription = "Decline", tint = alertRed) }
                                    }
                                } else {
                                    IconButton(onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.declineSettlement(groupId, req.id)
                                                Toast.makeText(context, "Request cancelled", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                        }
                                    }) { Icon(Icons.Default.Clear, contentDescription = "Cancel Request", tint = inkMuted) }
                                }
                            }

                            if (effectiveProof.isNotBlank()) {
                                Spacer(modifier = Modifier.height(d.space8))
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(d.radiusMD))
                                        .clickable { selectedReceiptUrlForPreview = effectiveProof },
                                    color = inkPrimary.copy(alpha = 0.08f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = d.space12, vertical = d.space8),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = inkPrimary, modifier = Modifier.size(16.dp))
                                        Text("🧾 Attached Payment Proof (Tap to view)", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelSmall, color = inkPrimary)
                                    }
                                }
                            } else if (!isCreditor) {
                                Spacer(modifier = Modifier.height(d.space8))
                                TextButton(
                                    onClick = {
                                        targetPendingSettlementForProof = req
                                        pendingProofPicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                            )
                                        )
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("📷 Attach Payment Proof", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelSmall, color = inkPrimary)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Text("WHO OWES WHAT", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = inkMuted, letterSpacing = 1.5.sp)
                Spacer(modifier = Modifier.height(d.space8))
            }

            if (displayDebts.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text(if (debts.isEmpty()) "All settled up!" else "You are all settled up!", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = inkMuted)
                    }
                }
            } else {
                items(displayDebts) { debt ->
                    val debtorName = userNames[debt.fromUser] ?: "Debtor"
                    val creditorName = userNames[debt.toUser] ?: "Creditor"
                    val isPayer = debt.fromUser == currentUserId

                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = d.rowHeightLg).padding(vertical = d.space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            avatarUrl = memberProfilesMap[debt.fromUser]?.avatarUrl ?: "",
                            displayName = debtorName,
                            size = d.avatarMd
                        )
                        Spacer(modifier = Modifier.width(d.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debtorName, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = inkPrimary)
                            Text("owes $creditorName", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = inkMuted)
                        }
                        val formattedDebtAmount = if (debt.amount % 1.0 == 0.0) debt.amount.toInt().toString() else String.format(java.util.Locale.US, "%.2f", debt.amount)
                        Text(
                            text = "\u20b9$formattedDebtAmount",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textMonoLarge,
                            color = alertRed
                        )
                        if (isPayer) {
                            Spacer(modifier = Modifier.width(d.space8))
                            Surface(
                                onClick = { onSettleClick(debt) },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = accentIndigo,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    "Settle",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelSmall,
                                    color = colors.canvasChalk,
                                    modifier = Modifier.padding(horizontal = d.space12, vertical = d.space4)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = borderWhisper)
                }
            }

            if (confirmedSettlements.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(d.space16))
                    Text("SETTLED TRANSACTIONS", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = inkMuted, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(d.space8))
                }
                items(confirmedSettlements) { settlement ->
                    val senderName = userNames[settlement.fromUser] ?: "Payer"
                    val receiverName = userNames[settlement.toUser] ?: "Receiver"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = d.rowHeightLg)
                            .padding(vertical = d.space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(d.avatarMd)
                                .clip(CircleShape)
                                .background(colors.surfaceCard),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "✓",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textBodyMedium,
                                color = colors.inkPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(d.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "$senderName paid $receiverName",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = d.textTitleMedium,
                                color = colors.inkPrimary
                            )
                            val methodText = if (settlement.method == "UPI") "Paid via UPI" else "Paid in cash"
                            val proofUrl = settlement.receiptUrl.ifBlank { settlement.receiptUrls.firstOrNull() ?: "" }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = methodText,
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelMedium,
                                    color = inkMuted
                                )
                                if (proofUrl.isNotBlank()) {
                                    Text(
                                        text = "· 🧾 Proof",
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textLabelMedium,
                                        color = colors.inkPrimary,
                                        modifier = Modifier.clickable { selectedReceiptUrlForPreview = proofUrl }
                                    )
                                }
                            }
                        }
                        Text(
                            text = "\u20b9${settlement.amount.formatCurrency()}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textMonoLarge,
                            color = inkMuted
                        )
                    }
                    HorizontalDivider(color = borderWhisper)
                }
            }

            // Settle Up + Simplify spacer
            item { Spacer(modifier = Modifier.height(160.dp)) }
        }

        // Sticky Settle Up button
        if (debts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(colors.canvasChalk.copy(alpha = 0.97f))
                    .navigationBarsPadding()
                    .padding(horizontal = d.space16, vertical = d.space12),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        val myDebt = debts.find { it.fromUser == currentUserId }
                        if (myDebt != null) {
                            onSettleClick(myDebt)
                        } else {
                            Toast.makeText(context, "You have no outstanding debts to settle!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.buttonColors(containerColor = inkPrimary)
                ) {
                    Text("Settle Up", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = colors.canvasChalk)
                }
                TextButton(
                    onClick = { showFullLedger = !showFullLedger }
                ) {
                    Text(if (showFullLedger) "Hide full ledger" else "Show full ledger", fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = accentIndigo)
                }
            }
        }
        
        if (selectedReceiptUrlForPreview != null) {
            AlertDialog(
                onDismissRequest = { selectedReceiptUrlForPreview = null },
                containerColor = colors.surfaceCard,
                title = { Text("Payment Proof / Receipt", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary) },
                text = {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp), contentAlignment = Alignment.Center) {
                        coil.compose.AsyncImage(
                            model = selectedReceiptUrlForPreview,
                            contentDescription = "Payment Receipt Proof",
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(d.radiusMD))
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedReceiptUrlForPreview = null }) {
                        Text("Close", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary)
                    }
                }
            )
        }
    }
}

// ── Settings Tab ─────────────────────────────────────────────
@Composable
private fun StyledSettingsTab(
    group: Group?,
    userNamesMap: Map<String, String>,
    memberProfilesMap: Map<String, UserProfile>,
    onBack: () -> Unit,
    onNavigateToReports: ((String) -> Unit)? = null
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var memberInput by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }

    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showRenameConfirm by remember { mutableStateOf(false) }
    var tempNameInput by remember(group?.name) { mutableStateOf(group?.name ?: "") }

    if (group == null) return

    val expensesFlow = remember(group.id) { FirebaseManager.observeExpenses(group.id) }
    val expensesState = expensesFlow.collectAsState(initial = emptyList())
    val settlementsFlow = remember(group.id) { FirebaseManager.observeSettlements(group.id) }
    val settlementsState = settlementsFlow.collectAsState(initial = emptyList())
    val membersList = remember(group) { group.members.keys.toList() }
    val netBalancesMap = remember(membersList, expensesState.value, settlementsState.value) {
        DebtSolver.calculateNetBalances(membersList, expensesState.value, settlementsState.value)
    }

    val qrBitmap = remember(group.id) {
        generateQRCodeBitmap("splitsmith://join?code=${group.id}", 512)
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text("Leave Group?", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave this group? If you are the last member, the group will be permanently deleted.", fontFamily = OutfitFamily) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLeaveConfirm = false
                        coroutineScope.launch {
                            try {
                                FirebaseManager.leaveGroup(group.id)
                                Toast.makeText(context, "You left the group.", Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Leave", color = colors.alertRed, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) {
                    Text("Cancel", color = colors.inkMuted, fontFamily = OutfitFamily)
                }
            },
            shape = RoundedCornerShape(d.radiusLG),
            containerColor = colors.surfaceCard
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Group?", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete this group? This action cannot be undone.", fontFamily = OutfitFamily) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        coroutineScope.launch {
                            try {
                                FirebaseManager.deleteGroup(group.id)
                                Toast.makeText(context, "Group deleted.", Toast.LENGTH_SHORT).show()
                                onBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = colors.alertRed, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = colors.inkMuted, fontFamily = OutfitFamily)
                }
            },
            shape = RoundedCornerShape(d.radiusLG),
            containerColor = colors.surfaceCard
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Group", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                    Text("Enter new name for the group:", fontFamily = OutfitFamily)
                    OutlinedTextField(
                        value = tempNameInput,
                        onValueChange = { tempNameInput = it },
                        singleLine = true,
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
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempNameInput.trim().isNotEmpty()) {
                            showRenameDialog = false
                            showRenameConfirm = true
                        }
                    },
                    enabled = tempNameInput.trim().isNotEmpty() && tempNameInput.trim() != group.name
                ) {
                    Text("Save", color = colors.inkPrimary, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = colors.inkMuted, fontFamily = OutfitFamily)
                }
            },
            shape = RoundedCornerShape(d.radiusLG),
            containerColor = colors.surfaceCard
        )
    }

    if (showRenameConfirm) {
        AlertDialog(
            onDismissRequest = { showRenameConfirm = false },
            title = { Text("Confirm Rename", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to rename this group to '${tempNameInput.trim()}'?", fontFamily = OutfitFamily) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameConfirm = false
                        coroutineScope.launch {
                            try {
                                FirebaseManager.updateGroupName(group.id, tempNameInput.trim())
                                Toast.makeText(context, "Group name updated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Confirm", color = colors.inkPrimary, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameConfirm = false }) {
                    Text("Cancel", color = colors.inkMuted, fontFamily = OutfitFamily)
                }
            },
            shape = RoundedCornerShape(d.radiusLG),
            containerColor = colors.surfaceCard
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(d.space24),
        verticalArrangement = Arrangement.spacedBy(d.space20)
    ) {
        // Group Settings Header
        item {
            Text(
                text = "Group Settings",
                fontFamily = OutfitFamily,
                fontWeight = FontWeight.Bold,
                fontSize = d.textTitleLarge,
                color = colors.inkPrimary
            )
        }

        val myUid = FirebaseManager.currentUserId
        val isGroupAdmin = group.adminId == myUid || group.admins[myUid] == true
        val pendingApplicants = group.joinRequests.keys.toList()

        if (isGroupAdmin && pendingApplicants.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                    Text(
                        text = "PENDING JOIN REQUESTS",
                        fontFamily = OutfitFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.inkMuted,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    pendingApplicants.forEach { applicantUid ->
                        val displayName = userNamesMap[applicantUid] ?: applicantUid.take(6).uppercase()
                        val applicantProfile = memberProfilesMap[applicantUid]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                                UserAvatar(avatarUrl = applicantProfile?.avatarUrl ?: "", displayName = displayName, size = d.avatarSm)
                                Text(
                                    text = displayName,
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = d.textBodyLarge,
                                    color = colors.inkPrimary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.approveJoinRequest(group.id, applicantUid)
                                                Toast.makeText(context, "Approved!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Approve", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.positiveGreen)
                                }
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.declineJoinRequest(group.id, applicantUid)
                                                Toast.makeText(context, "Declined", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Text("Decline", fontFamily = OutfitFamily, color = colors.alertRed)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(d.space8))
                    HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
                }
            }
        }

        // Group Name Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "GROUP NAME",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = group.name,
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = d.textTitleMedium,
                        color = colors.inkPrimary
                    )
                    
                    IconButton(
                        onClick = {
                            tempNameInput = group.name
                            showRenameDialog = true
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Group Name",
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        }

        // Group Budget Config Section
        item {
            var showEditGroupBudgetDialog by remember { mutableStateOf(false) }
            val currentBudget = group.budget
            val isBudgetSet = currentBudget.limit > 0.0
            val typeDisplay = when (currentBudget.type) {
                "YEARLY" -> "Yearly"
                "EVENT" -> "Event / Trip"
                else -> "Monthly"
            }
            val valueText = if (isBudgetSet) "₹${currentBudget.limit.formatCurrency()} ($typeDisplay)" else "Not set"

            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "GROUP BUDGET CONFIG",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEditGroupBudgetDialog = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Group Budget Limit",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textTitleMedium,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = valueText,
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = if (isBudgetSet) colors.inkPrimary else colors.inkMuted
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(d.radiusSM),
                        color = colors.surfaceCard,
                        border = BorderStroke(0.5.dp, colors.borderWhisper)
                    ) {
                        Text(
                            text = if (isBudgetSet) "Edit" else "Set Budget",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textLabelSmall,
                            color = colors.inkPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }

            if (showEditGroupBudgetDialog) {
                var inputLimit by remember { mutableStateOf(if (isBudgetSet) currentBudget.limit.toInt().toString() else "10000") }
                var selectedType by remember { mutableStateOf(currentBudget.type.ifEmpty { "MONTHLY" }) }

                AlertDialog(
                    onDismissRequest = { showEditGroupBudgetDialog = false },
                    containerColor = colors.surfaceCard,
                    shape = RoundedCornerShape(d.radiusLG),
                    title = { Text("Group Budget Configuration", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                            Text("Select Budget Cycle Type:", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("MONTHLY" to "Monthly", "YEARLY" to "Yearly", "EVENT" to "Event / Trip").forEach { (typeKey, label) ->
                                    val isSel = selectedType == typeKey
                                    Surface(
                                        shape = RoundedCornerShape(d.radiusSM),
                                        color = if (isSel) colors.inkPrimary else colors.canvasChalk,
                                        border = BorderStroke(0.5.dp, colors.borderWhisper),
                                        modifier = Modifier.clickable { selectedType = typeKey }
                                    ) {
                                        Text(
                                            label,
                                            fontFamily = OutfitFamily,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = d.textLabelSmall,
                                            color = if (isSel) colors.canvasChalk else colors.inkPrimary,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = inputLimit,
                                onValueChange = { inputLimit = it },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(d.radiusSM),
                                label = { Text("Limit Amount (₹ INR)", fontFamily = OutfitFamily, color = colors.inkMuted) },
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
                                val newLimit = inputLimit.toDoubleOrNull() ?: 0.0
                                coroutineScope.launch {
                                    try {
                                        FirebaseManager.updateGroupBudgetConfig(group.id, BudgetConfig(limit = newLimit, type = selectedType))
                                        showEditGroupBudgetDialog = false
                                        Toast.makeText(context, "Group budget updated!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                            shape = RoundedCornerShape(d.radiusMD)
                        ) {
                            Text("Save Budget", fontFamily = OutfitFamily, color = colors.canvasChalk)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showEditGroupBudgetDialog = false }) {
                            Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                        }
                    }
                )
            }
        }

        // Group Reports & Analytics Shortcut Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "REPORTS & ANALYTICS",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToReports?.invoke(group.id) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Group Report & Analytics",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textTitleMedium,
                            color = colors.inkPrimary
                        )
                        Text(
                            text = "View member breakdowns & export CSV",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = colors.inkMuted
                        )
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(d.radiusSM),
                        color = colors.surfaceCard,
                        border = BorderStroke(0.5.dp, colors.borderWhisper)
                    ) {
                        Text(
                            text = "View Report →",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textLabelSmall,
                            color = colors.inkPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        }

        // Group Icon Picker Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Text(
                    text = "GROUP ICON",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(d.space12),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(listOf("Home", "Trip", "Work", "Event", "Food", "Payment", "Shopping", "Dining", "Drinks", "Pets", "Education", "Tech", "Other")) { iconName ->
                        val isSelected = group.iconName == iconName
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
                                .clickable {
                                    coroutineScope.launch {
                                        try {
                                            FirebaseManager.updateGroupIcon(group.id, iconName)
                                            Toast.makeText(context, "Group icon updated!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to update icon: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                        ) {
                            GroupIconView(
                                iconName = iconName,
                                size = 36.dp,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        }

        // Invite Code Section (no cards)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(d.space8)
            ) {
                Text(
                    text = "INVITE CODE",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Text(
                        text = group.id.uppercase(),
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textMonoLarge,
                        color = colors.inkPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    TextButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(group.id))
                            Toast.makeText(context, "Copied code to clipboard!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copy", fontFamily = OutfitFamily, color = colors.inkPrimary, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val cachePath = File(context.cacheDir, "images")
                                    cachePath.mkdirs()
                                    val file = File(cachePath, "qr_code.png")
                                    val stream = FileOutputStream(file)
                                    qrBitmap?.compress(Bitmap.CompressFormat.PNG, 100, stream)
                                    stream.close()

                                    val contentUri = FileProvider.getUriForFile(context, "com.splitsmith.app.fileprovider", file)
                                    if (contentUri != null) {
                                        val shareText = "Hey! Join my SplitSmith group '${group.name}' by clicking this link:\nhttps://splitsmith.web.app/join?code=${group.id}&v=1"
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "image/png"
                                            putExtra(Intent.EXTRA_STREAM, contentUri)
                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Group QR Code"))
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Share", fontFamily = OutfitFamily, color = colors.inkPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "Invite friends to this group by sharing this code.",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelSmall,
                    color = colors.inkMuted
                )
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        }

        // QR Code Display Section (no cards)
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(d.space12)
            ) {
                Text(
                    text = "GROUP QR CODE",
                    fontFamily = OutfitFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.inkMuted,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "Group QR Code",
                        modifier = Modifier
                            .size(180.dp)
                            .background(Color.White)
                            .padding(d.space12)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(colors.borderWhisper),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.inkPrimary)
                    }
                }

                Text(
                    text = "Show this QR code to friends. They can scan it directly from the Home tab to join this group instantly.",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelSmall,
                    color = colors.inkMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = d.textLabelSmall * 1.3f
                )
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        }

        item {
            var isCategoriesExpanded by remember { mutableStateOf(false) }
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCategoriesExpanded = !isCategoriesExpanded }
                        .padding(vertical = d.space4),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GROUP CUSTOM CATEGORIES",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkMuted,
                            letterSpacing = 1.5.sp
                        )
                        val catCount = group.customCategories.size
                        Text(
                            text = if (catCount == 0) "None configured" else "$catCount custom categories configured",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    }
                    Icon(
                        imageVector = if (isCategoriesExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                        contentDescription = "Toggle Custom Categories Settings Expand",
                        tint = colors.inkMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (isCategoriesExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    var showAddCategory by remember { mutableStateOf(false) }
                    var categoryNameInput by remember { mutableStateOf("") }
                    
                    if (showAddCategory) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(d.space12)
                        ) {
                            OutlinedTextField(
                                value = categoryNameInput,
                                onValueChange = { categoryNameInput = it },
                                placeholder = { Text("Category name", fontFamily = OutfitFamily, color = colors.inkMuted) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
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
                            Button(
                                onClick = {
                                    val trimmed = categoryNameInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.addGroupCustomCategory(group.id, trimmed)
                                                Toast.makeText(context, "Category added!", Toast.LENGTH_SHORT).show()
                                                categoryNameInput = ""
                                                showAddCategory = false
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                                shape = RoundedCornerShape(d.radiusMD)
                            ) {
                                Text("Add", fontFamily = OutfitFamily, color = colors.canvasChalk)
                            }
                            TextButton(onClick = { 
                                showAddCategory = false
                                categoryNameInput = ""
                            }) {
                                Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showAddCategory = true },
                            modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                            border = BorderStroke(1.dp, colors.borderWhisper),
                            shape = RoundedCornerShape(d.radiusMD),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkPrimary)
                        ) {
                            Text("+ Add Group Category", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(d.space8))
                    
                    if (group.customCategories.isEmpty()) {
                        Text(
                            text = "No custom categories added to this group yet.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    } else {
                        group.customCategories.forEach { cat ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = d.space4),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = cat,
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textBodyLarge,
                                    color = colors.inkPrimary
                                )
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            try {
                                                FirebaseManager.deleteGroupCustomCategory(group.id, cat)
                                                Toast.makeText(context, "Category deleted!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Category",
                                        tint = colors.alertRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(d.space8))
                HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
            }
        }

        // Members List
        item {
            Text(
                text = "GROUP MEMBERS",
                fontFamily = OutfitFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkMuted,
                letterSpacing = 1.5.sp
            )
        }

        items(group.members.keys.toList()) { uid ->
            val memberName = userNamesMap[uid] ?: "User"
            val isCurrent = uid == FirebaseManager.currentUserId
            val finalName = if (isCurrent) "$memberName (You)" else memberName

            val isOwner = uid == group.adminId
            val isCoAdmin = group.admins[uid] == true
            val myUid = FirebaseManager.currentUserId ?: ""
            val currentUserIsAdmin = group.adminId == myUid || group.admins[myUid] == true

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = d.rowHeightSm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val profile = memberProfilesMap[uid]
                UserAvatar(
                    avatarUrl = profile?.avatarUrl ?: "",
                    displayName = memberName,
                    size = d.avatarSm
                )
                Spacer(modifier = Modifier.width(d.space12))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = finalName,
                        fontFamily = OutfitFamily,
                        fontSize = d.textBodyLarge,
                        color = colors.inkPrimary
                    )
                    if (isOwner) {
                        Text(
                            text = "Group Owner",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    } else if (isCoAdmin) {
                        Text(
                            text = "Admin",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    }
                }

                if (!isOwner && !isCoAdmin && currentUserIsAdmin) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    FirebaseManager.makeUserAdmin(group.id, uid)
                                    Toast.makeText(context, "$memberName is now an admin!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = d.space8, vertical = d.space4)
                    ) {
                        Text(
                            text = "Make Admin",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkPrimary
                        )
                    }
                } else if (!isOwner && isCoAdmin && (group.adminId == myUid || group.adminId.isEmpty())) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    FirebaseManager.revokeUserAdmin(group.id, uid)
                                    Toast.makeText(context, "Revoked admin status for $memberName", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = d.space8, vertical = d.space4)
                    ) {
                        Text(
                            text = "Revoke Admin",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.alertRed
                        )
                    }
                }
            }
            HorizontalDivider(color = colors.borderWhisper)
        }

        // Add Member Input Row
        item {
            Spacer(modifier = Modifier.height(d.space8))
            Text(
                text = "ADD NEW MEMBER",
                fontFamily = OutfitFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = colors.inkMuted,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(d.space8))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.space12)
            ) {
                OutlinedTextField(
                    value = memberInput,
                    onValueChange = { memberInput = it },
                    placeholder = { Text("Email or User Code", fontFamily = OutfitFamily, color = colors.inkMuted) },
                    modifier = Modifier.weight(1f),
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

                Button(
                    onClick = {
                        if (memberInput.trim().isEmpty()) return@Button
                        isAdding = true
                        coroutineScope.launch {
                            try {
                                val resolved = FirebaseManager.searchUserByEmail(memberInput.trim())
                                    ?: FirebaseManager.searchUserByCode(memberInput.trim())
                                if (resolved != null) {
                                    FirebaseManager.inviteUserToGroup(group.id, resolved.uid)
                                    Toast.makeText(context, "Invitation sent to ${resolved.displayName}!", Toast.LENGTH_SHORT).show()
                                    memberInput = ""
                                } else {
                                    Toast.makeText(context, "User not found", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isAdding = false
                            }
                        }
                    },
                    enabled = !isAdding && memberInput.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                    shape = RoundedCornerShape(d.radiusMD)
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(color = colors.canvasChalk, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Add", fontFamily = OutfitFamily, color = colors.canvasChalk)
                    }
                }
            }
        }

        // Leave Group Button
        item {
            Spacer(modifier = Modifier.height(d.space16))
            Button(
                onClick = {
                    val currentUserId = FirebaseManager.currentUserId ?: ""
                    val myNet = netBalancesMap[currentUserId] ?: 0.0
                    if (kotlin.math.abs(myNet) > 0.01) {
                        val netStr = if (myNet > 0) "you are owed \u20b9${myNet.formatCurrency()}" else "you owe \u20b9${(-myNet).formatCurrency()}"
                        Toast.makeText(context, "Cannot leave group while $netStr. Please settle up first!", Toast.LENGTH_LONG).show()
                    } else {
                        showLeaveConfirm = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = colors.alertRed),
                shape = RoundedCornerShape(d.radiusMD)
            ) {
                Text(
                    text = "Leave Group",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Delete Group Button (Admin only)
        val canDeleteGroup = group.adminId == FirebaseManager.currentUserId || group.adminId.isEmpty()
        if (canDeleteGroup) {
            item {
                Spacer(modifier = Modifier.height(d.space12))
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(d.buttonHeight),
                    border = BorderStroke(1.dp, colors.alertRed),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.alertRed)
                ) {
                    Text(
                        text = "Delete Group (Admin)",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(d.space32))
            }
        } else {
            item { Spacer(modifier = Modifier.height(d.space32)) }
        }
    }
}

private fun generateQRCodeBitmap(content: String, size: Int): Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(
                    x, y,
                    if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                )
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupExpenseDetailBottomSheet(
    expense: Expense,
    groupId: String,
    userNames: Map<String, String>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    var showMenu by remember { mutableStateOf(false) }
    val isCreator = expense.createdBy == FirebaseManager.currentUserId

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = d.space24, vertical = d.space16),
            verticalArrangement = Arrangement.spacedBy(d.space16)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                    Surface(
                        shape = CircleShape,
                        color = colors.inkPrimary,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = colors.canvasChalk, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column {
                        Text(expense.description.ifEmpty { "Expense Details" }, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleMedium, color = colors.inkPrimary)
                        Text(expense.category, fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                    }
                }

                if (isCreator) {
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
                                    onEdit()
                                },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary) }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Expense", fontFamily = OutfitFamily, color = colors.alertRed) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.alertRed) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

            // Prominent Amount
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "₹${if (expense.amount % 1.0 == 0.0) expense.amount.toInt().toString() else String.format("%.2f", expense.amount)}",
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                    color = colors.inkPrimary
                )
                Text(text = "Total Expense", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
            }

            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

            // Details metadata
            Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                val formattedDate = remember(expense.date) {
                    val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(expense.date))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Paid By", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textBodyLarge)
                    Text(userNames[expense.paidBy] ?: "Member", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, color = colors.inkPrimary, fontSize = d.textBodyLarge)
                }

                val attachmentUrls = remember(expense) { expense.getEffectiveReceiptUrls() }
                if (attachmentUrls.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(d.space8))
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Date", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textBodyLarge)
                    Text(formattedDate, fontFamily = OutfitFamily, fontWeight = FontWeight.Medium, color = colors.inkPrimary, fontSize = d.textBodyLarge)
                }
            }

            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

            // Member Split Shares
            Text("SPLIT BREAKDOWN", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
            Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                expense.splits.forEach { (uid, share) ->
                    val name = userNames[uid] ?: "Member"
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name, fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                        Text("₹${if (share % 1.0 == 0.0) share.toInt().toString() else String.format("%.2f", share)}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space8))

            val myUid = FirebaseManager.currentUserId ?: ""
            val myShare = expense.splits[myUid] ?: 0.0
            val coroutineScope = rememberCoroutineScope()
            val context = LocalContext.current

            if (expense.paidBy != myUid && myShare > 0) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            try {
                                FirebaseManager.addSettlement(
                                    groupId = groupId,
                                    toUser = expense.paidBy,
                                    amount = myShare,
                                    method = "DIRECT"
                                )
                                Toast.makeText(context, "Recorded settlement of \u20b9${myShare.formatCurrency()} for your share!", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary)
                ) {
                    Text("Settle My Share (\u20b9${myShare.formatCurrency()})", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}



