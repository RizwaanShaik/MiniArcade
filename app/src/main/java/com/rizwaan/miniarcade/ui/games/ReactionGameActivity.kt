package com.rizwaan.miniarcade.ui.games

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.models.GameScore
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityReactionGameBinding
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import com.rizwaan.miniarcade.util.SoundManager
import kotlinx.coroutines.launch
import kotlin.random.Random

class ReactionGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReactionGameBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var soundManager: SoundManager
    
    private val handler = Handler(Looper.getMainLooper())
    private var gameState = GameState.IDLE
    private var startTime = 0L
    private var currentRound = 0
    private val totalRounds = 5
    private val reactionTimes = mutableListOf<Long>()
    private var earlyTapsForCurrentRound = 0  // Track early taps for current round only
    private var totalEarlyTaps = 0  // Track total early taps for display
    private var previousHighScore = Long.MAX_VALUE  // Track previous average time (lower is better)
    
    private var pendingGoRunnable: Runnable? = null
    private var pendingResetRunnable: Runnable? = null
    
    enum class GameState {
        IDLE,           // Ready to start - RED, "TAP TO START"
        WAITING,        // Waiting for green - RED, "WAIT..."
        GO,             // Tap now! - GREEN, "TAP NOW!"
        TOO_EARLY,      // Tapped too early - RED, "TOO EARLY!"
        SHOWING_RESULT, // Showing reaction time - GREEN with time
        FINISHED        // All rounds complete
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityReactionGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        prefsManager = PreferencesManager(this)
        firebaseRepo = FirebaseRepository()
        soundManager = SoundManager.getInstance(this)
        
        resetToIdle()
        setupClickListeners()
        loadPreviousHighScore()
    }
    
    private fun loadPreviousHighScore() {
        lifecycleScope.launch {
            val player = prefsManager.currentPlayer ?: return@launch
            val playerData = firebaseRepo.getPlayerScores(player.id)
            val previousAvg = playerData?.getScore(GameType.REACTION_TIME) ?: 0L
            // For reaction time, 0 means no previous score, so use MAX_VALUE
            // Lower is better for reaction time (average)
            previousHighScore = if (previousAvg > 0) previousAvg else Long.MAX_VALUE
        }
    }
    
    private fun resetToIdle() {
        cancelAllPendingActions()
        
        currentRound = 0
        reactionTimes.clear()
        earlyTapsForCurrentRound = 0
        totalEarlyTaps = 0
        gameState = GameState.IDLE
        
        setRedBackground()
        binding.tvInstruction.text = "TAP TO START"
        binding.tvSubtext.text = "Tap anywhere to begin"
        binding.tvSubtext.visibility = View.VISIBLE
        binding.tvReactionTime.visibility = View.GONE
        binding.tvReactionLabel.visibility = View.GONE
        binding.penaltyLayout.visibility = View.GONE
        binding.tvTryAgain.visibility = View.GONE
        binding.tvRound.text = "0/$totalRounds"
        binding.tvBestTime.text = "---"
        binding.tvAvgTime.text = "---"
    }
    
    private fun cancelAllPendingActions() {
        pendingGoRunnable?.let { handler.removeCallbacks(it) }
        pendingResetRunnable?.let { handler.removeCallbacks(it) }
        pendingGoRunnable = null
        pendingResetRunnable = null
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { 
            cancelAllPendingActions()
            finish() 
        }
        
        binding.gameArea.setOnClickListener {
            handleTap()
        }
    }
    
    private fun handleTap() {
        when (gameState) {
            GameState.IDLE -> startRound()
            GameState.WAITING -> tooEarly()
            GameState.GO -> recordReaction()
            GameState.TOO_EARLY -> retryRound()  // Don't reset early taps when retrying
            GameState.SHOWING_RESULT -> { /* Ignore */ }
            GameState.FINISHED -> { /* Ignore */ }
        }
    }
    
    private fun startRound() {
        cancelAllPendingActions()
        
        currentRound++
        earlyTapsForCurrentRound = 0  // Reset early taps for new round
        gameState = GameState.WAITING
        
        setupRoundUI()
    }
    
    private fun retryRound() {
        // Retry the same round after early tap - DON'T reset earlyTapsForCurrentRound
        cancelAllPendingActions()
        
        // Don't increment currentRound or reset earlyTapsForCurrentRound
        gameState = GameState.WAITING
        
        setupRoundUI()
    }
    
    private fun setupRoundUI() {
        binding.tvRound.text = "$currentRound/$totalRounds"
        
        setRedBackground()
        binding.tvInstruction.text = "WAIT..."
        binding.tvSubtext.visibility = View.VISIBLE
        binding.tvReactionTime.visibility = View.GONE
        binding.tvReactionLabel.visibility = View.GONE
        binding.penaltyLayout.visibility = View.GONE
        binding.tvTryAgain.visibility = View.GONE
        
        // Random delay between 1.5 and 4 seconds
        val delay = Random.nextLong(1500, 4000)
        pendingGoRunnable = Runnable {
            if (gameState == GameState.WAITING) {
                showGo()
            }
        }
        handler.postDelayed(pendingGoRunnable!!, delay)
    }
    
    private fun showGo() {
        gameState = GameState.GO
        startTime = System.currentTimeMillis()
        
        // Smooth transition to green
        binding.gameArea.alpha = 0.9f
        setGreenBackground()
        binding.gameArea.animate()
            .alpha(1f)
            .setDuration(100)
            .start()
        
        binding.tvInstruction.text = "TAP NOW!"
        binding.tvSubtext.text = "As fast as you can!"
        binding.tvSubtext.visibility = View.VISIBLE
        binding.penaltyLayout.visibility = View.GONE
        binding.tvTryAgain.visibility = View.GONE
        
        // Pulse animation with overshoot
        binding.tvInstruction.scaleX = 0.7f
        binding.tvInstruction.scaleY = 0.7f
        binding.tvInstruction.alpha = 0f
        binding.tvInstruction.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
    }
    
    private fun tooEarly() {
        cancelAllPendingActions()
        gameState = GameState.TOO_EARLY
        
        // Track early tap for this round
        earlyTapsForCurrentRound++
        totalEarlyTaps++
        
        soundManager.playLoseLife()
        setRedBackground()
        binding.tvInstruction.text = "TOO EARLY!"
        binding.tvSubtext.visibility = View.GONE
        binding.tvReactionTime.visibility = View.GONE
        binding.tvReactionLabel.visibility = View.GONE
        
        // Show "Tap to try again" with different font
        binding.tvTryAgain.visibility = View.VISIBLE
        binding.tvTryAgain.alpha = 0f
        binding.tvTryAgain.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
        
        // Show penalty for this round only (10ms per tap, added to average)
        binding.tvPenalty.text = "+10ms penalty"
        binding.penaltyLayout.visibility = View.VISIBLE
        binding.penaltyLayout.alpha = 0f
        binding.penaltyLayout.scaleX = 0.5f
        binding.penaltyLayout.scaleY = 0.5f
        binding.penaltyLayout.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator(1.5f))
            .start()
        
        // Update average immediately to show penalty effect
        updateStats()
        
        // Shake animation for instruction
        binding.tvInstruction.animate()
            .translationX(-15f)
            .setDuration(50)
            .withEndAction {
                binding.tvInstruction.animate()
                    .translationX(15f)
                    .setDuration(50)
                    .withEndAction {
                        binding.tvInstruction.animate()
                            .translationX(-10f)
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
            }
            .start()
        
        currentRound--
        binding.tvRound.text = "$currentRound/$totalRounds"
    }
    
    private fun recordReaction() {
        val rawReactionTime = System.currentTimeMillis() - startTime
        // Don't add penalty to individual rounds - we'll add it directly to average later
        android.util.Log.d("ReactionGame", "recordReaction: round=$currentRound, rawTime=$rawReactionTime, earlyTaps=$earlyTapsForCurrentRound")
        
        reactionTimes.add(rawReactionTime)
        
        soundManager.playCorrect()
        gameState = GameState.SHOWING_RESULT
        
        // Keep green background for showing result
        setGreenBackground()
        binding.tvInstruction.text = "NICE!"
        binding.tvSubtext.visibility = View.GONE
        binding.penaltyLayout.visibility = View.GONE
        binding.tvTryAgain.visibility = View.GONE
        // Show the raw reaction time (no penalty shown here)
        binding.tvReactionTime.text = "$rawReactionTime"
        binding.tvReactionTime.visibility = View.VISIBLE
        binding.tvReactionLabel.visibility = View.VISIBLE
        
        // Pop animation for reaction time
        binding.tvReactionTime.scaleX = 0.5f
        binding.tvReactionTime.scaleY = 0.5f
        binding.tvReactionTime.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(300)
            .setInterpolator(OvershootInterpolator())
            .start()
        
        updateStats()
        
        if (currentRound >= totalRounds) {
            gameState = GameState.FINISHED
            pendingResetRunnable = Runnable { showGameOver() }
            handler.postDelayed(pendingResetRunnable!!, 1500)
        } else {
            pendingResetRunnable = Runnable {
                gameState = GameState.IDLE
                setRedBackground()
                binding.tvInstruction.text = "TAP TO START"
                binding.tvSubtext.text = "Round ${currentRound + 1} of $totalRounds"
                binding.tvSubtext.visibility = View.VISIBLE
                binding.tvReactionTime.visibility = View.GONE
                binding.tvReactionLabel.visibility = View.GONE
                binding.penaltyLayout.visibility = View.GONE
                binding.tvTryAgain.visibility = View.GONE
            }
            handler.postDelayed(pendingResetRunnable!!, 1500)
        }
    }
    
    private fun setRedBackground() {
        binding.gameArea.setBackgroundResource(R.drawable.bg_reaction_red)
    }
    
    private fun setGreenBackground() {
        binding.gameArea.setBackgroundResource(R.drawable.bg_reaction_green)
    }
    
    private fun updateStats() {
        if (reactionTimes.isNotEmpty()) {
            val best = reactionTimes.minOrNull() ?: 0
            // Calculate average without penalties (penalties added at the end)
            val rawAvg = kotlin.math.round(reactionTimes.average()).toLong()
            // Add penalty directly to average: 10ms per early tap
            val penalty = totalEarlyTaps * 10L
            val avg = rawAvg + penalty
            
            android.util.Log.d("ReactionGame", "updateStats: reactionTimes=$reactionTimes, rawAvg=$rawAvg, totalEarlyTaps=$totalEarlyTaps, penalty=$penalty, finalAvg=$avg, best=$best")
            
            binding.tvBestTime.text = "$best"
            binding.tvAvgTime.text = "$avg"
        } else if (totalEarlyTaps > 0) {
            // Show penalty even if no rounds completed yet
            val penalty = totalEarlyTaps * 10L
            binding.tvBestTime.text = "---"
            binding.tvAvgTime.text = "+$penalty"
        } else {
            // No reaction times and no penalties
            binding.tvBestTime.text = "---"
            binding.tvAvgTime.text = "---"
        }
    }
    
    private fun showGameOver() {
        // Safety check: ensure we have reaction times
        if (reactionTimes.isEmpty()) {
            android.util.Log.e("ReactionGame", "showGameOver called with no reaction times!")
            return
        }
        
        val best = reactionTimes.minOrNull() ?: 0
        // Calculate average without penalties first, then add penalty directly to average
        val rawAvg = kotlin.math.round(reactionTimes.average()).toLong()
        // Add penalty directly to average: 10ms per early tap
        val penalty = totalEarlyTaps * 10L
        val avg = rawAvg + penalty
        
        android.util.Log.d("ReactionGame", "showGameOver: reactionTimes=$reactionTimes, rawAvg=$rawAvg, totalEarlyTaps=$totalEarlyTaps, penalty=$penalty, finalAvg=$avg")
        
        // Check if this is a new best average (lower is better for reaction time)
        // Only count as high score if there was a previous score AND we beat it
        val isNewHighScore = avg > 0 && previousHighScore != Long.MAX_VALUE && avg < previousHighScore
        if (isNewHighScore) {
            soundManager.playHighscore()
        } else {
            soundManager.playVictory()  // Completed all rounds - victory sound
        }
        
        saveScore(avg)
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        dialogBinding.tvResultEmoji.text = when {
            avg < 200 -> "🚀"
            avg < 300 -> "⚡"
            avg < 400 -> "👍"
            else -> "🐢"
        }
        
        dialogBinding.tvTitle.text = when {
            avg < 200 -> "Lightning Fast!"
            avg < 300 -> "Great Reflexes!"
            avg < 400 -> "Good Job!"
            else -> "Keep Practicing!"
        }
        
        dialogBinding.tvScore.text = "Average: $avg ms"
        
        // Show badges using helper function
        // For reaction time, convert previousHighScore (Long.MAX_VALUE means no previous score)
        val prevScore = if (previousHighScore == Long.MAX_VALUE) 0L else previousHighScore
        GameOverHelper.showBadges(dialogBinding, GameType.REACTION_TIME, avg, prevScore, this)
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Best Time"
        dialogBinding.tvStat1Value.text = "$best ms"
        dialogBinding.tvStat2Label.text = "Average"
        dialogBinding.tvStat2Value.text = "$avg ms"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.REACTION_TIME)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .setOnCancelListener { finish() }
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            resetToIdle()
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun saveScore(averageTime: Long) {
        val player = prefsManager.currentPlayer ?: return
        
        val score = GameScore(
            playerId = player.id,
            playerUsername = player.username,
            gameType = GameType.REACTION_TIME,
            score = averageTime,
            extras = mapOf(
                "best" to (reactionTimes.minOrNull() ?: 0L),
                "earlyTaps" to totalEarlyTaps.toLong()
            )
        )
        
        lifecycleScope.launch {
            val saved = firebaseRepo.saveScore(score)
            android.util.Log.d("ReactionGame", "Score saved: $saved, playerId: ${player.id}, username: ${player.username}, average: $averageTime")
            firebaseRepo.incrementGamesPlayed(player.id)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        cancelAllPendingActions()
    }
}
