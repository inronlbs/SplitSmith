package com.splitsmith.app.ui.quicksplit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.QrCodeScanner
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
import com.splitsmith.app.data.UserProfile
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.theme.OutfitFamily
import com.splitsmith.app.ui.components.dotGridBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSplitScreen(
    onBack: () -> Unit
) {
    val d = LocalDimens.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var targetUser by remember { mutableStateOf<UserProfile?>(null) }
    var recentContacts by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    val userProfileState = FirebaseManager.observeUserProfile().collectAsState(initial = null)

    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var splitMode by remember { mutableStateOf("EQUAL") }
    var customOweAmount by remember { mutableStateOf("") }
    var paidByMe by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedAttachmentUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var existingAttachmentUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var existingDriveFileIds by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        val pAmt = FirebaseManager.pendingExpenseAmount
        val pDesc = FirebaseManager.pendingExpenseDesc
        val pCategory = FirebaseManager.pendingExpenseCategory
        val pDate = FirebaseManager.pendingExpenseDate
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
        val pAttachment = FirebaseManager.pendingExpenseAttachmentUri
        if (pAttachment != null) {
            selectedAttachmentUris = listOf(pAttachment)
            FirebaseManager.pendingExpenseAttachmentUri = null
        }
        val pUser = FirebaseManager.pendingQuickSplitUser
        if (pUser != null) {
            targetUser = pUser
            FirebaseManager.pendingQuickSplitUser = null
        }
    }

    val colors = LocalSplitColors.current
    val canvasChalk = colors.canvasChalk
    val accentIndigo = colors.inkPrimary
    val inkPrimary = colors.inkPrimary
    val inkMuted = colors.inkMuted
    val borderWhisper = colors.borderWhisper
    val alertRed = colors.alertRed

    // Observe connected users and merge with recent contacts (deduplicated)
    val connectionsState = FirebaseManager.observeConnections().collectAsState(initial = emptyList())
    val connectedUsers = connectionsState.value

    LaunchedEffect(Unit) {
        recentContacts = FirebaseManager.getRecentDirectContacts()
    }

    val allContacts = remember(connectedUsers, recentContacts) {
        (connectedUsers + recentContacts).distinctBy { it.uid }
    }

    // Filter contacts strictly by displayName locally
    val filteredLocalContacts = remember(searchQuery, allContacts) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            allContacts
        } else {
            allContacts.filter {
                it.displayName.contains(trimmed, ignoreCase = true)
            }
        }
    }

    // Process scanned or decoded QR payload
    fun handleQrPayload(payload: String) {
        if (payload.isBlank()) return
        coroutineScope.launch {
            isLoading = true
            try {
                val cleanCode = com.splitsmith.app.util.QrPayloadParser.extractCleanCode(payload)
                val resolvedUser = FirebaseManager.searchUserByCode(payload)
                    ?: (if (cleanCode.contains("@")) FirebaseManager.searchUserByEmail(cleanCode) else null)

                if (resolvedUser != null && resolvedUser.uid.isNotEmpty()) {
                    FirebaseManager.addConnection(resolvedUser.uid)
                    targetUser = resolvedUser
                    Toast.makeText(context, "Connected with ${resolvedUser.displayName}!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "User not found or invalid QR code", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading QR. Please try again.", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    // QR camera scan launcher
    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = com.journeyapps.barcodescanner.ScanContract(),
        onResult = { result ->
            if (result.contents != null) {
                handleQrPayload(result.contents)
            }
        }
    )

    // Gallery QR screenshot picker
    val galleryQrLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val stream = context.contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(stream)
                        stream?.close()
                        if (bitmap != null) {
                            val decodedText = decodeQrFromBitmap(bitmap)
                            withContext(Dispatchers.Main) {
                                if (!decodedText.isNullOrEmpty()) {
                                    handleQrPayload(decodedText)
                                } else {
                                    Toast.makeText(context, "No valid SplitSmith QR code found in screenshot", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Could not process image: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    )

    val amountVal = amountStr.toDoubleOrNull() ?: 0.0

    val p2pOwedShare = remember(amountVal, splitMode, customOweAmount, paidByMe) {
        val shareVal = when (splitMode) {
            "EQUAL" -> amountVal / 2.0
            "OWE_ALL" -> if (paidByMe) amountVal else 0.0
            "OWED_ALL" -> if (paidByMe) 0.0 else amountVal
            "CUSTOM" -> customOweAmount.toDoubleOrNull() ?: 0.0
            else -> amountVal / 2.0
        }
        if (paidByMe) shareVal else -shareVal
    }

    Scaffold(
        containerColor = canvasChalk,
        modifier = Modifier.dotGridBackground(colors.dotColor),
        topBar = {
            TopAppBar(
                title = { Text("Quick Split", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = inkPrimary)
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
                .imePadding()
        ) {
            if (targetUser == null) {
                // ─── PART 1: Pick a Person ───
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(d.space8))
                        Text(
                            text = "Split directly with connected friends, or lookup by email/code.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = inkMuted
                        )
                        Spacer(modifier = Modifier.height(d.space4))
                    }

                    // Search / Lookup input & QR options
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(d.space8)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(d.space8)
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(d.radiusSM),
                                    placeholder = { Text("Search connected friends by name...", fontFamily = OutfitFamily, fontSize = 13.sp) },
                                    singleLine = true,
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Search, contentDescription = "Search", tint = inkMuted)
                                            }
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = accentIndigo,
                                        unfocusedBorderColor = borderWhisper,
                                        focusedContainerColor = colors.surfaceCard,
                                        unfocusedContainerColor = colors.surfaceCard,
                                        focusedTextColor = inkPrimary,
                                        unfocusedTextColor = inkPrimary
                                    )
                                )

                                // High-Contrast QR Camera Scan Button
                                Button(
                                    onClick = {
                                        val options = com.journeyapps.barcodescanner.ScanOptions()
                                        options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                                        options.setPrompt("Scan a SplitSmith User QR Code")
                                        options.setCameraId(0)
                                        options.setBeepEnabled(false)
                                        options.setBarcodeImageEnabled(true)
                                        qrScanLauncher.launch(options)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = inkPrimary, contentColor = canvasChalk),
                                    shape = RoundedCornerShape(d.radiusSM),
                                    modifier = Modifier.size(52.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.QrCodeScanner,
                                        contentDescription = "Scan QR",
                                        tint = canvasChalk,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                // Gallery QR Screenshot Import Button
                                OutlinedButton(
                                    onClick = { galleryQrLauncher.launch("image/*") },
                                    shape = RoundedCornerShape(d.radiusSM),
                                    border = BorderStroke(1.dp, borderWhisper),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = inkPrimary),
                                    modifier = Modifier.size(52.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoLibrary,
                                        contentDescription = "Import QR Screenshot",
                                        tint = inkPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Remote Database Search Trigger (If email or code typed)
                            val trimmedQuery = searchQuery.trim()
                            if (trimmedQuery.isNotEmpty() && (trimmedQuery.contains("@") || trimmedQuery.length == 6)) {
                                Surface(
                                    onClick = {
                                        coroutineScope.launch {
                                            isLoading = true
                                            val resolved = if (trimmedQuery.contains("@")) {
                                                FirebaseManager.searchUserByEmail(trimmedQuery)
                                            } else {
                                                FirebaseManager.searchUserByCode(trimmedQuery)
                                            }
                                            isLoading = false
                                            if (resolved != null) {
                                                targetUser = resolved
                                            } else {
                                                Toast.makeText(context, "No user found for '$trimmedQuery'", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(d.radiusSM),
                                    color = colors.surfaceCard,
                                    border = BorderStroke(1.dp, borderWhisper),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = d.space16, vertical = d.space12),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Search database for '$trimmedQuery'",
                                            fontFamily = OutfitFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = d.textBodyMedium,
                                            color = inkPrimary
                                        )
                                        Icon(Icons.Default.Search, contentDescription = null, tint = inkMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    // Contacts / Connected Users List
                    item {
                        Text(
                            text = if (searchQuery.isEmpty()) "CONNECTED FRIENDS (${filteredLocalContacts.size})" else "MATCHING FRIENDS (${filteredLocalContacts.size})",
                            fontFamily = OutfitFamily,
                            fontSize = d.textLabelSmall,
                            color = inkMuted,
                            letterSpacing = 1.5.sp
                        )
                    }

                    if (filteredLocalContacts.isNotEmpty()) {
                        items(filteredLocalContacts) { contact ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { targetUser = contact }
                                    .padding(vertical = d.space8),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(d.avatarMd)
                                        .clip(CircleShape)
                                        .background(inkPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textBodyMedium,
                                        color = canvasChalk
                                    )
                                }
                                Spacer(modifier = Modifier.width(d.space12))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.displayName, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = inkPrimary)
                                    Text(contact.email, fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = inkMuted)
                                }
                            }
                            HorizontalDivider(color = borderWhisper)
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = d.space24),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (searchQuery.isEmpty()) "No connected friends yet.\nScan a QR code or search by email/user code above." else "No connected friends match '$searchQuery'.\nUse search icon above for global email/code lookup.",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textBodyMedium,
                                    color = inkMuted,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            } else {
                // ─── PART 2: Split Details screen ───
                val user = targetUser!!
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    // Target User Header Chip
                    item {
                        Spacer(modifier = Modifier.height(d.space4))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(d.radiusMD),
                            color = colors.surfaceCard,
                            border = BorderStroke(1.dp, borderWhisper)
                        ) {
                            Row(
                                modifier = Modifier.padding(d.space16),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(d.space12)) {
                                    Box(
                                        modifier = Modifier
                                            .size(d.avatarLg)
                                            .clip(CircleShape)
                                            .background(inkPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                                            fontFamily = OutfitFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = d.textTitleMedium,
                                            color = canvasChalk
                                        )
                                    }
                                    Column {
                                        Text(user.displayName, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = inkPrimary)
                                        Text(user.email, fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = inkMuted)
                                    }
                                }
                                TextButton(onClick = { targetUser = null }) {
                                    Text("Change", fontFamily = OutfitFamily, color = inkMuted, fontSize = d.textLabelMedium)
                                }
                            }
                        }
                    }

                    // Amount input field
                    item {
                        Text("TOTAL AMOUNT", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = inkMuted, letterSpacing = 1.5.sp)
                        Spacer(modifier = Modifier.height(d.space8))
                        OutlinedTextField(
                            value = amountStr,
                            onValueChange = { amountStr = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                            shape = RoundedCornerShape(d.radiusSM),
                            prefix = { Text("\u20b9", fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = inkPrimary) },
                            placeholder = { Text("0.00", fontFamily = JetBrainsMonoFamily, fontSize = d.textTitleLarge) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentIndigo,
                                unfocusedBorderColor = borderWhisper,
                                focusedContainerColor = colors.surfaceCard,
                                unfocusedContainerColor = colors.surfaceCard,
                                focusedTextColor = inkPrimary,
                                unfocusedTextColor = inkPrimary
                            ),
                            textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = inkPrimary)
                        )
                    }

                    // Description field
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                            shape = RoundedCornerShape(d.radiusSM),
                            placeholder = { Text("What was it for?", fontFamily = OutfitFamily, fontSize = d.textBodyMedium) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentIndigo,
                                unfocusedBorderColor = borderWhisper,
                                focusedContainerColor = colors.surfaceCard,
                                unfocusedContainerColor = colors.surfaceCard,
                                focusedTextColor = inkPrimary,
                                unfocusedTextColor = inkPrimary
                            ),
                            textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = inkPrimary)
                        )
                    }

                    // Who Paid Row
                    item {
                        Text("WHO PAID?", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = inkMuted, letterSpacing = 1.5.sp)
                        Spacer(modifier = Modifier.height(d.space8))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                            listOf(true to "You Paid", false to "${user.displayName} Paid").forEach { (isMe, label) ->
                                val isSelected = paidByMe == isMe
                                Surface(
                                    onClick = { paidByMe = isMe },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    color = if (isSelected) accentIndigo else colors.surfaceCard,
                                    border = if (!isSelected) BorderStroke(1.dp, borderWhisper) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = OutfitFamily,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = d.textLabelLarge,
                                        color = if (isSelected) canvasChalk else inkMuted,
                                        modifier = Modifier.padding(vertical = d.space12),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    // Split mode row
                    item {
                        Text("SPLIT MODE", fontFamily = OutfitFamily, fontSize = d.textLabelSmall, color = inkMuted, letterSpacing = 1.5.sp)
                        Spacer(modifier = Modifier.height(d.space8))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(d.space8)) {
                            listOf("EQUAL" to "50/50", "OWE_ALL" to "Owe All", "CUSTOM" to "Custom").forEach { (mode, label) ->
                                val isSelected = splitMode == mode
                                Surface(
                                    onClick = { splitMode = mode },
                                    shape = RoundedCornerShape(d.radiusFull),
                                    color = if (isSelected) inkPrimary else colors.surfaceCard,
                                    border = if (!isSelected) BorderStroke(1.dp, borderWhisper) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = OutfitFamily,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = d.textLabelLarge,
                                        color = if (isSelected) canvasChalk else inkMuted,
                                        modifier = Modifier.padding(vertical = d.space12),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (splitMode == "CUSTOM") {
                            Spacer(modifier = Modifier.height(d.space8))
                            OutlinedTextField(
                                value = customOweAmount,
                                onValueChange = { customOweAmount = it },
                                modifier = Modifier.fillMaxWidth().heightIn(min = d.inputHeight),
                                shape = RoundedCornerShape(d.radiusSM),
                                placeholder = { Text("Amount they owe you (\u20b9)", fontFamily = OutfitFamily) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentIndigo,
                                    unfocusedBorderColor = borderWhisper
                                )
                            )
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

                    // Live Preview Alert card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(d.radiusMD),
                            color = if (p2pOwedShare > 0) colors.surfaceCard else alertRed.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, if (p2pOwedShare > 0) borderWhisper else alertRed.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.padding(d.space16),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (p2pOwedShare > 0) "${user.displayName} owes you" else "You owe ${user.displayName}",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = d.textBodyLarge,
                                    color = if (p2pOwedShare > 0) inkPrimary else alertRed
                                )
                                Text(
                                    text = "\u20b9${"%.2f".format(Math.abs(p2pOwedShare))}",
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = d.textTitleMedium,
                                    color = if (p2pOwedShare > 0) inkPrimary else alertRed
                                )
                            }
                        }
                    }
                }

                // Sticky Add Split Button
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = canvasChalk,
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = {
                            if (amountVal <= 0 || description.trim().isEmpty()) {
                                Toast.makeText(context, "Please enter valid amount and description", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            val calculatedOwed = Math.abs(p2pOwedShare)
                            val finalPaidBy = if (paidByMe) FirebaseManager.currentUserId ?: "" else user.uid
                            isLoading = true
                            coroutineScope.launch {
                                try {
                                    val finalUploadedUrls = existingAttachmentUrls.toMutableList()
                                    val finalDriveFileIds = existingDriveFileIds.toMutableList()

                                    // Step 1: Save attachments locally first
                                    val localSavedUris = mutableListOf<android.net.Uri>()
                                    if (selectedAttachmentUris.isNotEmpty()) {
                                        selectedAttachmentUris.forEach { uri ->
                                            val localSavedUri = com.splitsmith.app.data.LocalStorageManager.saveAttachmentLocally(context, uri, "quick")
                                            val effectiveUri = localSavedUri ?: uri
                                            localSavedUris.add(effectiveUri)
                                            finalUploadedUrls.add(effectiveUri.toString())
                                        }
                                    }

                                    // Step 2: Save to Firebase (get real document ID)
                                    val newSplitId = FirebaseManager.createDirectSplit(
                                        withUserId = user.uid,
                                        description = description.trim(),
                                        amount = amountVal,
                                        myShare = calculatedOwed,
                                        paidBy = finalPaidBy,
                                        category = selectedCategory,
                                        date = selectedDateMillis,
                                        receiptUrls = finalUploadedUrls,
                                        receiptDriveFileIds = finalDriveFileIds
                                    )
                                    Toast.makeText(context, "Quick Split saved!", Toast.LENGTH_SHORT).show()

                                    // Immediate Drive upload with WorkManager fallback
                                    if (localSavedUris.isNotEmpty() && userProfileState.value?.driveSyncEnabled == true) {
                                        val applicationContext = context.applicationContext
                                        val targetId = newSplitId
                                        if (com.splitsmith.app.data.GoogleDriveManager.hasDrivePermission(applicationContext)) {
                                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                                localSavedUris.forEach { effectiveUri ->
                                                    try {
                                                        val driveResult = com.splitsmith.app.data.GoogleDriveManager.uploadAttachment(
                                                            context = applicationContext,
                                                            inputUri = effectiveUri,
                                                            folderCategoryName = "Direct Splits",
                                                            dateMillis = selectedDateMillis,
                                                            expenseId = targetId
                                                        )
                                                        when (driveResult) {
                                                            is com.splitsmith.app.data.DriveUploadResult.Success -> {
                                                                FirebaseManager.attachDriveFileToDirectSplit(
                                                                    splitId = targetId,
                                                                    driveFileId = driveResult.fileId,
                                                                    webUrl = driveResult.webViewLink,
                                                                    localUriPath = effectiveUri.toString()
                                                                )
                                                            }
                                                            is com.splitsmith.app.data.DriveUploadResult.Failure -> {
                                                                com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                                                    context = applicationContext,
                                                                    localUri = effectiveUri,
                                                                    originalLocalUriPath = effectiveUri.toString(),
                                                                    folderCategoryName = "Direct Splits",
                                                                    dateMillis = selectedDateMillis,
                                                                    expenseId = targetId,
                                                                    isPersonal = false,
                                                                    groupId = ""
                                                                )
                                                                com.splitsmith.app.data.DriveSyncWorker.enqueue(applicationContext)
                                                            }
                                                        }
                                                    } catch (e: Exception) {
                                                        com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                                            context = applicationContext,
                                                            localUri = effectiveUri,
                                                            originalLocalUriPath = effectiveUri.toString(),
                                                            folderCategoryName = "Direct Splits",
                                                            dateMillis = selectedDateMillis,
                                                            expenseId = targetId,
                                                            isPersonal = false,
                                                            groupId = ""
                                                        )
                                                        com.splitsmith.app.data.DriveSyncWorker.enqueue(applicationContext)
                                                    }
                                                }
                                            }
                                        } else {
                                            localSavedUris.forEach { effectiveUri ->
                                                com.splitsmith.app.data.PendingDriveUploadsManager.enqueueUpload(
                                                    context = applicationContext,
                                                    localUri = effectiveUri,
                                                    originalLocalUriPath = effectiveUri.toString(),
                                                    folderCategoryName = "Direct Splits",
                                                    dateMillis = selectedDateMillis,
                                                    expenseId = targetId,
                                                    isPersonal = false,
                                                    groupId = ""
                                                )
                                            }
                                            Toast.makeText(context, "Receipt saved locally. Link Google Drive in Settings to sync.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    onBack()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(d.buttonHeight)
                            .padding(horizontal = d.space16),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.buttonColors(containerColor = inkPrimary, contentColor = canvasChalk),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = canvasChalk, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Save Quick Split", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge)
                        }
                    }
                }
            }
        }
    }
}

// Multi-pass ZXing QR Decoder for Gallery Screenshots
private fun decodeQrFromBitmap(bitmap: Bitmap): String? {
    return try {
        // Downscale bitmap if too large for ZXing processing
        val maxDim = 1024
        val scaledBitmap = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val targetW = if (bitmap.width > bitmap.height) maxDim else (maxDim * ratio).toInt()
            val targetH = if (bitmap.height > bitmap.width) maxDim else (maxDim / ratio).toInt()
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
        } else {
            bitmap
        }

        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val source = com.google.zxing.RGBLuminanceSource(width, height, pixels)
        val binaryBitmap = com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))
        val reader = com.google.zxing.MultiFormatReader()
        val result = reader.decode(binaryBitmap)
        result.text
    } catch (e: Exception) {
        null
    }
}
