package com.rizwaan.miniarcade.data.models

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class Player(
    val id: String = "",
    val nickname: String = "",
    val avatarEmoji: String = "🎮",
    val createdAt: Long = System.currentTimeMillis(),
    val totalGamesPlayed: Int = 0,
    val totalScore: Long = 0
) {
    // No-argument constructor required for Firebase
    constructor() : this("", "", "🎮", 0L, 0, 0L)
    
    fun toMap(): Map<String, Any?> {
        return mapOf(
            "id" to id,
            "nickname" to nickname,
            "avatarEmoji" to avatarEmoji,
            "createdAt" to createdAt,
            "totalGamesPlayed" to totalGamesPlayed,
            "totalScore" to totalScore
        )
    }
}
