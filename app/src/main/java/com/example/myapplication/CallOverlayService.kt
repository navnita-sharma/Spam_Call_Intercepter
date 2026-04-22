package com.example.myapplication

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.media.MediaPlayer
import android.net.Uri
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService

/**
 * Modern Call Overlay Service using Jetpack Compose.
 * Implements draggable WindowManager overlay, auto-scrolling transcript,
 * and strict state management to ensure fresh data per call.
 */
class CallOverlayService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var overlayComposeView: ComposeView? = null
    private var windowParams: WindowManager.LayoutParams? = null
    
    // UI State
    private val liveTranscriptState = mutableStateOf("")
    private val isScamState = mutableStateOf(false)
    private val scamReasonState = mutableStateOf("")
    private val isWhitelistedState = mutableStateOf(false)

    private val finalTranscript = StringBuilder()
    private var audioRecord: AudioRecord? = null
    private var recognizer: Recognizer? = null
    private var model: Model? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var recognitionJob: Job? = null
    
    // Lifecycle Owners for Compose support
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val viewModelStore: ViewModelStore = store
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var nlpDetector: NLPScamDetector? = null
    private var callUpdateReceiver: BroadcastReceiver? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        nlpDetector = NLPScamDetector(this)

        callUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val state = intent?.getStringExtra("state")
                val incomingNumber = intent?.getStringExtra("number") ?: ""
                when (state) {
                    TelephonyManager.EXTRA_STATE_RINGING,
                    TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                        isWhitelistedState.value = WhitelistManager.isWhitelisted(this@CallOverlayService, incomingNumber)
                        if (overlayComposeView == null) {
                            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                            resetCallUIState()
                            showOverlay()
                            if (!isWhitelistedState.value) {
                                startVosk()
                            } else {
                                updateOverlayUi(getString(R.string.scanner_disabled), false, "")
                            }
                        } else {
                            if (isWhitelistedState.value) {
                                stopListening()
                                updateOverlayUi(getString(R.string.scanner_disabled), false, "")
                            }
                        }
                        if (state == TelephonyManager.EXTRA_STATE_OFFHOOK && !isWhitelistedState.value) {
                            try {
                                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager.isSpeakerphoneOn = true
                                restartAudioRecord()
                            } catch (e: Exception) {
                                Log.e("CallOverlayService", "Audio routing failed", e)
                            }
                        }
                    }
                    TelephonyManager.EXTRA_STATE_IDLE -> {
                        stopListening()
                        // RESET FSM on hangup
                        nlpDetector?.reset()
                        stopAlerts()
                        try {
                            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            audioManager.mode = AudioManager.MODE_NORMAL
                            audioManager.isSpeakerphoneOn = false
                            audioManager.isMicrophoneMute = false
                        } catch (e: Exception) {}
                        
                        overlayComposeView?.let {
                            try { windowManager.removeView(it) } catch (e: Exception) {}
                        }
                        overlayComposeView = null
                    }
                }
            }
        }
        val filter = IntentFilter("com.example.guardian.CALL_UPDATE")
        registerReceiver(callUpdateReceiver, filter)
    }

    private fun resetCallUIState() {
        finalTranscript.setLength(0)
        liveTranscriptState.value = "Scanning call audio..."
        isScamState.value = false
        scamReasonState.value = ""
        // RESET FSM for new call context
        nlpDetector?.reset()
        initAlertResources()
    }

    private fun initAlertResources() {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val uriString = sharedPref.getString("customAlertUri", null)
        
        try {
            mediaPlayer?.release()
            mediaPlayer = if (uriString != null) {
                MediaPlayer().apply {
                    setDataSource(this@CallOverlayService, Uri.parse(uriString))
                    prepare()
                }
            } else {
                // Fallback to system default notification sound if raw/default_alert is missing
                val defaultUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                MediaPlayer().apply {
                    setDataSource(this@CallOverlayService, defaultUri)
                    prepare()
                }
            }
            mediaPlayer?.isLooping = true
        } catch (e: Exception) {
            Log.e("CallOverlayService", "Sound init failed: $e")
            if (uriString != null) {
                 // Try fallback to system default
                 try {
                     val defaultUri = android.provider.Settings.System.DEFAULT_NOTIFICATION_URI
                     mediaPlayer = MediaPlayer().apply {
                         setDataSource(this@CallOverlayService, defaultUri)
                         prepare()
                     }
                     mediaPlayer?.isLooping = true
                 } catch (ex: Exception) {
                     Log.e("CallOverlayService", "System sound fallback failed", ex)
                 }
            }
        }
        
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private fun startAlerts() {
        try {
            mediaPlayer?.start()
            val pattern = longArrayOf(0, 500, 200)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e("CallOverlayService", "Alert start failed", e)
        }
    }

    private fun stopAlerts() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {}
    }

    private fun showOverlay() {
        windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        overlayComposeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CallOverlayService)
            setViewTreeViewModelStoreOwner(this@CallOverlayService)
            setViewTreeSavedStateRegistryOwner(this@CallOverlayService)

            setContent {
                MaterialTheme {
                    ScamOverlayUI(
                        transcript = liveTranscriptState.value,
                        isScam = isScamState.value,
                        reason = scamReasonState.value,
                        isWhitelisted = isWhitelistedState.value,
                        onDrag = { dx, dy -> updateWindowPosition(dx, dy) }
                    )
                }
            }
        }
        windowManager.addView(overlayComposeView, windowParams)
    }

    private fun updateWindowPosition(dx: Int, dy: Int) {
        windowParams?.let { params ->
            params.x += dx
            params.y += dy
            // FIX: Ensure the actual WindowManager window moves
            windowManager.updateViewLayout(overlayComposeView, params)
        }
    }

    @Composable
    fun ScamOverlayUI(transcript: String, isScam: Boolean, reason: String, isWhitelisted: Boolean, onDrag: (Int, Int) -> Unit) {
        val scrollState = rememberScrollState()
        
        // Auto-scroll logic
        LaunchedEffect(transcript) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        Card(
            modifier = Modifier
                .padding(16.dp)
                .heightIn(max = 280.dp)
                .width(320.dp)
                // DRAGGABLE HOOK
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                    }
                },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isScam) Color(0xFFC62828) else Color(0xFAF4F4F4)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isWhitelisted) "Trusted Safe Caller" else if (isScam) "🚨 THREAT DETECTED" else "Shield AI Active",
                        color = if (isScam) Color.White else Color(0xFF2E7D32),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.weight(1f))
                    if (!isScam) {
                        Surface(
                            color = Color(0xFF2E7D32),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("SECURE", color = Color.White, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                    }
                }
                
                if (isScam || reason.isNotEmpty()) {
                    Text(
                        text = if (isScam) "Triggered: $reason" else "Status: $reason",
                        color = if (isScam) Color.White else Color(0xFF2E7D32),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                if (isWhitelisted) {
                    Text(
                        text = "Call scanning bypassed for Trusted Contact.",
                        fontSize = 14.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    Box(modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = transcript,
                            color = if (isScam) Color.White else Color.Black,
                            fontSize = 16.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                }
            }
        }
    }

    private fun updateOverlayUi(transcript: String, isScam: Boolean, reason: String) {
        Handler(Looper.getMainLooper()).post {
            liveTranscriptState.value = transcript
            isScamState.value = isScam
            scamReasonState.value = reason
        }
    }

    private fun startVosk() {
        StorageService.unpack(this, "model-en-in", "model",
            { model ->
                this.model = model
                startListening()
            },
            { exception ->
                Log.e("CallOverlayService", "Model failed", exception)
                updateOverlayUi("AI Engine Error.", false, "")
            })
    }

    private fun startListening() {
        val loadedModel = model ?: return
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2

        try {
            recognizer = Recognizer(loadedModel, 16000.0f)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            audioRecord?.startRecording()
            updateOverlayUi("Scanning live call...", false, "")

            recognitionJob = serviceScope.launch {
                val buffer = ShortArray(bufferSize)
                while (isActive) {
                    val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readSize > 0) {
                        val isFinal = recognizer?.acceptWaveForm(buffer, readSize) == true
                        val jsonString = if (isFinal) recognizer?.result else recognizer?.partialResult
                        handleResult(jsonString, isFinal)
                    } else if (readSize < 0) {
                        restartAudioRecord()
                        delay(1000)
                    }
                }
            }
        } catch (e: Exception) {
            updateOverlayUi("Microphone access blocked.", false, "")
        }
    }

    private fun restartAudioRecord() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val sampleRate = 16000
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT) * 2
        try {
            audioRecord?.release()
            audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            audioRecord?.startRecording()
        } catch (e: Exception) {}
    }

    private suspend fun handleResult(jsonString: String?, isFinal: Boolean) {
        if (jsonString.isNullOrBlank()) return
        val text = try {
            val obj = JSONObject(jsonString)
            if (obj.has("text")) obj.getString("text")
            else if (obj.has("partial")) obj.getString("partial")
            else ""
        } catch (e: Exception) { "" }

        withContext(Dispatchers.Main) {
            if (isFinal) {
                if (text.isNotBlank()) {
                    if (finalTranscript.isNotEmpty()) finalTranscript.append(" ")
                    finalTranscript.append(text)
                    checkForScam(text)
                }
                updateOverlayUi(finalTranscript.toString(), isScamState.value, scamReasonState.value)
            } else {
                val current = if (finalTranscript.isEmpty()) text else "$finalTranscript $text"
                updateOverlayUi(current, isScamState.value, scamReasonState.value)
                if (text.isNotBlank()) checkForScam(text)
            }
        }
    }

    private fun checkForScam(text: String) {
        val result = nlpDetector?.analyzeText(text) ?: return
        
        // Update the UI with the latest reason/warning from the FSM
        if (result.reason.isNotEmpty() && !isScamState.value) {
            Handler(Looper.getMainLooper()).post {
                scamReasonState.value = result.reason
            }
        }

        if (result.isScam) {
            if (!isScamState.value) {
                Handler(Looper.getMainLooper()).post {
                    isScamState.value = true
                    scamReasonState.value = result.reason
                }
                try {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    audioManager.isMicrophoneMute = true
                    Log.w("CallOverlayService", "Hardware MUTE activated: ${result.reason}")
                    startAlerts()
                } catch (e: Exception) {}
            }
        }
    }

    private fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
        try { recognizer?.close() } catch (e: Exception) {}
        recognizer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        serviceScope.cancel()
        callUpdateReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        stopListening()
        stopAlerts()
        overlayComposeView?.let { try { windowManager.removeView(it) } catch (e: Exception) {} }
    }
}


