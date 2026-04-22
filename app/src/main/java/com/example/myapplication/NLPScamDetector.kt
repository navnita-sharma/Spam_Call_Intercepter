package com.example.myapplication

import android.content.Context
import android.util.Log
import kotlin.math.sqrt

/**
 * Result data class for Scam Detection
 */
data class DetectionResult(
    val isScam: Boolean,
    val reason: String = "",
    val method: String = "",
    val confidence: Float = 0f,
    val state: String = "IDLE"
)

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 1: Immediate Keyword Blacklist (catches single dangerous words)
// ─────────────────────────────────────────────────────────────────────────────
object KeywordBlacklist {
    val entries = mapOf(
        "otp" to "Requesting One-Time Password — credential theft attempt.",
        "pin" to "Requesting PIN — credential theft attempt.",
        "gift card" to "Requesting gift card payment — untraceable payment scam.",
        "anydesk" to "Requesting remote access software — tech support scam.",
        "teamviewer" to "Requesting remote access software — tech support scam.",
        "remote access" to "Requesting device remote access — tech support scam.",
        "bitcoin" to "Requesting cryptocurrency payment — untraceable payment scam.",
        "crypto" to "Requesting cryptocurrency payment — untraceable payment scam.",
        "lottery" to "Claiming lottery/prize winnings — advance fee scam.",
        "refund" to "Suspicious refund offer requiring account details.",
        "verify account" to "Phishing attempt to steal login credentials.",
        "social security" to "Suspicious SSN/SSA inquiry — identity theft attempt."
    )

