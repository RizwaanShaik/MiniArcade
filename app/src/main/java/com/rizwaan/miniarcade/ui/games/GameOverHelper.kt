package com.rizwaan.miniarcade.ui.games

import android.view.View
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

object GameOverHelper {
    
    private val firebaseRepository = FirebaseRepository()
    
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
                    dialogBinding.tvRank1Name.text = score1.playerNickname.replaceFirstChar { it.uppercase() }
                    dialogBinding.tvRank1Score.text = formatScore(score1.score, gameType)
                }
                
                // Rank 2
                if (scores.size > 1) {
                    val score2 = scores[1]
                    dialogBinding.rank2Layout.visibility = View.VISIBLE
                    dialogBinding.tvRank2Name.text = score2.playerNickname.replaceFirstChar { it.uppercase() }
                    dialogBinding.tvRank2Score.text = formatScore(score2.score, gameType)
                }
                
                // Rank 3
                if (scores.size > 2) {
                    val score3 = scores[2]
                    dialogBinding.rank3Layout.visibility = View.VISIBLE
                    dialogBinding.tvRank3Name.text = score3.playerNickname.replaceFirstChar { it.uppercase() }
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

