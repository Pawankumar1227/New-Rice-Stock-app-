package com.example.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.R
import com.example.data.StockEntry
import com.example.data.SyncStatusState
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

private fun createPhotoUri(context: Context): Uri {
    val directory = File(context.cacheDir, "camera_photos").apply { mkdirs() }
    val file = File(directory, "camera_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "com.example.fileprovider",
        file
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockEntryScreen(viewModel: StockViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val entries by viewModel.allEntries.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val syncState by viewModel.syncState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Launcher for camera photo capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            tempCameraUri?.let { viewModel.addPhotoUri(it) }
        }
    }

    // Launcher for gallery selection
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        uris.forEach { viewModel.addPhotoUri(it) }
    }

    val pendingCount = entries.count { it.syncStatus == "PENDING" || it.syncStatus == "FAILED" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Monogram box in EADDFF background, 21005D text
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEADDFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "S",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF21005D)
                            )
                        }

                        // Bold tracking tight title & localized sync engine subtitle
                        Column {
                            Text(
                                text = "StockSync",
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 20.sp,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Local Sync Engine",
                                fontSize = 11.sp,
                                color = Color(0xFF49454F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    // Brand Profile badge on right
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "SRO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Connection notification banner adhering to Bold Typography theme shape & container style
            val bannerBg = if (isOnline) Color(0xFFE8F5E9) else Color(0xFFFFDAD6)
            val bannerBorderColor = if (isOnline) Color(0xFF2E7D32) else Color(0xFFBA1A1A)
            val bannerTxtColor = if (isOnline) Color(0xFF1B5E20) else Color(0xFF410002)
            val bannerTitle = if (isOnline) "Online Sync Active" else "Offline Mode Active"
            val bannerSub = if (isOnline) {
                "Fully connected to servers. Instantly backing up entries to Google Sheets!"
            } else {
                if (pendingCount > 0) {
                    "$pendingCount item${if (pendingCount != 1) "s" else ""} saved to local storage. Auto-syncing when internet resumes."
                } else {
                    "No pending items. System will securely save new entries locally."
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bannerBg)
                    .border(BorderStroke(1.2.dp, bannerBorderColor), RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Circle indicator on left
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(bannerBorderColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isOnline) "✓" else "!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = bannerTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = bannerTxtColor
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = bannerSub,
                            fontSize = 11.sp,
                            color = bannerTxtColor.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Medium,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Tab Rows with theme colors
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EditNote, contentDescription = "Form")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Entry Form", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("entry_form_tab")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = Color.White
                                        ) {
                                            Text(pendingCount.toString())
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = "Queue")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Queue", fontWeight = FontWeight.Bold)
                        }
                    },
                    modifier = Modifier.testTag("sync_queue_tab")
                )
            }

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                if (selectedTab == 0) {
                    FormTabContent(
                        viewModel = viewModel,
                        isOnline = isOnline,
                        onCameraClick = {
                            val uri = createPhotoUri(context)
                            tempCameraUri = uri
                            cameraLauncher.launch(uri)
                        },
                        onGalleryClick = {
                            galleryLauncher.launch("image/*")
                        }
                    )
                } else {
                    QueueTabContent(
                        viewModel = viewModel,
                        entries = entries,
                        isOnline = isOnline,
                        syncState = syncState
                    )
                }
            }
        }
    }
}

