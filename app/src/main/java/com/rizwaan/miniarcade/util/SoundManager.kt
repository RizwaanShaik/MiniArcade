package com.rizwaan.miniarcade.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.rizwaan.miniarcade.data.local.PreferencesManager

/**
 * Manages sound effects and haptic feedback for the game
 */
class SoundManager(private val context: Context) {
    
    private val prefsManager = PreferencesManager(context)
    
    private val soundPool: SoundPool by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
    }
    
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
    
    // Sound IDs (will be 0 if sound file doesn't exist)
    private var tapSoundId: Int = 0
    private var successSoundId: Int = 0
    private var failSoundId: Int = 0
    private var correctSoundId: Int = 0
    private var wrongSoundId: Int = 0
    private var gameOverSoundId: Int = 0
    
    private var isLoaded = false
    
    init {
        loadSounds()
    }
    
    private fun loadSounds() {
        try {
            // Try to load custom sounds from raw folder
            // If files don't exist, we'll use haptic feedback only
            val resources = context.resources
            val packageName = context.packageName
            
            // These will return 0 if resource doesn't exist
            tapSoundId = tryLoadSound("tap")
            successSoundId = tryLoadSound("success")
            failSoundId = tryLoadSound("fail")
            correctSoundId = tryLoadSound("correct")
            wrongSoundId = tryLoadSound("wrong")
            gameOverSoundId = tryLoadSound("game_over")
            
            soundPool.setOnLoadCompleteListener { _, _, status ->
                if (status == 0) isLoaded = true
            }
        } catch (e: Exception) {
            // Sounds not available, will use haptic only
        }
    }
    
    private fun tryLoadSound(name: String): Int {
        return try {
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) soundPool.load(context, resId, 1) else 0
        } catch (e: Exception) {
            0
        }
    }
    
    fun playTap() {
        if (prefsManager.soundEnabled && tapSoundId != 0) {
            soundPool.play(tapSoundId, 0.5f, 0.5f, 1, 0, 1f)
        }
        vibrateLight()
    }
    
    fun playSuccess() {
        if (prefsManager.soundEnabled && successSoundId != 0) {
            soundPool.play(successSoundId, 0.8f, 0.8f, 1, 0, 1f)
        }
        vibrateSuccess()
    }
    
    fun playFail() {
        if (prefsManager.soundEnabled && failSoundId != 0) {
            soundPool.play(failSoundId, 0.8f, 0.8f, 1, 0, 1f)
        }
        vibrateFail()
    }
    
    fun playCorrect() {
        if (prefsManager.soundEnabled && correctSoundId != 0) {
            soundPool.play(correctSoundId, 0.6f, 0.6f, 1, 0, 1f)
        }
        vibrateLight()
    }
    
    fun playWrong() {
        if (prefsManager.soundEnabled && wrongSoundId != 0) {
            soundPool.play(wrongSoundId, 0.6f, 0.6f, 1, 0, 1f)
        }
        vibrateWrong()
    }
    
    fun playGameOver() {
        if (prefsManager.soundEnabled && gameOverSoundId != 0) {
            soundPool.play(gameOverSoundId, 0.8f, 0.8f, 1, 0, 1f)
        }
        vibrateGameOver()
    }
    
    // Haptic feedback methods
    private fun vibrateLight() {
        if (!prefsManager.vibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }
    
    private fun vibrateSuccess() {
        if (!prefsManager.vibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(50)
        }
    }
    
    private fun vibrateFail() {
        if (!prefsManager.vibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100)
        }
    }
    
    private fun vibrateWrong() {
        if (!prefsManager.vibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 50, 50, 50), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 50, 50, 50), -1)
        }
    }
    
    private fun vibrateGameOver() {
        if (!prefsManager.vibrationEnabled) return
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 100, 200), -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 100, 100, 200), -1)
        }
    }
    
    fun release() {
        try {
            soundPool.release()
        } catch (e: Exception) {
            // Ignore
        }
    }
    
    companion object {
        @Volatile
        private var instance: SoundManager? = null
        
        fun getInstance(context: Context): SoundManager {
            return instance ?: synchronized(this) {
                instance ?: SoundManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

