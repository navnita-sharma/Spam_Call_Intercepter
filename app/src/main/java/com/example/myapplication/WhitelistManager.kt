package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object WhitelistManager {
    private const val PREFS_NAME = "whitelist_prefs"
    private const val CONTACTS_KEY = "trusted_contacts_json"

    fun getTrustedContacts(context: Context): List<SafeContact> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(CONTACTS_KEY, "[]")
        val result = mutableListOf<SafeContact>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(SafeContact(
                    name = obj.getString("name"),
                    relation = obj.optString("relation", "Family"),
                    phone = obj.getString("phone")
                ))
            }
        } catch (e: Exception) {}
        return result
    }

    fun saveTrustedContacts(context: Context, contacts: List<SafeContact>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        contacts.forEach { contact ->
            val obj = JSONObject()
            obj.put("name", contact.name)
            obj.put("relation", contact.relation)
            obj.put("phone", contact.phone)
            array.put(obj)
        }
        prefs.edit().putString(CONTACTS_KEY, array.toString()).apply()
    }

    fun isWhitelisted(context: Context, phoneNumber: String): Boolean {
        if (phoneNumber.isBlank()) return false
        val contacts = getTrustedContacts(context)
        val normalizedInput = phoneNumber.replace("\\D".toRegex(), "")
        return contacts.any {
            val normalizedContact = it.phone.replace("\\D".toRegex(), "")
            if (normalizedContact.length > 5 && normalizedInput.length > 5) {
                normalizedContact.endsWith(normalizedInput) || normalizedInput.endsWith(normalizedContact)
            } else {
                normalizedContact == normalizedInput
            }
        }
    }
}
