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
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
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

    val imageUri = remember(imageUriStr) {
        val decoded = Uri.decode(imageUriStr)
        Uri.parse(decoded)
    }

    // State Variables
    var amountStr by remember { mutableStateOf("") }
    var receiverName by remember { mutableStateOf("") }
    var transactionId by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    var parsedDateMillis by remember { mutableStateOf<Long?>(null) }
    var isDateExtracted by remember { mutableStateOf(true) }
    var isIncomingPayment by remember { mutableStateOf(false) }
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
    var showCropDialog by remember { mutableStateOf(false) }

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
                var bmp: Bitmap? = null
                try {
                    context.contentResolver.openInputStream(imageUri)?.use { stream ->
                        bmp = BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SlipImport", "ContentResolver openInputStream failed: ${e.message}")
                }
                if (bmp == null && imageUri.scheme == "file" && imageUri.path != null) {
                    try {
                        bmp = BitmapFactory.decodeFile(imageUri.path)
                    } catch (e: Exception) {
                        android.util.Log.e("SlipImport", "BitmapFactory decodeFile failed: ${e.message}")
                    }
                }

                val finalBmp = bmp
                if (finalBmp != null) {
                    loadedBitmap = finalBmp

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

                    // Step 2: Run Local ML Kit OCR & Embedded Barcode Scan (100% offline, free, instant)
                    statusMessage = "Extracting text & scanning QR from image..."
                    var localOcrText = ""
                    try {
                        localOcrText = recognizeTextFromBitmap(finalBmp)
                    } catch (e: Exception) {
                        android.util.Log.e("SlipImport", "Local OCR failed: ${e.message}")
                    }

                    var qrPayload = ""
                    try {
                        qrPayload = scanBarcodesFromBitmap(finalBmp)
                    } catch (e: Exception) {
                        android.util.Log.e("SlipImport", "Embedded QR scan failed: ${e.message}")
                    }

                    var amountParsed = ""
                    var receiverParsed = ""
                    var txnIdParsed = ""
                    var noteParsed = ""
                    var incomingParsed = false
                    var dateParsed: Long? = null
                    var dateFound = false

                    if (localOcrText.isNotEmpty()) {
                        val parsed = parseOCRText(localOcrText)
                        amountParsed = parsed.amount
                        receiverParsed = parsed.receiver
                        txnIdParsed = parsed.txnId
                        noteParsed = parsed.paymentNote
                        incomingParsed = parsed.isIncoming
                        val (dMillis, dFound) = parseDate(localOcrText)
                        dateParsed = dMillis
                        dateFound = dFound
                    }

                    // Populate fields using local OCR with clipboard fallback
                    amountStr = amountParsed.ifEmpty { clipboardParsedAmount }
                    receiverName = receiverParsed.ifEmpty { clipboardParsedReceiver }
                    transactionId = txnIdParsed
                    parsedDateMillis = dateParsed ?: System.currentTimeMillis()
                    isDateExtracted = dateFound
                    isIncomingPayment = incomingParsed
                    selectedCategory = parseCategory(receiverName)
                    if (remarks.isEmpty()) {
                        remarks = noteParsed.ifEmpty { suggestSmartTitle(receiverName, selectedCategory) }
                    }

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
                        Surface(
                            onClick = { showCropDialog = true },
                            shape = RoundedCornerShape(d.radiusFull),
                            color = colors.inkPrimary,
                            contentColor = colors.canvasChalk,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(d.space12)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = d.space12, vertical = d.space4 + 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit & Crop", modifier = Modifier.size(16.dp))
                                Text("Edit & Crop", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textLabelSmall)
                            }
                        }
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

            if (showCropDialog) {
                com.splitsmith.app.ui.components.attachments.ReceiptEditorModal(
                    imageUri = imageUri,
                    onDismiss = { showCropDialog = false },
                    onEditedImageSaved = { editedUri ->
                        showCropDialog = false
                        coroutineScope.launch(Dispatchers.IO) {
                            isLoading = true
                            statusMessage = "Extracting text from enhanced receipt..."
                            try {
                                val stream = context.contentResolver.openInputStream(editedUri)
                                val bmp = BitmapFactory.decodeStream(stream)
                                stream?.close()
                                if (bmp != null) {
                                    loadedBitmap = bmp
                                    val localOcrText = recognizeTextFromBitmap(bmp)
                                    if (localOcrText.isNotEmpty()) {
                                        val parsed = parseOCRText(localOcrText)
                                        withContext(Dispatchers.Main) {
                                            if (parsed.amount.isNotEmpty()) amountStr = parsed.amount
                                            if (parsed.receiver.isNotEmpty()) receiverName = parsed.receiver
                                            if (parsed.txnId.isNotEmpty()) transactionId = parsed.txnId
                                            if (parsed.paymentNote.isNotEmpty()) remarks = parsed.paymentNote
                                            selectedCategory = parseCategory(receiverName)
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                withContext(Dispatchers.Main) { isLoading = false }
                            }
                        }
                    }
                )
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                            Text("Transaction Date", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                            if (!isDateExtracted) {
                                Text("• Today (Estimated)", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                            }
                        }
                        
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

                    // Entry Title / Description
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Entry Title (e.g. Fish and Chips, Petrol, Dinner)", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = colors.inkMuted)
                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. Fish and Chips, Dinner, Vegetables, Petrol", fontFamily = OutfitFamily, color = colors.inkMuted) },
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

            // Google Drive Backup Toggle Card
            var uploadToDrive by remember(profileState.value) { mutableStateOf(profileState.value?.driveSyncEnabled ?: true) }
            val hasDrivePermission = remember(context) { com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(context) }
            val driveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) {
                uploadToDrive = true
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(d.radiusLG),
                border = BorderStroke(1.dp, colors.borderWhisper),
                color = colors.surfaceCard
            ) {
                Column(modifier = Modifier.padding(d.space16)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Backup Receipt to Google Drive", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textBodyMedium, color = colors.inkPrimary)
                            Text("Save a copy of this slip to your SplitSmith Drive folder", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = colors.inkMuted)
                        }
                        Switch(
                            checked = uploadToDrive,
                            onCheckedChange = { checked ->
                                uploadToDrive = checked
                                if (checked && !hasDrivePermission) {
                                    com.splitsmith.app.data.GoogleDriveManager.requestDrivePermission(driveLauncher, context)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.canvasChalk,
                                checkedTrackColor = colors.inkPrimary,
                                uncheckedThumbColor = colors.inkMuted,
                                uncheckedTrackColor = colors.borderWhisper
                            )
                        )
                    }

                    if (uploadToDrive && !hasDrivePermission) {
                        Spacer(modifier = Modifier.height(d.space8))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFEF3C7),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B)),
                            shape = RoundedCornerShape(d.radiusSM)
                        ) {
                            Row(
                                modifier = Modifier.padding(d.space12),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Google Drive Access Required",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textLabelMedium,
                                    color = Color(0xFF92400E),
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        com.splitsmith.app.data.GoogleDriveManager.requestDrivePermission(driveLauncher, context)
                                    },
                                    shape = RoundedCornerShape(d.radiusSM),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706), contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Grant Access", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            val entryTitle = remarks.ifEmpty { suggestSmartTitle(receiverName, selectedCategory) }
                            val personalNote = buildString {
                                if (receiverName.isNotEmpty()) append("Paid to $receiverName. ")
                                if (sourcePaymentApp.isNotEmpty()) append("Via $sourcePaymentApp. ")
                                if (transactionId.isNotEmpty()) append("Txn ID: $transactionId")
                            }.trim()

                            val uid = FirebaseManager.currentUserId ?: return@launch
                            val expenseRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(uid).collection("personal_expenses").document()

                            val expId = expenseRef.id

                            FirebaseManager.addPersonalExpense(
                                description = entryTitle,
                                amount = amt,
                                category = selectedCategory,
                                note = personalNote,
                                date = parsedDateMillis ?: System.currentTimeMillis()
                            )

                            Toast.makeText(context, "Logged as Personal Expense!", Toast.LENGTH_SHORT).show()

                            // Asynchronous non-blocking background Drive upload / queueing
                            val targetUri = imageUri
                            if (uploadToDrive && targetUri != null) {
                                val applicationContext = context.applicationContext
                                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                                    try {
                                        val driveResult = com.splitsmith.app.data.GoogleDriveManager.uploadAttachment(
                                            context = applicationContext,
                                            inputUri = targetUri,
                                            folderCategoryName = "Personal Expenses",
                                            dateMillis = parsedDateMillis ?: System.currentTimeMillis(),
                                            expenseId = expId
                                        )
                                        if (driveResult != null) {
                                            FirebaseManager.attachDriveFileToPersonalExpense(
                                                expenseId = expId,
                                                driveFileId = driveResult.fileId,
                                                webUrl = driveResult.webViewLink
                                            )
                                        } else {
                                            com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                                context = applicationContext,
                                                localUri = targetUri,
                                                folderCategoryName = "Personal Expenses",
                                                dateMillis = parsedDateMillis ?: System.currentTimeMillis(),
                                                expenseId = expId,
                                                isPersonal = true
                                            )
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                            context = applicationContext,
                                            localUri = targetUri,
                                            folderCategoryName = "Personal Expenses",
                                            dateMillis = parsedDateMillis ?: System.currentTimeMillis(),
                                            expenseId = expId,
                                            isPersonal = true
                                        )
                                    }
                                }
                            }

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
                    val entryTitle = remarks.ifEmpty { suggestSmartTitle(receiverName, selectedCategory) }
                    FirebaseManager.pendingExpenseAmount = amountStr
                    FirebaseManager.pendingExpenseDesc = if (receiverName.isNotEmpty()) "$entryTitle ($receiverName)" else entryTitle
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
                                    val entryTitle = remarks.ifEmpty { suggestSmartTitle(receiverName, selectedCategory) }
                                    FirebaseManager.pendingExpenseAmount = amountStr
                                    FirebaseManager.pendingExpenseDesc = if (receiverName.isNotEmpty()) "$entryTitle ($receiverName)" else entryTitle
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

private fun scanBarcodesFromBitmap(bitmap: Bitmap): String {
    return try {
        val intArray = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(intArray, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = com.google.zxing.RGBLuminanceSource(bitmap.width, bitmap.height, intArray)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        val reader = com.google.zxing.MultiFormatReader()
        val result = reader.decode(binaryBitmap)
        result.text ?: ""
    } catch (e: Exception) {
        ""
    }
}

private data class ParsedSlip(
    val amount: String,
    val receiver: String,
    val txnId: String,
    val paymentNote: String = "",
    val isIncoming: Boolean = false
)

private val INVOICE_NOISE_KEYWORDS = setOf(
    "subtotal", "grand total", "total amount", "net amount", "net payable", "amount paid",
    "item total", "cgst", "sgst", "igst", "vat", "tax", "round off", "discount",
    "delivery charge", "delivery fee", "platform fee", "convenience fee", "packaging charge",
    "service tax", "tip", "tipping", "mrp", "savings", "cashback", "qty", "quantity",
    "hsn", "sac", "rate", "description", "amount(rs)", "amount (rs)", "sl no", "s.no",
    "invoice date", "due date", "po no", "po number", "eway bill", "cin", "pan", "gstin",
    "fssai", "fssai lic no", "lic no", "tax invoice", "bill of supply", "retail invoice",
    "cash memo", "receipt", "acknowledgement", "original for recipient", "duplicate for transporter",
    "triplicate for supplier", "copy", "terms and conditions", "terms & conditions", "thank you", "visit again"
)

private fun cleanReceiverName(raw: String): String {
    if (raw.isBlank()) return ""
    val rawTrimmed = raw.trim()
    if (rawTrimmed.length <= 2) return ""
    val rawLower = rawTrimmed.lowercase()

    // 1. REJECT SENDER / BANK / CREDIT / DEBIT LINES
    if (rawLower.startsWith("from:") || rawLower.startsWith("from ") || 
        rawLower.startsWith("debited from") || rawLower.startsWith("sent from") ||
        rawLower.startsWith("credited to") || rawLower.startsWith("credited from")) {
        return ""
    }
    if (rawLower.contains("federal bank") || rawLower.contains("punjab national bank") || rawLower.contains("yesbank") || rawLower.contains("hdfc") || rawLower.contains("icici") || rawLower.contains("state bank") || rawLower.contains("fed new") || rawLower.contains("axis bank") || rawLower.contains("kotak")) {
        return ""
    }

    // 2. REJECT DATE & TIMESTAMP LINES
    val isTimestamp = Regex("(?i)\\b(?:am|pm|jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec|202\\d|at \\d{1,2}:\\d{2})\\b").containsMatchIn(rawLower) ||
                      Regex("(?i)\\b\\d{1,2}:\\d{2}\\s*(?:am|pm)?\\b").containsMatchIn(rawLower)
    if (isTimestamp) return ""

    // 3. REJECT UPI VPA / HANDLES / EMAIL ADDRESSES
    if (rawLower.contains("@") || rawLower.endsWith(".upi") || rawLower.endsWith(".paytm") || rawLower.endsWith(".ybl") || rawLower.endsWith(".okicici")) {
        return ""
    }

    // 4. REJECT PURE NUMERIC / PHONE / TXN ID / UTR LINES (Must contain text, not just numbers)
    if (rawLower.matches(Regex("^[0-9\\s\\-\\.:/#()]+$")) || rawLower.matches(Regex("^[a-z0-9]{12,35}$")) || !rawTrimmed.matches(Regex(".*[A-Za-z]{2,}.*"))) {
        return ""
    }

    // 5. CLEAN STATUS BANNERS & PREFIXES
    var cleaned = rawTrimmed.replace("\"", "")
    cleaned = cleaned.replace(Regex("(?i)^(transaction|payment|transfer|money|paid|sent)\\s+(successful|completed|success|to|from)\\b"), "")
    cleaned = cleaned.replace(Regex("(?i)^(paid to|sent to|payment to|pay to|to:|to\\b|from:)\\s*"), "")
    cleaned = cleaned.replace(Regex("(?i)banking name:?\\s*"), "")
    cleaned = cleaned.replace(Regex("(?i)merchant name:?\\s*"), "")
    cleaned = cleaned.replace(Regex("(?i)upi id:?\\s*"), "")
    cleaned = cleaned.trim(' ', ':', '-', '•', '·')

    val lower = cleaned.lowercase()

    // 6. REJECT BOILERPLATE UI LABELS, INVOICE NOISE WORDS, AND SYSTEM STRINGS
    if (cleaned.length <= 2 ||
        !cleaned.matches(Regex(".*[A-Za-z]{2,}.*")) ||
        INVOICE_NOISE_KEYWORDS.any { lower.contains(it) } ||
        lower.contains("transaction") ||
        lower.contains("successful") ||
        lower.contains("completed") ||
        lower.contains("transfer details") ||
        lower.contains("transaction details") ||
        lower.contains("payment details") ||
        lower.contains("invoice to") ||
        lower.contains("billed to") ||
        lower.contains("order id") ||
        lower.contains("debited") ||
        lower.contains("credited") ||
        lower.contains("balance") ||
        lower.contains("powered by") ||
        lower.contains("view details") ||
        lower.contains("share receipt") ||
        lower.contains("check balance") ||
        lower.contains("100% secure") ||
        lower in setOf("paid to", "sent to", "paid", "sent", "to", "from", "success", "upi", "gpay", "phonepe", "paytm", "cred", "bhim", "amazon pay")
    ) {
        return ""
    }

    return cleaned
}

private fun isPhoneOrSystemId(lineLower: String, numStr: String): Boolean {
    val cleanDigits = numStr.filter { it.isDigit() }
    
    // 1. Indian 10-digit mobile number starting with 6,7,8,9
    if (cleanDigits.length == 10 && cleanDigits[0] in '6'..'9') return true
    if (cleanDigits.length == 11 && cleanDigits.startsWith("0") && cleanDigits[1] in '6'..'9') return true
    if (cleanDigits.length == 12 && cleanDigits.startsWith("91") && cleanDigits[2] in '6'..'9') return true

    // 2. PIN Codes (6 digits starting with 1-8 without decimal point)
    if (!numStr.contains(".") && cleanDigits.length == 6 && cleanDigits[0] in '1'..'8') return true

    // 3. Phone / Mobile / Contact / Account / GSTIN / FSSAI / PIN / HSN / Ref Labels
    if (lineLower.contains("ph:") || lineLower.contains("phone") || lineLower.contains("mob:") || lineLower.contains("mobile") ||
        lineLower.contains("contact") || lineLower.contains("tel:") || lineLower.contains("gstin") || lineLower.contains("fssai") ||
        lineLower.contains("hsn") || lineLower.contains("sac") || lineLower.contains("pin code") || lineLower.contains("pincode") ||
        lineLower.contains("account") || lineLower.contains("a/c") || lineLower.contains("utr") || lineLower.contains("ref no") ||
        lineLower.contains("txn id") || lineLower.contains("order id")) {
        return true
    }

    // 4. Implausible total amount (> 500,000 INR) without explicit currency symbol
    val valD = numStr.toDoubleOrNull() ?: 0.0
    if (valD > 500000.0 && !lineLower.contains("₹") && !lineLower.contains("inr") && !lineLower.contains("total")) {
        return true
    }

    return false
}

private fun parseOCRText(text: String): ParsedSlip {
    val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    val lowerText = text.lowercase()
    
    var amount = ""
    var receiver = ""
    var txnId = ""

    data class AmountCandidate(val value: String, val score: Int)
    val candidates = mutableListOf<AmountCandidate>()
    
    // 1. Score all potential numeric values in receipt lines
    for (line in lines) {
        val lineLower = line.lowercase()
        // Filter out invoice noise lines (subtotal, qty, taxes, item lines)
        if (INVOICE_NOISE_KEYWORDS.any { kw -> lineLower.contains(kw) && !lineLower.contains("grand total") && !lineLower.contains("total amount") && !lineLower.contains("amount paid") && !lineLower.contains("net amount") }) {
            if (lineLower.contains("subtotal") || lineLower.contains("qty") || lineLower.contains("nozzle") || lineLower.contains("item") || lineLower.contains("cgst") || lineLower.contains("sgst") || lineLower.contains("igst") || lineLower.contains("vat")) {
                continue
            }
        }

        // Strict numeric pattern (clean currency symbols, commas, and trailing /-)
        val rawTokens = Regex("(?:₹|Rs\\.?|INR)?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s*(?:/\\-)?").findAll(line)
        for (match in rawTokens) {
            val rawNum = match.groupValues[1].replace(",", "").replace(Regex("^0+(?=\\d)"), "")
            // Amount MUST be strictly numeric
            if (!rawNum.matches(Regex("^\\d{1,6}(?:\\.\\d{1,2})?$"))) continue
            val valD = rawNum.toDoubleOrNull() ?: 0.0
            if (valD <= 0.0) continue

            if (isPhoneOrSystemId(lineLower, rawNum)) continue

            var score = 0
            val isExplicitTotal = lineLower.contains("total") || lineLower.contains("amount paid") || lineLower.contains("net amount") || lineLower.contains("amount(rs)") || lineLower.contains("net payable")
            val hasCurrencySymbol = lineLower.contains("₹") || lineLower.contains("inr") || lineLower.contains("rs.") || lineLower.contains("rs ")
            val hasDecimals = rawNum.contains(".")

            if (isExplicitTotal) score += 100
            if (hasCurrencySymbol) score += 60
            if (hasDecimals) score += 40
            if (valD >= 10.0 && valD <= 500000.0) score += 20

            if (score >= 60) {
                candidates.add(AmountCandidate(rawNum, score))
            }
        }
    }

    val bestCandidate = candidates.maxByOrNull { it.score }
    if (bestCandidate != null && bestCandidate.score >= 60) {
        amount = bestCandidate.value
    }

    // Format amount cleanly (remove trailing .00 if integer)
    if (amount.endsWith(".00")) {
        amount = amount.dropLast(3)
    }
    
    // 2. Parse Receiver Name (Merchant / Vendor)
    for (i in lines.indices) {
        val line = lines[i]
        val lineLower = line.lowercase().trim()

        if (lineLower.startsWith("from:") || lineLower.startsWith("from ") || lineLower.startsWith("debited from")) {
            continue
        }

        // Pattern 0: Tax Invoice "Sold By: <Merchant>", "Billed By: <Merchant>", "Vendor: <Merchant>", "Seller: <Merchant>"
        if (lineLower.startsWith("sold by") || lineLower.startsWith("billed by") || lineLower.startsWith("vendor") || lineLower.startsWith("seller")) {
            val rest = line.substringAfter(":").trim()
            var candidate = cleanReceiverName(rest)
            if (candidate.isEmpty() && i + 1 < lines.size) {
                candidate = cleanReceiverName(lines[i + 1])
            }
            if (candidate.isNotEmpty() && !candidate.contains("invoice to", ignoreCase = true)) {
                receiver = candidate
                break
            }
        }
        if (lineLower.contains("bistro") || lineLower.contains("blinkit foods")) {
            receiver = "Blinkit Foods Limited (Bistro)"
            break
        }

        // Pattern A: Paytm Quotes "Pay To <Name>"
        val payToMatch = Regex("(?i)Pay\\s+To\\s+([A-Za-z0-9\\s]{2,35})").find(line)
        if (payToMatch != null) {
            val candidate = cleanReceiverName(payToMatch.groupValues[1])
            if (candidate.isNotEmpty()) {
                receiver = candidate
                break
            }
        }

        // Pattern B: Explicit labels "Banking Name: <Merchant>" or "Merchant Name: <Name>"
        if (lineLower.startsWith("banking name") || lineLower.startsWith("merchant name") || lineLower.startsWith("receiver")) {
            val rest = line.substringAfter(":").trim()
            val candidate = cleanReceiverName(rest)
            if (candidate.isNotEmpty()) {
                receiver = candidate
                break
            } else if (i + 1 < lines.size) {
                val nextCandidate = cleanReceiverName(lines[i + 1])
                if (nextCandidate.isNotEmpty()) {
                    receiver = nextCandidate
                    break
                }
            }
        }

        // Pattern C: "Paid to", "Sent to", "Payment to"
        if (lineLower.startsWith("paid to") || lineLower.startsWith("sent to") || lineLower.startsWith("payment to")) {
            val rest = cleanReceiverName(line)
            if (rest.isNotEmpty()) {
                receiver = rest
                break
            }
            for (j in 1..3) {
                if (i + j < lines.size) {
                    val candidateLine = lines[i + j]
                    val candidate = cleanReceiverName(candidateLine)
                    if (candidate.isNotEmpty() && !candidateLine.contains("@") && !candidateLine.startsWith("₹") && !candidateLine.startsWith("Rs") && !candidateLine.startsWith("INR")) {
                        receiver = candidate
                        break
                    }
                }
            }
            if (receiver.isNotEmpty()) break
        }

        // Pattern D: "To: <Name>" or "To <Name>"
        if (lineLower.startsWith("to:") || lineLower.startsWith("to ")) {
            val candidate = cleanReceiverName(line)
            if (candidate.isNotEmpty() && !line.contains("@") && !candidate.equals("pay", ignoreCase = true)) {
                receiver = candidate
                break
            }
        }

        // Pattern E: UPI ID line containing bullet/separator
        if (line.contains("@")) {
            val parts = line.split("•", "-", "·")
            if (parts.size > 1) {
                val candidate = cleanReceiverName(parts[0])
                if (candidate.isNotEmpty()) {
                    receiver = candidate
                    break
                }
            } else if (i > 0) {
                val candidate = cleanReceiverName(lines[i - 1])
                if (candidate.isNotEmpty()) {
                    receiver = candidate
                    break
                }
            }
        }
    }

    // Fallback: search for first valid non-boilerplate merchant name line
    if (receiver.isEmpty()) {
        for (line in lines) {
            val lineLower = line.lowercase()
            if (lineLower.startsWith("from") || lineLower.contains("bank") || lineLower.contains("invoice to")) continue
            val candidate = cleanReceiverName(line)
            if (candidate.length > 2 && candidate.matches(Regex(".*[A-Za-z]{2,}.*")) && !line.contains("@") && !line.matches(Regex(".*\\d{4,}.*")) && !line.startsWith("₹") && !line.startsWith("Rs") && !line.startsWith("INR")) {
                receiver = candidate
                break
            }
        }
    }

    receiver = cleanReceiverName(receiver)
    
    // 3. Parse Transaction ID / UTR / Ref No (Min length 8, Must contain digits, Cannot be plain text)
    val invoiceMatch = Regex("(?i)(?:Invoice\\s*No|Inv\\s*No|Order\\s*Id|Ref\\s*No)[:\\-\\s]*([A-Za-z0-9\\-/]{8,35})").find(text)
    if (invoiceMatch != null) {
        val candidate = invoiceMatch.groupValues[1].trim()
        if (candidate.length >= 8 && candidate.matches(Regex(".*[0-9].*"))) {
            txnId = candidate
        }
    }
    
    if (txnId.isEmpty()) {
        val utrMatch = Regex("\\b(\\d{12,16})\\b").find(text)
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
                    val words = Regex("\\b([A-Za-z0-9\\-/]{8,35})\\b").findAll(line).map { it.groupValues[1] }.toList()
                    val filteredWords = words.filter { w ->
                        val wl = w.lowercase()
                        w.length >= 8 && w.matches(Regex(".*[0-9].*")) &&
                        wl != "transaction" && wl != "successful" && wl != "completed" && wl != "payment" && wl != "details"
                    }
                    if (filteredWords.isNotEmpty()) {
                        txnId = filteredWords.first()
                        break
                    } else if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.length in 8..35 && nextLine.matches(Regex("^[A-Za-z0-9\\-/]+$")) && nextLine.matches(Regex(".*[0-9].*"))) {
                            txnId = nextLine
                            break
                        }
                    }
                }
            }
        }
    }
    
    if (txnId.isEmpty()) {
        val allIds = Regex("\\b([A-Za-z0-9]{8,35})\\b").findAll(text).map { it.groupValues[1] }.toList()
        val filteredIds = allIds.filter { w ->
            val wl = w.lowercase()
            w.length >= 8 && w.matches(Regex(".*[0-9].*")) &&
            wl != "transaction" && wl != "successful" && wl != "completed" && wl != "payment" && wl != "details" && wl != "delivery"
        }
        if (filteredIds.isNotEmpty()) {
            txnId = filteredIds.first()
        }
    }
    
    // 4. Parse Payment Note & Direction
    var paymentNote = ""
    val isIncoming = lowerText.contains("received from") || 
                     lowerText.contains("money received") || 
                     lowerText.contains("credited to") || 
                     lowerText.contains("credit from")

    val noteMatch = Regex("(?i)(?:note|message|remarks|for):?\\s*([A-Za-z0-9\\s]{2,40})").find(text)
    if (noteMatch != null) {
        paymentNote = noteMatch.groupValues[1].trim()
    } else {
        for (i in 0 until lines.size - 2) {
            val line = lines[i]
            val nextLine = lines[i + 1]
            val statusLine = lines[i + 2].lowercase()
            
            val isAmount = line.contains("₹") || line.contains("Rs") || line.matches(Regex(".*\\d{2,}.*"))
            val isStatus = statusLine.contains("completed") || statusLine.contains("successful") || statusLine.contains("paid")
            
            if (isAmount && isStatus && nextLine.length in 2..40) {
                val candidateLower = nextLine.lowercase()
                if (!candidateLower.contains("completed") && !candidateLower.contains("successful") && !candidateLower.contains("paid") && !candidateLower.contains("bank") && !candidateLower.contains("upi")) {
                    paymentNote = nextLine.trim()
                    break
                }
            }
        }
    }
    
    return ParsedSlip(amount, receiver, txnId, paymentNote, isIncoming)
}

