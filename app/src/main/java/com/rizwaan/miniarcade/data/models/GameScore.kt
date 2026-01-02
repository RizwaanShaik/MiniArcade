package com.rizwaan.miniarcade.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class GameScore(
    val id: String = "",
    val playerId: String = "",
    val playerUsername: String = "",
    val playerAvatar: String = "🎮",   // Avatar emoji
    val gameType: GameType = GameType.REACTION_TIME,
    val score: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val extras: Map<String, Any> = emptyMap()
) {
    // No-argument constructor required for Firebase
    constructor() : this("", "", "", "🎮", GameType.REACTION_TIME, 0, 0L, emptyMap())
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "playerId" to playerId,
            "playerUsername" to playerUsername,
            "playerAvatar" to playerAvatar,
            "gameType" to gameType.name,
            "score" to score,
            "timestamp" to timestamp,
            "extras" to extras
        )
    }
}

enum class GameType(val displayName: String, val emoji: String, val description: String) {
    REACTION_TIME("Reaction Time", "⚡", "Tap as fast as you can!"),
    MEMORY_FLIP("Memory Flip", "🧠", "Match the pairs!"),
    PATTERN_SNAP("Pattern Snap", "🧩", "Remember the pattern!"),
    COLOR_CATCH("Color Catch", "🌈", "Tap the right colors!"),
    WORD_SCRAMBLE("Word Scramble", "📝", "Unscramble the word!"),
    RHYTHM_TAP("Rhythm Tap", "🎵", "Swipe to sort!")
}
