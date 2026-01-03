package com.rizwaan.miniarcade.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.models.Player
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class FirebaseRepository {
    
    companion object {
        private const val TAG = "FirebaseRepository"
        private const val DATABASE_URL = "https://miniarcade-rushmalai-default-rtdb.asia-southeast1.firebasedatabase.app/"
    }
    
    private val database: FirebaseDatabase? by lazy {
        try {
            Log.d(TAG, "Getting Firebase Database instance")
            // Persistence is enabled in MiniArcadeApplication
            FirebaseDatabase.getInstance(DATABASE_URL)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Database not configured", e)
            null
        }
    }
    
    private val auth: FirebaseAuth? by lazy {
        try {
            Log.d(TAG, "Initializing Firebase Auth")
            val firebaseAuth = FirebaseAuth.getInstance()
            Log.d(TAG, "Firebase Auth initialized, current user: ${firebaseAuth.currentUser?.uid}")
            firebaseAuth
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth not configured", e)
            null
        }
    }
    
    private val playersRef get() = try { database?.getReference("players") } catch (e: Exception) { null }
    private val leaderboardRef get() = try { database?.getReference("leaderboard") } catch (e: Exception) { null }
    
    val isAvailable: Boolean get() = try { database != null && auth != null } catch (e: Exception) { false }
    
    suspend fun ensureAuthenticated(): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Log.e(TAG, "Firebase Auth is null - not initialized")
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            
            if (firebaseAuth.currentUser != null) {
                Log.d(TAG, "Already authenticated as: ${firebaseAuth.currentUser?.uid}")
                cont.resume(true)
                return@suspendCancellableCoroutine
            }
            
            Log.d(TAG, "Attempting anonymous sign-in...")
            firebaseAuth.signInAnonymously()
                .addOnSuccessListener { result ->
                    Log.d(TAG, "Anonymous sign-in successful! UID: ${result.user?.uid}")
                    cont.resume(true)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Anonymous sign-in FAILED: ${exception.message}", exception)
                    // Common issue: Anonymous auth not enabled in Firebase Console
                    Log.e(TAG, "Make sure Anonymous Authentication is ENABLED in Firebase Console!")
                    cont.resume(false) 
                }
        } catch (e: Exception) {
            Log.e(TAG, "Auth exception: ${e.message}", e)
            cont.resume(false)
        }
    }
    
    suspend fun isUsernameAvailable(username: String): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val ref = playersRef
            if (ref == null) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }
            
            ref.orderByChild("username").equalTo(username.lowercase())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(!snapshot.exists())
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(true)
                    }
                })
        } catch (e: Exception) {
            cont.resume(true)
        }
    }
    
    suspend fun getPlayerByUsername(username: String): Player? = suspendCancellableCoroutine { cont ->
        try {
            val ref = playersRef
            if (ref == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            
            ref.orderByChild("username").equalTo(username.lowercase())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val player = snapshot.children.firstOrNull()?.getValue(Player::class.java)
                        cont.resume(player)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(null)
                    }
                })
        } catch (e: Exception) {
            cont.resume(null)
        }
    }
    
    // ==================== EMAIL/PASSWORD AUTHENTICATION ====================
    
    /**
     * Register a new user with email and password
     */
    suspend fun registerWithEmail(email: String, password: String, username: String, avatarEmoji: String): Player? = suspendCancellableCoroutine { cont ->
        try {
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Log.e(TAG, "Firebase Auth is null")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid != null) {
                        // Create player profile in database
                        val player = Player(
                            id = uid,
                            email = email.lowercase(),
                            username = username.lowercase(),
                            avatarEmoji = avatarEmoji,
                            createdAt = System.currentTimeMillis()
                        )
                        
                        playersRef?.child(uid)?.setValue(player.toMap())
                            ?.addOnSuccessListener {
                                Log.d(TAG, "Player profile created for: $username")
                                cont.resume(player)
                            }
                            ?.addOnFailureListener { e ->
                                Log.e(TAG, "Failed to create player profile", e)
                                cont.resume(null)
                            }
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Registration failed: ${e.message}", e)
                    cont.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Register exception: ${e.message}", e)
            cont.resume(null)
        }
    }
    
    /**
     * Login with email and password
     */
    suspend fun loginWithEmail(email: String, password: String): Player? = suspendCancellableCoroutine { cont ->
        try {
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Log.e(TAG, "Firebase Auth is null")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid
                    if (uid != null) {
                        // Fetch player profile from database
                        playersRef?.child(uid)?.addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                val player = snapshot.getValue(Player::class.java)
                                if (player != null) {
                                    Log.d(TAG, "Login successful for: ${player.username}")
                                    cont.resume(player)
                                } else {
                                    Log.e(TAG, "Player profile not found for UID: $uid")
                                    cont.resume(null)
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {
                                Log.e(TAG, "Failed to fetch player profile", error.toException())
                                cont.resume(null)
                            }
                        })
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Login failed: ${e.message}", e)
                    cont.resume(null)
                }
        } catch (e: Exception) {
            Log.e(TAG, "Login exception: ${e.message}", e)
            cont.resume(null)
        }
    }
    
    /**
     * Login with username and password
     * First looks up the email by username, then logs in with email
     */
    suspend fun loginWithUsername(username: String, password: String): Player? {
        try {
            // Find player by username to get their email
            val player = getPlayerByUsername(username)
            if (player == null) {
                Log.e(TAG, "No player found with username: $username")
                return null
            }
            
            // Now login with the email
            return loginWithEmail(player.email, password)
        } catch (e: Exception) {
            Log.e(TAG, "Login with username exception: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            
            firebaseAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    Log.d(TAG, "Password reset email sent to: $email")
                    cont.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to send password reset email", e)
                    cont.resume(false)
                }
        } catch (e: Exception) {
            cont.resume(false)
        }
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        auth?.signOut()
    }
    
    /**
     * Get current authenticated user's UID
     */
    fun getCurrentUserId(): String? = auth?.currentUser?.uid
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean = auth?.currentUser != null
    
    // ==================== PLAYER MANAGEMENT ====================
    
    /**
     * Saves score directly to player record - only updates if it's a better score.
     * For Reaction Time & Memory Flip: lower is better
     * For all other games: higher is better
     */
    suspend fun saveScore(score: GameScore): Boolean = suspendCancellableCoroutine { cont ->
        try {
            Log.d(TAG, "saveScore called: gameType=${score.gameType}, playerId=${score.playerId}, score=${score.score}")
            
            val ref = playersRef
            if (ref == null) {
                Log.e(TAG, "saveScore: playersRef is NULL!")
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            
            // Map game type to field name in player record
            val fieldName = when (score.gameType) {
                GameType.REACTION_TIME -> "reactionTime"
                GameType.MEMORY_FLIP -> "memoryFlip"
                GameType.PATTERN_SNAP -> "patternSnap"
                GameType.COLOR_CATCH -> "colorCatch"
                GameType.WORD_SCRAMBLE -> "wordScramble"
                GameType.RHYTHM_TAP -> "rhythmTap"
            }
            
            Log.d(TAG, "saveScore: Updating players/${score.playerId}/$fieldName")
            
            val playerRef = ref.child(score.playerId)
            
            // First, get the full player data to calculate total score
            playerRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(playerSnapshot: DataSnapshot) {
                    val player = playerSnapshot.getValue(Player::class.java)
                    val existingScore = playerSnapshot.child(fieldName).getValue(Long::class.java) ?: 0L
                    Log.d(TAG, "saveScore: existingScore=$existingScore, newScore=${score.score}")
                    
                    // Determine if we should update (0 means no score yet)
                    // Reaction Time: lower is better
                    // All other games: higher is better
                    val shouldUpdate = when (score.gameType) {
                        GameType.REACTION_TIME -> existingScore == 0L || score.score < existingScore
                        else -> score.score > existingScore
                    }
                    
                    Log.d(TAG, "saveScore: shouldUpdate=$shouldUpdate")
                    
                    if (shouldUpdate) {
                        // Update the game score
                        val updates = mutableMapOf<String, Any>()
                        updates[fieldName] = score.score
                        
                        // Calculate new total score
                        val newTotalScore = calculateTotalScore(
                            player = player,
                            updatedGameType = score.gameType,
                            newScore = score.score
                        )
                        updates["totalScore"] = newTotalScore
                        
                        playerRef.updateChildren(updates)
                            .addOnSuccessListener { 
                                Log.d(TAG, "saveScore: SUCCESS! Updated $fieldName to ${score.score}, totalScore to $newTotalScore")
                                cont.resume(true) 
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "saveScore: FAILED! ${e.message}", e)
                                cont.resume(false) 
                            }
                    } else {
                        Log.d(TAG, "saveScore: No update needed (existing score is better)")
                        cont.resume(true)
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "saveScore: CANCELLED! ${error.message}")
                    cont.resume(false)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "saveScore exception: ${e.message}", e)
            cont.resume(false)
        }
    }
    
    /**
     * Calculate total score from all game scores
     * Reaction Time: converts to points (10000 - time) since lower is better
     * Other games: uses score directly (higher is better)
     */
    private fun calculateTotalScore(player: Player?, updatedGameType: GameType, newScore: Long): Long {
        val reactionTime = if (updatedGameType == GameType.REACTION_TIME) newScore else (player?.reactionTime ?: 0L)
        val memoryFlip = if (updatedGameType == GameType.MEMORY_FLIP) newScore else (player?.memoryFlip ?: 0L)
        val patternSnap = if (updatedGameType == GameType.PATTERN_SNAP) newScore else (player?.patternSnap ?: 0L)
        val colorCatch = if (updatedGameType == GameType.COLOR_CATCH) newScore else (player?.colorCatch ?: 0L)
        val wordScramble = if (updatedGameType == GameType.WORD_SCRAMBLE) newScore else (player?.wordScramble ?: 0L)
        val rhythmTap = if (updatedGameType == GameType.RHYTHM_TAP) newScore else (player?.rhythmTap ?: 0L)
        
        // Convert Reaction Time to points (lower time = higher points)
        val reactionPoints = if (reactionTime > 0) 10000L - reactionTime else 0L
        
        // Sum all scores
        return reactionPoints + memoryFlip + patternSnap + colorCatch + wordScramble + rhythmTap
    }
    
    /**
     * Get leaderboard for total score (combined across all games)
     */
    fun getTotalLeaderboard(limit: Int = 50): Flow<List<GameScore>> {
        val ref = try { playersRef } catch (e: Exception) { null }
        if (ref == null) return flow { emit(emptyList()) }
        
        return callbackFlow {
            try {
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val scores = snapshot.children.mapNotNull { playerSnapshot ->
                            val player = playerSnapshot.getValue(Player::class.java)
                            val totalScore = player?.totalScore ?: 0L
                            
                            // Only include players who have a total score > 0
                            if (player != null && totalScore > 0) {
                                // Count games played
                                val gamesPlayed = listOf(
                                    player.reactionTime,
                                    player.memoryFlip,
                                    player.patternSnap,
                                    player.colorCatch,
                                    player.wordScramble,
                                    player.rhythmTap
                                ).count { it > 0 }
                                
                                GameScore(
                                    id = player.id,
                                    playerId = player.id,
                                    playerUsername = player.username,
                                    playerAvatar = player.avatarEmoji,
                                    gameType = GameType.REACTION_TIME, // Placeholder for total
                                    score = totalScore,
                                    extras = mapOf("gamesPlayed" to gamesPlayed)
                                )
                            } else null
                        }.sortedByDescending { it.score }.take(limit)
                        
                        Log.d(TAG, "getTotalLeaderboard: Found ${scores.size} scores")
                        trySend(scores)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "getTotalLeaderboard cancelled: ${error.message}")
                        trySend(emptyList())
                    }
                }
                
                ref.addValueEventListener(listener)
                awaitClose { ref.removeEventListener(listener) }
            } catch (e: Exception) {
                Log.e(TAG, "getTotalLeaderboard exception: ${e.message}", e)
                trySend(emptyList())
                close()
            }
        }
    }
    
    /**
     * Get leaderboard for a specific game type by querying all players
     * and sorting by their score for that game
     */
    fun getLeaderboard(gameType: GameType, limit: Int = 10): Flow<List<GameScore>> {
        val ref = try { playersRef } catch (e: Exception) { null }
        if (ref == null) return flow { emit(emptyList()) }
        
        // Map game type to field name
        val fieldName = when (gameType) {
            GameType.REACTION_TIME -> "reactionTime"
            GameType.MEMORY_FLIP -> "memoryFlip"
            GameType.PATTERN_SNAP -> "patternSnap"
            GameType.COLOR_CATCH -> "colorCatch"
            GameType.WORD_SCRAMBLE -> "wordScramble"
            GameType.RHYTHM_TAP -> "rhythmTap"
        }
        
        return callbackFlow {
            try {
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val scores = snapshot.children.mapNotNull { playerSnapshot ->
                            val player = playerSnapshot.getValue(Player::class.java)
                            val score = player?.getScore(gameType) ?: 0L
                            
                            // Only include players who have a score > 0
                            if (player != null && score > 0) {
                                GameScore(
                                    id = player.id,
                                    playerId = player.id,
                                    playerUsername = player.username,
                                    playerAvatar = player.avatarEmoji,
                                    gameType = gameType,
                                    score = score
                                )
                            } else null
                        }.let { list ->
                            // Sort: lower is better for Reaction Time only
                            // All other games: higher is better
                            when (gameType) {
                                GameType.REACTION_TIME -> 
                                    list.sortedBy { it.score }.take(limit)
                                else -> 
                                    list.sortedByDescending { it.score }.take(limit)
                            }
                        }
                        
                        Log.d(TAG, "getLeaderboard($gameType): Found ${scores.size} scores")
                        trySend(scores)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "getLeaderboard cancelled: ${error.message}")
                        trySend(emptyList())
                    }
                }
                
                ref.addValueEventListener(listener)
                awaitClose { ref.removeEventListener(listener) }
            } catch (e: Exception) {
                Log.e(TAG, "getLeaderboard exception: ${e.message}", e)
                trySend(emptyList())
                close()
            }
        }
    }
    
    /**
     * Get player's personal scores (refresh from Firebase)
     */
    suspend fun getPlayerScores(playerId: String): Player? = suspendCancellableCoroutine { cont ->
        try {
            playersRef?.child(playerId)?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val player = snapshot.getValue(Player::class.java)
                    Log.d(TAG, "getPlayerScores: $player")
                    cont.resume(player)
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "getPlayerScores cancelled: ${error.message}")
                    cont.resume(null)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "getPlayerScores exception: ${e.message}", e)
            cont.resume(null)
        }
    }
    
    fun getAllPlayers(): Flow<List<Player>> {
        val ref = try { playersRef } catch (e: Exception) { null }
        if (ref == null) return flow { emit(emptyList()) }
        
        return callbackFlow {
            try {
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val players = snapshot.children.mapNotNull { it.getValue(Player::class.java) }
                        trySend(players)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        trySend(emptyList())
                    }
                }
                
                ref.addValueEventListener(listener)
                awaitClose { ref.removeEventListener(listener) }
            } catch (e: Exception) {
                trySend(emptyList())
                close()
            }
        }
    }
    
    /**
     * Increment the games played counter for a player
     */
    suspend fun incrementGamesPlayed(playerId: String) {
        try {
            val playerRef = playersRef?.child(playerId)
            playerRef?.child("totalGamesPlayed")?.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentCount = snapshot.getValue(Int::class.java) ?: 0
                    playerRef.child("totalGamesPlayed").setValue(currentCount + 1)
                    Log.d(TAG, "Games played updated: ${currentCount + 1}")
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to increment games played", error.toException())
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Increment games played error: ${e.message}", e)
        }
    }
    
    suspend fun updatePlayerAvatar(playerId: String, avatarEmoji: String) {
        try {
            playersRef?.child(playerId)?.updateChildren(
                mapOf("avatarEmoji" to avatarEmoji)
            )
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Failed to update avatar", e)
        }
    }
}
