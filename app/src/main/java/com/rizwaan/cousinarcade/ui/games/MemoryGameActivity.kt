package com.rizwaan.cousinarcade.ui.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.rizwaan.cousinarcade.R
import com.rizwaan.cousinarcade.data.local.PreferencesManager
import com.rizwaan.cousinarcade.data.models.GameScore
import com.rizwaan.cousinarcade.data.models.GameType
import com.rizwaan.cousinarcade.data.repository.FirebaseRepository
import com.rizwaan.cousinarcade.databinding.ActivityMemoryGameBinding
import com.rizwaan.cousinarcade.databinding.DialogGameOverBinding
import com.rizwaan.cousinarcade.ui.adapters.MemoryCard
import com.rizwaan.cousinarcade.ui.adapters.MemoryCardAdapter
import com.rizwaan.cousinarcade.util.SoundManager
import kotlinx.coroutines.launch

class MemoryGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMemoryGameBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var cardAdapter: MemoryCardAdapter
    private lateinit var soundManager: SoundManager
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentLevel = 1
    private val maxLevel = 5  // Only 5 rounds now
    private var pairsFound = 0
    private var totalPairs = 0
    private var moves = 0
    private var totalMoves = 0 // Track total moves across all levels
    private var firstCard: Int? = null
    private var isChecking = false
    private var isGameStarted = false
    
    private val emojis = listOf(
        "🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑",
        "🐶", "🐱", "🐼", "🐨", "🦁", "🐯", "🐸", "🦋",
        "⭐", "🌙", "☀️", "🌈", "❤️", "💎", "🎈", "🎁",
        "🚀", "✈️", "🚗", "🚲", "⚽", "🏀", "🎸", "🎮"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityMemoryGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        prefsManager = PreferencesManager(this)
        firebaseRepo = FirebaseRepository()
        soundManager = SoundManager.getInstance(this)
        
        setupClickListeners()
        resetGame()
        
        binding.startOverlay.visibility = View.VISIBLE
    }
    
    private fun resetGame() {
        currentLevel = 1
        totalMoves = 0
        updateUI()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnStart.setOnClickListener {
            animateButton(it) {
                startGame()
            }
        }
    }
    
    private fun animateButton(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(80)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(80)
                    .withEndAction { onComplete() }
                    .start()
            }
            .start()
    }
    
    private fun startGame() {
        binding.startOverlay.visibility = View.GONE
        isGameStarted = true
        setupLevel()
    }
    
    private fun setupLevel() {
        pairsFound = 0
        moves = 0
        firstCard = null
        isChecking = false
        
        val gridSize = getGridSize()
        totalPairs = (gridSize.first * gridSize.second) / 2
        
        val cards = createCards(totalPairs)
        
        cardAdapter = MemoryCardAdapter(cards) { position ->
            onCardClicked(position)
        }
        
        binding.rvCards.apply {
            layoutManager = GridLayoutManager(this@MemoryGameActivity, gridSize.second)
            adapter = cardAdapter
        }
        
        updateUI()
    }
    
    private fun getGridSize(): Pair<Int, Int> {
        // Returns (rows, columns)
        // Round 1: 3 rows, Round 2: 4 rows, etc.
        return when (currentLevel) {
            1 -> 3 to 4  // 3 rows x 4 cols = 12 cards = 6 pairs
            2 -> 4 to 4  // 4 rows x 4 cols = 16 cards = 8 pairs
            3 -> 5 to 4  // 5 rows x 4 cols = 20 cards = 10 pairs
            4 -> 6 to 4  // 6 rows x 4 cols = 24 cards = 12 pairs
            else -> 7 to 4  // 7 rows x 4 cols = 28 cards = 14 pairs
        }
    }
    
    private fun createCards(pairs: Int): List<MemoryCard> {
        val selectedEmojis = emojis.shuffled().take(pairs)
        val cards = mutableListOf<MemoryCard>()
        
        selectedEmojis.forEachIndexed { index, emoji ->
            cards.add(MemoryCard(index * 2, emoji))
            cards.add(MemoryCard(index * 2 + 1, emoji))
        }
        
        return cards.shuffled()
    }
    
    private fun onCardClicked(position: Int) {
        if (isChecking) return
        
        cardAdapter.flipCard(position, true)
        
        if (firstCard == null) {
            firstCard = position
        } else {
            moves++
            totalMoves++
            isChecking = true
            checkMatch(firstCard!!, position)
        }
        
        updateUI()
    }
    
    private fun checkMatch(pos1: Int, pos2: Int) {
        handler.postDelayed({
            val card1Emoji = getCardEmoji(pos1)
            val card2Emoji = getCardEmoji(pos2)
            
            if (card1Emoji == card2Emoji) {
                cardAdapter.matchCards(pos1, pos2)
                pairsFound++
                soundManager.playCorrect()
                
                if (pairsFound >= totalPairs) {
                    levelComplete()
                }
            } else {
                cardAdapter.flipCard(pos1, false)
                cardAdapter.flipCard(pos2, false)
                soundManager.playWrong()
            }
            
            firstCard = null
            isChecking = false
            updateUI()
        }, 800)
    }
    
    private fun getCardEmoji(position: Int): String {
        val viewHolder = binding.rvCards.findViewHolderForAdapterPosition(position)
        return (viewHolder as? MemoryCardAdapter.CardViewHolder)?.itemView?.let { 
            it.findViewById<android.widget.TextView>(R.id.cardFront)?.text?.toString() 
        } ?: ""
    }
    
    private fun updateUI() {
        binding.tvPairs.text = "$pairsFound/$totalPairs"
        binding.tvMoves.text = "$moves"
        binding.tvLevel.text = "Round $currentLevel/$maxLevel"
        // Note: No score display - fewer moves = better
    }
    
    private fun levelComplete() {
        if (currentLevel >= maxLevel) {
            showGameOver(true)
        } else {
            showLevelComplete()
        }
    }
    
    private fun showLevelComplete() {
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        dialogBinding.tvResultEmoji.text = "🎉"
        dialogBinding.tvTitle.text = "Round $currentLevel Complete!"
        dialogBinding.tvScore.text = "Moves this level: $moves"
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Pairs"
        dialogBinding.tvStat1Value.text = "$pairsFound"
        dialogBinding.tvStat2Label.text = "Total Moves"
        dialogBinding.tvStat2Value.text = "$totalMoves"
        
        dialogBinding.btnPlayAgain.text = "Next Round"
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_CousinArcade)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            currentLevel++
            setupLevel()
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            saveScore(totalMoves.toLong())
            finish()
        }
        
        dialog.show()
    }
    
    private fun showGameOver(isWin: Boolean) {
        saveScore(totalMoves.toLong())
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        dialogBinding.tvResultEmoji.text = if (isWin) "🏆" else "😅"
        dialogBinding.tvTitle.text = if (isWin) "Memory Master!" else "Game Over!"
        dialogBinding.tvScore.text = "Total Moves: $totalMoves"
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Rounds"
        dialogBinding.tvStat1Value.text = "$currentLevel"
        dialogBinding.tvStat2Label.text = "Total Moves"
        dialogBinding.tvStat2Value.text = "$totalMoves"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.MEMORY_FLIP)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_CousinArcade)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            resetGame()
            binding.startOverlay.visibility = View.VISIBLE
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun saveScore(moves: Long) {
        val player = prefsManager.currentPlayer ?: return
        
        // For Memory Flip, lower moves is better (stored as negative for sorting)
        val gameScore = GameScore(
            playerId = player.id,
            playerNickname = player.nickname,
            gameType = GameType.MEMORY_FLIP,
            score = moves,
            extras = mapOf("rounds" to currentLevel)
        )
        
        lifecycleScope.launch {
            firebaseRepo.saveScore(gameScore)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

