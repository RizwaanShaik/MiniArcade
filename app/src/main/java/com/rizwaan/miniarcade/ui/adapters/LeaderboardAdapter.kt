package com.rizwaan.miniarcade.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.databinding.ItemLeaderboardBinding
import java.text.SimpleDateFormat
import java.util.*

class LeaderboardAdapter(
    private var gameType: GameType
) : ListAdapter<GameScore, LeaderboardAdapter.LeaderboardViewHolder>(DiffCallback()) {

    fun updateGameType(type: GameType) {
        gameType = type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LeaderboardViewHolder {
        val binding = ItemLeaderboardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LeaderboardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LeaderboardViewHolder, position: Int) {
        holder.bind(getItem(position), position + 1)
    }

    inner class LeaderboardViewHolder(
        private val binding: ItemLeaderboardBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(score: GameScore, rank: Int) {
            val context = binding.root.context
            
            // Rank with medal emoji for top 3
            binding.tvRank.text = when (rank) {
                1 -> "🥇"
                2 -> "🥈"
                3 -> "🥉"
                else -> "$rank"
            }
            
            // Card background color based on rank
            val cardColor = when (rank) {
                1 -> ContextCompat.getColor(context, R.color.leaderboard_gold)
                2 -> ContextCompat.getColor(context, R.color.leaderboard_silver)
                3 -> ContextCompat.getColor(context, R.color.leaderboard_bronze)
                else -> ContextCompat.getColor(context, R.color.surface)
            }
            binding.rootLayout.setCardBackgroundColor(cardColor)
            
            // Text colors for top 3 (darker for contrast on bright backgrounds)
            val textColor = when (rank) {
                1, 2, 3 -> ContextCompat.getColor(context, R.color.background_dark)
                else -> ContextCompat.getColor(context, R.color.text_primary)
            }
            val secondaryColor = when (rank) {
                1, 2, 3 -> ContextCompat.getColor(context, R.color.background_dark)
                else -> ContextCompat.getColor(context, R.color.text_secondary)
            }
            
            binding.tvPlayerName.setTextColor(textColor)
            binding.tvDate.setTextColor(secondaryColor)
            binding.tvScore.setTextColor(textColor)
            binding.tvScoreLabel.setTextColor(secondaryColor)
            binding.tvRank.setTextColor(textColor)
            
            // Player info
            binding.tvAvatar.text = score.playerAvatar
            binding.tvPlayerName.text = score.playerUsername.replaceFirstChar { it.uppercase() }
            binding.tvDate.text = dateFormat.format(Date(score.timestamp))
            
            // Score formatting based on game type
            val (scoreText, labelText) = formatScore(score)
            binding.tvScore.text = scoreText
            binding.tvScoreLabel.text = labelText
        }
        
        private fun formatScore(score: GameScore): Pair<String, String> {
            // Check if this is a combined score (has gamesPlayed in extras)
            val isCombined = score.extras?.containsKey("gamesPlayed") == true
            
            return when {
                isCombined -> Pair("${score.score}", "pts")
                gameType == GameType.REACTION_TIME -> Pair("${score.score}", "ms")
                gameType == GameType.MEMORY_FLIP -> Pair("${score.score}", "moves")
                else -> Pair("${score.score}", "pts")
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<GameScore>() {
        override fun areItemsTheSame(oldItem: GameScore, newItem: GameScore) = 
            oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: GameScore, newItem: GameScore) = 
            oldItem == newItem
    }
}
