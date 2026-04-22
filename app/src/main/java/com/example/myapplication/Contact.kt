package com.example.myapplication

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R

// ─────────────────────────────────────────────────────────────────────────────
// Data Models
// ─────────────────────────────────────────────────────────────────────────────

/** Represents a trusted contact shown on the main list. */
data class SafeContact(val name: String, val relation: String, val phone: String)

/** Represents a raw device contact fetched via ContentResolver. */
data class DeviceContact(val name: String, val phone: String)

// ─────────────────────────────────────────────────────────────────────────────
// Activity
// ─────────────────────────────────────────────────────────────────────────────

class ContactsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                TrustedFamilyScreen()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Part 2A: ContentResolver — fetch all device contacts (name + phone)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Reads all contacts from the device using ContentResolver.
 * Must be called AFTER READ_CONTACTS permission is granted.
 * Returns a deduplicated list of [DeviceContact].
 */
fun fetchDeviceContacts(contentResolver: ContentResolver): List<DeviceContact> {
    val contacts = mutableListOf<DeviceContact>()
    val seen = mutableSetOf<String>() // deduplicate by phone number

    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER
    )

    val cursor = contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,   // no WHERE filter — fetch all
        null,
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC" // sorted A→Z
    )

    cursor?.use {
        val nameCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val phoneCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)

        while (it.moveToNext()) {
            val name = it.getString(nameCol)?.trim() ?: continue
            val phone = it.getString(phoneCol)?.trim() ?: continue
            val normalised = phone.replace("\\s".toRegex(), "")

            if (normalised !in seen) {
                seen.add(normalised)
                contacts.add(DeviceContact(name = name, phone = phone))
            }
        }
    }
    return contacts
}

