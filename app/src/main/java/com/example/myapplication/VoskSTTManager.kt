package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/**
 * VoskSTTManager handles background Speech-to-Text.
 * Uses strict 16kHz sampling and VOICE_RECOGNITION source for maximum accuracy.
 */
class VoskSTTManager(
    private val context: Context,
    private val model: Model
) {
    private var recognizer: Recognizer? = null
    private var audioRecord: AudioRecord? = null
    private var recognitionJob: Job? = null

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2

    private val finalTranscript = StringBuilder()

    fun startListening(
        scope: CoroutineScope,
        onUpdate: (liveText: String, isFinal: Boolean, newlySpoken: String) -> Unit
    ) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e("VoskSTTManager", "Missing RECORD_AUDIO permission")
            return
        }

        stopListening()
        finalTranscript.clear()

        try {
            // Strict 16000.0f match for model
            recognizer = Recognizer(model, 16000.0f)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e("VoskSTTManager", "Init failed", e)
            return
        }

        recognitionJob = scope.launch(Dispatchers.IO) {
            val buffer = ShortArray(bufferSize)
            
            while (isActive) {
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (readSize > 0) {
                    val isFinal = recognizer?.acceptWaveForm(buffer, readSize) == true
                    val jsonString = if (isFinal) recognizer?.result else recognizer?.partialResult
                    
                    if (!jsonString.isNullOrBlank()) {
                        handleResult(jsonString, isFinal, onUpdate)
                    }
                } else if (readSize < 0) {
                    restartAudioRecord()
                    delay(1000)
                }
            }
        }
    }

    fun stopListening() {
        recognitionJob?.cancel()
        recognitionJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {}
        audioRecord = null
        
        try {
            recognizer?.close()
        } catch (e: Exception) {}
        recognizer = null
    }

    private suspend fun handleResult(
        jsonString: String, 
        isFinal: Boolean,
        onUpdate: (liveText: String, isFinal: Boolean, newlySpoken: String) -> Unit
    ) {
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
                }
                onUpdate(finalTranscript.toString(), true, text)
            } else {
                val liveDisplay = if (finalTranscript.isEmpty()) text else "$finalTranscript $text"
                onUpdate(liveDisplay, false, text)
            }
        }
    }

    private fun restartAudioRecord() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            audioRecord?.release()
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord?.startRecording()
        } catch (e: Exception) {}
    }
}
