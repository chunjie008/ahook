package com.wzh.ai

import android.content.Context
import android.content.SharedPreferences

class PreferencesUtil(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("disclaimer_prefs", Context.MODE_PRIVATE)

    fun hasAgreedToDisclaimer(): Boolean {
        return prefs.getBoolean("has_agreed_to_disclaimer", false)
    }

    fun setAgreedToDisclaimer(agreed: Boolean) {
        prefs.edit().putBoolean("has_agreed_to_disclaimer", agreed).apply()
    }
}