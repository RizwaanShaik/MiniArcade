package com.rizwaan.miniarcade.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Player(
    val id: String = "",           // Firebase Auth UID
    val email: String = "",        // User's email for login
    val username: String = "",     // Display name (also used for login)
    val avatarEmoji: String = "🎮",
    val createdAt: Long = System.currentTimeMillis(),
    val totalGamesPlayed: Int = 0,
    
    // Best scores for each game (stored directly in player record)
    val reactionTime: Long = 0,      // Best reaction time in ms (lower is better)
    val memoryFlip: Long = 0,        // Best moves count (lower is better)
    val patternSnap: Long = 0,       // Best score (higher is better)
    val colorCatch: Long = 0,        // Best score (higher is better)
    val wordScramble: Long = 0,      // Best score (higher is better)
    val rhythmTap: Long = 0          // Best score (higher is better)
) {
    // No-argument constructor required for Firebase
    constructor() : this("", "", "", "🎮", 0L, 0, 0L, 0L, 0L, 0L, 0L, 0L)
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "email" to email,
            "username" to username,
            "avatarEmoji" to avatarEmoji,
            "createdAt" to createdAt,
            "totalGamesPlayed" to totalGamesPlayed,
            "reactionTime" to reactionTime,
            "memoryFlip" to memoryFlip,
            "patternSnap" to patternSnap,
            "colorCatch" to colorCatch,
            "wordScramble" to wordScramble,
            "rhythmTap" to rhythmTap
        )
    }
    
    /**
     * Get score for a specific game type
     */
    fun getScore(gameType: GameType): Long {
        return when (gameType) {
            GameType.REACTION_TIME -> reactionTime
            GameType.MEMORY_FLIP -> memoryFlip
            GameType.PATTERN_SNAP -> patternSnap
            GameType.COLOR_CATCH -> colorCatch
            GameType.WORD_SCRAMBLE -> wordScramble
            GameType.RHYTHM_TAP -> rhythmTap
        }
    }
}
