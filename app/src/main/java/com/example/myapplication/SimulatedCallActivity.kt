package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.vosk.Model
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * SimulatedCallActivity: Verified Scam Interceptor
 * 
 * Uses Multi-Layered Detection:
 * 1. NLP Semantic Analysis (TFLite)
 * 2. Keyword Combination Logic
 * 3. Suspicious Word Blacklist
 */
class SimulatedCallActivity : ComponentActivity(), RecognitionListener {

    private var speechService: SpeechService? = null
    private var model: Model? = null
    private lateinit var audioManager: AudioManager
    
    // THE NLP BRAIN
    private var nlpDetector: NLPScamDetector? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: android.os.Vibrator? = null

    // Compose State
    private var liveTranscript by mutableStateOf("")
    private var isScamDetected by mutableStateOf(false)
    private var isWhitelisted by mutableStateOf(false)
    private var detectionReason by mutableStateOf("")
    private var isMuted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        val incomingNumber = intent.getStringExtra("number") ?: "Unknown"
        isWhitelisted = WhitelistManager.isWhitelisted(this, incomingNumber)

        if (!isWhitelisted) {
            liveTranscript = getString(R.string.shield_label)
            nlpDetector = NLPScamDetector(this)
            initVosk()
            
            // Load custom alert sound
            val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
            val customUriString = sharedPref.getString("customAlertUri", null)
            initMediaPlayer(customUriString)
        } else {
            liveTranscript = getString(R.string.scanner_disabled)
        }
        
