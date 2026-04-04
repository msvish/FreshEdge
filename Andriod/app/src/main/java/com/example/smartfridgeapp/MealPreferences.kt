package com.example.smartfridgeapp

import android.content.Context

object MealPreferences {
    private const val PREF_NAME = "meal_prefs"
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_BREAKFAST = "breakfast"
    private const val KEY_LUNCH = "lunch"
    private const val KEY_DINNER = "dinner"

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
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SETUP_DONE, true)
            .putString(KEY_BREAKFAST, breakfast)
            .putString(KEY_LUNCH, lunch)
            .putString(KEY_DINNER, dinner)
            .apply()
    }

    fun getMealTimes(context: Context): Triple<String, String, String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return Triple(
            prefs.getString(KEY_BREAKFAST, "08:00") ?: "08:00",
            prefs.getString(KEY_LUNCH, "12:00") ?: "12:00",
            prefs.getString(KEY_DINNER, "18:00") ?: "18:00"
        )
    }

    fun clearSetup(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }
}