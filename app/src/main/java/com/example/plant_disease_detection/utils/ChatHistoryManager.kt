package com.example.plant_disease_detection.utils

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

object ChatHistoryManager {
    private lateinit var prefs: SharedPreferences

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("PlantChatHistory", Context.MODE_PRIVATE)
    }

    private fun storageKeyForUser(userKey: String): String {
        val safe = userKey.lowercase(Locale.ROOT).trim().replace(Regex("[^a-z0-9]"), "_")
        return "chat_$safe"
    }

    fun load(userKey: String): String {
        return prefs.getString(storageKeyForUser(userKey), null).orEmpty()
    }

    fun save(userKey: String, transcript: String) {
        prefs.edit().putString(storageKeyForUser(userKey), transcript).apply()
    }

    fun clear(userKey: String) {
        prefs.edit().remove(storageKeyForUser(userKey)).apply()
    }
}