        // Always reset mic state on start
        audioManager.isMicrophoneMute = false

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF0F0F0F)) {
                    SimulatedCallScreen(incomingNumber)
                }
            }
        }
    }

    // --- Multi-Layered Detection System ---

    private fun checkForThreats(text: String) {
        val input = text.lowercase()
        if (input.isBlank()) return

        // 1. Run the Multi-Layered NLP Scam Detector
        val nlpResult = nlpDetector?.analyzeText(text)
        
        if (nlpResult?.isScam == true) {
            Log.d("ScamDetectionCheck", "✅ THREAT IDENTIFIED via ${nlpResult.method}: ${nlpResult.reason}")
            triggerAlert("${nlpResult.method}: ${nlpResult.reason}")
            return
        }
    }

    private fun triggerAlert(reason: String) {
        if (!isScamDetected) {
            runOnUiThread {
                isScamDetected = true
                detectionReason = reason
                isMuted = true
                audioManager.isMicrophoneMute = true // AUTOMATIC MUTE
                
                // Play the alert sound
                try {
                    mediaPlayer?.start()
                    vibrator = getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    val pattern = longArrayOf(0, 500, 200)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator?.vibrate(pattern, 0)
                    }
                } catch (e: Exception) {
                    Log.e("AudioAlert", "Playback failed", e)
                }
            }
        }
    }

    private fun initMediaPlayer(uriString: String?) {
        try {
            mediaPlayer = if (uriString != null) {
                MediaPlayer().apply {
                    setDataSource(this@SimulatedCallActivity, Uri.parse(uriString))
                    prepare()
                }
            } else {
                // Fallback to system default notification sound if raw/default_alert is missing
                val defaultUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                MediaPlayer().apply {
                    setDataSource(this@SimulatedCallActivity, defaultUri)
                    prepare()
                }
            }
            mediaPlayer?.isLooping = true
        } catch (e: Exception) {
            Log.e("AudioAlert", "MediaPlayer init failed: $e")
            // Attempt fallback to default if custom failed
            if (uriString != null) initMediaPlayer(null)
        }
    }

    // --- Vosk Callbacks ---

    private fun initVosk() {
        StorageService.unpack(this, "model-en-in", "model",
            { voskModel -> this.model = voskModel; startSpeechService() },
            { e -> liveTranscript = "Error: Local AI missing" }
        )
    }

    private fun startSpeechService() {
        model?.let {
            try {
                val rec = org.vosk.Recognizer(it, 16000.0f)
                speechService = SpeechService(rec, 16000.0f)
                speechService?.startListening(this)
                liveTranscript = "Call Active: Listening for threats..."
            } catch (e: Exception) { Log.e("Vosk", "Start failed", e) }
        }
    }

    override fun onPartialResult(hypothesis: String) {
        val text = parseJson(hypothesis, "partial")
        if (text.isNotEmpty()) {
            liveTranscript = text
            checkForThreats(text) // Check every semi-sentence
        }
    }

    override fun onResult(hypothesis: String) {
        val text = parseJson(hypothesis, "text")
        if (text.isNotEmpty()) {
            liveTranscript = text
            checkForThreats(text)
        }
    }

    override fun onFinalResult(hypothesis: String) {
        val text = parseJson(hypothesis, "text")
        if (text.isNotEmpty()) {
            liveTranscript = text
            checkForThreats(text)
        }
    }

    private fun parseJson(json: String, key: String): String = 
        try { JSONObject(json).optString(key, "") } catch (e: Exception) { "" }

    override fun onError(e: Exception) { Log.e("Vosk", "Error", e) }
    override fun onTimeout() {}

    private fun handleHangUp() {
        speechService?.stop()
        speechService?.shutdown()
        nlpDetector?.close()
        audioManager.isMicrophoneMute = false
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechService?.stop()
        speechService?.shutdown()
        nlpDetector?.close()
        
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    // --- Compose UI ---

    @Composable
    fun SimulatedCallScreen(incomingNumber: String) {
        val context = LocalContext.current
        var callTimer by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                callTimer++
            }
        }

        // Permissions
        val micReq = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }
        LaunchedEffect(Unit) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                micReq.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // Main Call UI
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                Icon(Icons.Default.AccountCircle, null, tint = if (isWhitelisted) Color(0xFF2E7D32) else Color.DarkGray, modifier = Modifier.size(110.dp))
                Spacer(modifier = Modifier.height(20.dp))
                if (isWhitelisted) {
                    Text("TRUSTED CONTACT", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFF2E7D32))
                    Text(incomingNumber, color = Color.Gray, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(String.format("%02d:%02d", callTimer / 60, callTimer % 60), color = Color.Gray, fontSize = 16.sp)
                } else {
                    Text("UNKNOWN NUMBER", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White)
                    Text(String.format("%02d:%02d", callTimer / 60, callTimer % 60), color = Color.Gray, fontSize = 16.sp)

                    // NLP Status Badge (For Verification)
                    val nlpStatus = "4-LAYER ENGINE ACTIVE"
                    Surface(
                        color = Color.DarkGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text(
                            nlpStatus, color = Color.Green,
                            fontSize = 10.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Transcript Area
                    Card(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                        shape = RoundedCornerShape(32.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "\"$liveTranscript\"",
                                color = Color.White,
                                fontSize = 19.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 28.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(if (isWhitelisted) 1f else 1.3f))

                IconButton(
                    onClick = { handleHangUp() },
                    modifier = Modifier.size(85.dp).clip(CircleShape).background(Color(0xFFB71C1C))
                ) {
                    Icon(Icons.Default.CallEnd, null, tint = Color.White, modifier = Modifier.size(38.dp))
                }
                Spacer(modifier = Modifier.height(40.dp))
            }

            // --- THE BRIGHT RED ALERT OVERLAY ---
            if (isScamDetected) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Red).padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.GppBad, null, tint = Color.White, modifier = Modifier.size(160.dp))
                        Spacer(modifier = Modifier.height(30.dp))
                        Text(
                            text = "SCAM",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "DETECTED",
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            lineHeight = 36.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "DO NOT SHARE OTP",
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center
                        )
                        
                        // DISPLAY WHY IT IS SPAM
                        Surface(
                            color = Color.Black.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.padding(vertical = 24.dp)
                        ) {
                            Text(
                                "REASON: $detectionReason",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center
                            )
                        }

                        Text("MIC AUTOMATICALLY MUTED", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(50.dp))
                        
                        Button(
                            onClick = { handleHangUp() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth().height(70.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("DISCONNECT NOW", color = Color.Red, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}
