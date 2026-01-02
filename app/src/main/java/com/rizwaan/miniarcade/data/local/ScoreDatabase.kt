package com.rizwaan.miniarcade.data.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Local Room database for caching scores when offline
 * Scores are synced to Firebase when connection is available
 */

@Entity(tableName = "pending_scores")
data class PendingScore(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val playerId: String,
    val playerNickname: String,
    val gameType: String,
    val score: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val extras: String = "{}", // JSON string
    val isSynced: Boolean = false
)

@Dao
interface PendingScoreDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(score: PendingScore): Long
    
    @Query("SELECT * FROM pending_scores WHERE isSynced = 0 ORDER BY timestamp ASC")
    suspend fun getUnsynced(): List<PendingScore>
    
    @Query("UPDATE pending_scores SET isSynced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)
    
    @Query("DELETE FROM pending_scores WHERE isSynced = 1")
    suspend fun deleteSynced()
    
    @Query("SELECT * FROM pending_scores WHERE playerNickname = :nickname ORDER BY timestamp DESC LIMIT :limit")
    fun getScoresByPlayer(nickname: String, limit: Int = 50): Flow<List<PendingScore>>
    
    @Query("SELECT * FROM pending_scores WHERE gameType = :gameType ORDER BY score ASC LIMIT :limit")
    fun getTopScores(gameType: String, limit: Int = 10): Flow<List<PendingScore>>
    
    @Query("SELECT COUNT(*) FROM pending_scores WHERE isSynced = 0")
    suspend fun getUnsyncedCount(): Int
}

@Database(entities = [PendingScore::class], version = 1, exportSchema = false)
abstract class ScoreDatabase : RoomDatabase() {
    
    abstract fun pendingScoreDao(): PendingScoreDao
    
    companion object {
        @Volatile
        private var INSTANCE: ScoreDatabase? = null
        
        fun getInstance(context: Context): ScoreDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScoreDatabase::class.java,
                    "score_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

