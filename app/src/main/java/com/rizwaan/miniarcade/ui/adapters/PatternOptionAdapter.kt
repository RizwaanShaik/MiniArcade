package com.rizwaan.miniarcade.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rizwaan.miniarcade.R

class PatternOptionAdapter(
    private var options: List<String>,
    private val onOptionClicked: (Int, String) -> Unit
) : RecyclerView.Adapter<PatternOptionAdapter.OptionViewHolder>() {

    private val usedPositions = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pattern_option, parent, false)
        return OptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(options[position], position in usedPositions)
    }

    override fun getItemCount() = options.size

    fun updateOptions(newOptions: List<String>) {
        options = newOptions
        usedPositions.clear()
        notifyDataSetChanged()
    }

    fun markUsed(position: Int) {
        usedPositions.add(position)
        notifyItemChanged(position)
    }

    fun resetUsed() {
        usedPositions.clear()
        notifyDataSetChanged()
    }

    inner class OptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEmoji: TextView = itemView.findViewById(R.id.tvEmoji)

        fun bind(emoji: String, isUsed: Boolean) {
            tvEmoji.text = emoji
            tvEmoji.alpha = if (isUsed) 0.3f else 1f
            
            itemView.setOnClickListener {
                if (!isUsed) {
                    onOptionClicked(adapterPosition, emoji)
                }
            }
        }
    }
}

