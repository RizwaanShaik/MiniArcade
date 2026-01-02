package com.rizwaan.miniarcade.ui.adapters

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.rizwaan.miniarcade.databinding.ItemMemoryCardBinding

data class MemoryCard(
    val id: Int,
    val emoji: String,
    var isFlipped: Boolean = false,
    var isMatched: Boolean = false
)

class MemoryCardAdapter(
    private var cards: List<MemoryCard>,
    private val onCardClicked: (Int) -> Unit
) : RecyclerView.Adapter<MemoryCardAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val binding = ItemMemoryCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CardViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        holder.bind(cards[position], position)
    }

    override fun getItemCount() = cards.size

    fun updateCards(newCards: List<MemoryCard>) {
        cards = newCards
        notifyDataSetChanged()
    }

    fun flipCard(position: Int, isFlipped: Boolean) {
        if (position in cards.indices) {
            cards[position].isFlipped = isFlipped
            notifyItemChanged(position, "flip")
        }
    }

    fun matchCards(pos1: Int, pos2: Int) {
        if (pos1 in cards.indices && pos2 in cards.indices) {
            cards[pos1].isMatched = true
            cards[pos2].isMatched = true
            notifyItemChanged(pos1, "match")
            notifyItemChanged(pos2, "match")
        }
    }

    inner class CardViewHolder(
        private val binding: ItemMemoryCardBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private var isAnimating = false

        fun bind(card: MemoryCard, position: Int) {
            binding.cardFront.text = card.emoji
            
            // Initial state without animation
            if (card.isMatched) {
                binding.cardFront.visibility = View.VISIBLE
                binding.cardBack.visibility = View.INVISIBLE
                binding.cardView.alpha = 0.6f
                binding.cardView.scaleX = 0.95f
                binding.cardView.scaleY = 0.95f
            } else if (card.isFlipped) {
                binding.cardFront.visibility = View.VISIBLE
                binding.cardBack.visibility = View.INVISIBLE
                binding.cardView.alpha = 1f
            } else {
                binding.cardFront.visibility = View.INVISIBLE
                binding.cardBack.visibility = View.VISIBLE
                binding.cardView.alpha = 1f
                binding.cardView.scaleX = 1f
                binding.cardView.scaleY = 1f
            }
            
            binding.cardView.setOnClickListener {
                if (!card.isFlipped && !card.isMatched && !isAnimating) {
                    animateCardPress {
                        onCardClicked(adapterPosition)
                    }
                }
            }
            
            // Entry animation for new cards
            binding.root.alpha = 0f
            binding.root.translationY = 20f
            binding.root.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay((position % 8) * 30L)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        
        private fun animateCardPress(onComplete: () -> Unit) {
            binding.cardView.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(60)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    binding.cardView.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .setInterpolator(OvershootInterpolator(2f))
                        .withEndAction { onComplete() }
                        .start()
                }
                .start()
        }
        
        fun animateFlip(toFront: Boolean) {
            if (isAnimating) return
            isAnimating = true
            
            val flipOut = ObjectAnimator.ofFloat(binding.cardView, View.SCALE_X, 1f, 0f)
            flipOut.duration = 120
            flipOut.interpolator = AccelerateDecelerateInterpolator()
            
            val flipIn = ObjectAnimator.ofFloat(binding.cardView, View.SCALE_X, 0f, 1f)
            flipIn.duration = 120
            flipIn.interpolator = OvershootInterpolator(1.2f)
            
            flipOut.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (toFront) {
                        binding.cardFront.visibility = View.VISIBLE
                        binding.cardBack.visibility = View.INVISIBLE
                    } else {
                        binding.cardFront.visibility = View.INVISIBLE
                        binding.cardBack.visibility = View.VISIBLE
                    }
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {}
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            val animatorSet = AnimatorSet()
            animatorSet.playSequentially(flipOut, flipIn)
            animatorSet.addListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    isAnimating = false
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            animatorSet.start()
        }
        
        fun animateMatch() {
            // Pulse and fade animation for matched cards
            val pulse = AnimatorSet()
            
            val scaleUpX = ObjectAnimator.ofFloat(binding.cardView, View.SCALE_X, 1f, 1.1f)
            val scaleUpY = ObjectAnimator.ofFloat(binding.cardView, View.SCALE_Y, 1f, 1.1f)
            scaleUpX.duration = 150
            scaleUpY.duration = 150
            scaleUpX.interpolator = DecelerateInterpolator()
            scaleUpY.interpolator = DecelerateInterpolator()
            
            val scaleDownX = ObjectAnimator.ofFloat(binding.cardView, View.SCALE_X, 1.1f, 0.95f)
            val scaleDownY = ObjectAnimator.ofFloat(binding.cardView, View.SCALE_Y, 1.1f, 0.95f)
            val fadeOut = ObjectAnimator.ofFloat(binding.cardView, View.ALPHA, 1f, 0.6f)
            scaleDownX.duration = 200
            scaleDownY.duration = 200
            fadeOut.duration = 200
            
            pulse.play(scaleUpX).with(scaleUpY)
            pulse.play(scaleDownX).with(scaleDownY).with(fadeOut).after(scaleUpX)
            pulse.start()
        }
    }
    
    override fun onBindViewHolder(holder: CardViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            when (payloads[0]) {
                "flip" -> {
                    val card = cards[position]
                    holder.animateFlip(card.isFlipped)
                }
                "match" -> {
                    holder.animateMatch()
                }
            }
        }
    }
}
