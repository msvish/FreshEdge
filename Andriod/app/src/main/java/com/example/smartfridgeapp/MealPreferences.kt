package com.example.smartfridgeapp

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

object MealPreferences {
    private const val PREF_NAME = "meal_prefs"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_BREAKFAST = "breakfast"
    private const val KEY_LUNCH = "lunch"
    private const val KEY_DINNER = "dinner"

    // Firestore: collection "meal_times", document "user_1"
    // (single user app, so one fixed document is fine)
    private const val FS_COLLECTION = "meal_times"
    private const val FS_DOCUMENT   = "user_1"

    fun isSetupDone(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SETUP_DONE, false)
    }

    fun saveMealTimes(
        context: Context,
        breakfast: String,
        lunch: String,
        dinner: String
    ) {
        // ── 1. Save locally (unchanged) ──────────────────────────
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SETUP_DONE, true)
            .putString(KEY_BREAKFAST, breakfast)
            .putString(KEY_LUNCH, lunch)
            .putString(KEY_DINNER, dinner)
            .apply()

        // ── 2. Sync to Firestore so Pi can read it ───────────────
        val db = FirebaseFirestore.getInstance()
        val data = hashMapOf(
            "breakfast"  to breakfast,
            "lunch"      to lunch,
            "dinner"     to dinner,
            "updated_at" to com.google.firebase.Timestamp.now()
        )

        db.collection(FS_COLLECTION)
            .document(FS_DOCUMENT)
            .set(data)
            .addOnSuccessListener {
                Log.d("MealPreferences", "✅ Meal times synced to Firestore")
            }
            .addOnFailureListener { e ->
                Log.e("MealPreferences", "❌ Firestore sync failed: ${e.message}")
            }
    }

    fun getMealTimes(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_BREAKFAST, "08:00") ?: "08:00",
            prefs.getString(KEY_LUNCH,     "12:00") ?: "12:00",
            prefs.getString(KEY_DINNER,    "18:00") ?: "18:00"
        )
    }

    fun clearSetup(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}