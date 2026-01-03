package com.rizwaan.miniarcade.ui.games

import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.WordDictionary
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityWordGameBinding
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import com.rizwaan.miniarcade.util.SoundManager
import kotlinx.coroutines.launch

class WordGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWordGameBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var soundManager: SoundManager
    
    private var timer: CountDownTimer? = null
    private var wordsGuessed = 0
    private var totalScore = 0L  // Accumulated score
    private var currentWord = ""
    private var currentHint = ""
    private var timeLeft = 30
    
    // Track letters - using index-based approach for reliability
    private var allLetters = mutableListOf<LetterInfo>()
    private var answerSlots = arrayOfNulls<Int>(20) // Index of letter in slot, null if empty
    
    // Hint system - max 3 hints per game
    private var hintsRemaining = 3
    private val maxHints = 3
    private var hintUsedThisWord = false  // Track if hint was used for current word (kills time bonus)
    
    // Lives system - 3 lives per game
    private var lives = 3
    private val maxLives = 3
    private var previousHighScore = 0L  // Track previous high score for new high score detection
    
    // Prevent rapid clicks
    private var isProcessingClick = false
    
    data class LetterInfo(
        val char: Char,
        val index: Int,
        var view: TextView? = null,
        var isUsed: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityWordGameBinding.inflate(layoutInflater)
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
            previousHighScore = playerData?.getScore(GameType.WORD_SCRAMBLE) ?: 0L
        }
    }
    
    private fun setupGame() {
        wordsGuessed = 0
        totalScore = 0L
        hintsRemaining = maxHints
        lives = maxLives
        updateUI()
        updateHintButton()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { 
            timer?.cancel()
            finish() 
        }
        
        binding.btnStart.setOnClickListener {
            animateButton(binding.btnStart) {
                startGame()
            }
        }
        
        binding.btnHint.setOnClickListener {
            if (hintsRemaining > 0) {
                animateButton(binding.btnHint) {
                    showHint()
                }
            }
        }
        
        binding.btnClear.setOnClickListener {
            animateButton(binding.btnClear) {
                clearAnswer()
            }
        }
    }
    
    private fun animateButton(view: View, onComplete: () -> Unit) {
        view.animate()
            .scaleX(0.92f)
            .scaleY(0.92f)
            .setDuration(60)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(60)
                    .setInterpolator(OvershootInterpolator(2f))
                    .withEndAction { onComplete() }
                    .start()
            }
            .start()
    }
    
    private fun startGame() {
        binding.startOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                binding.startOverlay.visibility = View.GONE
            }
            .start()
        startRound()
    }
    
    private fun startRound() {
        allLetters.clear()
        answerSlots = arrayOfNulls(20)
        isProcessingClick = false
        hintUsedThisWord = false  // Reset hint flag for new word
        
        // Get word based on words guessed (difficulty increases)
        val level = wordsGuessed + 1
        val (word, hint) = WordDictionary.getWordForLevel(level)
        currentWord = word
        currentHint = hint
        
        // Create shuffled letters
        val shuffledChars = currentWord.toList().shuffled()
        shuffledChars.forEachIndexed { index, char ->
            allLetters.add(LetterInfo(char, index))
        }
        
        // Set time based on word length with better scaling for longer words
        // Formula: base time + (length * multiplier)
        // Longer words get more time but with diminishing returns
        timeLeft = when {
            currentWord.length <= 4 -> 12 + (currentWord.length * 3)  // 21-24 seconds
            currentWord.length <= 6 -> 15 + (currentWord.length * 3)  // 27-33 seconds
            currentWord.length <= 8 -> 18 + (currentWord.length * 3)  // 39-42 seconds
            currentWord.length <= 10 -> 20 + (currentWord.length * 2) // 36-40 seconds
            else -> 25 + (currentWord.length * 2)  // 47+ seconds for 11+ letters
        }
        
        binding.tvHint.visibility = View.GONE
        
        setupWordDisplay()
        startTimer()
        updateUI()
    }
    
    private fun setupWordDisplay() {
        binding.answerSlotsLayout.removeAllViews()
        binding.scrambledLettersLayout.removeAllViews()
        
        // Show word length indicator for words ≥8 letters
        val isLongWord = currentWord.length >= 8
        if (isLongWord) {
            binding.tvWordLength.text = "${currentWord.length} letters"
            binding.tvWordLength.visibility = View.VISIBLE
            binding.tvWordLength.alpha = 0f
            binding.tvWordLength.animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        } else {
            binding.tvWordLength.visibility = View.GONE
        }
        
        // Set flexWrap based on word length
        // For words ≥8 letters, allow wrapping to create balanced rows
        binding.answerSlotsLayout.flexWrap = if (isLongWord) {
            FlexWrap.WRAP
        } else {
            FlexWrap.NOWRAP
        }
        
        // Calculate balanced row split for long words
        val lettersPerRow = if (isLongWord) {
            // Split into two balanced rows
            (currentWord.length + 1) / 2  // Round up for odd numbers
        } else {
            currentWord.length  // Single row for short words
        }
        
        // Create answer slots
        for (i in currentWord.indices) {
            val shouldWrapBefore = isLongWord && i == lettersPerRow
            val slot = createSlotView(shouldWrapBefore)
            slot.tag = i
            
            binding.answerSlotsLayout.addView(slot)
            
            slot.alpha = 0f
            slot.translationY = 30f
            slot.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(i * 40L)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        
        // Create scrambled letters (always single line with scroll)
        allLetters.forEachIndexed { index, letterInfo ->
            val letterView = createLetterView(letterInfo.char.toString())
            letterInfo.view = letterView
            letterInfo.isUsed = false
            letterView.tag = index
            
            letterView.setOnClickListener {
                onLetterClicked(index)
            }
            
            binding.scrambledLettersLayout.addView(letterView)
            
            letterView.alpha = 0f
            letterView.scaleX = 0.7f
            letterView.scaleY = 0.7f
            letterView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(80L + index * 50L)
                .setDuration(200)
                .setInterpolator(OvershootInterpolator(1.5f))
                .start()
        }
    }
    
    private fun createSlotView(wrapBefore: Boolean = false): TextView {
        // Adjust size based on word length to fit on screen
        val letterSize = getAdaptiveLetterSize()
        val letterTextSize = getAdaptiveTextSize()
        val margin = getAdaptiveMargin()
        
        return TextView(this).apply {
            text = ""
            textSize = letterTextSize
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.text_hint))
            
            layoutParams = FlexboxLayout.LayoutParams(letterSize, letterSize).apply {
                setMargins(margin, margin, margin, margin)
                this.isWrapBefore = wrapBefore
            }
            
            setBackgroundResource(R.drawable.bg_word_slot)
        }
    }
    
    private fun createLetterView(text: String): TextView {
        // Adjust size based on word length to fit on screen
        val letterSize = getAdaptiveLetterSize()
        val letterTextSize = getAdaptiveTextSize()
        val margin = getAdaptiveMargin()
        
        return TextView(this).apply {
            this.text = text
            textSize = letterTextSize
            gravity = Gravity.CENTER
            setTextColor(getColor(R.color.white))
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            
            layoutParams = FlexboxLayout.LayoutParams(letterSize, letterSize).apply {
                setMargins(margin, margin, margin, margin)
            }
            
            setBackgroundResource(R.drawable.bg_word_letter)
        }
    }
    
    private fun getAdaptiveLetterSize(): Int {
        // Smaller letters for longer words to fit on screen
        val baseSize = resources.getDimensionPixelSize(R.dimen.word_letter_size)
        return when {
            currentWord.length >= 14 -> (baseSize * 0.4).toInt()   // Extra small for 14+ letters
            currentWord.length >= 13 -> (baseSize * 0.45).toInt()    // Extra small for 13 letters
            currentWord.length >= 12 -> (baseSize * 0.5).toInt()     // Very small for 12 letters
            currentWord.length >= 10 -> (baseSize * 0.6).toInt()    // Small for 10-11 letters
            currentWord.length >= 8 -> (baseSize * 0.65).toInt()    // Medium-small for 8-9 letters
            currentWord.length >= 7 -> (baseSize * 0.75).toInt()    // Medium for 7 letters
            currentWord.length >= 6 -> (baseSize * 0.85).toInt()   // Slightly smaller for 6 letters
            currentWord.length >= 5 -> (baseSize * 0.9).toInt()     // Slightly smaller for 5 letters
            else -> baseSize  // Full size for 3-4 letters
        }
    }
    
    private fun getAdaptiveTextSize(): Float {
        // Smaller text for longer words
        return when {
            currentWord.length >= 14 -> 12f   // Extra small for 14+ letters
            currentWord.length >= 13 -> 13f   // Extra small for 13 letters
            currentWord.length >= 12 -> 14f   // Very small for 12 letters
            currentWord.length >= 10 -> 16f   // Small for 10-11 letters
            currentWord.length >= 8 -> 18f    // Medium-small for 8-9 letters
            currentWord.length >= 7 -> 20f    // Medium for 7 letters
            currentWord.length >= 6 -> 22f    // Slightly smaller for 6 letters
            currentWord.length >= 5 -> 24f    // Slightly smaller for 5 letters
            else -> 26f  // Full size for 3-4 letters
        }
    }
    
    private fun getAdaptiveMargin(): Int {
        // Smaller margins for longer words to fit more on screen
        return when {
            currentWord.length >= 13 -> 2  // 2dp margin for 13+ letters
            currentWord.length >= 10 -> 3  // 3dp margin for 10-12 letters
            else -> 4  // 4dp margin for shorter words
        }
    }
    
    private fun onLetterClicked(letterIndex: Int) {
        if (isProcessingClick) return
        if (letterIndex >= allLetters.size) return
        
        val letterInfo = allLetters[letterIndex]
        if (letterInfo.isUsed) return
        
        val slotIndex = answerSlots.indexOfFirst { it == null }
        if (slotIndex == -1 || slotIndex >= currentWord.length) return
        
        isProcessingClick = true
        
        letterInfo.isUsed = true
        answerSlots[slotIndex] = letterIndex
        
        // Smooth fade out animation
        letterInfo.view?.animate()
            ?.alpha(0.3f)
            ?.scaleX(0.9f)
            ?.scaleY(0.9f)
            ?.setDuration(120)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
        
        // Fill the slot with smooth animation
        val slotView = binding.answerSlotsLayout.getChildAt(slotIndex) as? TextView
        slotView?.apply {
            text = letterInfo.char.toString()
            setTextColor(getColor(R.color.white))
            setBackgroundResource(R.drawable.bg_word_letter)
            
            setOnClickListener {
                onSlotClicked(slotIndex)
            }
            
            scaleX = 1.15f
            scaleY = 1.15f
            animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(120)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    isProcessingClick = false
                    
                    if (getFilledSlotCount() == currentWord.length) {
                        checkAnswer()
                    }
                }
                .start()
        } ?: run {
            isProcessingClick = false
        }
    }
    
    private fun onSlotClicked(slotIndex: Int) {
        if (isProcessingClick) return
        
        val letterIndex = answerSlots[slotIndex] ?: return
        if (letterIndex >= allLetters.size) return
        
        isProcessingClick = true
        
        val letterInfo = allLetters[letterIndex]
        
        letterInfo.isUsed = false
        answerSlots[slotIndex] = null
        
        // Restore letter with smooth animation
        letterInfo.view?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(120)
            ?.setInterpolator(OvershootInterpolator(1.5f))
            ?.start()
        
        shiftAnswerLeft(slotIndex)
        
        isProcessingClick = false
    }
    
    private fun shiftAnswerLeft(fromIndex: Int) {
        for (i in fromIndex until currentWord.length - 1) {
            answerSlots[i] = answerSlots[i + 1]
        }
        answerSlots[currentWord.length - 1] = null
        
        for (i in currentWord.indices) {
            val slotView = binding.answerSlotsLayout.getChildAt(i) as? TextView ?: continue
            val letterIdx = answerSlots[i]
            
            if (letterIdx != null && letterIdx < allLetters.size) {
                val letterInfo = allLetters[letterIdx]
                slotView.text = letterInfo.char.toString()
                slotView.setTextColor(getColor(R.color.white))
                slotView.setBackgroundResource(R.drawable.bg_word_letter)
                val currentSlotIndex = i
                slotView.setOnClickListener {
                    onSlotClicked(currentSlotIndex)
                }
            } else {
                slotView.text = ""
                slotView.setTextColor(getColor(R.color.text_hint))
                slotView.setBackgroundResource(R.drawable.bg_word_slot)
                slotView.setOnClickListener(null)
            }
        }
    }
    
    private fun getFilledSlotCount(): Int {
        return answerSlots.take(currentWord.length).count { it != null }
    }
    
    private fun getCurrentAnswer(): String {
        val sb = StringBuilder()
        for (i in currentWord.indices) {
            val letterIdx = answerSlots[i]
            if (letterIdx != null && letterIdx < allLetters.size) {
                sb.append(allLetters[letterIdx].char)
            }
        }
        return sb.toString()
    }
    
    private fun clearAnswer() {
        if (isProcessingClick) return
        isProcessingClick = true
        
        for (i in currentWord.indices) {
            val letterIdx = answerSlots[i] ?: continue
            if (letterIdx < allLetters.size) {
                val letterInfo = allLetters[letterIdx]
                letterInfo.isUsed = false
                letterInfo.view?.animate()
                    ?.alpha(1f)
                    ?.scaleX(1f)
                    ?.scaleY(1f)
                    ?.setDuration(120)
                    ?.start()
            }
            answerSlots[i] = null
        }
        
        for (i in currentWord.indices) {
            val slotView = binding.answerSlotsLayout.getChildAt(i) as? TextView ?: continue
            slotView.text = ""
            slotView.setTextColor(getColor(R.color.text_hint))
            slotView.setBackgroundResource(R.drawable.bg_word_slot)
            slotView.setOnClickListener(null)
        }
        
        isProcessingClick = false
    }
    
    private fun checkAnswer() {
        timer?.cancel()
        
        val answer = getCurrentAnswer()
        
        // Use the new isCorrectAnswer method which handles anagrams properly
        val isCorrect = WordDictionary.isCorrectAnswer(answer, currentWord)
        
        if (isCorrect) {
            wordsGuessed++
            
            // New scoring: (wordLength * 10) + min(timeLeft * 2, 50)
            // Time bonus is 0 if hint was used for this word
            val timeBonus = if (hintUsedThisWord) 0 else minOf(timeLeft * 2, 50)
            val wordPoints = (currentWord.length * 10) + timeBonus
            totalScore += wordPoints
            
            soundManager.playCorrect()
            
            val bonusText = if (hintUsedThisWord) "(no time bonus)" else ""
            showFeedback(true, "+$wordPoints pts! $bonusText")
            
            binding.root.postDelayed({
                startRound()
            }, 1000)
        } else {
            // Wrong answer - lose a life instead of game over
            lives--
            soundManager.playLoseLife()
            
            if (lives <= 0) {
                showFeedback(false, "Word: $currentWord\nHint: $currentHint")
                binding.root.postDelayed({
                    showGameOver()
                }, 2000)
            } else {
                showFeedback(false, "Wrong!\n\nWord: $currentWord\nHint: $currentHint\n\n❤️ $lives lives left")
                binding.root.postDelayed({
                    startRound()
                }, 2000)
            }
        }
        
        updateUI()
    }
    
    private fun showFeedback(correct: Boolean, message: String) {
        val color = if (correct) R.color.game_green else R.color.game_red
        
        binding.tvHint.text = message
        binding.tvHint.setTextColor(getColor(color))
        binding.tvHint.visibility = View.VISIBLE
        
        binding.tvHint.alpha = 0f
        binding.tvHint.translationY = 20f
        binding.tvHint.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
    
    private fun showHint() {
        if (hintsRemaining <= 0) return
        
        hintsRemaining--
        hintUsedThisWord = true  // Mark hint used - kills time bonus
        updateHintButton()
        
        binding.tvHint.text = "💡 $currentHint"
        binding.tvHint.setTextColor(getColor(R.color.game_yellow))
        binding.tvHint.visibility = View.VISIBLE
        
        binding.tvHint.alpha = 0f
        binding.tvHint.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        timeLeft = maxOf(5, timeLeft - 3)
    }
    
    private fun updateHintButton() {
        binding.btnHint.text = "💡 Hint ($hintsRemaining)"
        binding.btnHint.alpha = if (hintsRemaining > 0) 1f else 0.5f
        binding.btnHint.isEnabled = hintsRemaining > 0
    }
    
    private fun startTimer() {
        timer?.cancel()
        
        timer = object : CountDownTimer(timeLeft * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = (millisUntilFinished / 1000).toInt()
                binding.tvTimer.text = "⏱️ $timeLeft"
                
                if (timeLeft <= 5) {
                    binding.tvTimer.setTextColor(getColor(R.color.game_red))
                }
            }
            
            override fun onFinish() {
                timeLeft = 0
                binding.tvTimer.text = "⏱️ 0"
                
                // Time out - lose a life
                lives--
                soundManager.playLoseLife()
                updateUI()
                
                if (lives <= 0) {
                    showFeedback(false, "Time's up!\n\nWord: $currentWord\nHint: $currentHint")
                    binding.root.postDelayed({
                        showGameOver()
                    }, 1500)
                } else {
                    showFeedback(false, "Time's up!\n\nWord: $currentWord\nHint: $currentHint\n\n❤️ $lives lives left")
                    binding.root.postDelayed({
                        startRound()
                    }, 1500)
                }
            }
        }.start()
    }
    
    private fun updateUI() {
        binding.tvLevel.text = "❤️".repeat(lives) + "🖤".repeat(maxLives - lives)
        binding.tvScore.text = "Score: $totalScore"
        binding.tvStreak.text = if (wordsGuessed > 0) "🔥 $wordsGuessed" else ""
        binding.tvTimer.setTextColor(getColor(R.color.game_yellow))
    }
    
    private fun showGameOver() {
        timer?.cancel()
        
        // Check if this is a new high score
        val isNewHighScore = totalScore > previousHighScore
        if (isNewHighScore) {
            soundManager.playHighscore()
        } else {
            soundManager.playGameOver()
        }
        
        saveScore()
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        dialogBinding.tvResultEmoji.text = when {
            wordsGuessed >= 10 -> "📚"
            wordsGuessed >= 5 -> "📝"
            else -> "👍"
        }
        
        dialogBinding.tvTitle.text = when {
            wordsGuessed >= 10 -> "Word Wizard!"
            wordsGuessed >= 5 -> "Great Vocabulary!"
            else -> "Good Try!"
        }
        
        dialogBinding.tvScore.text = "Score: $totalScore"
        
        // Show badges using helper function
        GameOverHelper.showBadges(dialogBinding, GameType.WORD_SCRAMBLE, totalScore, previousHighScore, this)
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Words Solved"
        dialogBinding.tvStat1Value.text = "$wordsGuessed"
        dialogBinding.tvStat2Label.text = "Score"
        dialogBinding.tvStat2Value.text = "$totalScore"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.WORD_SCRAMBLE)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            setupGame()
            binding.startOverlay.alpha = 1f
            binding.startOverlay.visibility = View.VISIBLE
            binding.tvHint.visibility = View.GONE
            binding.answerSlotsLayout.removeAllViews()
            binding.scrambledLettersLayout.removeAllViews()
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun saveScore() {
        val player = prefsManager.currentPlayer ?: return
        
        val gameScore = GameScore(
            playerId = player.id,
            playerUsername = player.username,
            gameType = GameType.WORD_SCRAMBLE,
            score = totalScore,
            extras = mapOf("wordsGuessed" to wordsGuessed)
        )
        
        lifecycleScope.launch {
            val saved = firebaseRepo.saveScore(gameScore)
            android.util.Log.d("WordGame", "Score saved: $saved, wordsGuessed: $wordsGuessed, score: $totalScore")
            firebaseRepo.incrementGamesPlayed(player.id)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
