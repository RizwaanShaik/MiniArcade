package com.rizwaan.cousinarcade.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.rizwaan.cousinarcade.data.models.GameType
import com.rizwaan.cousinarcade.databinding.ItemGameBinding

class GameAdapter(
    private val games: List<GameType>,
    private val onGameSelected: (GameType) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val binding = ItemGameBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return GameViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        holder.bind(games[position], position)
    }

    override fun getItemCount() = games.size

    inner class GameViewHolder(
        private val binding: ItemGameBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gameType: GameType, position: Int) {
            binding.tvGameEmoji.text = gameType.emoji
            binding.tvGameName.text = gameType.displayName
            binding.tvGameDescription.text = gameType.description
            
            // Entrance animation
            binding.root.alpha = 0f
            binding.root.translationY = 50f
            binding.root.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(position * 80L)
                .setDuration(350)
                .setInterpolator(OvershootInterpolator(0.8f))
                .start()
            
            // Click animation
            binding.root.setOnClickListener {
                binding.root.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(80)
                    .withEndAction {
                        binding.root.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(80)
                            .withEndAction {
                                onGameSelected(gameType)
                            }
                            .start()
                    }
                    .start()
            }
        }
    }
}