private fun parseDate(text: String): Pair<Long?, Boolean> {
    // ISO Date format: 2026-07-25 or 2026/07/25
    val isoRegex = Regex("\\b(202\\d)[-/\\.]([01]\\d)[-/\\.]([0-3]\\d)\\b")
    val matchIso = isoRegex.find(text)
    if (matchIso != null) {
        try {
            val year = matchIso.groupValues[1].toInt()
            val month = matchIso.groupValues[2].toInt() - 1
            val day = matchIso.groupValues[3].toInt()
            if (month in 0..11 && day in 1..31) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return Pair(cal.timeInMillis, true)
            }
        } catch (e: Exception) {}
    }

    val dateRegex = Regex("(?i)\\b(\\d{1,2})[\\s,/\\.\\-]+([A-Za-z]{3,9})[\\s,/\\.\\-]+(\\d{2,4})\\b")
    val match = dateRegex.find(text)
    if (match != null) {
        try {
            val day = match.groupValues[1].toInt()
            val monthStr = match.groupValues[2].lowercase().take(3)
            var year = match.groupValues[3].toInt()
            if (year < 100) year += 2000

            val month = parseMonthIndex(monthStr)
            if (month != -1) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return Pair(cal.timeInMillis, true)
            }
        } catch (e: Exception) {}
    }

    val noYearRegex = Regex("(?i)\\b(\\d{1,2})[\\s,/\\.\\-]+([A-Za-z]{3,9})\\b")
    val match2 = noYearRegex.find(text)
    if (match2 != null) {
        try {
            val day = match2.groupValues[1].toInt()
            val monthStr = match2.groupValues[2].lowercase().take(3)
            val month = parseMonthIndex(monthStr)
            if (month != -1) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return Pair(cal.timeInMillis, true)
            }
        } catch (e: Exception) {}
    }

    val numRegex = Regex("\\b(\\d{1,2})[/\\.\\-](\\d{1,2})[/\\.\\-](\\d{2,4})\\b")
    val match3 = numRegex.find(text)
    if (match3 != null) {
        try {
            val day = match3.groupValues[1].toInt()
            val month = match3.groupValues[2].toInt() - 1
            var year = match3.groupValues[3].toInt()
            if (year < 100) year += 2000
            if (month in 0..11 && day in 1..31) {
                val cal = java.util.Calendar.getInstance()
                cal.set(java.util.Calendar.YEAR, year)
                cal.set(java.util.Calendar.MONTH, month)
                cal.set(java.util.Calendar.DAY_OF_MONTH, day)
                cal.set(java.util.Calendar.HOUR_OF_DAY, 12)
                cal.set(java.util.Calendar.MINUTE, 0)
                cal.set(java.util.Calendar.SECOND, 0)
                cal.set(java.util.Calendar.MILLISECOND, 0)
                return Pair(cal.timeInMillis, true)
            }
        } catch (e: Exception) {}
    }

    return Pair(System.currentTimeMillis(), false)
}