@Composable
fun FormTabContent(
    viewModel: StockViewModel,
    isOnline: Boolean,
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSubmitting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.2.dp, Color(0xFFF3EDF7))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                
                Text(
                    text = "🌾 Shri Ram Overseas",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = M3Primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    letterSpacing = (-0.5).sp
                )
                Text(
                    text = "Rice Stock Entry Form",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )

                // Section: Rice Type Details
                FormSectionHeader(title = "Rice Type Details", icon = Icons.Outlined.Grass)

                StockDropdownField(
                    label = "Rice Type",
                    selectedValue = viewModel.riceType,
                    options = listOf("Select Rice Type", "Basmati", "Non Basmati", "Rice Bran"),
                    onValueSelected = { viewModel.riceType = if (it == "Select Rice Type") "" else it }
                )

                StockDropdownField(
                    label = "Rice Type 1",
                    selectedValue = viewModel.riceType1,
                    onValueSelected = { viewModel.riceType1 = if (it == "Select Type 1") "" else it },
                    options = listOf(
                        "Select Type 1", "1718", "1121", "1509", "Sharbati", "TAJ", "1885",
                        "1847", "PUSSA", "1886", "1401", "Sugandha", "PR106", "IR64",
                        "Jeera Kasala", "Fak", "Polish", "Fak Nakku"
                    )
                )

                StockDropdownField(
                    label = "Rice Type 2",
                    selectedValue = viewModel.riceType2,
                    onValueSelected = { viewModel.riceType2 = if (it == "Select Type 2") "" else it },
                    options = listOf("Select Type 2", "G/S", "W/S", "L/S", "STEAM", "None")
                )

                StockDropdownField(
                    label = "Rice Type 3",
                    selectedValue = viewModel.riceType3,
                    onValueSelected = { viewModel.riceType3 = if (it == "Select Type 3") "" else it },
                    options = listOf("Select Type 3", "Resort", "Sizer", "Rejection", "None")
                )

                StockDropdownField(
                    label = "Rice Type 4",
                    selectedValue = viewModel.riceType4,
                    onValueSelected = { viewModel.riceType4 = if (it == "Select Type 4") "" else it },
                    options = listOf(
                        "Select Type 4", "Wand", "2nd Wand", "Tibar", "Super Tibar", "Dubar",
                        "S. Mongra", "Mongra", "Nakku", "Mix Broken", "Broken", "Rejection", "None"
                    )
                )

                // Section: Dara Details
                FormSectionHeader(title = "Dara Details", icon = Icons.Outlined.Assignment)

                StockDropdownField(
                    label = "Dara Status *",
                    selectedValue = viewModel.daraStatus,
                    onValueSelected = { viewModel.daraStatus = if (it == "Select Dara Status") "" else it },
                    options = listOf("Select Dara Status", "Yes", "No")
                )

                // Section: Party & Logistics (Conditional view based on Dara Status)
                if (viewModel.daraStatus != "No") {
                    FormSectionHeader(title = "Party & Logistics", icon = Icons.Outlined.Business)

                    OutlinedTextField(
                        value = viewModel.partyName,
                        onValueChange = { viewModel.partyName = it },
                        label = { Text("Party Name *") },
                        placeholder = { Text("Enter Party Name") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("party_name_field"),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = viewModel.station,
                        onValueChange = { viewModel.station = it },
                        label = { Text("Station *") },
                        placeholder = { Text("e.g. Karnal, Delhi") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("station_field"),
                        singleLine = true
                    )
                }

                StockDropdownField(
                    label = "Status *",
                    selectedValue = viewModel.status,
                    onValueSelected = { viewModel.status = if (it == "Select Status") "" else it },
                    options = listOf("Select Status", "In", "Out")
                )

                // Section: Weight & Quantity
                FormSectionHeader(title = "Weight & Quantity", icon = Icons.Outlined.Scale)

                OutlinedTextField(
                    value = viewModel.weight,
                    onValueChange = { viewModel.weight = it },
                    label = { Text("Weight (K.G) *") },
                    placeholder = { Text("e.g. 1000") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("weight_field"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = viewModel.bags,
                    onValueChange = { viewModel.bags = it },
                    label = { Text("Number of Bags *") },
                    placeholder = { Text("e.g. 20") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .testTag("bags_field"),
                    singleLine = true
                )

                // Section: Location
                FormSectionHeader(title = "Location", icon = Icons.Outlined.Place)

                StockDropdownField(
                    label = "Location *",
                    selectedValue = viewModel.location,
                    onValueSelected = { viewModel.location = if (it == "Select Location") "" else it },
                    options = listOf(
                        "Select Location", "Lab Godown", "Bardana Godown", "Godown No.1",
                        "Sortex No. 4 (Hodi)", "Sortex No. 5 (Hodi)", "Sortex No. 4 (Plant)",
                        "Sortex No. 5 (Plant)", "S.S Side (WH)", "Shiv Shankar Side (WH)",
                        "Fak Godown", "Polish Godown", "Kanta Godown (WH)"
                    )
                )

                // Section: Photos
                FormSectionHeader(title = "Photos", icon = Icons.Outlined.PhotoCamera)

                UploadDashedBox(
                    onCameraClick = onCameraClick,
                    onGalleryClick = onGalleryClick
                )

                // Image Preview row/grid
                if (viewModel.selectedPhotoUris.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${viewModel.selectedPhotoUris.size} photo(s) selected",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.selectedPhotoUris.forEach { uri ->
                            Box(
                                modifier = Modifier
                                    .size(85.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.5.dp, Color(0xFFDDDDF0), RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Delete Overlay Button
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(24.dp)
                                        .background(
                                            Color(0xFFC0392B).copy(alpha = 0.9f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.removePhotoUri(uri) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        if (!isSubmitting) {
                            scope.launch {
                                isSubmitting = true
                                val error = viewModel.submitForm()
                                isSubmitting = false
                                if (error != null) {
                                    Toast.makeText(context, "❌ $error", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "✅ Entry Saved Successfully!", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_entry_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = M3Primary),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSubmitting
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Icon(Icons.Default.Check, contentDescription = "Submit")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "✅ Submit Entry",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QueueTabContent(
    viewModel: StockViewModel,
    entries: List<StockEntry>,
    isOnline: Boolean,
    syncState: SyncStatusState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Sync operation banner
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.dp, Color(0xFFEADDFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sync Manager",
                        tint = M3Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync Controller",
                        fontWeight = FontWeight.ExtraBold,
                        color = M3Primary,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { viewModel.triggerAutoSync() },
                        colors = ButtonDefaults.buttonColors(containerColor = M3Primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.testTag("sync_now_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync icon", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sync Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Sync status monitoring message
                Spacer(modifier = Modifier.height(8.dp))
                when (syncState) {
                    is SyncStatusState.Idle -> {
                        Text(
                            text = "Idle. System automatically attempts submission in the background when network improves.",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is SyncStatusState.Progress -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(14.dp)
                                    .testTag("sync_progress_spinner"),
                                strokeWidth = 2.dp,
                                color = M3Primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Status: ${syncState.message}",
                                fontSize = 12.sp,
                                color = M3Primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    is SyncStatusState.Success -> {
                        Text(
                            text = "✅ Success: ${syncState.message}",
                            fontSize = 12.sp,
                            color = Color(0xFF155724),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    is SyncStatusState.Error -> {
                        Text(
                            text = "❌ Error: ${syncState.message}",
                            fontSize = 12.sp,
                            color = Color(0xFF721C24),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // List Header label in bold typography
        Text(
            text = "📋 Local Log Journal (${entries.size} Record${if (entries.size != 1) "s" else ""})",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = M3Primary,
            modifier = Modifier.padding(bottom = 10.dp),
            letterSpacing = (-0.25).sp
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = "Empty Queue",
                        tint = Color.Gray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No stock entries saved yet.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Go to 'Entry Form' to add stock details.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    QueueItemRow(entry = entry, onDeleteClick = { viewModel.deleteEntry(entry) })
                }
            }
        }
    }
}

@Composable
fun QueueItemRow(entry: StockEntry, onDeleteClick: () -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Delete Entry") },
            text = { Text("Are you sure you want to delete this stock entry? If it is pending, it will be lost form Google Sheets.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFC0392B))
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            modifier = Modifier.testTag("delete_confirm_dialog")
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("stock_entry_card_${entry.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.2.dp, Color(0xFFF3EDF7))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Status and Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp)),
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))

                // Status Badge
                val badgeBg = when (entry.syncStatus) {
                    "SUCCESS" -> Color(0xFFE8F5E9)
                    "SYNCING" -> Color(0xFFFFF8E1)
                    "FAILED" -> Color(0xFFFFEBEE)
                    else -> Color(0xFFF3EDF7) // PENDING / LOCAL
                }
                val badgeColor = when (entry.syncStatus) {
                    "SUCCESS" -> Color(0xFF2E7D32)
                    "SYNCING" -> Color(0xFFF57F17)
                    "FAILED" -> Color(0xFFC62828)
                    else -> Color(0xFF6750A4) // PENDING / LOCAL
                }
                val badgeText = when (entry.syncStatus) {
                    "SUCCESS" -> "Synced ✓"
                    "SYNCING" -> "Uploading 🔄"
                    "FAILED" -> "Failed ❌"
                    else -> "Saved Local ⏳"
                }

                Box(
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = badgeColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Logistics Title Details
            val detailTitle = buildString {
                if (entry.riceType.isNotEmpty()) append(entry.riceType)
                if (entry.riceType1.isNotEmpty()) {
                    if (isNotEmpty()) append(" - ")
                    append(entry.riceType1)
                }
                if (entry.riceType2.isNotEmpty()) {
                    if (isNotEmpty()) append(" (")
                    append(entry.riceType2)
                    if (entry.riceType1.isNotEmpty()) append(")")
                }
            }

            Text(
                text = if (detailTitle.isNotEmpty()) detailTitle else "Raw Rice Stock Batch",
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = M3Primary,
                letterSpacing = (-0.25).sp
            )

            // Dynamic items 3/4
            val subText = getSubTextString(entry)
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFF0F0F0))

            // Details grid description
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    RowKeyValue("Dara status:", entry.daraStatus)
                    if (entry.daraStatus == "Yes") {
                        RowKeyValue("Party:", entry.partyName)
                        RowKeyValue("Station:", entry.station)
                    }
                    RowKeyValue("Location:", entry.location)
                }

                Column(modifier = Modifier.weight(1f)) {
                    RowKeyValue("Direction:", entry.status)
                    RowKeyValue("Weight (KG):", entry.weight.toString())
                    RowKeyValue("Bags:", entry.bags.toString())
                }
            }

            // Sync error details if present
            if (entry.syncStatus == "FAILED" && !entry.errorMessage.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF3CD), RoundedCornerShape(4.dp))
                        .border(0.5.dp, Color(0xFF856404), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "Error detail: ${entry.errorMessage}",
                        fontSize = 11.sp,
                        color = Color(0xFF856404)
                    )
                }
            }

            // Photos Row preview
            val localPaths = entry.toPhotoPathList()
            if (localPaths.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    localPaths.forEach { path ->
                        val f = File(path)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(0.5.dp, Color.LightGray, RoundedCornerShape(4.dp))
                        ) {
                            if (f.exists()) {
                                AsyncImage(
                                    model = f,
                                    contentDescription = "Stored photo preview",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                // Fallback/synced photo URL if local path deleted
                                val urlIndex = localPaths.indexOf(path)
                                val urls = entry.toPhotoUrlList()
                                if (urlIndex in urls.indices) {
                                    AsyncImage(
                                        model = urls[urlIndex],
                                        contentDescription = "Synced photo url",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0xFFEEEEEE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.BrokenImage, contentDescription = "Missing", size20Modifier())
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Row Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = { showDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("delete_entry_${entry.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete record",
                        tint = Color(0xFFC0392B),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getSubTextString(entry: StockEntry): String {
    val items = mutableListOf<String>()
    if (entry.riceType3.isNotEmpty() && entry.riceType3 != "None") {
        items.add("Type 3: ${entry.riceType3}")
    }
    if (entry.riceType4.isNotEmpty() && entry.riceType4 != "None") {
        items.add("Type 4: ${entry.riceType4}")
    }
    return items.joinToString(", ")
}

@Composable
fun RowKeyValue(key: String, value: String) {
    if (value.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "$key ",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 11.sp,
                color = Color.Black,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun FormSectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 10.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF3EDF7))
            .drawBehind {
                // Left Primary color indicator strip
                drawRect(
                    color = M3Primary,
                    size = this.size.copy(width = 4.dp.toPx())
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = M3Primary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title.uppercase(Locale.getDefault()),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = M3Primary,
            letterSpacing = 0.75.sp
        )
    }
}

@Composable
fun StockDropdownField(
    label: String,
    selectedValue: String,
    options: List<String>,
    onValueSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = if (selectedValue.isEmpty()) options.first() else selectedValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = "Expand dropdown",
                    modifier = Modifier.clickable { expanded = !expanded }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .testTag("dropdown_${label.replace(" ", "_").replace("*", "").lowercase(Locale.getDefault())}"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = M3Primary,
                unfocusedBorderColor = Color(0xFFCCCCCC),
                focusedLabelColor = M3Primary
            )
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color.White)
                .heightIn(max = 240.dp)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            fontWeight = if (option == selectedValue) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == selectedValue) M3Primary else TextDark
                        )
                    },
                    onClick = {
                        onValueSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun UploadDashedBox(
    onCameraClick: () -> Unit,
    onGalleryClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .height(110.dp)
            .drawBehind {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                drawRoundRect(
                    color = WarmGold,
                    style = stroke,
                    cornerRadius = CornerRadius(10.dp.toPx())
                )
            }
            .background(SoftGold, RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = "Upload Stock Photos",
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = M3Primary,
                letterSpacing = (-0.25).sp
            )
            Text(
                text = "Photos saved offline with entry, uploads link directly",
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 12.dp),
                fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onCameraClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = BorderStroke(1.2.dp, M3Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("camera_upload_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = M3Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("📷 Camera", color = M3Primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                Button(
                    onClick = onGalleryClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    border = BorderStroke(1.2.dp, M3Primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("gallery_upload_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = M3Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🖼️ Gallery", color = M3Primary, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

// Small size helper Composable modifier to satisfy rules
@Composable
fun size20Modifier(): Modifier = Modifier.size(20.dp)
