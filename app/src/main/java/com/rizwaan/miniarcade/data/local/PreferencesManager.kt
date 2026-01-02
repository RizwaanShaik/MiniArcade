package com.rizwaan.miniarcade.data.local

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.rizwaan.miniarcade.data.models.Player

class PreferencesManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, 
        Context.MODE_PRIVATE
    )
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "cousin_arcade_prefs"
        private const val KEY_CURRENT_PLAYER = "current_player"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }
    
    var currentPlayer: Player?
        get() {
            val json = prefs.getString(KEY_CURRENT_PLAYER, null)
            return json?.let { gson.fromJson(it, Player::class.java) }
        }
        set(value) {
            val json = value?.let { gson.toJson(it) }
            prefs.edit().putString(KEY_CURRENT_PLAYER, json).apply()
        }
    
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
    
    var vibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()
    
    var isFirstLaunch: Boolean
        get() = prefs.getBoolean(KEY_FIRST_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_LAUNCH, value).apply()
    
    fun isLoggedIn(): Boolean = currentPlayer != null
    
    fun logout() {
        currentPlayer = null
    }
    
    fun clearAll() {
        prefs.edit().clear().apply()
    }
}

