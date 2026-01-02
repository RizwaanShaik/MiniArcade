package com.rizwaan.miniarcade.data.local

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages score syncing between local Room database and Firebase
 * - Saves scores locally first for reliability
 * - Syncs to Firebase when online
 * - Retries failed syncs automatically
 */
class ScoreSyncManager(context: Context) {
    
    private val database = ScoreDatabase.getInstance(context)
    private val dao = database.pendingScoreDao()
    private val firebaseRepo = FirebaseRepository()
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "ScoreSyncManager"
        
        @Volatile
        private var instance: ScoreSyncManager? = null
        
        fun getInstance(context: Context): ScoreSyncManager {
            return instance ?: synchronized(this) {
                instance ?: ScoreSyncManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Save score locally and attempt to sync to Firebase
     * Returns immediately after local save for responsive UI
     */
    suspend fun saveScore(score: GameScore) {
        // Save locally first
        val pendingScore = PendingScore(
            playerId = score.playerId,
            playerNickname = score.playerNickname,
            gameType = score.gameType.name,
            score = score.score,
            timestamp = score.timestamp,
            extras = gson.toJson(score.extras)
        )
        
        val localId = dao.insert(pendingScore)
        Log.d(TAG, "Score saved locally with id: $localId")
        
        // Try to sync in background
        scope.launch {
            syncPendingScores()
        }
    }
    
    /**
     * Sync all pending scores to Firebase
     * Called automatically when saving and can be called manually
     */
    suspend fun syncPendingScores() {
        if (!firebaseRepo.isAvailable) {
            Log.d(TAG, "Firebase not available, skipping sync")
            return
        }
        
        val unsynced = dao.getUnsynced()
        Log.d(TAG, "Found ${unsynced.size} unsynced scores")
        
        for (pending in unsynced) {
            try {
                val extras: Map<String, Any> = try {
                    val type = object : TypeToken<Map<String, Any>>() {}.type
                    gson.fromJson(pending.extras, type) ?: emptyMap()
                } catch (e: Exception) {
                    emptyMap()
                }
                
                val gameScore = GameScore(
                    id = UUID.randomUUID().toString(),
                    playerId = pending.playerId,
                    playerNickname = pending.playerNickname,
                    gameType = GameType.valueOf(pending.gameType),
                    score = pending.score,
                    timestamp = pending.timestamp,
                    extras = extras
                )
                
                val success = firebaseRepo.saveScore(gameScore)
                if (success) {
                    dao.markSynced(pending.id)
                    Log.d(TAG, "Synced score ${pending.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync score ${pending.id}", e)
            }
        }
        
        // Clean up old synced scores
        dao.deleteSynced()
    }
    
    /**
     * Get count of scores waiting to be synced
     */
    suspend fun getPendingCount(): Int {
        return dao.getUnsyncedCount()
    }
}

