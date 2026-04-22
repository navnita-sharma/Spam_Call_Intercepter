package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            Log.d("CallReceiver", "Phone state changed: $state, Number: $incomingNumber")

            val prefs = context.getSharedPreferences("guardian_prefs", Context.MODE_PRIVATE)
            val isAiListenerOn = prefs.getBoolean("ai_listener_on", false)

            if (!incomingNumber.isNullOrBlank() && state == TelephonyManager.EXTRA_STATE_RINGING) {
                prefs.edit().putString("last_incoming_number", incomingNumber).apply()
            }

            if (!isAiListenerOn) {
                Log.d("CallReceiver", "AI Listener is OFF. Ignoring call.")
                return
            }

            val broadcastIntent = Intent("com.example.guardian.CALL_UPDATE")
            broadcastIntent.setPackage(context.packageName)
            broadcastIntent.putExtra("state", state)
            broadcastIntent.putExtra("number", prefs.getString("last_incoming_number", ""))
            context.sendBroadcast(broadcastIntent)
        }
    }
}
