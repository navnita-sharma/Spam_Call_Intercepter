package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F7F7)) {
                    SettingsScreen(onBack = { finish() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE) }
    
    // Read the current font scale, or use default (1f)
    var fontScale by remember { mutableFloatStateOf(sharedPref.getFloat("fontScale", 1.0f)) }

    Scaffold(
        bottomBar = { GuardianBottomNav("Settings") },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF333333)
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // Font Scale Slider
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.TextFields, contentDescription = stringResource(R.string.app_font_size), tint = Color(0xFF004494))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.app_font_size), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${stringResource(R.string.scale_label)}: ${"%.1f".format(fontScale)}x", fontSize = 14.sp, color = Color.Gray)
                    Slider(
                        value = fontScale,
                        onValueChange = { 
                            fontScale = it 
                            sharedPref.edit().putFloat("fontScale", it).apply()
                        },
                        valueRange = 0.8f..1.5f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.preview_text),
                        fontSize = (14 * fontScale).sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // Language Selection
            var expanded by remember { mutableStateOf(false) }
            val currentLanguageCode = remember { AppCompatDelegate.getApplicationLocales().toLanguageTags().let { if (it.isEmpty()) "en" else it } }
            
            val languages = listOf(
                "en" to "English",
                "hi" to "Hindi (हिंदी)",
                "mr" to "Marathi (मराठी)",
                "bn" to "Bengali (বাংলা)"
            )
            
            val selectedLanguage = languages.find { it.first == currentLanguageCode }?.second ?: "English"

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = stringResource(R.string.app_language), tint = Color(0xFF004494))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.app_language), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedLanguage,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.select_language)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            languages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        expanded = false
                                        val appLocale = LocaleListCompat.forLanguageTags(code)
                                        AppCompatDelegate.setApplicationLocales(appLocale)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Scam Alert Sound Settings
            var customAlertUriString by remember { mutableStateOf(sharedPref.getString("customAlertUri", null)) }
            
            val openDocumentLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                uri?.let {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        sharedPref.edit().putString("customAlertUri", it.toString()).apply()
                        customAlertUriString = it.toString()
                        Toast.makeText(context, "Alert sound updated!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not persist permission: $e", Toast.LENGTH_LONG).show()
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Notifications, contentDescription = "Alert", tint = Color(0xFF004494))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Scam Alert Sound", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text(
                        text = if (customAlertUriString == null) "Current: Default Siren" else "Current: Custom Audio File",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    if (customAlertUriString != null) {
                        Text(
                            text = Uri.parse(customAlertUriString).lastPathSegment ?: "Custom file",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row {
                        Button(
                            onClick = { openDocumentLauncher.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF004494)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Pick Custom Audio")
                        }
                        
                        if (customAlertUriString != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    sharedPref.edit().remove("customAlertUri").apply()
                                    customAlertUriString = null
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Reset")
                            }
                        }
                    }
                }
            }
        }
    }
}
