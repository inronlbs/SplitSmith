package com.splitsmith.app.ui.quicksplit

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.splitsmith.app.theme.LocalSplitColors
import com.splitsmith.app.ui.components.dotGridBackground
import com.splitsmith.app.data.FirebaseManager
import com.splitsmith.app.data.UserProfile
import com.splitsmith.app.theme.JetBrainsMonoFamily
import com.splitsmith.app.theme.LocalDimens
import com.splitsmith.app.theme.OutfitFamily
import kotlinx.coroutines.launch

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
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }

    var amountStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Food") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var splitMode by remember { mutableStateOf("EQUAL") } // EQUAL (50/50), OWE_ALL, OWED_ALL, CUSTOM
    var customOweAmount by remember { mutableStateOf("") }
    var paidByMe by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

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
    }

    val colors = LocalSplitColors.current
    val canvasChalk = colors.canvasChalk
    val accentIndigo = colors.inkPrimary // shift highlights to inkPrimary for B&W minimalist look
    val inkPrimary = colors.inkPrimary
    val inkMuted = colors.inkMuted
    val borderWhisper = colors.borderWhisper
    val positiveGreen = colors.positiveGreen
    val alertRed = colors.alertRed

    // Load recent contacts on startup
    LaunchedEffect(Unit) {
        recentContacts = FirebaseManager.getRecentDirectContacts()
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotEmpty()) {
            searchResults = FirebaseManager.searchUsersInstantly(searchQuery)
        } else {
            searchResults = emptyList()
        }
    }

    // QR scanner launcher
    val qrScanLauncher = rememberLauncherForActivityResult(
        contract = com.journeyapps.barcodescanner.ScanContract(),
        onResult = { result ->
            if (result.contents != null) {
                coroutineScope.launch {
                    val user = FirebaseManager.searchUserByCode(result.contents)
                    if (user != null) {
                        targetUser = user
                        Toast.makeText(context, "Resolved user: ${user.displayName}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "User QR code not found in database", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    )

    val amountVal = amountStr.toDoubleOrNull() ?: 0.0

    // Calculate myShare (how much they owe me/I owe them)
    // If positive: they owe me. If negative: I owe them.
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
                            text = "Split directly with anyone by email, user code, or QR scan.",
                            fontFamily = OutfitFamily,
                            fontSize = d.textBodyMedium,
                            color = inkMuted
                        )
                        Spacer(modifier = Modifier.height(d.space8))
                    }

                    // Search / Lookup input
                    item {
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
                                placeholder = { Text("Enter email or user code", fontFamily = OutfitFamily, fontSize = d.textBodyMedium) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            if (searchQuery.trim().isEmpty()) return@IconButton
                                            coroutineScope.launch {
                                                isLoading = true
                                                val resolved = if (searchQuery.contains("@")) {
                                                    FirebaseManager.searchUserByEmail(searchQuery)
                                                } else {
                                                    FirebaseManager.searchUserByCode(searchQuery)
                                                }
                                                isLoading = false
                                                if (resolved != null) {
                                                    targetUser = resolved
                                                } else {
                                                    Toast.makeText(context, "User not found. Check query.", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = colors.inkMuted)
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentIndigo,
                                    unfocusedBorderColor = borderWhisper,
                                    focusedContainerColor = colors.surfaceCard,
                                    unfocusedContainerColor = colors.surfaceCard,
                                    focusedTextColor = colors.inkPrimary,
                                    unfocusedTextColor = colors.inkPrimary
                                )
                            )

                            // QR Scan Button
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
                                colors = ButtonDefaults.buttonColors(containerColor = inkPrimary),
                                shape = RoundedCornerShape(d.radiusSM),
                                modifier = Modifier.size(52.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("[QR]", fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            }
                        }
                    }

                    // Contacts / Recent list
                    val displayContacts = if (searchQuery.isEmpty()) recentContacts else searchResults

                    if (displayContacts.isNotEmpty()) {
                        item {
                            Text(
                                text = if (searchQuery.isEmpty()) "RECENT CONTACTS" else "SEARCH RESULTS",
                                fontFamily = OutfitFamily,
                                fontSize = d.textLabelSmall,
                                color = inkMuted,
                                letterSpacing = 1.5.sp
                            )
                        }

                        items(displayContacts) { contact ->
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
                                        .background(accentIndigo),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = contact.displayName.firstOrNull()?.uppercase() ?: "?",
                                        fontFamily = OutfitFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textBodyMedium,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(d.space12))
                                Column {
                                    Text(contact.displayName, fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textTitleMedium, color = inkPrimary)
                                    Text(contact.email, fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = inkMuted)
                                }
                            }
                            HorizontalDivider(color = borderWhisper)
                        }
                    }
                }
            } else {
                // ─── PART 2: Split Details screen ───
                val user = targetUser!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = d.space16),
                    verticalArrangement = Arrangement.spacedBy(d.space16)
                ) {
                    item {
                        // User Profile Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = d.space8),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(d.avatarMd)
                                    .clip(CircleShape)
                                    .background(accentIndigo),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                                    fontFamily = OutfitFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = d.textBodyMedium,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(d.space12))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.displayName, fontFamily = OutfitFamily, fontWeight = FontWeight.Bold, fontSize = d.textTitleLarge, color = inkPrimary)
                                Text("Direct Split", fontFamily = OutfitFamily, fontSize = d.textLabelMedium, color = inkMuted)
                            }
                            TextButton(onClick = { targetUser = null }) {
                                Text("Change", fontFamily = OutfitFamily, fontSize = d.textLabelLarge, color = accentIndigo)
                            }
                        }
                        HorizontalDivider(color = borderWhisper)
                    }

                    // Amount input hero
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = d.space16),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "\u20b9",
                                    fontFamily = OutfitFamily,
                                    fontSize = d.textHeadlineMedium,
                                    color = inkMuted,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                OutlinedTextField(
                                    value = amountStr,
                                    onValueChange = { amountStr = it },
                                    modifier = Modifier.width(180.dp),
                                    textStyle = TextStyle(
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = d.textDisplayLarge,
                                        color = inkPrimary,
                                        textAlign = TextAlign.Center
                                    ),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    placeholder = {
                                        Text(
                                            "0",
                                            fontFamily = JetBrainsMonoFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = d.textDisplayLarge,
                                            color = borderWhisper,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.Transparent,
                                        unfocusedBorderColor = Color.Transparent,
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    )
                                )
                            }
                        }
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
                                focusedTextColor = colors.inkPrimary,
                                unfocusedTextColor = colors.inkPrimary
                            ),
                            textStyle = TextStyle(fontFamily = OutfitFamily, fontSize = d.textBodyLarge, color = colors.inkPrimary)
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
                                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderWhisper) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = OutfitFamily,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = d.textLabelLarge,
                                        color = if (isSelected) colors.canvasChalk else inkMuted,
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
                                    border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, borderWhisper) else null,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = label,
                                        fontFamily = OutfitFamily,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        fontSize = d.textLabelMedium,
                                        color = if (isSelected) colors.canvasChalk else inkMuted,
                                        modifier = Modifier.padding(vertical = d.space12),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        if (splitMode == "CUSTOM") {
                            Spacer(modifier = Modifier.height(d.space12))
                            OutlinedTextField(
                                value = customOweAmount,
                                onValueChange = { customOweAmount = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(if (paidByMe) "Amount they owe you (\u20b9)" else "Amount you owe them (\u20b9)", fontFamily = OutfitFamily) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(d.radiusSM),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentIndigo,
                                    unfocusedBorderColor = borderWhisper
                                )
                            )
                        }
                    }

                    // Live Preview Alert card
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(d.radiusMD),
                            color = if (p2pOwedShare > 0) colors.surfaceCard else colors.alertRed.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (p2pOwedShare > 0) borderWhisper else alertRed.copy(alpha = 0.3f))
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
                                    FirebaseManager.createDirectSplit(
                                        withUserId = user.uid,
                                        description = description.trim(),
                                        amount = amountVal,
                                        myShare = calculatedOwed,
                                        paidBy = finalPaidBy,
                                        category = selectedCategory,
                                        date = selectedDateMillis
                                    )
                                    Toast.makeText(context, "Quick Split saved!", Toast.LENGTH_SHORT).show()
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
                            .padding(horizontal = d.space16)
                            .navigationBarsPadding()
                            .padding(vertical = d.space12),
                        shape = RoundedCornerShape(d.radiusMD),
                        colors = ButtonDefaults.buttonColors(containerColor = inkPrimary),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create Quick Split", fontFamily = OutfitFamily, fontWeight = FontWeight.SemiBold, fontSize = d.textLabelLarge, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

