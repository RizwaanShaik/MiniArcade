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
        private const val DATABASE_URL = "https://cousinarcade-6ec96-default-rtdb.asia-southeast1.firebasedatabase.app"
    }
    
    private val database: FirebaseDatabase? by lazy {
        try {
            Log.d(TAG, "Initializing Firebase Database with URL: $DATABASE_URL")
            val db = FirebaseDatabase.getInstance(DATABASE_URL)
            db.setPersistenceEnabled(true) // Enable offline persistence
            Log.d(TAG, "Firebase Database initialized successfully")
            db
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
    
    suspend fun isNicknameAvailable(nickname: String): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val ref = playersRef
            if (ref == null) {
                cont.resume(true)
                return@suspendCancellableCoroutine
            }
            
            ref.orderByChild("nickname").equalTo(nickname.lowercase())
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
    
    suspend fun getPlayerByNickname(nickname: String): Player? = suspendCancellableCoroutine { cont ->
        try {
            val ref = playersRef
            if (ref == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            
            ref.orderByChild("nickname").equalTo(nickname.lowercase())
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
    
    suspend fun createPlayer(nickname: String, avatarEmoji: String): Player? = suspendCancellableCoroutine { cont ->
        try {
            val ref = playersRef
            if (ref == null) {
                Log.e(TAG, "Players ref is null - database not initialized")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            
            val playerId = ref.push().key ?: return@suspendCancellableCoroutine cont.resume(null)
            val player = Player(
                id = playerId,
                nickname = nickname.lowercase(),
                avatarEmoji = avatarEmoji,
                createdAt = System.currentTimeMillis()
            )
            
            Log.d(TAG, "Creating player: $nickname with ID: $playerId")
            ref.child(playerId).setValue(player.toMap())
                .addOnSuccessListener { 
                    Log.d(TAG, "Player created successfully!")
                    cont.resume(player) 
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to create player: ${e.message}", e)
                    cont.resume(null) 
                }
        } catch (e: Exception) {
            Log.e(TAG, "Create player exception: ${e.message}", e)
            cont.resume(null)
        }
    }
    
    /**
     * Saves score directly to leaderboard - only keeps highest score per player per game.
     * For Reaction Time: lower is better
     * For all other games: higher is better
     */
    suspend fun saveScore(score: GameScore): Boolean = suspendCancellableCoroutine { cont ->
        try {
            val ref = leaderboardRef
            if (ref == null) {
                cont.resume(false)
                return@suspendCancellableCoroutine
            }
            
            val gameLeaderboardRef = ref.child(score.gameType.name).child(score.playerId)
            
            gameLeaderboardRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val existingScore = snapshot.child("score").getValue(Long::class.java) ?: 0L
                    val shouldUpdate = when (score.gameType) {
                        GameType.REACTION_TIME -> score.score < existingScore || existingScore == 0L
                        GameType.MEMORY_FLIP -> score.score < existingScore || existingScore == 0L // Lower moves is better
                        else -> score.score > existingScore
                    }
                    
                    if (shouldUpdate) {
                        val scoreId = gameLeaderboardRef.push().key ?: score.playerId
                        val scoreWithId = score.copy(id = scoreId)
                        gameLeaderboardRef.setValue(scoreWithId.toMap())
                            .addOnSuccessListener { cont.resume(true) }
                            .addOnFailureListener { cont.resume(false) }
                    } else {
                        cont.resume(true) // No update needed, but not a failure
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    cont.resume(false)
                }
            })
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Save score exception", e)
            cont.resume(false)
        }
    }
    
    fun getLeaderboard(gameType: GameType, limit: Int = 10): Flow<List<GameScore>> {
        val ref = try { leaderboardRef } catch (e: Exception) { null }
        if (ref == null) return flow { emit(emptyList()) }
        
        return callbackFlow {
            try {
                val query = if (gameType == GameType.REACTION_TIME) {
                    ref.child(gameType.name).orderByChild("score").limitToFirst(limit)
                } else {
                    ref.child(gameType.name).orderByChild("score").limitToLast(limit)
                }
                
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val scores = snapshot.children.mapNotNull { 
                            it.getValue(GameScore::class.java) 
                        }.let { list ->
                            if (gameType == GameType.REACTION_TIME) list else list.reversed()
                        }
                        trySend(scores)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        trySend(emptyList())
                    }
                }
                
                query.addValueEventListener(listener)
                awaitClose { query.removeEventListener(listener) }
            } catch (e: Exception) {
                trySend(emptyList())
                close()
            }
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
    
    suspend fun updatePlayerStats(playerId: String, gamesPlayed: Int, totalScore: Long) {
        try {
            playersRef?.child(playerId)?.updateChildren(
                mapOf(
                    "totalGamesPlayed" to gamesPlayed,
                    "totalScore" to totalScore
                )
            )
        } catch (e: Exception) {}
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
