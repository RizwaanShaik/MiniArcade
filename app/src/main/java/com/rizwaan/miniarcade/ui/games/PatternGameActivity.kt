package com.rizwaan.miniarcade.ui.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.flexbox.FlexboxLayout
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityPatternGameBinding
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import com.rizwaan.miniarcade.util.SoundManager
import kotlinx.coroutines.launch

class PatternGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPatternGameBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var soundManager: SoundManager
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var currentLevel = 1
    private var score = 0L
    private var lives = 3
    private var mistakes = 0  // Track total mistakes for scoring
    private var previousHighScore = 0L  // Track previous high score for new high score detection
    private var currentPattern = listOf<String>()
    private var userSelection = mutableListOf<String>()
    private var optionViews = mutableListOf<TextView>()
    private var usedIndices = mutableListOf<Int>()
    
    // Track selection info for tap-to-remove
    data class SelectionInfo(val emoji: String, val optionIndex: Int, val view: View)
    private var selectionInfoList = mutableListOf<SelectionInfo>()
    
    // Pattern lengths: 3,3,4,4,5,5,6,6,7,7,7...
    private val patternLengths = listOf(3, 3, 4, 4, 5, 5, 6, 6, 7, 7)
    
    private val emojiSets = listOf(
        listOf("🍎", "🍊", "🍋", "🍇", "🍓", "🍒", "🥝", "🍑"),
        listOf("🐶", "🐱", "🐼", "🦁", "🐯", "🐸", "🦊", "🐰"),
        listOf("⭐", "🌙", "☀️", "🌈", "❤️", "💎", "🔥", "💫"),
        listOf("🚀", "✈️", "🚗", "🚲", "⚽", "🏀", "🎸", "🎮"),
        listOf("🌸", "🌺", "🌻", "🌷", "🌹", "💐", "🍀", "🌴")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityPatternGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        prefsManager = PreferencesManager(this)
        firebaseRepo = FirebaseRepository()
        soundManager = SoundManager.getInstance(this)
        
        setupGame()
        setupClickListeners()
        loadPreviousHighScore()
    }
    
    private fun loadPreviousHighScore() {
        lifecycleScope.launch {
            val player = prefsManager.currentPlayer ?: return@launch
            val playerData = firebaseRepo.getPlayerScores(player.id)
            previousHighScore = playerData?.getScore(GameType.PATTERN_SNAP) ?: 0L
        }
    }
    
    private fun setupGame() {
        currentLevel = 1
        score = 0
        lives = 3
        updateUI()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnStart.setOnClickListener { 
            binding.btnStart.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    binding.btnStart.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction { startGame() }
                        .start()
                }
                .start()
        }
        
        // Clear button
        binding.btnClear.setOnClickListener {
            clearAllSelections()
        }
    }
    
    private fun startGame() {
        // Hide start overlay with animation
        binding.startOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.startOverlay.visibility = View.GONE
            }
            .start()
        startRound()
    }
    
    private fun clearAllSelections() {
        if (userSelection.isEmpty()) return
        
        // Restore all used options
        for (info in selectionInfoList) {
            if (info.optionIndex < optionViews.size) {
                optionViews[info.optionIndex].animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
        }
        
        // Clear all data
        userSelection.clear()
        usedIndices.clear()
        selectionInfoList.clear()
        binding.userSelectionArea.removeAllViews()
        
        soundManager.playTap()
    }
    
    private fun removeSelectionAt(position: Int) {
        if (position < 0 || position >= selectionInfoList.size) return
        
        val info = selectionInfoList[position]
        
        // Restore the option in the grid
        if (info.optionIndex < optionViews.size) {
            optionViews[info.optionIndex].animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .start()
        }
        
        // Remove from tracking lists
        userSelection.removeAt(position)
        usedIndices.remove(info.optionIndex)
        selectionInfoList.removeAt(position)
        
        // Rebuild the user selection area
        rebuildSelectionArea()
        
        soundManager.playTap()
    }
    
    private fun rebuildSelectionArea() {
        binding.userSelectionArea.removeAllViews()
        
        selectionInfoList.forEachIndexed { position, info ->
            val view = createSelectionView(info.emoji, position)
            binding.userSelectionArea.addView(view)
            // Update the view reference in the info
            selectionInfoList[position] = info.copy(view = view)
        }
    }
    
    private fun createSelectionView(emoji: String, position: Int): View {
        val size = resources.getDimensionPixelSize(R.dimen.pattern_item_size)
        val margin = 8
        
        return TextView(this).apply {
            text = emoji
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(0xFF000000.toInt())
            layoutParams = FlexboxLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            setBackgroundResource(R.drawable.bg_pattern_item)
            
            // Click to remove this emoji
            setOnClickListener {
                removeSelectionAt(position)
            }
        }
    }
    
    private fun getPatternLength(): Int {
        return if (currentLevel <= patternLengths.size) {
            patternLengths[currentLevel - 1]
        } else {
            7 // Max pattern length
        }
    }
    
    private fun startRound() {
        binding.btnStart.visibility = View.GONE
        userSelection.clear()
        usedIndices.clear()
        optionViews.clear()
        selectionInfoList.clear()
        
        // Generate pattern based on level
        val patternLength = getPatternLength()
        val emojiSet = emojiSets[(currentLevel - 1) % emojiSets.size]
        currentPattern = (1..patternLength).map { emojiSet.random() }
        
        showPattern()
    }
    
    private fun showPattern() {
        binding.tvInstruction.text = getString(R.string.watch_pattern)
        binding.optionsGrid.visibility = View.GONE
        binding.userSelectionArea.visibility = View.GONE
        binding.btnClear.visibility = View.GONE
        
        binding.patternDisplayArea.removeAllViews()
        
        // Show each emoji one by one with animation
        currentPattern.forEachIndexed { index, emoji ->
            handler.postDelayed({
                addEmojiToDisplay(binding.patternDisplayArea, emoji, true)
            }, index * 700L)
        }
        
        // After showing all, start input phase
        handler.postDelayed({
            startInputPhase()
        }, currentPattern.size * 700L + 800L)
    }
    
    private fun addEmojiToDisplay(container: FlexboxLayout, emoji: String, animate: Boolean) {
        val size = resources.getDimensionPixelSize(R.dimen.pattern_item_size)
        val margin = 8
        
        val textView = TextView(this).apply {
            text = emoji
            textSize = 36f
            gravity = Gravity.CENTER
            setTextColor(0xFF000000.toInt())
            layoutParams = FlexboxLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            setBackgroundResource(R.drawable.bg_pattern_item)
            
            if (animate) {
                alpha = 0f
                scaleX = 0.5f
                scaleY = 0.5f
            }
        }
        container.addView(textView)
        
        if (animate) {
            textView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }
    
    private fun startInputPhase() {
        binding.tvInstruction.text = getString(R.string.repeat_pattern)
        binding.patternDisplayArea.removeAllViews()
        
        // Create scrambled options
        val options = currentPattern.shuffled()
        
        binding.optionsGrid.removeAllViews()
        optionViews.clear()
        selectionInfoList.clear()
        
        val size = resources.getDimensionPixelSize(R.dimen.pattern_item_size)
        val margin = 8
        
        options.forEachIndexed { index, emoji ->
            val textView = TextView(this).apply {
                text = emoji
                textSize = 36f
                gravity = Gravity.CENTER
                setTextColor(0xFF000000.toInt())
                layoutParams = FlexboxLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                setBackgroundResource(R.drawable.bg_pattern_item)
                
                alpha = 0f
                scaleX = 0.5f
                scaleY = 0.5f
            }
            
            textView.setOnClickListener {
                if (index !in usedIndices) {
                    onOptionSelected(index, emoji, textView)
                }
            }
            
            binding.optionsGrid.addView(textView)
            optionViews.add(textView)
            
            textView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(index * 50L)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
        
        binding.optionsGrid.visibility = View.VISIBLE
        binding.userSelectionArea.removeAllViews()
        binding.userSelectionArea.visibility = View.VISIBLE
        binding.btnClear.visibility = View.VISIBLE
    }
    
    private fun onOptionSelected(index: Int, emoji: String, view: TextView) {
        userSelection.add(emoji)
        usedIndices.add(index)
        
        // Dim the selected option
        view.animate()
            .alpha(0.3f)
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(150)
            .start()
        
        // Add to selection area
        val position = selectionInfoList.size
        val selectionView = createSelectionView(emoji, position)
        
        // Animate the new selection view
        selectionView.alpha = 0f
        selectionView.scaleX = 0.5f
        selectionView.scaleY = 0.5f
        
        binding.userSelectionArea.addView(selectionView)
        
        selectionView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(200)
            .setInterpolator(OvershootInterpolator())
            .start()
        
        selectionInfoList.add(SelectionInfo(emoji, index, selectionView))
        
        soundManager.playTap()
        
        if (userSelection.size == currentPattern.size) {
            checkAnswer()
        }
    }
    
    private fun checkAnswer() {
        val isCorrect = userSelection == currentPattern
        
        if (isCorrect) {
            score += currentLevel * 100L
            currentLevel++
            soundManager.playCorrect()
            
            binding.tvInstruction.text = getString(R.string.correct)
            binding.tvInstruction.setTextColor(getColor(R.color.game_green))
            
            binding.tvInstruction.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(200)
                .withEndAction {
                    binding.tvInstruction.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(200)
                        .start()
                }
                .start()
            
            handler.postDelayed({
                binding.tvInstruction.setTextColor(getColor(R.color.text_primary))
                startRound()
            }, 1200)
        } else {
            lives--
            mistakes++  // Track mistakes for scoring
            soundManager.playFail()
            
            binding.tvInstruction.text = getString(R.string.wrong)
            binding.tvInstruction.setTextColor(getColor(R.color.game_red))
            
            binding.tvInstruction.animate()
                .translationX(-10f)
                .setDuration(50)
                .withEndAction {
                    binding.tvInstruction.animate()
                        .translationX(10f)
                        .setDuration(50)
                        .withEndAction {
                            binding.tvInstruction.animate()
                                .translationX(0f)
                                .setDuration(50)
                                .start()
                        }
                        .start()
                }
                .start()
            
            if (lives <= 0) {
                handler.postDelayed({ showGameOver() }, 1000)
            } else {
                handler.postDelayed({
                    binding.tvInstruction.setTextColor(getColor(R.color.text_primary))
                    startRound()
                }, 1500)
            }
        }
        
        updateUI()
    }
    
    private fun updateUI() {
        binding.tvScore.text = getString(R.string.score, score.toInt())
        binding.tvLives.text = "❤️".repeat(lives)
    }
    
    private fun showGameOver() {
        val finalScore = calculateScore()
        
        // Check if this is a new high score
        val isNewHighScore = finalScore > previousHighScore
        if (isNewHighScore) {
            soundManager.playHighscore()
        } else {
            soundManager.playGameOver()
        }
        
        saveScore()
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        dialogBinding.tvResultEmoji.text = when {
            currentLevel > 10 -> "🧠"
            currentLevel > 5 -> "🎯"
            else -> "👍"
        }
        
        dialogBinding.tvTitle.text = when {
            currentLevel > 10 -> "Pattern Master!"
            currentLevel > 5 -> "Great Memory!"
            else -> "Good Try!"
        }
        dialogBinding.tvScore.text = "Score: $finalScore"
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Level Reached"
        dialogBinding.tvStat1Value.text = "$currentLevel"
        dialogBinding.tvStat2Label.text = "Patterns"
        dialogBinding.tvStat2Value.text = "${currentLevel - 1}"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.PATTERN_SNAP)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            setupGame()
            binding.startOverlay.alpha = 1f
            binding.startOverlay.visibility = View.VISIBLE
            binding.optionsGrid.visibility = View.GONE
            binding.userSelectionArea.visibility = View.GONE
            binding.btnClear.visibility = View.GONE
            binding.patternDisplayArea.removeAllViews()
            binding.tvInstruction.text = getString(R.string.watch_pattern)
            binding.tvInstruction.setTextColor(getColor(R.color.text_primary))
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun calculateScore(): Long {
        // New scoring: (level * 100) - (mistakes * 50), floor at 0
        val rawScore = (currentLevel * 100) - (mistakes * 50)
        return maxOf(0, rawScore).toLong()
    }
    
    private fun saveScore() {
        val player = prefsManager.currentPlayer ?: return
        
        val finalScore = calculateScore()
        
        val gameScore = GameScore(
            playerId = player.id,
            playerUsername = player.username,
            gameType = GameType.PATTERN_SNAP,
            score = finalScore,
            extras = mapOf("level" to currentLevel, "mistakes" to mistakes)
        )
        
        lifecycleScope.launch {
            val saved = firebaseRepo.saveScore(gameScore)
            android.util.Log.d("PatternGame", "Score saved: $saved, level: $currentLevel, mistakes: $mistakes, score: $finalScore")
            firebaseRepo.incrementGamesPlayed(player.id)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
