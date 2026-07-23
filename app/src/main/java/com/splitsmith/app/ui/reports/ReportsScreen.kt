package com.splitsmith.app.ui.reports

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.Expense
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.theme.LocalSplitColors
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ReportTransaction(
    val description: String,
    val amount: Double, // positive = credit (others owe us), negative = debit (we spent/owe)
    val date: Long,
    val category: String,
    val typeName: String, // "Personal", "Group: Trip", "Direct Split"
    val groupId: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    initialGroupId: String? = null,
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val groupsFlow = remember { FirebaseManager.observeGroups() }
    val groupsState = groupsFlow.collectAsState(initial = emptyList())
    val groups = groupsState.value ?: emptyList()

    val personalExpensesFlow = remember { FirebaseManager.observePersonalExpenses() }
    val personalExpensesState = personalExpensesFlow.collectAsState(initial = emptyList())
    val personalExpenses = personalExpensesState.value ?: emptyList()

    val directSplitsFlow = remember { FirebaseManager.observeDirectSplits() }
    val directSplitsState = directSplitsFlow.collectAsState(initial = emptyList())
    val directSplits = directSplitsState.value ?: emptyList()

    val allGroupExpensesFlow = remember { FirebaseManager.observeAllUserGroupExpenses() }
    val allGroupExpensesState = allGroupExpensesFlow.collectAsState(initial = emptyList())
    val allGroupExpenses = allGroupExpensesState.value ?: emptyList()

    // Filter states
    var selectedGroupId by remember { mutableStateOf(initialGroupId) }
    var filterType by remember { mutableStateOf("MONTHLY") } // "MONTHLY" or "CUSTOM"
    var selectedCalendar by remember { mutableStateOf(Calendar.getInstance()) }
    var startDate by remember { mutableStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.timeInMillis) }
    var endDate by remember { mutableStateOf(Calendar.getInstance().timeInMillis) }

    // Date Picker Dialog Launcher
    val showDatePicker = { isStart: Boolean ->
        val calendar = Calendar.getInstance().apply {
            timeInMillis = if (isStart) startDate else endDate
        }
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val newCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                if (isStart) {
                    startDate = newCal.timeInMillis
                } else {
                    endDate = newCal.timeInMillis
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val colors = LocalSplitColors.current
    val canvasChalk = colors.canvasChalk
    val inkPrimary = colors.inkPrimary
    val inkMuted = colors.inkMuted
    val borderWhisper = colors.borderWhisper
    val positiveGreen = colors.positiveGreen
    val alertRed = colors.alertRed

    // Calculate all transactions list
    val allTransactions = remember(personalExpenses, directSplits, allGroupExpenses) {
        val list = mutableListOf<ReportTransaction>()
        val myUid = FirebaseManager.currentUserId ?: ""

        // 1. Personal Solo Expenses (always debits)
        personalExpenses.forEach { pe ->
            list.add(
                ReportTransaction(
                    description = pe.description.ifEmpty { "Personal Expense" },
                    amount = -pe.amount,
                    date = pe.date,
                    category = pe.category,
                    typeName = "Personal"
                )
            )
        }

        // 2. Direct Splits
        directSplits.forEach { ds ->
            if (ds.status == "PENDING") {
                val sign = if (ds.paidBy == myUid) 1.0 else -1.0
                list.add(
                    ReportTransaction(
                        description = ds.description.ifEmpty { "Direct Split" },
                        amount = ds.myShare * sign,
                        date = ds.date,
                        category = ds.category,
                        typeName = "Direct Split"
                    )
                )
            }
        }

        // 3. Group Expenses
        allGroupExpenses.forEach { geCtx ->
            val ge = geCtx.expense
            val share = ge.splits[myUid] ?: 0.0
            val netAmount = if (ge.paidBy == myUid) {
                ge.amount - share
            } else {
                -share
            }
            if (netAmount != 0.0) {
                list.add(
                    ReportTransaction(
                        description = ge.description.ifEmpty { "Group Split" },
                        amount = netAmount,
                        date = ge.date,
                        category = ge.category,
                        typeName = "Group: ${geCtx.groupName}",
                        groupId = geCtx.groupId
                    )
                )
            }
        }
        list
    }

    // Determine start/end ranges for queries
    val activeStart = remember(filterType, selectedCalendar, startDate) {
        if (filterType == "MONTHLY") {
            val c = selectedCalendar.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            c.timeInMillis
        } else {
            startDate
        }
    }

    val activeEnd = remember(filterType, selectedCalendar, endDate) {
        if (filterType == "MONTHLY") {
            val c = selectedCalendar.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, c.getActualMaximum(Calendar.DAY_OF_MONTH))
            c.set(Calendar.HOUR_OF_DAY, 23)
            c.set(Calendar.MINUTE, 59)
            c.set(Calendar.SECOND, 59)
            c.timeInMillis
        } else {
            endDate
        }
    }

    val filteredTransactions = remember(allTransactions, activeStart, activeEnd, selectedGroupId) {
        allTransactions.filter { tx ->
            val matchesDate = tx.date in activeStart..activeEnd
            val matchesGroup = if (selectedGroupId == null) true else tx.groupId == selectedGroupId
            matchesDate && matchesGroup
        }.sortedByDescending { it.date }
    }

    val totalCredits = remember(filteredTransactions) {
        filteredTransactions.filter { it.amount > 0 }.sumOf { it.amount }
    }

    val totalDebits = remember(filteredTransactions) {
        filteredTransactions.filter { it.amount < 0 }.sumOf { -it.amount }
    }

    // Share report logic
    val shareReport = {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val periodStr = if (filterType == "MONTHLY") {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedCalendar.time)
        } else {
            "${dateFormat.format(activeStart)} to ${dateFormat.format(activeEnd)}"
        }

        val reportText = buildString {
            appendLine("--- SPLITSMITH SPEND REPORT ---")
            appendLine("Period: $periodStr")
            appendLine()
            appendLine("TOTAL CREDITS (Receivables): \u20b9${String.format("%.2f", totalCredits)}")
            appendLine("TOTAL DEBITS (Spendings): \u20b9${String.format("%.2f", totalDebits)}")
            appendLine("NET BALANCE: \u20b9${String.format("%.2f", totalCredits - totalDebits)}")
            appendLine()
            appendLine("Ledger:")
            filteredTransactions.forEachIndexed { idx, tx ->
                val dateStr = dateFormat.format(tx.date)
                val sign = if (tx.amount >= 0) "+" else "-"
                appendLine("${idx + 1}. $dateStr - ${tx.description} (${tx.typeName}) [${tx.category}]: $sign\u20b9${String.format("%.2f", Math.abs(tx.amount))}")
            }
            appendLine("-------------------------------")
        }

        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, reportText)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Spend Report"))
    }

    val exportCSV = {
        try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val periodStr = if (filterType == "MONTHLY") {
                SimpleDateFormat("MMMM_yyyy", Locale.getDefault()).format(selectedCalendar.time)
            } else {
                "custom_period"
            }

            val sb = StringBuilder()
            sb.append("Date,Description,Type,Category,Amount (INR)\n")
            filteredTransactions.forEach { tx ->
                val dateStr = dateFormat.format(Date(tx.date))
                val desc = tx.description.replace(",", " ")
                sb.append("${dateStr},${desc},${tx.typeName},${tx.category},${String.format("%.2f", tx.amount)}\n")
            }

            val file = java.io.File(context.cacheDir, "splitsmith_report_${periodStr}.csv")
            file.writeText(sb.toString())

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Report CSV"))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "CSV Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = canvasChalk,
        topBar = {
            TopAppBar(
                title = { Text("Spend & Balance Reports", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = inkPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = exportCSV) {
                        Icon(Icons.Default.Download, contentDescription = "Export CSV", tint = inkPrimary)
                    }
                    IconButton(onClick = shareReport) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = inkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = canvasChalk)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(filterType) {
                    if (filterType == "MONTHLY") {
                        detectHorizontalDragGestures { change, dragAmount ->
                            change.consume()
                            if (dragAmount > 50) {
                                // Swipe right -> prev month
                                val next = selectedCalendar.clone() as Calendar
                                next.add(Calendar.MONTH, -1)
                                selectedCalendar = next
                            } else if (dragAmount < -50) {
                                // Swipe left -> next month
                                val next = selectedCalendar.clone() as Calendar
                                next.add(Calendar.MONTH, 1)
                                selectedCalendar = next
                            }
                        }
                    }
                }
        ) {
            // Group Selector Bar
            if (groups.isNotEmpty()) {
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = d.space24, vertical = d.space4),
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    item {
                        FilterChip(
                            selected = selectedGroupId == null,
                            onClick = { selectedGroupId = null },
                            label = { Text("All Activities", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                    items(groups) { g ->
                        FilterChip(
                            selected = selectedGroupId == g.id,
                            onClick = { selectedGroupId = g.id },
                            label = { Text(g.name, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }

            // View Mode Selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space24, vertical = d.space8),
                horizontalArrangement = Arrangement.spacedBy(d.space8)
            ) {
                FilterChip(
                    selected = filterType == "MONTHLY",
                    onClick = { filterType = "MONTHLY" },
                    label = { Text("Monthly View", fontFamily = OutfitFamily) }
                )
                FilterChip(
                    selected = filterType == "CUSTOM",
                    onClick = { filterType = "CUSTOM" },
                    label = { Text("Custom Range", fontFamily = OutfitFamily) }
                )
            }

            // Date Picker / Navigator area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space24, vertical = d.space8)
            ) {
                if (filterType == "MONTHLY") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            val next = selectedCalendar.clone() as Calendar
                            next.add(Calendar.MONTH, -1)
                            selectedCalendar = next
                        }) {
                            Text("←", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = inkPrimary)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedCalendar.time),
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textTitleLarge,
                                color = inkPrimary
                            )
                            Text(
                                text = "Swipe screen horizontally to switch",
                                fontFamily = OutfitFamily,
                                fontSize = 10.sp,
                                color = inkMuted
                            )
                        }

                        IconButton(onClick = {
                            val next = selectedCalendar.clone() as Calendar
                            next.add(Calendar.MONTH, 1)
                            selectedCalendar = next
                        }) {
                            Text("→", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = inkPrimary)
                        }
                    }
                } else {
                    val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(d.space12)
                    ) {
                        OutlinedButton(
                            onClick = { showDatePicker(true) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(d.radiusSM),
                            border = BorderStroke(1.dp, borderWhisper),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = inkPrimary)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "From: ${format.format(startDate)}", fontFamily = OutfitFamily, fontSize = d.textLabelSmall)
                        }

                        OutlinedButton(
                            onClick = { showDatePicker(false) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(d.radiusSM),
                            border = BorderStroke(1.dp, borderWhisper),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = inkPrimary)
                        ) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "To: ${format.format(endDate)}", fontFamily = OutfitFamily, fontSize = d.textLabelSmall)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space16))

            // Summary Bento Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space24),
                horizontalArrangement = Arrangement.spacedBy(d.space12)
            ) {
                // Credits Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(d.radiusLG),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderWhisper)
                ) {
                    Column(modifier = Modifier.padding(d.space16)) {
                        Text(
                            text = "CREDITS",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.inkPrimary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u20b9${String.format("%.2f", totalCredits)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textTitleMedium,
                            color = colors.inkPrimary
                        )
                    }
                }

                // Debits Card
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(d.radiusLG),
                    color = colors.surfaceCard,
                    border = BorderStroke(1.dp, colors.borderWhisper)
                ) {
                    Column(modifier = Modifier.padding(d.space16)) {
                        Text(
                            text = "DEBITS",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = alertRed,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "\u20b9${String.format("%.2f", totalDebits)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textTitleMedium,
                            color = alertRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space12))

            // Net Position Card
            val net = totalCredits - totalDebits
            val netColor = if (net >= 0) colors.inkPrimary else alertRed
            val netBg = colors.surfaceCard
            val netBorder = colors.borderWhisper

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = d.space24),
                shape = RoundedCornerShape(d.radiusLG),
                color = netBg,
                border = BorderStroke(1.dp, netBorder)
            ) {
                Row(
                    modifier = Modifier.padding(d.space16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "NET POSITION",
                            fontFamily = OutfitFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = netColor,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (net >= 0) "People owe you net" else "You owe net",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = inkMuted
                        )
                    }
                    Text(
                        text = "\u20b9${String.format("%.2f", Math.abs(net))}",
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textTitleLarge,
                        color = netColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(d.space24))

            // Transactions list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space8),
                verticalArrangement = Arrangement.spacedBy(d.space12)
            ) {
                item {
                    Text(
                        text = "TRANSACTION LEDGER (${filteredTransactions.size})",
                        fontFamily = OutfitFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = inkMuted,
                        letterSpacing = 1.5.sp
                    )
                }

                if (filteredTransactions.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = d.space32),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No transactions found in this period",
                                fontFamily = OutfitFamily,
                                fontSize = d.textBodyLarge,
                                color = inkMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredTransactions) { tx ->
                        val txDateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(tx.date)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = d.space4),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tx.description,
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = d.textBodyLarge,
                                    color = inkPrimary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = borderWhisper,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.padding(vertical = 1.dp)
                                    ) {
                                        Text(
                                            text = tx.typeName,
                                            fontFamily = OutfitFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = inkMuted,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "$txDateStr · ${tx.category}",
                                        fontFamily = OutfitFamily,
                                        fontSize = d.textLabelSmall,
                                        color = inkMuted
                                    )
                                }
                            }

                            val prefix = if (tx.amount >= 0) "+" else "-"
                            val txColor = if (tx.amount >= 0) positiveGreen else inkPrimary
                            Text(
                                text = "$prefix\u20b9${String.format("%.2f", Math.abs(tx.amount))}",
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textBodyLarge,
                                color = txColor
                            )
                        }
                        HorizontalDivider(color = borderWhisper, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

