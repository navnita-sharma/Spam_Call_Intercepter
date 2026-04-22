package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.provider.Settings
import android.text.TextUtils
import android.content.ComponentName
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                GuardianDashboardScreen()
            }
        }
    }

    // Helper to check if Accessibility Service is enabled
    fun isAccessibilityServiceEnabled(context: android.content.Context, service: Class<*>): Boolean {
        var accessibilityEnabled = 0
        val serviceStr = ComponentName(context, service).flattenToString()
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )
        } catch (e: Settings.SettingNotFoundException) {
            // Ignore
        }
        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
            if (settingValue != null) {
                val colonSplitter = TextUtils.SimpleStringSplitter(':')
                colonSplitter.setString(settingValue)
                while (colonSplitter.hasNext()) {
                    val accessibilityService = colonSplitter.next()
                    if (accessibilityService.equals(serviceStr, ignoreCase = true)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Root screen — owns the shared isAiListenerOn state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GuardianDashboardScreen() {
    val bgColor = Color(0xFFF7F7F7)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("guardian_prefs", android.content.Context.MODE_PRIVATE) }

    // Lifted state: shared between AiListenerCard and ActionButtons
    var isAiListenerOn by remember { mutableStateOf(prefs.getBoolean("ai_listener_on", false)) }

    Scaffold(
        bottomBar = { GuardianBottomNav() },
        containerColor = bgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            TopHeader()

            Spacer(modifier = Modifier.height(32.dp))
            ProtectionStatus(isAiListenerOn = isAiListenerOn)

            Spacer(modifier = Modifier.height(32.dp))
            // Pass state + toggle callback into the card
            AiListenerCard(
                isOn = isAiListenerOn,
                onToggle = { turnOn ->
                    if (turnOn) {
                        // Check overlay permission
                        if (!android.provider.Settings.canDrawOverlays(context)) {
                            Toast.makeText(context, "Please allow Display Over Other Apps", Toast.LENGTH_LONG).show()
                            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } else if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_PHONE_STATE) != android.content.pm.PackageManager.PERMISSION_GRANTED || 
                                   androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            // If they turn it on, ensure we have PHONE STATE and AUDIO permissions
                            (context as? MainActivity)?.requestPermissions(
                                arrayOf(android.Manifest.permission.READ_PHONE_STATE, android.Manifest.permission.RECORD_AUDIO),
                                101
                            )
                        } else if (context is MainActivity && !context.isAccessibilityServiceEnabled(context, CallOverlayService::class.java)) {
                            // Accessibility Service is required to overlay on real calls and read screen events
                            Toast.makeText(context, "Please enable Guardian Accessibility Service in settings", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } else {
                            isAiListenerOn = true
                            prefs.edit().putBoolean("ai_listener_on", true).apply()
                        }
                    } else {
                        isAiListenerOn = false
                        prefs.edit().putBoolean("ai_listener_on", false).apply()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
            // Pass current state into action buttons
            ActionButtons(isAiListenerOn = isAiListenerOn)

            Spacer(modifier = Modifier.height(32.dp))
            SecurityTipCard()

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Top header
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun TopHeader() {
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
                .background(Color.DarkGray),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Protection status circle — reacts to AI listener state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ProtectionStatus(isAiListenerOn: Boolean) {
    val circleColor by animateColorAsState(
        targetValue = if (isAiListenerOn) Color(0xFF90EE90) else Color(0xFFFFCCCC),
        animationSpec = tween(500), label = "circleColor"
    )
    val iconColor by animateColorAsState(
        targetValue = if (isAiListenerOn) Color(0xFF228B22) else Color(0xFFCC0000),
        animationSpec = tween(500), label = "iconColor"
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(circleColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isAiListenerOn) Icons.Default.Shield else Icons.Default.ShieldMoon,
                contentDescription = "Shield",
                tint = iconColor,
                modifier = Modifier.size(70.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAiListenerOn) stringResource(R.string.protection_active) else stringResource(R.string.protection_paused),
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp,
            color = Color(0xFF1A1A1A)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isAiListenerOn)
                stringResource(R.string.secure_msg)
            else
                stringResource(R.string.paused_msg),
            fontSize = 16.sp,
            color = Color.DarkGray
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AI Listener card — now a real interactive toggle
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AiListenerCard(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    // Animated thumb position: 0f = left (OFF), 1f = right (ON)
    val thumbOffset by animateFloatAsState(
        targetValue = if (isOn) 1f else 0f,
        animationSpec = tween(300), label = "thumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF004494) else Color(0xFF9E9E9E),
        animationSpec = tween(300), label = "trackColor"
    )
    val badgeColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF90EE90) else Color(0xFFFFCCCC),
        animationSpec = tween(300), label = "badgeColor"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (isOn) Color(0xFF228B22) else Color(0xFFCC0000),
        animationSpec = tween(300), label = "badgeTextColor"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.ai_listener),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = badgeColor
                ) {
                    Text(
                        text = if (isOn) stringResource(R.string.on_label) else stringResource(R.string.off_label),
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Tappable animated toggle track ────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(trackColor)
                    .clickable { onToggle(!isOn) },   // ← toggle on tap
                contentAlignment = Alignment.CenterStart
            ) {
                // OFF / ON labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.off_label),
                        color = if (!isOn) Color.White else Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.on_label),
                        color = if (isOn) Color.White else Color.White.copy(alpha = 0.4f),
                        fontWeight = FontWeight.Bold
                    )
                }

                // Animated thumb — slides left↔right based on thumbOffset
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val thumbDp = 48.dp
                    val maxOffsetDp = maxWidth - thumbDp - 8.dp
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp + (maxOffsetDp * thumbOffset))
                            .size(thumbDp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isOn) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Mic",
                            tint = trackColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isOn)
                    stringResource(R.string.ai_monitoring_msg)
                else
                    stringResource(R.string.tap_to_activate_msg),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Action buttons — "Test a Call" now checks AI listener state
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ActionButtons(isAiListenerOn: Boolean) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Trusted Family ────────────────────────────────────────────────────
        Button(
            onClick = {
                context.startActivity(Intent(context, ContactsActivity::class.java))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90EE90))
        ) {
            Icon(Icons.Default.Group, contentDescription = "Trusted", tint = Color(0xFF228B22))
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.trusted_family),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF228B22)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Test a Call ───────────────────────────────────────────────────────
        Button(
            onClick = {
                if (isAiListenerOn) {
                    // AI is ON → launch the simulated call screen
                    context.startActivity(
                        Intent(context, SimulatedCallActivity::class.java)
                    )
                } else {
                    // AI is OFF → inform the user
                    Toast.makeText(
                        context,
                        context.getString(R.string.turn_on_ai_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isAiListenerOn) Color(0xFF004494) else Color(0xFF9E9E9E)
            )
        ) {
            Icon(
                imageVector = if (isAiListenerOn) Icons.Default.Phone else Icons.Default.PhoneLocked,
                contentDescription = "Test Call",
                tint = Color.White
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                stringResource(R.string.test_call_label),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Security tip card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SecurityTipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFEBEBEB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Security Tip",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Never share your personal banking PIN with anyone over the phone, " +
                       "even if they claim to be from \"Guardian\" or your bank.",
                fontSize = 16.sp,
                color = Color(0xFF4A4A4A),
                lineHeight = 24.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFDFDFDF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = "Tip",
                    tint = Color(0xFFCC0000),
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom navigation bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun GuardianBottomNav(currentScreen: String = "Dashboard") {
    val context = LocalContext.current

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Shield, contentDescription = stringResource(R.string.dashboard)) },
            label = { Text(stringResource(R.string.dashboard), fontWeight = if (currentScreen == "Dashboard") FontWeight.Bold else FontWeight.Normal) },
            selected = currentScreen == "Dashboard",
            onClick = { 
                if (currentScreen != "Dashboard") {
                    val intent = Intent(context, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color(0xFF004494),
                indicatorColor = Color(0xFF004494)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Group, contentDescription = stringResource(R.string.contacts)) },
            label = { Text(stringResource(R.string.contacts), fontWeight = if (currentScreen == "Contacts") FontWeight.Bold else FontWeight.Normal) },
            selected = currentScreen == "Contacts",
            onClick = {
                if (currentScreen != "Contacts") {
                    val intent = Intent(context, ContactsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color(0xFF004494),
                indicatorColor = Color(0xFF004494)
            )
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings)) },
            label = { Text(stringResource(R.string.settings), fontWeight = if (currentScreen == "Settings") FontWeight.Bold else FontWeight.Normal) },
            selected = currentScreen == "Settings",
            onClick = { 
                if (currentScreen != "Settings") {
                    val intent = Intent(context, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                }
            },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = Color.White,
                selectedTextColor = Color(0xFF004494),
                indicatorColor = Color(0xFF004494)
            )
        )
    }
}