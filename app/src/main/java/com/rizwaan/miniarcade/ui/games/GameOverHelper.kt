package com.rizwaan.miniarcade.ui.games

import android.view.View
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

object GameOverHelper {
    
    private val firebaseRepository = FirebaseRepository()
    
    /**
     * Show and animate badges for new high score or personal best
     * @param dialogBinding The dialog binding
     * @param gameType The game type
     * @param currentScore The current score
     * @param previousHighScore The player's previous high score
     * @param context Context for PreferencesManager
     */
    fun showBadges(
        dialogBinding: DialogGameOverBinding,
        gameType: GameType,
        currentScore: Long,
        previousHighScore: Long,
        context: android.content.Context
    ) {
        // Check if it's a personal best (better than previous)
        val isPersonalBest = when (gameType) {
            GameType.REACTION_TIME -> {
                // For reaction time, lower is better
                previousHighScore == 0L || previousHighScore == Long.MAX_VALUE || currentScore < previousHighScore
            }
            else -> {
                currentScore > previousHighScore
            }
        }
        
        if (!isPersonalBest) {
            dialogBinding.tvNewHighScore.visibility = View.GONE
            dialogBinding.tvPersonalBest.visibility = View.GONE
            return
        }
        
        // Check if it's #1 on leaderboard (wait a bit for score to be saved)
        CoroutineScope(Dispatchers.Main).launch {
            // Small delay to allow score to be saved
            kotlinx.coroutines.delay(500)
            
            val prefsManager = PreferencesManager(context)
            val currentPlayer = prefsManager.currentPlayer
            
            if (currentPlayer == null) {
                // No player info, just show personal best
                dialogBinding.tvPersonalBest.visibility = View.VISIBLE
                dialogBinding.tvNewHighScore.visibility = View.GONE
                dialogBinding.tvPersonalBest.alpha = 0f
                dialogBinding.tvPersonalBest.scaleX = 0.5f
                dialogBinding.tvPersonalBest.scaleY = 0.5f
                dialogBinding.tvPersonalBest.animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(400)
                    .setStartDelay(200)
                    .withEndAction {
                        dialogBinding.tvPersonalBest.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
                return@launch
            }
            
            // Get top 10 to check if user is on leaderboard
            val leaderboard = firebaseRepository.getLeaderboard(gameType, 10).firstOrNull() ?: emptyList()
            
            // Check if current player is on leaderboard
            val userOnLeaderboard = leaderboard.any { it.playerId == currentPlayer.id }
            
            // Check if current score is #1 on leaderboard
            val isNewHighScore = when (gameType) {
                GameType.REACTION_TIME -> {
                    // For reaction time, check if current score is lower than or equal to #1
                    leaderboard.isNotEmpty() && 
                    leaderboard[0].playerId == currentPlayer.id &&
                    currentScore <= leaderboard[0].score
                }
                else -> {
                    // For other games, check if current score is higher than or equal to #1
                    leaderboard.isNotEmpty() && 
                    leaderboard[0].playerId == currentPlayer.id &&
                    currentScore >= leaderboard[0].score
                }
            }
            
            if (isNewHighScore) {
                // New High Score badge (#1 on leaderboard) - appears BEFORE title
                dialogBinding.tvNewHighScore.visibility = View.VISIBLE
                dialogBinding.tvPersonalBest.visibility = View.GONE
                dialogBinding.tvNewHighScore.alpha = 0f
                dialogBinding.tvNewHighScore.scaleX = 0.5f
                dialogBinding.tvNewHighScore.scaleY = 0.5f
                dialogBinding.tvNewHighScore.animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(400)
                    .setStartDelay(200)
                    .withEndAction {
                        dialogBinding.tvNewHighScore.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            } else if (isPersonalBest) {
                // Personal Best badge (better than previous but not #1, or user not on leaderboard)
                dialogBinding.tvPersonalBest.visibility = View.VISIBLE
                dialogBinding.tvNewHighScore.visibility = View.GONE
                dialogBinding.tvPersonalBest.alpha = 0f
                dialogBinding.tvPersonalBest.scaleX = 0.5f
                dialogBinding.tvPersonalBest.scaleY = 0.5f
                dialogBinding.tvPersonalBest.animate()
                    .alpha(1f)
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .setDuration(400)
                    .setStartDelay(200)
                    .withEndAction {
                        dialogBinding.tvPersonalBest.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(200)
                            .start()
                    }
                    .start()
            }
        }
    }
    
    fun loadLeaderboard(dialogBinding: DialogGameOverBinding, gameType: GameType) {
        if (!firebaseRepository.isAvailable) {
            dialogBinding.leaderboardLayout.visibility = View.GONE
            return
        }
        
        dialogBinding.leaderboardLayout.visibility = View.VISIBLE
        
        // Set loading state
        dialogBinding.tvRank1Name.text = "Loading..."
        dialogBinding.tvRank1Score.text = ""
        dialogBinding.tvRank2Name.text = ""
        dialogBinding.tvRank2Score.text = ""
        dialogBinding.tvRank3Name.text = ""
        dialogBinding.tvRank3Score.text = ""
        
        dialogBinding.rank2Layout.visibility = View.GONE
        dialogBinding.rank3Layout.visibility = View.GONE
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val scores = firebaseRepository.getLeaderboard(gameType, 3).firstOrNull() ?: emptyList()
                
                if (scores.isEmpty()) {
                    dialogBinding.tvRank1Name.text = "No scores yet"
                    dialogBinding.tvRank1Score.text = ""
                    return@launch
                }
                
                // Rank 1
                if (scores.isNotEmpty()) {
                    val score1 = scores[0]
                    dialogBinding.rank1Layout.visibility = View.VISIBLE
                    dialogBinding.tvRank1Name.text = score1.playerUsername.replaceFirstChar { it.uppercase() }
                    dialogBinding.tvRank1Score.text = formatScore(score1.score, gameType)
                }
                
                // Rank 2
                if (scores.size > 1) {
                    val score2 = scores[1]
                    dialogBinding.rank2Layout.visibility = View.VISIBLE
                    dialogBinding.tvRank2Name.text = score2.playerUsername.replaceFirstChar { it.uppercase() }
                    dialogBinding.tvRank2Score.text = formatScore(score2.score, gameType)
                }
                
                // Rank 3
                if (scores.size > 2) {
                    val score3 = scores[2]
                    dialogBinding.rank3Layout.visibility = View.VISIBLE
                    dialogBinding.tvRank3Name.text = score3.playerUsername.replaceFirstChar { it.uppercase() }
                    dialogBinding.tvRank3Score.text = formatScore(score3.score, gameType)
                }
                
            } catch (e: Exception) {
                dialogBinding.leaderboardLayout.visibility = View.GONE
            }
        }
    }
    
    private fun formatScore(score: Long, gameType: GameType): String {
        return when (gameType) {
            GameType.REACTION_TIME -> "${score}ms"
            GameType.MEMORY_FLIP -> "$score moves"
            else -> "$score"
        }
    }
}