    fun check(text: String): DetectionResult? {
        val lower = text.lowercase()
        for ((keyword, reason) in entries) {
            if (lower.contains(keyword)) {
                Log.d("ScamDetect", "🔴 KEYWORD HIT: '$keyword' in '$text'")
                return DetectionResult(
                    isScam = true, reason = reason,
                    method = "Keyword Blacklist", confidence = 1.0f, state = "SCAM_TRIGGERED"
                )
            }
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 2: Regex Pattern Matching (catches phrases and combinations)
// ─────────────────────────────────────────────────────────────────────────────
object PatternMatcher {
    private val patterns = listOf(
        Regex("(police|fbi|irs|cbi|officer|inspector|customs|department|government|court|judge|magistrate)") to
            "Impersonation of government or law enforcement.",
        Regex("(arrest|warrant|legal action|case filed|sue you|penalty|fine|jail|prison)") to
            "Threatening arrest or legal consequences.",
        Regex("(blocked|frozen|suspended|compromised|unauthorized|hacked|breach|locked)") to
            "Claiming account is compromised or blocked.",
        Regex("(urgent|immediately|right now|hurry|within .* hours|deadline|expire|last chance)") to
            "Creating urgency — high-pressure scam tactic.",
        Regex("(bank manager|bank officer|bank security|account department|credit card department)") to
            "Impersonating bank staff.",
        Regex("(send money|transfer|pay now|wire|payment|withdraw|deposit)") to
            "Requesting money transfer or payment.",
        Regex("(share|give|tell|provide|enter|type).{0,20}(code|password|otp|pin|cvv|ssn|number)") to
            "Requesting sensitive credentials.",
        Regex("(won|winner|prize|selected|lucky|congratulations).{0,30}(money|cash|reward|dollars|rupees)") to
            "Lottery or prize scam.",
        Regex("(download|install).{0,15}(app|application|software|anydesk|teamviewer)") to
            "Requesting installation of remote access software."
    )

    fun check(text: String): DetectionResult? {
        val lower = text.lowercase()
        for ((pattern, reason) in patterns) {
            if (pattern.containsMatchIn(lower)) {
                Log.d("ScamDetect", "🟠 PATTERN HIT: '$reason' in '$text'")
                return DetectionResult(
                    isScam = true, reason = reason,
                    method = "Pattern Match", confidence = 0.90f, state = "SCAM_TRIGGERED"
                )
            }
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 3: FSM (Finite State Machine) Scam Sequence Tracker
// ─────────────────────────────────────────────────────────────────────────────
class ScamFSM {
    enum class State { IDLE, AUTHORITY_CLAIMED, URGENCY_CREATED, SCAM_TRIGGERED }
    var currentState = State.IDLE; private set

    fun transition(intent: String): Boolean {
        val old = currentState
        when (intent) {
            "AUTHORITY" -> {
                if (currentState == State.IDLE) currentState = State.AUTHORITY_CLAIMED
            }
            "URGENCY" -> {
                if (currentState == State.IDLE || currentState == State.AUTHORITY_CLAIMED)
                    currentState = State.URGENCY_CREATED
            }
            "SCAM_ACTION" -> {
                if (currentState != State.IDLE) {
                    currentState = State.SCAM_TRIGGERED; return true
                }
            }
        }
        if (old != currentState) Log.d("ScamFSM", "State: $old → $currentState via $intent")
        return false
    }

    fun reset() { currentState = State.IDLE }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYER 4: Mini-FAISS (Cosine Similarity Vector Search)
// ─────────────────────────────────────────────────────────────────────────────
class MiniFAISS {
    private val vocabulary = listOf(
        "bank","manager","support","police","officer","agent","irs","ssa","customs","department","security","verification","customer","government","court","inspector",
        "blocked","compromised","arrest","warrant","unauthorized","penalty","immediately","urgent","suspended","frozen","hacked","expire","deadline",
        "otp","pin","code","password","gift","card","transfer","send","anydesk","teamviewer","remote","payment","withdraw","bitcoin","wallet","download","install"
    )
    private val authIndices = (0..15).toList()
    private val urgIndices = (16..28).toList()
    private val actIndices = (29..46).toList()

    fun classifyIntent(text: String): String? {
        val tokens = text.lowercase().replace(Regex("[^a-z\\s]"), "").split(Regex("\\s+")).filter { it.length > 1 }
        if (tokens.isEmpty()) return null
        val vec = DoubleArray(vocabulary.size)
        var hits = 0
        tokens.forEach { t -> val i = vocabulary.indexOf(t); if (i != -1) { vec[i] += 1.0; hits++ } }
        if (hits == 0) return null
        val a = score(vec, authIndices); val u = score(vec, urgIndices); val x = score(vec, actIndices)
        Log.d("MiniFAISS", "Scores — Auth:${"%.3f".format(a)} Urg:${"%.3f".format(u)} Act:${"%.3f".format(x)}")
        return when { x > 0.20 -> "SCAM_ACTION"; u > 0.20 -> "URGENCY"; a > 0.20 -> "AUTHORITY"; else -> null }
    }

    private fun score(input: DoubleArray, cluster: List<Int>): Double {
        var dot = 0.0; var norm = 0.0
        for (i in input.indices) { if (input[i] > 0) { norm += input[i] * input[i]; if (i in cluster) dot += input[i] } }
        return if (norm == 0.0) 0.0 else dot / (sqrt(norm) * sqrt(cluster.size.toDouble()))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN ENGINE: NLPScamDetector — wires all 4 layers together
// ─────────────────────────────────────────────────────────────────────────────
class NLPScamDetector(private val context: Context) {

    private val fsm = ScamFSM()
    private val vectorEngine = MiniFAISS()
    private val contextBuffer = StringBuilder()

    /**
     * Multi-layered detection pipeline:
     *   Layer 1: Keyword Blacklist (instant, exact match)
     *   Layer 2: Regex Pattern Matching (phrase-level)
     *   Layer 3+4: FSM + Vector Similarity (sequence-level)
     */
    fun analyzeText(transcript: String): DetectionResult {
        if (transcript.isBlank()) return DetectionResult(false)

        // ── LAYER 1: Immediate keyword match ──
        KeywordBlacklist.check(transcript)?.let { return it }

        // ── LAYER 2: Regex pattern match ──
        PatternMatcher.check(transcript)?.let { return it }

        // ── LAYER 3+4: FSM + Vector Similarity (contextual) ──
        contextBuffer.append(" ").append(transcript)
        if (contextBuffer.length > 500) contextBuffer.delete(0, contextBuffer.length - 500)

        val intent = vectorEngine.classifyIntent(contextBuffer.toString())
        if (intent != null) {
            val triggered = fsm.transition(intent)
            if (triggered) {
                return DetectionResult(
                    isScam = true,
                    reason = "CRITICAL: Multi-stage scam sequence confirmed via semantic analysis.",
                    method = "FSM+Vector", confidence = 0.95f, state = fsm.currentState.name
                )
            }
        }

        return DetectionResult(
            isScam = false,
            state = fsm.currentState.name
        )
    }

    fun reset() {
        fsm.reset()
        contextBuffer.setLength(0)
        Log.d("ScamDetect", "All detection state cleared.")
    }

    fun isModelLoaded(): Boolean = true
    fun close() { reset() }
}
