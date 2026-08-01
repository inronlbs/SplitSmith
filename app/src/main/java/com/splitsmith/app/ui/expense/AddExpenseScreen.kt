package com.splitsmith.app.ui.expense

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.ui.components.dotGridBackground
import kotlinx.coroutines.launch

private val memberColors = listOf(
    Color(0xFF6A6A66), Color(0xFF555552), Color(0xFF40403E),
    Color(0xFF7A7A76), Color(0xFF8F8F8A), Color(0xFF333331)
)
private fun memberColor(uid: String) = memberColors[Math.abs(uid.hashCode()) % memberColors.size]

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    groupId: String,
    expenseId: String? = null,
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val groupState = FirebaseManager.observeGroup(groupId).collectAsState(initial = null)
    val currentGroup = groupState.value

    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    val baseCategories = remember { listOf("Food", "Travel", "Stay", "Groceries", "Shopping", "Entertainment", "Rent", "Other") }
    val allCategories = remember(currentGroup?.customCategories) {
        baseCategories + (currentGroup?.customCategories ?: emptyList())
    }
    var selectedCategory by remember { mutableStateOf("Food") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryInput by remember { mutableStateOf("") }
    var selectedPayerId by remember { mutableStateOf("") }
    var splitMode by remember { mutableStateOf("EQUAL") }
    var customSplitInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var explicitlyEditedUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedMembers by remember { mutableStateOf<Set<String>>(emptySet()) }
    var userNamesMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }

    var showAdvancedSplitSheet by remember { mutableStateOf(false) }
    var showAttachmentPickerSheet by remember { mutableStateOf(false) }
    var selectedAttachmentUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    var existingAttachmentUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var existingDriveFileIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val userProfileState = FirebaseManager.observeUserProfile().collectAsState(initial = null)
    val userProfile = userProfileState.value
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(currentGroup) {
        val members = currentGroup?.members?.keys ?: return@LaunchedEffect
        val tempMap = mutableMapOf<String, String>()
        for (uid in members) {
            val profile = FirebaseManager.getUserProfile(uid)
            if (profile != null) tempMap[uid] = profile.displayName
        }
        userNamesMap = tempMap
        if (expenseId == null) {
            selectedMembers = members
            selectedPayerId = FirebaseManager.currentUserId ?: members.firstOrNull() ?: ""
        }
    }

    LaunchedEffect(Unit) {
        val pAmt = FirebaseManager.pendingExpenseAmount
        val pDesc = FirebaseManager.pendingExpenseDesc
        val pCategory = FirebaseManager.pendingExpenseCategory
        val pDate = FirebaseManager.pendingExpenseDate
        val pAttachment = FirebaseManager.pendingExpenseAttachmentUri
        if (pAmt != null) {
            amountStr = pAmt
            FirebaseManager.pendingExpenseAmount = null
        }
        if (pDesc != null) {
            description = pDesc
            FirebaseManager.pendingExpenseDesc = null
        }
        if (pCategory != null) {
            selectedCategory = pCategory
            FirebaseManager.pendingExpenseCategory = null
        }
        if (pDate != null) {
            selectedDateMillis = pDate
            FirebaseManager.pendingExpenseDate = null
        }
        if (pAttachment != null) {
            selectedAttachmentUris = listOf(pAttachment)
            FirebaseManager.pendingExpenseAttachmentUri = null
        }
    }

    LaunchedEffect(expenseId, currentGroup) {
        if (expenseId != null && currentGroup != null) {
            val exp = FirebaseManager.getExpense(groupId, expenseId)
            if (exp != null) {
                amountStr = exp.amount.toString()
                description = exp.description
                selectedCategory = exp.category
                selectedPayerId = exp.paidBy
                splitMode = exp.splitMode
                val tempInputs = mutableMapOf<String, String>()
                exp.splits.forEach { (uid, share) ->
                    tempInputs[uid] = share.toString()
                }
                customSplitInputs = tempInputs
                selectedMembers = exp.splits.keys
                selectedDateMillis = exp.date
                existingAttachmentUrls = exp.receiptUrls
                existingDriveFileIds = exp.receiptDriveFileIds
            }
        }
    }

    val amountVal = amountStr.toDoubleOrNull() ?: 0.0
    val eachShare = if (selectedMembers.isNotEmpty() && amountVal > 0)
        "= \u20b9${"%.2f".format(amountVal / selectedMembers.size)} each" else ""

    // Remainder calculation for live banner
    val liveRemainder = remember(customSplitInputs, amountVal, splitMode, selectedMembers) {
        if (splitMode == "EXACT") {
            val sum = selectedMembers.sumOf { customSplitInputs[it]?.toDoubleOrNull() ?: 0.0 }
            amountVal - sum
        } else if (splitMode == "PERCENTAGE") {
            val sum = selectedMembers.sumOf { customSplitInputs[it]?.toDoubleOrNull() ?: 0.0 }
            100.0 - sum
        } else 0.0
    }

    if (showAddCategoryDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAddCategoryDialog = false
                customCategoryInput = ""
            },
            title = { Text("Add Custom Category", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = customCategoryInput,
                    onValueChange = { customCategoryInput = it },
                    placeholder = { Text("Category name", fontFamily = OutfitFamily) },
                    singleLine = true,
                    shape = RoundedCornerShape(d.radiusSM),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.inkPrimary,
                        unfocusedBorderColor = colors.borderWhisper
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = customCategoryInput.trim()
                        if (trimmed.isNotEmpty()) {
                            coroutineScope.launch {
                                try {
                                    FirebaseManager.addGroupCustomCategory(groupId, trimmed)
                                    selectedCategory = trimmed
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        showAddCategoryDialog = false
                        customCategoryInput = ""
                    }
                ) {
                    Text("Add", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddCategoryDialog = false
                        customCategoryInput = ""
                    }
                ) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            },
            containerColor = colors.canvasChalk
        )
    }

    Scaffold(
        containerColor = colors.canvasChalk,
        contentWindowInsets = WindowInsets(0), // Clean status bar and bottom padding
        modifier = Modifier.dotGridBackground(colors.dotColor),
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.canvasChalk,
                border = BorderStroke(0.5.dp, colors.borderWhisper)
            ) {
                Button(
                    onClick = {
                        if (amountVal <= 0 || description.trim().isEmpty() || selectedPayerId.isEmpty()) {
                            Toast.makeText(context, "Invalid input data", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val computedSplits = mutableMapOf<String, Double>()
                        when (splitMode) {
                            "EQUAL" -> {
                                val count = selectedMembers.size
                                if (count > 0) {
                                    val baseShare = Math.floor((amountVal / count) * 100.0) / 100.0
                                    var totalAllocated = 0.0
                                    selectedMembers.forEach { uid -> computedSplits[uid] = baseShare; totalAllocated += baseShare }
                                    val remainder = Math.round((amountVal - totalAllocated) * 100.0) / 100.0
                                    computedSplits[selectedPayerId] = (computedSplits[selectedPayerId] ?: 0.0) + remainder
                                }
                            }
                            "EXACT" -> {
                                var sum = 0.0
                                selectedMembers.forEach { uid ->
                                    val exactVal = customSplitInputs[uid]?.toDoubleOrNull() ?: 0.0
                                    computedSplits[uid] = exactVal; sum += exactVal
                                }
                                if (Math.abs(sum - amountVal) > 0.05) {
                                    Toast.makeText(context, "Exact shares must sum to \u20b9$amountVal (got \u20b9$sum)", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                            }
                            "PERCENTAGE" -> {
                                var percentSum = 0.0
                                selectedMembers.forEach { uid ->
                                    val pct = customSplitInputs[uid]?.toDoubleOrNull() ?: 0.0
                                    percentSum += pct
                                    computedSplits[uid] = Math.round((amountVal * (pct / 100.0)) * 100.0) / 100.0
                                }
                                if (Math.abs(percentSum - 100.0) > 0.5) {
                                    Toast.makeText(context, "Percentages must sum to 100% (got ${percentSum}%)", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                            }
                            "SHARES" -> {
                                var shareSum = 0.0
                                selectedMembers.forEach { uid ->
                                    val shares = customSplitInputs[uid]?.toDoubleOrNull() ?: 0.0
                                    shareSum += shares
                                }
                                if (shareSum <= 0.0) {
                                    Toast.makeText(context, "Total shares must be greater than 0", Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                selectedMembers.forEach { uid ->
                                    val shares = customSplitInputs[uid]?.toDoubleOrNull() ?: 0.0
                                    computedSplits[uid] = Math.round((amountVal * (shares / shareSum)) * 100.0) / 100.0
                                }
                            }
                        }

                        isLoading = true
                        coroutineScope.launch {
                            try {
                                val finalUploadedUrls = existingAttachmentUrls.toMutableList()
                                val finalDriveFileIds = existingDriveFileIds.toMutableList()

                                // Step 1: Save attachments locally first
                                val localSavedUris = mutableListOf<android.net.Uri>()
                                if (selectedAttachmentUris.isNotEmpty()) {
                                    selectedAttachmentUris.forEach { uri ->
                                        val localSavedUri = com.splitsmith.app.data.LocalStorageManager.saveAttachmentLocally(context, uri, "exp")
                                        val effectiveUri = localSavedUri ?: uri
                                        localSavedUris.add(effectiveUri)
                                        finalUploadedUrls.add(effectiveUri.toString())
                                    }
                                }

                                // Step 2: Save to Firebase (get real document ID)
                                if (expenseId != null) {
                                    FirebaseManager.updateExpense(
                                        groupId = groupId,
                                        expenseId = expenseId,
                                        description = description.trim(),
                                        amount = amountVal,
                                        paidBy = selectedPayerId,
                                        category = selectedCategory,
                                        splitMode = splitMode,
                                        splits = computedSplits,
                                        date = selectedDateMillis,
                                        receiptUrls = finalUploadedUrls,
                                        receiptDriveFileIds = finalDriveFileIds
                                    )
                                    Toast.makeText(context, "Changes saved!", Toast.LENGTH_SHORT).show()
                                } else {
                                    val newExpenseId = FirebaseManager.addExpense(
                                        groupId = groupId,
                                        description = description.trim(),
                                        amount = amountVal,
                                        paidBy = selectedPayerId,
                                        category = selectedCategory,
                                        splitMode = splitMode,
                                        splits = computedSplits,
                                        receiptUrl = finalUploadedUrls.firstOrNull() ?: "",
                                        receiptUrls = finalUploadedUrls,
                                        receiptDriveFileIds = finalDriveFileIds,
                                        date = selectedDateMillis
                                    )
                                    Toast.makeText(context, "Expense added!", Toast.LENGTH_SHORT).show()

                                    // Step 3: Upload to Drive in background with real ID
                                    if (localSavedUris.isNotEmpty() && userProfile?.driveSyncEnabled == true) {
                                        val folderCategory = currentGroup?.name ?: "Group Expenses"
                                        val applicationContext = context.applicationContext
                                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                            localSavedUris.forEach { effectiveUri ->
                                                try {
                                                    val driveResult = com.splitsmith.app.data.GoogleDriveManager.uploadAttachment(
                                                        context = applicationContext,
                                                        inputUri = effectiveUri,
                                                        folderCategoryName = folderCategory,
                                                        dateMillis = selectedDateMillis,
                                                        expenseId = newExpenseId
                                                    )
                                                    if (driveResult != null) {
                                                        FirebaseManager.attachDriveFileToGroupExpense(
                                                            groupId = groupId,
                                                            expenseId = newExpenseId,
                                                            driveFileId = driveResult.fileId,
                                                            webUrl = driveResult.webViewLink
                                                        )
                                                    } else {
                                                        com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                                            context = applicationContext,
                                                            localUri = effectiveUri,
                                                            folderCategoryName = folderCategory,
                                                            dateMillis = selectedDateMillis,
                                                            expenseId = newExpenseId,
                                                            isPersonal = false,
                                                            groupId = groupId
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                                        context = applicationContext,
                                                        localUri = effectiveUri,
                                                        folderCategoryName = folderCategory,
                                                        dateMillis = selectedDateMillis,
                                                        expenseId = newExpenseId,
                                                        isPersonal = false,
                                                        groupId = groupId
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                onBack()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally { isLoading = false }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = d.space24, vertical = d.space16)
                        .height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = colors.canvasChalk, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = if (expenseId != null) "Save Changes" else "Add Expense",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = d.textLabelLarge,
                            color = colors.canvasChalk
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
                .imePadding()
        ) {
            // ── Custom minimal header ─────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = d.space16),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(d.iconSizeMd + 8.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(d.iconSizeMd)
                    )
                }
                Spacer(modifier = Modifier.width(d.space8))
                Text(
                    text = if (expenseId != null) "Edit Expense" else "Add Expense",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textTitleLarge,
                    color = colors.inkPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space8),
                verticalArrangement = Arrangement.spacedBy(d.space20)
            ) {
                // ── Amount Hero Input (Sleek and borderless) ──────────
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = d.space16),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "₹",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Light,
                                fontSize = 42.sp,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            BasicTextField(
                                value = amountStr,
                                onValueChange = { amountStr = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 48.sp,
                                    color = colors.inkPrimary,
                                    textAlign = TextAlign.Start
                                ),
                                modifier = Modifier
                                    .width(IntrinsicSize.Min)
                                    .widthIn(min = 60.dp),
                                decorationBox = { innerTextField ->
                                    Box(contentAlignment = Alignment.CenterStart) {
                                        if (amountStr.isEmpty()) {
                                            Text(
                                                text = "0",
                                                fontFamily = JetBrainsMonoFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 48.sp,
                                                color = colors.inkMuted
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }
                        if (eachShare.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(d.space4))
                            Text(
                                text = eachShare,
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelMedium,
                                color = colors.inkMuted
                            )
                        }
                    }
                }

                // ── Description Input ─────────────────────────
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                        shape = RoundedCornerShape(d.radiusSM),
                        placeholder = {
                            Text("What was it for?", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted)
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
                        textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                    )
                }

                // ── Date Picker Row ───────────────────────────
                item {
                    var showDatePicker by remember { mutableStateOf(false) }
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = selectedDateMillis
                    )
                    
                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        selectedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
                                        showDatePicker = false
                                    }
                                ) {
                                    Text("OK", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDatePicker = false }) {
                                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                                }
                            },
                            colors = DatePickerDefaults.colors(
                                containerColor = colors.canvasChalk
                            )
                        ) {
                            DatePicker(
                                state = datePickerState,
                                colors = DatePickerDefaults.colors(
                                    selectedDayContainerColor = colors.inkPrimary,
                                    selectedDayContentColor = colors.canvasChalk,
                                    todayContentColor = colors.inkPrimary,
                                    todayDateBorderColor = colors.inkPrimary
                                )
                            )
                        }
                    }

                    val formatter = remember { java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()) }
                    val dateStr = formatter.format(java.util.Date(selectedDateMillis))

                    Surface(
                        onClick = { showDatePicker = true },
                        shape = RoundedCornerShape(d.radiusSM),
                        color = colors.canvasChalk,
                        border = BorderStroke(1.dp, colors.borderWhisper),
                        modifier = Modifier.fillMaxWidth().height(d.inputHeight)
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
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Date",
                                    tint = colors.inkMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = dateStr,
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textBodyLarge,
                                    color = colors.inkPrimary
                                )
                            }
                            Text(
                                text = "Change",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = d.textLabelMedium,
                                color = colors.inkPrimary
                            )
                        }
                    }
                }

                // ── Category Selector ──────────────────────────
                item {
                    Text("CATEGORY", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(d.space8))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(d.space8),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(allCategories) { category ->
                            val isSelected = selectedCategory == category
                            Surface(
                                onClick = { selectedCategory = category },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = if (isSelected) colors.inkPrimary else colors.canvasChalk,
                                border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null
                            ) {
                                Text(
                                    text = category,
                                    fontFamily = OutfitFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = d.textLabelLarge,
                                    color = if (isSelected) colors.canvasChalk else colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = d.space16, vertical = d.space8)
                                )
                            }
                        }
                        item {
                            Surface(
                                onClick = { showAddCategoryDialog = true },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = colors.canvasChalk,
                                border = BorderStroke(1.dp, colors.borderWhisper)
                            ) {
                                Text(
                                    text = "+ Add Custom",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelLarge,
                                    color = colors.inkPrimary,
                                    modifier = Modifier.padding(horizontal = d.space16, vertical = d.space8)
                                )
                            }
                        }
                    }
                }

                // ── Receipts & Attachments Section ─────────────────
                item {
                    com.splitsmith.app.ui.components.attachments.AttachmentComponent(
                        selectedUris = selectedAttachmentUris,
                        existingUrls = existingAttachmentUrls,
                        existingDriveFileIds = existingDriveFileIds,
                        onUrisChanged = { selectedAttachmentUris = it },
                        onExistingUrlsChanged = { existingAttachmentUrls = it },
                        onDriveFileIdsChanged = { existingDriveFileIds = it }
                    )
                }

                // ── Paid By Selector ───────────────────────────
                item {
                    Text("PAID BY", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(d.space8))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        items(userNamesMap.entries.toList()) { (uid, name) ->
                            val isSelected = selectedPayerId == uid
                            Surface(
                                onClick = { selectedPayerId = uid },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = if (isSelected) colors.inkPrimary else colors.canvasChalk,
                                border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null
                            ) {
                                Text(
                                    text = name,
                                    fontFamily = OutfitFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = d.textLabelMedium,
                                    color = if (isSelected) colors.canvasChalk else colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = d.space16, vertical = d.space8)
                                )
                            }
                        }
                    }
                }

                // ── Split With Selector ────────────────────────
                item {
                    Text("SPLIT WITH", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
                    Spacer(modifier = Modifier.height(d.space8))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                    ) {
                        items(userNamesMap.entries.toList()) { (uid, name) ->
                            val isSelected = uid in selectedMembers
                            Surface(
                                onClick = {
                                    selectedMembers = if (isSelected) {
                                        if (selectedMembers.size > 1) selectedMembers - uid else selectedMembers
                                    } else {
                                        selectedMembers + uid
                                    }
                                },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = if (isSelected) colors.inkPrimary else colors.canvasChalk,
                                border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null
                            ) {
                                Text(
                                    text = name,
                                    fontFamily = OutfitFamily,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = d.textLabelMedium,
                                    color = if (isSelected) colors.canvasChalk else colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = d.space16, vertical = d.space8)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(d.space12))

                    // Split Mode Chips
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                        listOf("EQUAL", "EXACT", "PERCENTAGE", "SHARES").forEach { mode ->
                            val isActive = splitMode == mode
                            Surface(
                                onClick = {
                                    splitMode = mode
                                    explicitlyEditedUids = emptySet()
                                    if (mode != "EQUAL") showAdvancedSplitSheet = true
                                },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = if (isActive) colors.inkPrimary else colors.canvasChalk,
                                border = if (!isActive) BorderStroke(1.dp, colors.borderWhisper) else null
                            ) {
                                Text(
                                    text = mode.lowercase().replaceFirstChar { it.uppercase() },
                                    fontFamily = OutfitFamily,
                                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = d.textLabelSmall,
                                    color = if (isActive) colors.canvasChalk else colors.inkMuted,
                                    modifier = Modifier.padding(horizontal = d.space12, vertical = d.space8)
                                )
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(d.space8)) }
            }
        }
    }

    // ── Advanced Splits Customizer Sheet ────────────────────────
    if (showAdvancedSplitSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAdvancedSplitSheet = false },
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
                Text(
                    text = "Configure Custom Splits",
                    fontFamily = OutfitFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = d.textTitleLarge,
                    color = colors.inkPrimary
                )
                Text(
                    text = "Total amount to split: \u20b9$amountVal",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )

                // Live Remainder Display Banner
                if (splitMode == "EXACT" || splitMode == "PERCENTAGE") {
                    val unitStr = if (splitMode == "EXACT") "\u20b9" else "%"
                    val label = if (splitMode == "EXACT") "Remaining cash" else "Remaining percent"
                    val isBalanced = Math.abs(liveRemainder) < 0.05
                    Surface(
                        shape = RoundedCornerShape(d.radiusSM),
                        color = if (isBalanced) colors.canvasChalk else colors.alertRed.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, if (isBalanced) colors.borderWhisper else colors.alertRed.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = d.space12, vertical = d.space8),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkPrimary)
                                Text(
                                    text = "$unitStr${"%.2f".format(liveRemainder)}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = d.textLabelLarge,
                                    color = if (isBalanced) colors.positiveGreen else colors.alertRed
                                )
                            }
                            if (!isBalanced) {
                                Surface(
                                    onClick = {
                                        val membersList = selectedMembers.toList()
                                        val unsetMembers = membersList.filter { it !in explicitlyEditedUids }
                                        val targetMembers = if (unsetMembers.isNotEmpty()) unsetMembers else membersList
                                        val newInputs = customSplitInputs.toMutableMap()
                                        val totalAmt = amountStr.toDoubleOrNull() ?: 0.0

                                        if (splitMode == "PERCENTAGE") {
                                            val sumLockedPct = explicitlyEditedUids.sumOf { newInputs[it]?.toDoubleOrNull() ?: 0.0 }
                                            val remainingPct = (100.0 - sumLockedPct).coerceAtLeast(0.0)
                                            val perTarget = if (targetMembers.isNotEmpty()) remainingPct / targetMembers.size else 0.0
                                            targetMembers.forEach { targetUid ->
                                                newInputs[targetUid] = if (perTarget == 0.0) "" else if (perTarget % 1.0 == 0.0) perTarget.toInt().toString() else String.format("%.1f", perTarget)
                                            }
                                        } else if (splitMode == "EXACT" && totalAmt > 0) {
                                            val sumLockedAmt = explicitlyEditedUids.sumOf { newInputs[it]?.toDoubleOrNull() ?: 0.0 }
                                            val remainingAmt = (totalAmt - sumLockedAmt).coerceAtLeast(0.0)
                                            val perTarget = if (targetMembers.isNotEmpty()) remainingAmt / targetMembers.size else 0.0
                                            targetMembers.forEach { targetUid ->
                                                newInputs[targetUid] = if (perTarget == 0.0) "" else if (perTarget % 1.0 == 0.0) perTarget.toInt().toString() else String.format("%.2f", perTarget)
                                            }
                                        }
                                        customSplitInputs = newInputs
                                    },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    color = colors.inkPrimary,
                                    contentColor = colors.canvasChalk
                                ) {
                                    Text(
                                        text = "Distribute",
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textLabelSmall,
                                        modifier = Modifier.padding(horizontal = d.space12, vertical = d.space4 + 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(d.space12)
                ) {
                    items(selectedMembers.toList()) { uid ->
                        val name = userNamesMap[uid] ?: "Member"
                        val valInput = customSplitInputs[uid] ?: ""
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                            OutlinedTextField(
                                value = valInput,
                                onValueChange = { inputVal ->
                                    val newInputs = customSplitInputs.toMutableMap()
                                    newInputs[uid] = inputVal

                                    val newEditedUids = if (inputVal.trim().isNotEmpty() && (inputVal.toDoubleOrNull() ?: 0.0) >= 0.0) {
                                        explicitlyEditedUids + uid
                                    } else {
                                        explicitlyEditedUids - uid
                                    }
                                    explicitlyEditedUids = newEditedUids

                                    val membersList = selectedMembers.toList()
                                    if (membersList.size > 1) {
                                        val totalAmt = amountStr.toDoubleOrNull() ?: 0.0
                                        val unsetMembers = membersList.filter { it !in newEditedUids }

                                        if (unsetMembers.isNotEmpty()) {
                                            if (splitMode == "PERCENTAGE") {
                                                val sumLockedPct = newEditedUids.sumOf { newInputs[it]?.toDoubleOrNull() ?: 0.0 }
                                                val remainingPct = (100.0 - sumLockedPct).coerceAtLeast(0.0)
                                                val perUnsetPct = remainingPct / unsetMembers.size

                                                unsetMembers.forEach { unsetUid ->
                                                    newInputs[unsetUid] = if (perUnsetPct == 0.0) "" else if (perUnsetPct % 1.0 == 0.0) perUnsetPct.toInt().toString() else String.format("%.1f", perUnsetPct)
                                                }
                                            } else if (splitMode == "EXACT" && totalAmt > 0) {
                                                val sumLockedAmt = newEditedUids.sumOf { newInputs[it]?.toDoubleOrNull() ?: 0.0 }
                                                val remainingAmt = (totalAmt - sumLockedAmt).coerceAtLeast(0.0)
                                                val perUnsetAmt = remainingAmt / unsetMembers.size

                                                unsetMembers.forEach { unsetUid ->
                                                    newInputs[unsetUid] = if (perUnsetAmt == 0.0) "" else if (perUnsetAmt % 1.0 == 0.0) perUnsetAmt.toInt().toString() else String.format("%.2f", perUnsetAmt)
                                                }
                                            }
                                        }
                                    }
                                    customSplitInputs = newInputs
                                },
                                modifier = Modifier.width(120.dp).heightIn(min = d.inputHeight),
                                shape = RoundedCornerShape(d.radiusSM),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                placeholder = {
                                    Text(
                                        text = when (splitMode) { "EXACT" -> "\u20b90.0"; "PERCENTAGE" -> "%0"; else -> "Ratio" },
                                        fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted
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
                                textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
                            )
                        }
                    }
                }

                Button(
                    onClick = { showAdvancedSplitSheet = false },
                    modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                    shape = RoundedCornerShape(d.radiusMD),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary)
                ) {
                    Text("Apply custom split", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = colors.canvasChalk)
                }
            }
        }
    }
}


