package com.splitsmith.app.ui.slip

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlipImportScreen(
    imageUriStr: String,
    onBack: () -> Unit,
    onNavigateToQuickSplit: () -> Unit,
    onNavigateToAddExpense: (groupId: String, expenseId: String?) -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val imageUri = remember(imageUriStr) { Uri.parse(imageUriStr) }

    // State Variables
    var amountStr by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var parsedDateMillis by remember { mutableStateOf<Long?>(null) }
    var sourcePaymentApp by remember { mutableStateOf("") }

    val profileState = FirebaseManager.observeUserProfile().collectAsState(initial = null)
    val profile = profileState.value
    val baseCategories = remember { listOf("Food", "Travel", "Stay", "Groceries", "Shopping", "Entertainment", "Rent", "Other") }
    val allCategories = remember(profile?.customCategories) {
        baseCategories + (profile?.customCategories ?: emptyList())
    }
    var selectedCategory by remember { mutableStateOf("Other") }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var customCategoryInput by remember { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(true) }
    var statusMessage by remember { mutableStateOf("Initializing local OCR...") }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var showGroupSelector by remember { mutableStateOf(false) }
    val groupsState = FirebaseManager.observeGroups().collectAsState(initial = emptyList())
    
    val personalExpenses = FirebaseManager.observePersonalExpenses().collectAsState(initial = emptyList()).value
    val directSplits = FirebaseManager.observeDirectSplits().collectAsState(initial = emptyList()).value

    val duplicateStatus = remember(amountStr, receiverName, transactionId, parsedDateMillis, personalExpenses, directSplits) {
        val parsedAmt = amountStr.toDoubleOrNull() ?: 0.0
        if (parsedAmt <= 0.0) return@remember null
        
        // 1. Check exact Transaction ID matches in Personal Expenses notes
        if (transactionId.isNotEmpty()) {
            val exactPersonal = personalExpenses.firstOrNull { it.note.contains(transactionId) }
            if (exactPersonal != null) {
                val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                return@remember DuplicateResult(
                    isExactMatch = true,
                    message = "This payment has already been logged as personal expense on ${fmt.format(java.util.Date(exactPersonal.date))} ('${exactPersonal.description}')."
                )
            }
        }
        
        // 2. Check similar details in Personal Expenses (Same amount and same date/description)
        val similarPersonal = personalExpenses.firstOrNull { 
            it.amount == parsedAmt && (
                it.description.contains(receiverName, ignoreCase = true) || 
                receiverName.contains(it.description, ignoreCase = true) ||
                Math.abs(it.date - (parsedDateMillis ?: System.currentTimeMillis())) < 24 * 60 * 60 * 1000
            )
        }
        if (similarPersonal != null) {
            val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            return@remember DuplicateResult(
                isExactMatch = false,
                message = "Warning: Similar Personal Expense of ₹${similarPersonal.amount} on ${fmt.format(java.util.Date(similarPersonal.date))} ('${similarPersonal.description}') already exists."
            )
        }
        
        // 3. Check similar details in Direct Splits
        val similarDirect = directSplits.firstOrNull {
            it.amount == parsedAmt && (
                it.description.contains(receiverName, ignoreCase = true) ||
                receiverName.contains(it.description, ignoreCase = true) ||
                Math.abs(it.date - (parsedDateMillis ?: System.currentTimeMillis())) < 24 * 60 * 60 * 1000
            )
        }
        if (similarDirect != null) {
            val fmt = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            return@remember DuplicateResult(
                isExactMatch = false,
                message = "Warning: Similar Quick Split of ₹${similarDirect.amount} on ${fmt.format(java.util.Date(similarDirect.date))} ('${similarDirect.description}') already exists."
            )
        }
        
        null
    }

    // Load bitmap and parse locally via offline ML Kit OCR and Regex
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                if (inputStream != null) {
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    loadedBitmap = bmp

                    // Step 1: Perform Clipboard Local Fallback scanner (in case user copied notification)
                    var clipboardParsedAmount = ""
                    var clipboardParsedReceiver = ""
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clipData = clipboard.primaryClip
                        if (clipData != null && clipData.itemCount > 0) {
                            val text = clipData.getItemAt(0).text?.toString() ?: ""
                            val amtPattern = Pattern.compile("(?:Rs\\.?|INR|paid)\\s*(\\d+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE)
                            val amtMatcher = amtPattern.matcher(text)
                            if (amtMatcher.find()) {
                                clipboardParsedAmount = amtMatcher.group(1) ?: ""
                            }
                            val rxPattern = Pattern.compile("to\\s+([A-Za-z0-9 ]{2,25})", Pattern.CASE_INSENSITIVE)
                            val rxMatcher = rxPattern.matcher(text)
                            if (rxMatcher.find()) {
                                clipboardParsedReceiver = rxMatcher.group(1)?.trim() ?: ""
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SlipImport", "Clipboard parse error: ${e.message}")
                    }

                    // Step 2: Run Local ML Kit OCR (100% offline, free, instant)
                    statusMessage = "Extracting text from image locally..."
                    var localOcrText = ""
                    try {
                        localOcrText = recognizeTextFromBitmap(bmp)
                    } catch (e: Exception) {
                        android.util.Log.e("SlipImport", "Local OCR failed: ${e.message}")
                    }

                    var amountParsed = ""
                    var receiverParsed = ""
                    var txnIdParsed = ""
                    var dateParsed: Long? = null

                    if (localOcrText.isNotEmpty()) {
                        val parsed = parseOCRText(localOcrText)
                        amountParsed = parsed.amount
                        receiverParsed = parsed.receiver
                        txnIdParsed = parsed.txnId
                        dateParsed = parseDate(localOcrText)
                    }

                    // Populate fields using local OCR with clipboard fallback
                    amountStr = amountParsed.ifEmpty { clipboardParsedAmount }
                    receiverName = receiverParsed.ifEmpty { clipboardParsedReceiver }
                    transactionId = txnIdParsed
                    parsedDateMillis = dateParsed
                    selectedCategory = parseCategory(receiverName)

                    // Detect source payment app
                    val appText = localOcrText.ifEmpty { 
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        } catch (e: Exception) { "" }
                    }
                    sourcePaymentApp = when {
                        appText.contains("PhonePe", ignoreCase = true) -> "PhonePe"
                        appText.contains("Google Pay", ignoreCase = true) || appText.contains("G Pay", ignoreCase = true) -> "Google Pay"
                        appText.contains("Paytm", ignoreCase = true) -> "Paytm"
                        else -> ""
                    }

                    if (amountStr.isNotEmpty() || receiverName.isNotEmpty()) {
                        statusMessage = "Offline text extraction successful!"
                    } else {
                        statusMessage = "Could not detect details. You can enter them manually."
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SlipImport", "Bitmap decode failed: ${e.message}")
                statusMessage = "Failed to load payment slip screenshot."
            } finally {
                isLoading = false
            }
        }
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
                                    FirebaseManager.addCustomCategory(trimmed)
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Import Payment Slip", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.inkPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.surfaceCard)
            )
        },
        containerColor = colors.surfaceCard
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = d.space16, vertical = d.space8),
            verticalArrangement = Arrangement.spacedBy(d.space16)
        ) {
            // Receipt Image Card Preview
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(d.radiusLG),
                border = BorderStroke(1.dp, colors.borderWhisper),
                color = colors.surfaceCard,
                shadowElevation = d.cardElevation
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (loadedBitmap != null) {
                        Image(
                            bitmap = loadedBitmap!!.asImageBitmap(),
                            contentDescription = "Receipt Screenshot",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(d.space8)
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(d.space8)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = "Receipt", tint = colors.inkMuted, modifier = Modifier.size(48.dp))
                            Text("Loading receipt...", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(d.space8)
                            ) {
                                CircularProgressIndicator(color = colors.inkPrimary)
                                Text(statusMessage, fontFamily = OutfitFamily, fontSize = 12.sp, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            // Info/Warning Banner when AI Autofill failed or is empty
            if (!isLoading && amountStr.isEmpty() && receiverName.isEmpty()) {
                Surface(
                    color = colors.canvasChalk.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, colors.borderWhisper),
                    shape = RoundedCornerShape(d.radiusSM),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(d.space12),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(d.space12)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Info",
                            tint = colors.inkMuted,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "AI Autofill failed/unavailable. Please enter details manually below.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelMedium,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            // Extracted / Manual Form Fields
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(d.radiusLG),
                border = BorderStroke(1.dp, colors.borderWhisper),
                color = colors.surfaceCard,
                shadowElevation = d.cardElevation
            ) {
                Column(
                    modifier = Modifier.padding(d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space12)
                ) {
                    Text("VERIFY TRANSACTION DETAILS", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.2.sp)

                    // Duplicate Status Warning Banner
                    duplicateStatus?.let { dup ->
                        val bg = if (dup.isExactMatch) Color(0xFFFEE2E2) else Color(0xFFFEF3C7)
                        val border = if (dup.isExactMatch) Color(0xFFEF4444) else Color(0xFFF59E0B)
                        val textCol = if (dup.isExactMatch) Color(0xFF991B1B) else Color(0xFF92400E)
                        
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = d.space4),
                            color = bg,
                            border = BorderStroke(1.dp, border),
                            shape = RoundedCornerShape(d.radiusSM)
                        ) {
                            Row(
                                modifier = Modifier.padding(d.space12),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(d.space12)
                            ) {
                                Icon(
                                    imageVector = if (dup.isExactMatch) Icons.Default.Cancel else Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = border,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = dup.message,
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelMedium,
                                    color = textCol,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Amount Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Amount Paid (\u20b9)", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("0.00", fontFamily = JetBrainsMonoFamily, color = colors.inkMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(d.radiusSM),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.borderWhisper
                            )
                        )
                    }

                    // Receiver Name Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Paid To (Merchant/Person)", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        OutlinedTextField(
                            value = receiverName,
                            onValueChange = { receiverName = it },
                            textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Enter merchant/receiver name", fontFamily = OutfitFamily, color = colors.inkMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(d.radiusSM),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.borderWhisper
                            )
                        )
                    }

                    // Transaction ID Input
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Transaction ID", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        OutlinedTextField(
                            value = transactionId,
                            onValueChange = { transactionId = it },
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("UTI / Txn Ref Number", fontFamily = JetBrainsMonoFamily, color = colors.inkMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(d.radiusSM),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.borderWhisper
                            )
                        )
                    }

                    // Date Input / Picker Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Transaction Date", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        
                        var showDatePicker by remember { mutableStateOf(false) }
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = parsedDateMillis ?: System.currentTimeMillis()
                        )
                        
                        if (showDatePicker) {
                            DatePickerDialog(
                                onDismissRequest = { showDatePicker = false },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            parsedDateMillis = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
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

                        val formatter = remember { java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault()) }
                        val dateStr = formatter.format(java.util.Date(parsedDateMillis ?: System.currentTimeMillis()))

                        Surface(
                            onClick = { showDatePicker = true },
                            shape = RoundedCornerShape(d.radiusSM),
                            color = colors.surfaceCard,
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

                    // Notes / Remarks
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Description / Remarks", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Dinner, Rent, Fuel", fontFamily = OutfitFamily, color = colors.inkMuted) },
                            singleLine = true,
                            shape = RoundedCornerShape(d.radiusSM),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.inkPrimary,
                                unfocusedBorderColor = colors.borderWhisper
                            )
                        )
                    }

                    // Category Selector Chips Row
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("CATEGORY", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.5.sp)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(d.space8),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allCategories) { cat ->
                                val isSelected = selectedCategory.uppercase() == cat.uppercase()
                                Surface(
                                    onClick = { selectedCategory = cat },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    color = if (isSelected) colors.inkPrimary else colors.surfaceCard,
                                    border = if (!isSelected) BorderStroke(1.dp, colors.borderWhisper) else null,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = d.space12)) {
                                        Text(
                                            text = cat,
                                            fontFamily = OutfitFamily,
                                            fontSize = d.textLabelLarge,
                                            color = if (isSelected) colors.canvasChalk else colors.inkMuted
                                        )
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
                    }
                }
            }

            Spacer(modifier = Modifier.height(d.space8))

            // Action Options Row
            Text("WHAT WOULD YOU LIKE TO DO?", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textLabelSmall, color = colors.inkMuted, letterSpacing = 1.2.sp)

            // Button 1: Save as Personal Expense
            Button(
                onClick = {
                    if (duplicateStatus?.isExactMatch == true) {
                        Toast.makeText(context, "Exact duplicate found. Cannot log again.", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    coroutineScope.launch {
                        try {
                            val personalNote = buildString {
                                if (sourcePaymentApp.isNotEmpty()) append("Via $sourcePaymentApp. ")
                                if (transactionId.isNotEmpty()) append("Txn ID: $transactionId")
                            }
                            FirebaseManager.addPersonalExpense(
                                description = receiverName.ifEmpty { remarks },
                                amount = amt,
                                category = selectedCategory,
                                note = personalNote,
                                date = parsedDateMillis ?: System.currentTimeMillis()
                            )
                            Toast.makeText(context, "Logged as Personal Expense!", Toast.LENGTH_SHORT).show()
                            onBack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error saving: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    contentColor = colors.canvasChalk
                ),
                shape = RoundedCornerShape(d.radiusMD)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = "Personal")
                    Text("Log as Personal Expense", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                }
            }

            // Button 2: Split with Someone (Quick/Direct split)
            OutlinedButton(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    FirebaseManager.pendingExpenseAmount = amountStr
                    val desc = receiverName.ifEmpty { remarks }
                    FirebaseManager.pendingExpenseDesc = if (sourcePaymentApp.isNotEmpty()) "$desc (via $sourcePaymentApp)" else desc
                    FirebaseManager.pendingExpenseCategory = selectedCategory
                    FirebaseManager.pendingExpenseDate = parsedDateMillis
                    onNavigateToQuickSplit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight),
                border = BorderStroke(1.dp, colors.borderWhisper),
                shape = RoundedCornerShape(d.radiusMD),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkPrimary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Split", tint = colors.inkPrimary)
                    Text("Split with Someone Directly", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                }
            }

            // Button 3: Add to Shared Group
            OutlinedButton(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (amt == null || amt <= 0.0) {
                        Toast.makeText(context, "Please enter a valid amount", Toast.LENGTH_SHORT).show()
                        return@OutlinedButton
                    }
                    showGroupSelector = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(d.buttonHeight),
                border = BorderStroke(1.dp, colors.borderWhisper),
                shape = RoundedCornerShape(d.radiusMD),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.inkPrimary)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(d.space8)
                ) {
                    Icon(imageVector = Icons.Default.Groups, contentDescription = "Groups", tint = colors.inkPrimary)
                    Text("Split inside Shared Group", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                }
            }

            Spacer(modifier = Modifier.height(d.space16))
        }
    }

    if (showGroupSelector) {
        val activeGroups = groupsState.value
        ModalBottomSheet(
            onDismissRequest = { showGroupSelector = false },
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
                                    showGroupSelector = false
                                    FirebaseManager.pendingExpenseAmount = amountStr
                                    val desc = receiverName.ifEmpty { remarks }
                                    FirebaseManager.pendingExpenseDesc = if (sourcePaymentApp.isNotEmpty()) "$desc (via $sourcePaymentApp)" else desc
                                    FirebaseManager.pendingExpenseCategory = selectedCategory
                                    FirebaseManager.pendingExpenseDate = parsedDateMillis
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

private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String = suspendCancellableCoroutine { continuation ->
    try {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                continuation.resume(visionText.text)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    } catch (e: Exception) {
        continuation.resumeWithException(e)
    }
}

private data class ParsedSlip(
    val amount: String,
    val receiver: String,
    val txnId: String
)

private fun parseOCRText(text: String): ParsedSlip {
    val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    
    var amount = ""
    var receiver = ""
    var txnId = ""
    
    // 1. Parse Amount
    val amountCandidates = mutableListOf<String>()
    for (line in lines) {
        val m = Regex("[₹R][sS]?\\.?\\s*([0-9,]+(?:\\.[0-9]{2})?)").find(line)
        if (m != null) {
            amountCandidates.add(m.groupValues[1].replace(",", ""))
        } else {
            val m2 = Regex("^\\s*[A-Za-z₹]?\\s*([0-9,]+(?:\\.[0-9]{2})?)\\s*$").find(line)
            if (m2 != null) {
                amountCandidates.add(m2.groupValues[1].replace(",", ""))
            }
        }
    }
    
    val cleanAmounts = amountCandidates
        .map { it.replace(",", "") }
        .filter { it.isNotEmpty() && it.length <= 7 }
    
    if (cleanAmounts.isNotEmpty()) {
        val sorted = cleanAmounts.distinct().sortedBy { it.length }
        var foundSuffixMatch = false
        for (i in sorted.indices) {
            val short = sorted[i]
            for (j in (i + 1) until sorted.size) {
                val long = sorted[j]
                if (long.startsWith("7") && long.endsWith(short) && long.length == short.length + 1) {
                    amount = short
                    foundSuffixMatch = true
                    break
                }
            }
            if (foundSuffixMatch) break
        }
        if (!foundSuffixMatch) {
            val first = cleanAmounts.first()
            if (first.startsWith("7") && first.length >= 3) {
                amount = first.substring(1)
            } else {
                amount = first
            }
        }
    }
    
    // 2. Parse Receiver Name
    for (i in lines.indices) {
        val line = lines[i]
        val lineLower = line.lowercase()
        
        // Pattern: "Paid to" (PhonePe style)
        if (lineLower.startsWith("paid to")) {
            val rest = line.substring(7).trim()
            if (rest.length > 2) {
                receiver = if (rest.contains("@")) rest.split("@").first().trim() else rest
            } else if (i + 1 < lines.size) {
                var candidate = lines[i + 1]
                if (candidate.length <= 2 && i + 2 < lines.size) {
                    candidate = lines[i + 2]
                }
                receiver = if (candidate.contains("@")) candidate.split("@").first().trim() else candidate
            }
            break
        }
        
        // Pattern: "To: <Name>" or "To <Name>" (GPay style)
        if (lineLower.startsWith("to:") || lineLower.startsWith("to ")) {
            val name = line.replace(Regex("(?i)^To:?\\s*"), "").trim()
            if (name.isNotEmpty() && !name.contains("@") && !name.equals("pay", ignoreCase = true)) {
                receiver = name
                break
            }
        }
        
        // Pattern: UPI ID line containing bullet/separator
        if (line.contains("@")) {
            val parts = line.split("•", "-", "·")
            if (parts.size > 1) {
                val candidate = parts[0].trim()
                if (candidate.isNotEmpty() && candidate.length > 2 && !candidate.contains("@")) {
                    receiver = candidate
                    break
                }
            } else if (i > 0) {
                val prev = lines[i - 1]
                val prevLower = prev.lowercase()
                if (prevLower != "paid to" && prevLower != "sent to" && prevLower != "debited from" && prevLower != "completed" && prevLower != "successful" && prevLower != "transaction successful" && !prev.startsWith("₹") && !prev.startsWith("Rs") && prev.length > 2) {
                    receiver = prev
                    break
                }
            }
        }
    }
    
    // Fallback: search for first non-numeric name-like line
    if (receiver.isEmpty() || receiver.lowercase().trim() in setOf("sent to", "debited from", "paid to", "from", "to")) {
        receiver = ""
        for (line in lines) {
            val lineLower = line.lowercase().trim()
            if (line.length > 3 && 
                !lineLower.contains("transaction") &&
                !lineLower.contains("successful") &&
                !lineLower.contains("completed") &&
                !lineLower.contains("debited") &&
                !lineLower.contains("balance") &&
                !lineLower.contains("sent to") &&
                !lineLower.contains("paid to") &&
                !lineLower.contains("powered by") &&
                !lineLower.contains("@") &&
                !line.matches(Regex(".*\\d{4,}.*")) &&
                lineLower != "from" &&
                lineLower != "to"
            ) {
                receiver = line
                break
            }
        }
    }
    
    // 3. Parse Transaction ID / UTR
    val utrMatch = Regex("\\b(\\d{12})\\b").find(text)
    if (utrMatch != null) {
        txnId = utrMatch.groupValues[1]
    } else {
        for (i in lines.indices) {
            val line = lines[i]
            val lineLower = line.lowercase()
            val isTxnLabel = lineLower.contains("transaction id") || 
                             lineLower.contains("utr") ||
                             lineLower.contains("ref number") ||
                             lineLower.contains("txn id")
            
            if (isTxnLabel) {
                val words = Regex("\\b([A-Za-z0-9]{8,22})\\b").findAll(line).map { it.groupValues[1] }.toList()
                val filteredWords = words.filter { w ->
                    val wl = w.lowercase()
                    wl != "transaction" && wl != "id" && wl != "google" && wl != "phonepe" && wl != "upi" &&
                    wl != "completed" && wl != "successful" && wl != "ref" && wl != "number" && wl != "utr" &&
                    wl != "from" && wl != "to" && wl != "paid"
                }
                if (filteredWords.isNotEmpty()) {
                    txnId = filteredWords.first()
                    break
                } else if (i + 1 < lines.size) {
                    val nextLine = lines[i + 1]
                    if (nextLine.matches(Regex("^[A-Za-z0-9]{8,22}$"))) {
                        txnId = nextLine
                        break
                    }
                }
            }
        }
    }
    
    if (txnId.isEmpty()) {
        val allIds = Regex("\\b([A-Za-z0-9]{8,22})\\b").findAll(text).map { it.groupValues[1] }.toList()
        val filteredIds = allIds.filter { w ->
            val wl = w.lowercase()
            wl != "transaction" && wl != "id" && wl != "google" && wl != "phonepe" && wl != "upi" &&
            wl != "completed" && wl != "successful" && wl != "ref" && wl != "number" && wl != "utr" &&
            wl != "from" && wl != "to" && wl != "paid" && wl != "delivery"
        }
        if (filteredIds.isNotEmpty()) {
            txnId = filteredIds.first()
        }
    }
    
    return ParsedSlip(amount, receiver, txnId)
}

private fun parseDate(text: String): Long? {
    val dateRegex = Regex("\\b(\\d{1,2})\\s+([A-Za-z]{3})\\s+(\\d{4})\\b")
    val match = dateRegex.find(text)
    if (match != null) {
        try {
            val day = match.groupValues[1].toInt()
            val monthStr = match.groupValues[2].lowercase()
            val year = match.groupValues[3].toInt()
            
            val month = when (monthStr) {
                "jan" -> 0
                "feb" -> 1
                "mar" -> 2
                "apr" -> 3
                "may" -> 4
                "jun" -> 5
                "jul" -> 6
                "aug" -> 7
                "sep" -> 8
                "oct" -> 9
                "nov" -> 10
                "dec" -> 11
                else -> -1
            }
            
            if (month != -1) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return cal.timeInMillis
            }
        } catch (e: Exception) {
            android.util.Log.e("SlipImport", "Date parsing failed: ${e.message}")
        }
    }
    return null
}

private fun parseCategory(receiverName: String): String {
    val nameLower = receiverName.lowercase()
    return when {
        nameLower.contains("travel") || nameLower.contains("cab") || nameLower.contains("uber") || nameLower.contains("ola") || nameLower.contains("railway") || nameLower.contains("flight") || nameLower.contains("metro") || nameLower.contains("auto") -> "Travel"
        nameLower.contains("stay") || nameLower.contains("hotel") || nameLower.contains("room") || nameLower.contains("pg") || nameLower.contains("hostel") || nameLower.contains("airbnb") -> "Stay"
        nameLower.contains("food") || nameLower.contains("restaurant") || nameLower.contains("cafe") || nameLower.contains("swiggy") || nameLower.contains("zomato") || nameLower.contains("dhaba") || nameLower.contains("canteen") || nameLower.contains("tea") || nameLower.contains("bakery") || nameLower.contains("juice") -> "Food"
        nameLower.contains("amazon") || nameLower.contains("flipkart") || nameLower.contains("myntra") || nameLower.contains("shop") || nameLower.contains("store") || nameLower.contains("mart") || nameLower.contains("mall") || nameLower.contains("clothing") || nameLower.contains("supermarket") || nameLower.contains("grocer") || nameLower.contains("zepto") || nameLower.contains("blinkit") -> "Other"
        else -> "Other"
    }
}

data class DuplicateResult(val isExactMatch: Boolean, val message: String)


