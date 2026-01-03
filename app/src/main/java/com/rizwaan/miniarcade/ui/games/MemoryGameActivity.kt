package com.rizwaan.miniarcade.ui.games

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
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityMemoryGameBinding
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import com.rizwaan.miniarcade.ui.adapters.MemoryCard
import com.rizwaan.miniarcade.ui.adapters.MemoryCardAdapter
import com.rizwaan.miniarcade.util.SoundManager
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
    private var completedLevels = 0  // Track completed levels for scoring
    private var pairsFound = 0
    private var totalPairs = 0
    private var moves = 0
    private var totalMoves = 0 // Track total moves across all levels
    private var firstCard: Int? = null
    private var isChecking = false
    private var isGameStarted = false
    private var previousHighScore = 0L  // Track previous high score for new high score detection
    
    // Improved scoring tracking
    data class LevelStats(
        val level: Int,
        val pairs: Int,
        val moves: Int,
        val mismatches: Int,
        val perfectMoves: Int // pairs * 2 (minimum moves needed)
    )
    private val levelStatsList = mutableListOf<LevelStats>()
    private var currentLevelMismatches = 0
    private var currentCombo = 0
    private var totalComboBonus = 0L
    
    // Level difficulty weights (harder levels worth more)
    private val levelWeights = mapOf(
        1 to 1.0,
        2 to 1.2,
        3 to 1.4,
        4 to 1.7,
        5 to 2.0
    )
    
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
        loadPreviousHighScore()
        
        binding.startOverlay.visibility = View.VISIBLE
    }
    
    private fun loadPreviousHighScore() {
        lifecycleScope.launch {
            val player = prefsManager.currentPlayer ?: return@launch
            val playerData = firebaseRepo.getPlayerScores(player.id)
            previousHighScore = playerData?.getScore(GameType.MEMORY_FLIP) ?: 0L
        }
    }
    
    private fun resetGame() {
        currentLevel = 1
        completedLevels = 0
        totalMoves = 0
        levelStatsList.clear()
        currentLevelMismatches = 0
        currentCombo = 0
        totalComboBonus = 0L
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
        currentLevelMismatches = 0
        currentCombo = 0  // Reset combo for new level
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
                // Match found - increase combo and add bonus
                currentCombo++
                // Combo bonus: each consecutive match adds more points
                totalComboBonus += (currentCombo * 20)
                
                cardAdapter.matchCards(pos1, pos2)
                pairsFound++
                
                // Play streak sound if 2+ consecutive matches, otherwise play correct sound
                if (currentCombo > 1) {
                    soundManager.playCombo()  // Streak sound for consecutive matches
                } else {
                    soundManager.playCorrect()  // First match sound
                }
                
                if (pairsFound >= totalPairs) {
                    levelComplete()
                }
            } else {
                // Mismatch - reset combo and track mismatch
                currentCombo = 0
                currentLevelMismatches++
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
        completedLevels = currentLevel  // Mark current level as completed
        
        // Save level stats for scoring
        // Perfect moves = number of pairs (each pair requires 1 match check)
        val perfectMoves = totalPairs
        val levelStats = LevelStats(
            level = currentLevel,
            pairs = totalPairs,
            moves = moves,
            mismatches = currentLevelMismatches,
            perfectMoves = perfectMoves
        )
        levelStatsList.add(levelStats)
        
        // Play victory sound after each round completion
        soundManager.playVictory()
        
        // Save score after each level completion so partial progress is saved
        saveScore()
        
        if (currentLevel >= maxLevel) {
            showGameOver(true)
        } else {
            showLevelComplete()
        }
    }
    
    private fun showLevelComplete() {
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        val isPerfectLevel = currentLevelMismatches == 0
        val perfectMoves = totalPairs
        val excessMoves = maxOf(0, moves - perfectMoves)
        
        dialogBinding.tvResultEmoji.text = if (isPerfectLevel) "⭐" else "🎉"
        dialogBinding.tvTitle.text = if (isPerfectLevel) {
            "Perfect Round $currentLevel!"
        } else {
            "Round $currentLevel Complete!"
        }
        
        val currentScore = calculateScore()
        dialogBinding.tvScore.text = "Score: $currentScore"
        
        // Show "Perfect" text under score if perfect level
        dialogBinding.tvPerfect.visibility = if (isPerfectLevel) View.VISIBLE else View.GONE
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Moves"
        dialogBinding.tvStat1Value.text = "$moves"  // Just show the number
        dialogBinding.tvStat2Label.text = "Mismatches"
        dialogBinding.tvStat2Value.text = "$currentLevelMismatches"
        
        dialogBinding.btnPlayAgain.text = "Next Round"
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
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
            // Score already saved in levelComplete(), just finish
            finish()
        }
        
        dialog.show()
    }
    
    private fun showGameOver(isWin: Boolean) {
        val finalScore = calculateScore()
        
        // Calculate stats for display
        val totalPerfectMoves = levelStatsList.sumOf { it.perfectMoves }
        val totalExcessMoves = maxOf(0, totalMoves - totalPerfectMoves)
        val perfectLevels = levelStatsList.count { it.mismatches == 0 }
        
        // Check if this is a new high score
        val isNewHighScore = finalScore > previousHighScore
        if (isNewHighScore) {
            soundManager.playHighscore()
        } else if (!isWin) {
            soundManager.playGameOver()  // Quit early - game over sound
        }
        // Note: Victory sound already played in levelComplete() for each round
        
        saveScore()
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        dialogBinding.tvResultEmoji.text = if (isWin) "🏆" else "😅"
        dialogBinding.tvTitle.text = if (isWin) "Memory Master!" else "Game Over!"
        dialogBinding.tvScore.text = "Score: $finalScore"
        
        // Show badges using helper function
        GameOverHelper.showBadges(dialogBinding, GameType.MEMORY_FLIP, finalScore, previousHighScore, this)
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Rounds"
        dialogBinding.tvStat1Value.text = "$completedLevels"
        dialogBinding.tvStat2Label.text = "Perfect Levels"
        dialogBinding.tvStat2Value.text = "$perfectLevels/$completedLevels"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.MEMORY_FLIP)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
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
    
    private fun calculateScore(): Long {
        if (levelStatsList.isEmpty()) return 0L
        
        var totalScore = 0L
        
        // Calculate score per level with improved formula
        levelStatsList.forEach { stats ->
            val levelWeight = levelWeights[stats.level] ?: 1.0
            
            // Perfect moves = number of pairs (each pair requires 1 match check)
            val excessMoves = maxOf(0, stats.moves - stats.perfectMoves)
            
            // Perfect level bonus (no mismatches)
            val perfectLevelBonus = if (stats.mismatches == 0) 300L else 0L
            
            // Level score with difficulty weighting
            // Formula: (pairs * basePoints * weight) + perfectBonus - (excessMoves * penalty)
            val levelScore = (stats.pairs * 150 * levelWeight).toLong() +
                    perfectLevelBonus -
                    (excessMoves * 20)
            
            totalScore += levelScore
        }
        
        // Add completion bonus (one-time bonus for finishing rounds)
        totalScore += (completedLevels * 100)
        
        // Add combo bonus (rewards consecutive matches)
        totalScore += totalComboBonus
        
        return maxOf(0, totalScore)
    }
    
    private fun calculateTotalPairs(): Int {
        // Sum pairs from completed levels only
        return levelStatsList.sumOf { it.pairs }
    }
    
    private fun saveScore() {
        val player = prefsManager.currentPlayer ?: return
        
        // New composite score: higher is better
        val score = calculateScore()
        
        val gameScore = GameScore(
            playerId = player.id,
            playerUsername = player.username,
            gameType = GameType.MEMORY_FLIP,
            score = score,
            extras = mapOf("rounds" to completedLevels, "totalMoves" to totalMoves)
        )
        
        lifecycleScope.launch {
            val saved = firebaseRepo.saveScore(gameScore)
            android.util.Log.d("MemoryGame", "Score saved: $saved, score: $score, rounds: $currentLevel, moves: $totalMoves")
            firebaseRepo.incrementGamesPlayed(player.id)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}

