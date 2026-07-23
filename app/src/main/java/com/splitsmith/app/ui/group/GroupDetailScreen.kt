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
    // Expenses (0) is DEFAULT selected
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedDebtForSettlement by remember { mutableStateOf<Debt?>(null) }
    var userNamesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var memberProfilesMap by remember { mutableStateOf<Map<String, UserProfile>>(emptyMap()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

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
    val debts = remember(netBalances) { DebtSolver.resolveDebts(netBalances) }

    val colors = LocalSplitColors.current
    val canvasChalk   = colors.canvasChalk
    val accentIndigo  = colors.inkPrimary
    val inkPrimary    = colors.inkPrimary
    val inkMuted      = colors.inkMuted
    val borderWhisper = colors.borderWhisper
    val positiveGreen = colors.positiveGreen
    val alertRed      = colors.alertRed

    if (expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { expenseToDelete = null },
            title = { Text("Delete Expense", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete '${expenseToDelete?.description}'?", fontFamily = OutfitFamily) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val exp = expenseToDelete
                        if (exp != null) {
                            coroutineScope.launch {
                                try {
                                    FirebaseManager.deleteExpense(groupId, exp.id)
                                    Toast.makeText(context, "Expense deleted", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        expenseToDelete = null
                    }
                ) {
                    Text("Delete", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = alertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = inkMuted)
                }
            },
            containerColor = canvasChalk
        )
    }

    Scaffold(
        containerColor = canvasChalk,
        contentWindowInsets = WindowInsets(0), // Ignore default window insets to lay out correctly
        floatingActionButton = {
            if (selectedTab == 0 && isMember) {
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
                    if (isPending) {
                        Text(
                            text = "Pending Admin Approval",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textTitleMedium,
                            color = Color(0xFFD97706),
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

                // ── Minimal Group Budget Header Line (Only if budget limit > 0) ───
                val groupBudgetLimit = currentGroup.budget.limit
                if (groupBudgetLimit > 0.0) {
                    val budgetType = currentGroup.budget.type
                    val groupSpent = remember(expenses, budgetType) {
                        val cal = java.util.Calendar.getInstance()
                        val startTime = when (budgetType) {
                            "YEARLY" -> {
                                cal.set(java.util.Calendar.DAY_OF_YEAR, 1)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                cal.timeInMillis
                            }
                            "EVENT" -> 0L
                            else -> { // MONTHLY
                                cal.set(java.util.Calendar.DAY_OF_MONTH, 1)
                                cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
                                cal.set(java.util.Calendar.MINUTE, 0)
                                cal.set(java.util.Calendar.SECOND, 0)
                                cal.set(java.util.Calendar.MILLISECOND, 0)
                                cal.timeInMillis
                            }
                        }
                        expenses.filter { it.date >= startTime }.sumOf { it.amount }
                    }
                    val groupBudgetProgress = (groupSpent / groupBudgetLimit).coerceIn(0.0, 1.0)
                    val typeLabel = when (budgetType) {
                        "YEARLY" -> "Yearly"
                        "EVENT" -> "Event"
                        else -> "Monthly"
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
                            Text(
                                text = "$typeLabel Budget",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = inkMuted
                            )
                            Text(
                                text = "₹${"%.0f".format(groupSpent)} / ₹${"%.0f".format(groupBudgetLimit)}",
                                fontFamily = JetBrainsMonoFamily,
                                fontSize = d.textLabelMedium,
                                fontWeight = FontWeight.Bold,
                                color = inkPrimary
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

                val myUid = FirebaseManager.currentUserId
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

                Spacer(modifier = Modifier.height(d.space12))

                // ── Custom pill tab bar (compact 2-tab layout) ────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space24),
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    listOf("Expenses", "Balances").forEachIndexed { index, label ->
                        val isActive = selectedTab == index
                        val bgColor by animateColorAsState(
                            targetValue = if (isActive) accentIndigo else Color.Transparent,
                            label = "tabBg$index"
                        )
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) canvasChalk else inkMuted,
                            label = "tabText$index"
                        )
                        Surface(
                            onClick = { selectedTab = index },
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

                // ── Tab content ───────────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
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

            ModalBottomSheet(onDismissRequest = { selectedDebtForSettlement = null }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(d.space24)
                        .navigationBarsPadding(),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    Text("Settle Balance", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary)
                    Text("$fromName owes $toName \u20b9${debt.amount}", fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkMuted)

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    val receiverUpi = FirebaseManager.getReceiverUpiId(debt.toUser)
                                    val comment = Uri.encode("SplitSmith Settlement")
                                    val upiUri = Uri.parse("upi://pay?pa=$receiverUpi&pn=$toName&am=${debt.amount}&cu=INR&tn=$comment")
                                    val intent = Intent(Intent.ACTION_VIEW, upiUri)
                                    context.startActivity(Intent.createChooser(intent, "Pay via UPI App"))
                                    FirebaseManager.addSettlement(groupId, debt.toUser, debt.amount, "UPI", "UPI_REF_AUTO")
                                    Toast.makeText(context, "UPI intent launched.", Toast.LENGTH_LONG).show()
                                    selectedDebtForSettlement = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk)
                    ) { Text("Pay via UPI", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge) }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                try {
                                    FirebaseManager.addSettlement(groupId, debt.toUser, debt.amount, "CASH")
                                    Toast.makeText(context, "Cash request sent.", Toast.LENGTH_LONG).show()
                                    selectedDebtForSettlement = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkPrimary)
                    ) { Text("Mark Paid in Cash", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge) }
                }
            }
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
    val pendingRequests = remember(settlements) {
        settlements.filter { it.status == "PENDING" && it.toUser == currentUserId }
    }
    val confirmedSettlements = remember(settlements) {
        settlements.filter { it.status == "CONFIRMED" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space16, ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Pending confirmations
            if (pendingRequests.isNotEmpty()) {
                item {
                    Text("Pending Cash Confirmations", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold,
                        fontSize = d.textLabelLarge, color = inkPrimary)
                    Spacer(modifier = Modifier.height(d.space8))
                }
                items(pendingRequests) { req ->
                    val payerName = userNames[req.fromUser] ?: "Payer"
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = d.space8),
                        shape = RoundedCornerShape(d.radiusLG),
                        color = borderWhisper.copy(alpha = 0.3f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(d.space16),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("$payerName says they paid cash", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyLarge, color = inkPrimary)
                                Text("\u20b9${req.amount}", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textBodyMedium, color = inkMuted)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(d.space4)) {
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        try { FirebaseManager.approveSettlement(groupId, req.id)
                                            Toast.makeText(context, "Confirmed!", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                    }
                                }) { Icon(Icons.Default.Check, contentDescription = "Approve", tint = inkPrimary) }
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        try { FirebaseManager.declineSettlement(groupId, req.id)
                                            Toast.makeText(context, "Declined", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() }
                                    }
                                }) { Icon(Icons.Default.Clear, contentDescription = "Decline", tint = alertRed) }
                            }
                        }
                    }
                }
            }

            item {
                Text("WHO OWES WHAT", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = inkMuted, letterSpacing = 1.5.sp)
                Spacer(modifier = Modifier.height(d.space8))
            }

            if (debts.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Text("All settled up!", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = inkMuted)
                    }
                }
            } else {
                items(debts) { debt ->
                    val debtorName = userNames[debt.fromUser] ?: "Debtor"
                    val creditorName = userNames[debt.toUser] ?: "Creditor"
                    val isPayer = debt.fromUser == currentUserId

                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = d.rowHeightLg).padding(vertical = d.space12),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(d.avatarMd).clip(CircleShape).background(hueForMember(debt.fromUser)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                debtorName.firstOrNull()?.uppercase() ?: "?",
                                fontFamily = OutfitFamily, fontWeight = FontWeight.Bold,
                                fontSize = d.textBodyMedium, color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(d.space12))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(debtorName, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = inkPrimary)
                            Text("owes $creditorName", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = inkMuted)
                        }
                        Text(
                            text = "\u20b9${debt.amount}",
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
                                color = inkMuted
                            )
                            Text(
                                "Via ${settlement.method} · Settled",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelMedium,
                                color = inkMuted
                            )
                        }
                        Text(
                            text = "₹${settlement.amount}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textMonoLarge,
                            color = inkMuted
                        )
                        Spacer(modifier = Modifier.width(d.space8))
                        Surface(
                            shape = RoundedCornerShape(d.radiusFull),
                            color = colors.surfaceCard,
                            border = BorderStroke(1.dp, borderWhisper)
                        ) {
                            Text(
                                "Settled",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelSmall,
                                color = inkMuted,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    HorizontalDivider(color = borderWhisper)
                }
            }

            // Settle Up + Simplify spacer
            item { Spacer(modifier = Modifier.height(80.dp)) }
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
                    onClick = { /* show settlement sheet for first own debt */ },
                    modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.buttonColors(containerColor = inkPrimary)
                ) {
                    Text("Settle Up", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = colors.canvasChalk)
                }
                TextButton(onClick = {}) {
                    Text("Simplify Debts", fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = accentIndigo)
                }
            }
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

    val qrBitmap = remember(group.id) {
        generateQRCodeBitmap(group.id, 512)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = displayName,
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = d.textBodyLarge,
                                color = colors.inkPrimary
                            )
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
            val valueText = if (isBudgetSet) "₹${"%.0f".format(currentBudget.limit)} ($typeDisplay)" else "Not set"

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
                } else if (isCoAdmin && (group.adminId == myUid || group.adminId.isEmpty())) {
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
                                val resolved = if (memberInput.contains("@")) {
                                    FirebaseManager.searchUserByEmail(memberInput.trim())
                                } else {
                                    FirebaseManager.searchUserByCode(memberInput.trim())
                                }
                                if (resolved != null) {
                                    FirebaseManager.addMemberToGroup(group.id, resolved.uid)
                                    Toast.makeText(context, "Added ${resolved.displayName} to group!", Toast.LENGTH_SHORT).show()
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
                onClick = { showLeaveConfirm = true },
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
        }
    }
}



