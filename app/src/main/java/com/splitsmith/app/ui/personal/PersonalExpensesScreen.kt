package com.splitsmith.app.ui.personal

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import com.splitsmith.app.data.PersonalExpense
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalExpensesScreen(
    showAddPersonalInitially: Boolean = false,
    onNavigateToQuickSplit: () -> Unit,
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showAddPersonalSheet by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("ALL") }
    var isSearchVisible by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<PersonalExpense?>(null) }
    var selectedExpenseDetail by remember { mutableStateOf<PersonalExpense?>(null) }

    val personalExpensesFlow = remember { FirebaseManager.observePersonalExpenses() }
    val personalExpensesState = personalExpensesFlow.collectAsState(initial = emptyList())
    val userProfileFlow = remember { FirebaseManager.observeUserProfile() }
    val userProfileState = userProfileFlow.collectAsState(initial = null)

    val personalExpenses = personalExpensesState.value
    val profile = userProfileState.value

    // Auto-open sheet if triggered from FAB Quick Actions
    LaunchedEffect(showAddPersonalInitially) {
        if (showAddPersonalInitially) {
            showAddPersonalSheet = true
        }
    }

    // Default categories + custom ones
    val baseCategories = listOf("Groceries", "Food", "Travel", "Stay", "Shopping", "Entertainment", "Rent", "Other")
    val allCategories = remember(profile?.customCategories) {
        baseCategories + (profile?.customCategories ?: emptyList())
    }

    val currentMonthStart = remember {
        java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Calculate current calendar month spent
    val monthlySpend = remember(personalExpenses, currentMonthStart) {
        personalExpenses.filter { it.date >= currentMonthStart }.sumOf { it.amount }
    }
    val budgetLimit = (profile?.budget?.limit ?: 15000.0).let { if (it <= 0) 15000.0 else it }

    // Filter personal expenses
    val filteredExpenses = remember(personalExpenses, searchQuery, selectedCategoryFilter) {
        personalExpenses.filter { exp ->
            val matchesSearch = exp.description.contains(searchQuery, ignoreCase = true) || exp.note.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategoryFilter == "ALL" || exp.category.uppercase() == selectedCategoryFilter.uppercase()
            matchesSearch && matchesCategory
        }.sortedByDescending { it.date }
    }

    var expenseToDelete by remember { mutableStateOf<PersonalExpense?>(null) }

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
                                    FirebaseManager.deletePersonalExpense(exp.id)
                                    Toast.makeText(context, "Expense deleted", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        expenseToDelete = null
                    }
                ) {
                    Text("Delete", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.alertRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { expenseToDelete = null }) {
                    Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                }
            },
            containerColor = colors.canvasChalk
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
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(horizontal = d.space24, vertical = d.space24),
                verticalArrangement = Arrangement.spacedBy(d.space20)
            ) {
                // Unified Header (No back button, matching Split Expenses / Dashboard)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expenses",
                            fontFamily = OutfitFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textHeadlineLarge,
                            color = colors.inkPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        IconButton(onClick = { isSearchVisible = !isSearchVisible }) {
                            Icon(
                                imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Toggle Search",
                                tint = colors.inkPrimary
                            )
                        }
                    }
                }
                // Budget tracker inline overview (no cards)
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = d.space8)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Current Expenses for the Month", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                            Text("Monthly Budget: \u20b9${budgetLimit.toInt()}", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                        }
                        Spacer(modifier = Modifier.height(d.space8))
                        Text(
                            text = "\u20b9${"%.2f".format(monthlySpend)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = d.textDisplayLarge,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(d.space12))

                        val progress = (monthlySpend / budgetLimit).coerceIn(0.0, 1.0).toFloat()
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(d.radiusFull)),
                            color = if (progress > 0.9f) colors.alertRed else colors.inkPrimary,
                            trackColor = colors.borderWhisper
                        )
                    }
                }

                // Search & Filter controls (only shown when search is visible)
                if (isSearchVisible) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                            shape = RoundedCornerShape(d.radiusSM),
                            placeholder = { Text("Search personal expenses...", fontFamily = OutfitFamily, color = colors.inkMuted) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.inkMuted) },
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

                    // Horizontal Category Chips row
                    item {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(d.space8),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item {
                                val isAllSelected = selectedCategoryFilter == "ALL"
                                Surface(
                                    onClick = { selectedCategoryFilter = "ALL" },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    color = if (isAllSelected) colors.inkPrimary else colors.surfaceCard,
                                    border = if (!isAllSelected) BorderStroke(1.dp, colors.borderWhisper) else null,
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space12)) {
                                        Text("All Categories", fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = if (isAllSelected) colors.canvasChalk else colors.inkMuted)
                                    }
                                }
                            }
                            items(allCategories) { cat ->
                                val isSelected = selectedCategoryFilter.uppercase() == cat.uppercase()
                                Surface(
                                    onClick = { selectedCategoryFilter = cat },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    color = if (isSelected) colors.inkPrimary else colors.surfaceCard,
                                    border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null,
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space12)) {
                                        Text(cat, fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = if (isSelected) colors.canvasChalk else colors.inkMuted)
                                    }
                                }
                            }
                        }
                    }
                }

                // Expense List items
                if (filteredExpenses.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = d.space32), contentAlignment = Alignment.Center) {
                            Text("No expenses match filters.\nTap + to log one.", fontFamily = OutfitFamily, fontSize = d.textBodyMedium, color = colors.inkMuted, textAlign = TextAlign.Center)
                        }
                    }
                } else {
                    item {
                        Text("SPENDING LOGS", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
                    }

                    items(filteredExpenses) { exp ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedExpenseDetail = exp }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = d.space12),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(exp.description, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = colors.inkPrimary)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(d.space8)
                                    ) {
                                        val formatter = remember { java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()) }
                                        val dateStr = formatter.format(java.util.Date(exp.date))
                                        Text("${exp.category} · $dateStr", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                                        
                                        // Detect if note has app name
                                        val appTag = when {
                                            exp.note.contains("PhonePe", ignoreCase = true) -> "via PhonePe"
                                            exp.note.contains("Google Pay", ignoreCase = true) || exp.note.contains("G Pay", ignoreCase = true) -> "via Google Pay"
                                            exp.note.contains("Paytm", ignoreCase = true) -> "via Paytm"
                                            else -> ""
                                        }
                                        if (appTag.isNotEmpty()) {
                                            Text("· $appTag", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                                        }
                                    }
                                }
                                Text(
                                    text = "₹${if (exp.amount % 1.0 == 0.0) exp.amount.toInt().toString() else String.format("%.2f", exp.amount)}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = d.textMonoLarge,
                                    color = colors.inkPrimary
                                )
                            }
                            HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }

        // Add / Edit Personal Expense Bottom Sheet
        val currentEdit = editingExpense
        val isSheetOpen = showAddPersonalSheet || currentEdit != null
        if (isSheetOpen) {
            val isEditing = currentEdit != null
            val initialAmount = if (currentEdit != null) currentEdit.amount.toString() else ""
            val initialDesc = if (currentEdit != null) currentEdit.description else ""
            val initialCategory = if (currentEdit != null) currentEdit.category else "Groceries"
            val initialNote = if (currentEdit != null) currentEdit.note else ""
            val initialDate = if (currentEdit != null) currentEdit.date else System.currentTimeMillis()

            var newAmountStr by remember(currentEdit, showAddPersonalSheet) { mutableStateOf(initialAmount) }
            var newDesc by remember(currentEdit, showAddPersonalSheet) { mutableStateOf(initialDesc) }
            var newCategory by remember(currentEdit, showAddPersonalSheet) { mutableStateOf(initialCategory) }
            var newNote by remember(currentEdit, showAddPersonalSheet) { mutableStateOf(initialNote) }
            var newDateMillis by remember(currentEdit, showAddPersonalSheet) { mutableStateOf(initialDate) }
            var isPersonalLoading by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = {
                    showAddPersonalSheet = false
                    editingExpense = null
                },
                containerColor = colors.surfaceCard,
                dragHandle = { BottomSheetDefaults.DragHandle(color = colors.borderWhisper) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .imePadding()
                        .navigationBarsPadding()
                        .padding(horizontal = d.space24, vertical = d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    Text(
                        text = if (isEditing) "Edit Personal Expense" else "Add Personal Expense",
                        fontFamily = OutfitFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = d.textTitleLarge,
                        color = colors.inkPrimary
                    )

                    // Hero amount input (Centered tightly with Rupee symbol)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = d.space8),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "\u20b9",
                            fontFamily = OutfitFamily,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.inkMuted,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        BasicTextField(
                            value = newAmountStr,
                            onValueChange = { newAmountStr = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 42.sp,
                                color = colors.inkPrimary
                            ),
                            modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 60.dp),
                            decorationBox = { innerTextField ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (newAmountStr.isEmpty()) {
                                        Text("0", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = 42.sp, color = colors.inkMuted)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }

                    // Description Input
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Description (e.g. Weekly veggies)", fontFamily = OutfitFamily, color = colors.inkMuted) },
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

                    // Note Input
                    OutlinedTextField(
                        value = newNote,
                        onValueChange = { newNote = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Additional notes (optional)", fontFamily = OutfitFamily, color = colors.inkMuted) },
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

                    // Date selector row
                    var showDatePicker by remember { mutableStateOf(false) }
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = newDateMillis
                    )
                    
                    if (showDatePicker) {
                        DatePickerDialog(
                            onDismissRequest = { showDatePicker = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        newDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
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
                                containerColor = colors.surfaceCard
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
                    val dateStr = formatter.format(java.util.Date(newDateMillis))

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

                    // Category chips with Add Custom Category button
                    Text("CATEGORY", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(d.space8),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(allCategories) { cat ->
                            val isSelected = newCategory.uppercase() == cat.uppercase()
                            Surface(
                                onClick = { newCategory = cat },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = if (isSelected) colors.inkPrimary else colors.surfaceCard,
                                border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space12)) {
                                    Text(cat, fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = if (isSelected) colors.canvasChalk else colors.inkMuted)
                                }
                            }
                        }
                        item {
                            Surface(
                                onClick = { showAddCategoryDialog = true },
                                shape = RoundedCornerShape(d.radiusFull),
                                color = colors.surfaceCard,
                                border = BorderStroke(1.dp, colors.borderWhisper),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space12)) {
                                    Text("+ Add Custom", fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = colors.inkPrimary)
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val amt = newAmountStr.toDoubleOrNull() ?: 0.0
                            if (amt <= 0 || newDesc.trim().isEmpty()) {
                                Toast.makeText(context, "Invalid amount or description", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isPersonalLoading = true
                            coroutineScope.launch {
                                try {
                                    if (currentEdit != null) {
                                        FirebaseManager.updatePersonalExpense(
                                            id = currentEdit.id,
                                            description = newDesc.trim(),
                                            amount = amt,
                                            category = newCategory,
                                            note = newNote.trim(),
                                            date = newDateMillis
                                        )
                                        Toast.makeText(context, "Changes saved", Toast.LENGTH_SHORT).show()
                                    } else {
                                        FirebaseManager.addPersonalExpense(
                                            description = newDesc.trim(),
                                            amount = amt,
                                            category = newCategory,
                                            note = newNote.trim(),
                                            date = newDateMillis
                                        )
                                        Toast.makeText(context, "Expense saved to wallet", Toast.LENGTH_SHORT).show()
                                    }
                                    showAddPersonalSheet = false
                                    editingExpense = null
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isPersonalLoading = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(d.buttonHeight),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                        enabled = !isPersonalLoading
                    ) {
                        if (isPersonalLoading) {
                            CircularProgressIndicator(color = colors.canvasChalk, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = if (isEditing) "Save Changes" else "Save Expense",
                                fontFamily = OutfitFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = d.textLabelLarge,
                                color = colors.canvasChalk
                            )
                        }
                    }
                }
            }
        }

        // Add Custom Category Dialog
        if (showAddCategoryDialog) {
            var customCatName by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showAddCategoryDialog = false },
                containerColor = colors.surfaceCard,
                title = { Text("New Custom Category", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, color = colors.inkPrimary) },
                text = {
                    OutlinedTextField(
                        value = customCatName,
                        onValueChange = { customCatName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Subscriptions", fontFamily = OutfitFamily, color = colors.inkMuted) },
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
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = customCatName.trim()
                            if (trimmed.isNotEmpty()) {
                                coroutineScope.launch {
                                    FirebaseManager.addCustomCategory(trimmed)
                                    Toast.makeText(context, "Category added!", Toast.LENGTH_SHORT).show()
                                    showAddCategoryDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary),
                        shape = RoundedCornerShape(d.radiusMD)
                    ) {
                        Text("Add", fontFamily = OutfitFamily, color = colors.canvasChalk)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddCategoryDialog = false }) {
                        Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
                    }
                }
            )
        }

        // Personal Expense Detail Bottom Sheet
        if (selectedExpenseDetail != null) {
            val exp = selectedExpenseDetail!!
            var showMenu by remember { mutableStateOf(false) }

            ModalBottomSheet(
                onDismissRequest = { selectedExpenseDetail = null },
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
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
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
                                Text(exp.description.ifEmpty { "Personal Expense" }, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleMedium, color = colors.inkPrimary)
                                Text(exp.category, fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                            }
                        }

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
                                        editingExpense = exp
                                        selectedExpenseDetail = null
                                    },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = colors.inkPrimary) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Expense", fontFamily = OutfitFamily, color = colors.alertRed) },
                                    onClick = {
                                        showMenu = false
                                        selectedExpenseDetail = null
                                        expenseToDelete = exp
                                    },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.alertRed) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                    // Large Amount
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = d.space8),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "₹${if (exp.amount % 1.0 == 0.0) exp.amount.toInt().toString() else String.format("%.2f", exp.amount)}",
                            fontFamily = JetBrainsMonoFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 42.sp,
                            color = colors.inkPrimary
                        )
                        Spacer(modifier = Modifier.height(d.space4))
                        Text(
                            text = "Personal Spend",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = colors.inkMuted
                        )
                    }

                    HorizontalDivider(color = colors.borderWhisper, thickness = 0.5.dp)

                    // Details metadata list
                    Column(verticalArrangement = Arrangement.spacedBy(d.space12)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Category", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textBodyLarge)
                            Text(exp.category, fontFamily = OutfitFamily, fontWeight = FontWeight.Medium, color = colors.inkPrimary, fontSize = d.textBodyLarge)
                        }

                        val formatter = remember { java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()) }
                        val dateStr = formatter.format(java.util.Date(exp.date))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Date", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textBodyLarge)
                            Text(dateStr, fontFamily = OutfitFamily, fontWeight = FontWeight.Medium, color = colors.inkPrimary, fontSize = d.textBodyLarge)
                        }

                        if (exp.note.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Note", fontFamily = OutfitFamily, color = colors.inkMuted, fontSize = d.textBodyLarge)
                                Text(exp.note, fontFamily = OutfitFamily, color = colors.inkPrimary, fontSize = d.textBodyLarge)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(d.space8))
                }
            }
        }
    }
}