private fun parseMonthIndex(m: String): Int {
    return when (m.lowercase().take(3)) {
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

private fun suggestSmartTitle(receiverName: String, category: String): String {
    val lower = receiverName.lowercase()
    return when {
        lower.contains("amazon") || lower.contains("pod") || lower.contains("delivery") -> "Amazon Delivery"
        lower.contains("printer") || lower.contains("prininter") || lower.contains("print") || lower.contains("press") || lower.contains("xerox") -> "Printing & Stationery"
        lower.contains("fish") -> "Fish & Chips"
        lower.contains("coffee") || lower.contains("starbucks") || lower.contains("cafe") || lower.contains("tea") || lower.contains("chai") -> "Coffee & Snacks"
        lower.contains("swiggy") || lower.contains("zomato") || lower.contains("restaurant") || lower.contains("dhaba") || lower.contains("canteen") || lower.contains("diner") || lower.contains("bistro") || lower.contains("food") -> "Dinner / Meal"
        lower.contains("petrol") || lower.contains("fuel") || lower.contains("shell") || lower.contains("hp ") || lower.contains("iocl") || lower.contains("bpcl") -> "Petrol"
        lower.contains("mart") || lower.contains("fresh") || lower.contains("grocer") || lower.contains("zepto") || lower.contains("blinkit") || lower.contains("bigbasket") || lower.contains("veg") -> "Vegetables & Groceries"
        lower.contains("uber") || lower.contains("ola") || lower.contains("rapido") || lower.contains("cab") || lower.contains("auto") || lower.contains("metro") || lower.contains("rail") -> "Cab / Travel"
        lower.contains("flipkart") || lower.contains("myntra") -> "Online Shopping"
        category.isNotEmpty() && category != "Other" -> category
        receiverName.isNotEmpty() -> "Transfer to $receiverName"
        else -> "Payment Slip"
    }
}

@Composable
fun ImageCropDialog(
    bitmap: Bitmap,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val d = LocalDimens.current
    val colors = LocalSplitColors.current

    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.canvasChalk,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(d.space8)
            ) {
                Icon(imageVector = Icons.Default.Crop, contentDescription = "Crop", tint = colors.inkPrimary)
                Text("Crop Receipt Image", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = colors.inkPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                verticalArrangement = Arrangement.spacedBy(d.space12)
            ) {
                Text(
                    text = "Adjust crop bounds to isolate receipt details & remove background noise:",
                    fontFamily = OutfitFamily,
                    fontSize = d.textLabelMedium,
                    color = colors.inkMuted
                )

                // Image Preview with Crop Border
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(d.radiusMD))
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(8.dp)
                    )
                }

                // Crop Sliders
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Vertical Crop (Top / Bottom)", fontFamily = OutfitFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.inkMuted)
                    RangeSlider(
                        value = cropTop..cropBottom,
                        onValueChange = { range ->
                            cropTop = range.start.coerceIn(0f, cropBottom - 0.1f)
                            cropBottom = range.endInclusive.coerceAtLeast(cropTop + 0.1f)
                        },
                        colors = SliderDefaults.colors(thumbColor = colors.inkPrimary, activeTrackColor = colors.inkPrimary)
                    )

                    Text("Horizontal Crop (Left / Right)", fontFamily = OutfitFamily, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.inkMuted)
                    RangeSlider(
                        value = cropLeft..cropRight,
                        onValueChange = { range ->
                            cropLeft = range.start.coerceIn(0f, cropRight - 0.1f)
                            cropRight = range.endInclusive.coerceAtLeast(cropLeft + 0.1f)
                        },
                        colors = SliderDefaults.colors(thumbColor = colors.inkPrimary, activeTrackColor = colors.inkPrimary)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    try {
                        val bmpWidth = bitmap.width
                        val bmpHeight = bitmap.height
                        val x = (cropLeft * bmpWidth).toInt().coerceIn(0, bmpWidth - 1)
                        val y = (cropTop * bmpHeight).toInt().coerceIn(0, bmpHeight - 1)
                        val width = ((cropRight - cropLeft) * bmpWidth).toInt().coerceIn(1, bmpWidth - x)
                        val height = ((cropBottom - cropTop) * bmpHeight).toInt().coerceIn(1, bmpHeight - y)

                        val cropped = Bitmap.createBitmap(bitmap, x, y, width, height)
                        onCropConfirmed(cropped)
                    } catch (e: Exception) {
                        onCropConfirmed(bitmap)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.inkPrimary, contentColor = colors.canvasChalk),
                shape = RoundedCornerShape(d.radiusMD)
            ) {
                Text("Apply Crop", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", fontFamily = OutfitFamily, color = colors.inkMuted)
            }
        }
    )
}

data class DuplicateResult(val isExactMatch: Boolean, val message: String)