// ─────────────────────────────────────────────────────────────────────────────
// Part 2B + 2C: Main "Trusted Family" Screen
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustedFamilyScreen() {
    val context = LocalContext.current
    val bgColor = Color(0xFFF8F9FA)

    // ── State: main trusted-contacts list ────
    val trustedContacts = remember { mutableStateListOf<SafeContact>() }

    LaunchedEffect(Unit) {
        val initialContacts = WhitelistManager.getTrustedContacts(context)
        if (initialContacts.isEmpty()) {
            val defaults = listOf(
                SafeContact("Rahul", "Grandson", "+1 (555) 012-3456"),
                SafeContact("Sarah", "Daughter", "+1 (555) 098-7654")
            )
            trustedContacts.addAll(defaults)
            WhitelistManager.saveTrustedContacts(context, defaults)
        } else {
            trustedContacts.addAll(initialContacts)
        }
    }

    // ── State: bottom-sheet visibility ───────────────────────────────────────
    var showSheet by remember { mutableStateOf(false) }

    // ── State: device contacts (fetched lazily when sheet opens) ─────────────
    var deviceContacts by remember { mutableStateOf<List<DeviceContact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    
    // ── State: which device contacts the user has checked ────────────────────
    val checkedContacts = remember { mutableStateListOf<DeviceContact>() }

    // ── State: permission denied snackbar ────────────────────────────────────
    var showPermissionRationale by remember { mutableStateOf(false) }

    // ─────────────────────────────────────────────────────────────────────────
    // Part 2B: Runtime Permission Launcher for READ_CONTACTS
    // ─────────────────────────────────────────────────────────────────────────
    val contactPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted → fetch contacts and open sheet
            deviceContacts = fetchDeviceContacts(context.contentResolver)
            checkedContacts.clear()
            searchQuery = ""
            showSheet = true
        } else {
            showPermissionRationale = true
        }
    }

    /** Called when user taps "+ Add Safe Number" */
    fun onAddContactsClicked() {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (alreadyGranted) {
            deviceContacts = fetchDeviceContacts(context.contentResolver)
            checkedContacts.clear()
            searchQuery = ""
            showSheet = true
        } else {
            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Part 2C: ModalBottomSheet for contact selection
    // ─────────────────────────────────────────────────────────────────────────
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Header
                Text(
                    text = stringResource(R.string.select_contacts),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1A1A1A)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.choose_people),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                if (deviceContacts.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_contacts),
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        placeholder = { Text(stringResource(R.string.search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    val filteredContacts = deviceContacts.filter { 
                        it.name.contains(searchQuery, ignoreCase = true) || 
                        it.phone.contains(searchQuery)
                    }
                    
                    // Scrollable contact list with checkboxes
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                    ) {
                        items(filteredContacts) { contact ->
                            val isChecked = checkedContacts.contains(contact)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        if (checked) checkedContacts.add(contact)
                                        else checkedContacts.remove(contact)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF004494)
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = contact.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = contact.phone,
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                            HorizontalDivider(color = Color(0xFFEEEEEE))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save Button — adds checked contacts to the trusted list
                Button(
                    onClick = {
                        checkedContacts.forEach { device ->
                            // Avoid duplicates by checking phone number
                            val alreadyAdded = trustedContacts.any { it.phone == device.phone }
                            if (!alreadyAdded) {
                                trustedContacts.add(
                                    SafeContact(
                                        name = device.name,
                                        relation = "Family",   // default label
                                        phone = device.phone
                                    )
                                )
                            }
                        }
                        WhitelistManager.saveTrustedContacts(context, trustedContacts)
                        showSheet = false
                    },
                    enabled = checkedContacts.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004494))
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "${stringResource(R.string.save_label)} ${if (checkedContacts.isEmpty()) "" else "(${checkedContacts.size})"}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Snackbar if permission was denied
    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF004494)) },
            title = { Text(stringResource(R.string.permission_required)) },
            text = {
                Text(stringResource(R.string.contacts_permission_msg))
            },
            confirmButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text(stringResource(R.string.ok), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main Screen Layout
    // ─────────────────────────────────────────────────────────────────────────
    Scaffold(
        bottomBar = { GuardianBottomNav("Contacts") },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            TopHeaderContacts()

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.trusted_family).replace(" ", "\n"),
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF1A1A1A),
                lineHeight = 44.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.trusted_desc),
                fontSize = 18.sp,
                color = Color(0xFF4A4A4A),
                lineHeight = 26.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            // Dynamically rendered trusted contacts list
            trustedContacts.forEach { contact ->
                ContactCard(contact, onDelete = { 
                    trustedContacts.remove(it) 
                    WhitelistManager.saveTrustedContacts(context, trustedContacts)
                })
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // "+ Add Safe Number" → triggers permission then opens bottom sheet
            Button(
                onClick = { onAddContactsClicked() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004494))
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Add", tint = Color.White)
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(R.string.add_safe_number), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(32.dp))
            WhitelistActiveCard()
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Sub-composables
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TopHeaderContacts() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Logo",
                tint = Color(0xFF004494),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.shield_label),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF004494),
                letterSpacing = 1.sp
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.DarkGray)
        }
    }
}

/** Contact card with a swipe-to-delete trash icon */
@Composable
fun ContactCard(contact: SafeContact, onDelete: (SafeContact) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F0F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF004494)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = contact.name.first().uppercaseChar().toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${contact.name} — ${contact.relation}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = contact.phone,
                    fontSize = 15.sp,
                    color = Color.DarkGray,
                    fontWeight = FontWeight.Medium
                )
            }

            IconButton(onClick = { onDelete(contact) }) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Remove",
                    tint = Color(0xFFCC0000)
                )
            }
        }
    }
}

@Composable
fun WhitelistActiveCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF98FB98))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF228B22)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Shield, contentDescription = "Active", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Your Whitelist is Active",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF006400)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unknown numbers are currently being screened by Guardian AI.",
                    fontSize = 15.sp,
                    color = Color(0xFF228B22),
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp
                )
            }
        }
    }
}