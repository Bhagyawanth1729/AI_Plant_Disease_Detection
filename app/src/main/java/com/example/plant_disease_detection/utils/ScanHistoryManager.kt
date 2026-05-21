package com.example.plant_disease_detection.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

data class ScanHistoryEntry(
    val timestampMillis: Long = System.currentTimeMillis(),
    val imageUri: String,
    val disease: String,
    val confidencePercent: Float = 0f,
    val sourceLabel: String? = null
)

object ScanHistoryManager {
    private lateinit var prefs: SharedPreferences
    private val gson = Gson()
    private const val MAX_ENTRIES = 200

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("PlantScanHistory", Context.MODE_PRIVATE)
    }

    private fun storageKeyForUser(userKey: String): String {
        val safe = userKey.lowercase(Locale.ROOT).trim().replace(Regex("[^a-z0-9]"), "_")
        return "scans_$safe"
    }

    fun loadList(userKey: String): List<ScanHistoryEntry> {
        val json = prefs.getString(storageKeyForUser(userKey), null) ?: return emptyList()
        val type = object : TypeToken<List<ScanHistoryEntry>>() {}.type
        return try {
            gson.fromJson<List<ScanHistoryEntry>>(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addEntry(userKey: String, entry: ScanHistoryEntry) {
        val next = loadList(userKey).toMutableList()
        next.add(0, entry)
        while (next.size > MAX_ENTRIES) {
            next.removeAt(next.size - 1)
        }
        prefs.edit().putString(storageKeyForUser(userKey), gson.toJson(next)).apply()
    }

    fun clear(userKey: String) {
        prefs.edit().remove(storageKeyForUser(userKey)).apply()
    }
}

