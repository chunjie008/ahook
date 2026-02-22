package com.wzh.ai

import android.content.Context
import android.content.SharedPreferences

class PreferencesUtil(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ahook_prefs", Context.MODE_PRIVATE)

    // --- 免责声明相关 ---
    fun hasAgreedToDisclaimer(): Boolean {
        return prefs.getBoolean("has_agreed_to_disclaimer", false)
    }

    fun setAgreedToDisclaimer(agreed: Boolean) {
        prefs.edit().putBoolean("has_agreed_to_disclaimer", agreed).apply()
    }

    // --- MQTT 配置相关 ---
    fun getMqttBrokerUrl(): String {
        return prefs.getString("mqtt_broker_url", "tcp://192.168.50.18:1883") ?: "tcp://192.168.50.18:1883"
    }

    fun setMqttBrokerUrl(url: String) {
        prefs.edit().putString("mqtt_broker_url", url).apply()
    }

    fun getMqttUsername(): String {
        return prefs.getString("mqtt_username", "") ?: ""
    }

    fun setMqttUsername(username: String) {
        prefs.edit().putString("mqtt_username", username).apply()
    }

    fun getMqttPassword(): String {
        return prefs.getString("mqtt_password", "") ?: ""
    }

    fun setMqttPassword(password: String) {
        prefs.edit().putString("mqtt_password", password).apply()
    }

    fun getMqttLogTopicPrefix(): String {
        return prefs.getString("mqtt_log_topic_prefix", "ahook/logs/") ?: "ahook/logs/"
    }

    fun setMqttLogTopicPrefix(prefix: String) {
        prefs.edit().putString("mqtt_log_topic_prefix", prefix).apply()
    }

    fun isMqttEnabled(): Boolean {
        return prefs.getBoolean("mqtt_enabled", false)
    }

    fun setMqttEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("mqtt_enabled", enabled).apply()
    }
}