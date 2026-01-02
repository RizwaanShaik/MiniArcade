package com.rizwaan.miniarcade.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.databinding.ItemAvatarBinding

class AvatarAdapter(
    private val avatars: List<String>,
    private val onAvatarSelected: (String) -> Unit
) : RecyclerView.Adapter<AvatarAdapter.AvatarViewHolder>() {

    private var selectedAvatar: String = avatars.firstOrNull() ?: ""
    private val animatedPositions = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AvatarViewHolder {
        val binding = ItemAvatarBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AvatarViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AvatarViewHolder, position: Int) {
        holder.bind(avatars[position], position)
    }

    override fun getItemCount() = avatars.size

    fun setSelected(avatar: String) {
        val oldSelected = selectedAvatar
        selectedAvatar = avatar
        
        val oldIndex = avatars.indexOf(oldSelected)
        val newIndex = avatars.indexOf(avatar)
        
        if (oldIndex >= 0) notifyItemChanged(oldIndex, "selection")
        if (newIndex >= 0) notifyItemChanged(newIndex, "selection")
    }
    
    // Reset animations when adapter is reused
    fun resetAnimations() {
        animatedPositions.clear()
    }

    inner class AvatarViewHolder(
        private val binding: ItemAvatarBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(avatar: String, position: Int) {
            binding.tvEmoji.text = avatar
            
            val isSelected = avatar == selectedAvatar
            
            // Update background
            binding.tvEmoji.setBackgroundResource(
                if (isSelected) R.drawable.bg_avatar_selected 
                else R.drawable.bg_avatar_unselected
            )
            
            // Set scale based on selection (no animation here)
            val targetScale = if (isSelected) 1.15f else 1f
            binding.tvEmoji.scaleX = targetScale
            binding.tvEmoji.scaleY = targetScale
            
            // Entrance animation - only run once per position
            if (position !in animatedPositions) {
                animatedPositions.add(position)
                binding.root.alpha = 0f
                binding.root.animate()
                    .alpha(1f)
                    .setStartDelay(position * 40L)
                    .setDuration(200)
                    .start()
            }
            
            binding.root.setOnClickListener {
                if (avatar != selectedAvatar) {
                    // Pop animation on selection
                    binding.tvEmoji.animate()
                        .scaleX(1.3f)
                        .scaleY(1.3f)
                        .setDuration(80)
                        .withEndAction {
                            binding.tvEmoji.animate()
                                .scaleX(1.15f)
                                .scaleY(1.15f)
                                .setDuration(80)
                                .start()
                        }
                        .start()
                    
                    onAvatarSelected(avatar)
                }
            }
        }
    }
}
