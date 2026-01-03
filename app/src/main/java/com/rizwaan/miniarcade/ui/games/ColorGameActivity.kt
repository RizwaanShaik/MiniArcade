package com.rizwaan.miniarcade.ui.games

import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
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
import com.rizwaan.miniarcade.databinding.ActivityColorGameBinding
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import com.rizwaan.miniarcade.util.SoundManager
import kotlinx.coroutines.launch
import kotlin.random.Random

class ColorGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColorGameBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var soundManager: SoundManager
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var isPlaying = false
    private var score = 0L
    private var lives = 3
    private var currentLevel = 1
    private var targetColor: GameColor? = null
    private var spawnDelay = 1200L
    private var fallDuration = 3000L
    private var gameStartTime = 0L
    private val activeObjects = mutableListOf<ObjectData>()
    
    // Tracking for improved scoring
    private var ballsSpawned = 0  // Total balls spawned (excluding bombs)
    private var catches = 0        // Successful catches of target color
    private var previousHighScore = 0L  // Track previous high score for new high score detection
    
    data class ObjectData(
        val view: View,
        val type: String, // "target", "other", "bomb"
        val animator: ValueAnimator,
        var handled: Boolean = false
    )
    
    private val colors = listOf(
        GameColor("RED", "🔴", Color.parseColor("#FF5252")),
        GameColor("BLUE", "🔵", Color.parseColor("#2196F3")),
        GameColor("GREEN", "🟢", Color.parseColor("#4CAF50")),
        GameColor("YELLOW", "🟡", Color.parseColor("#FFD740"))
    )
    
    data class GameColor(val name: String, val emoji: String, val color: Int)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityColorGameBinding.inflate(layoutInflater)
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
            previousHighScore = playerData?.getScore(GameType.COLOR_CATCH) ?: 0L
        }
    }
    
    private fun setupGame() {
        score = 0
        lives = 3
        currentLevel = 1
        spawnDelay = 1200L
        fallDuration = 3000L
        isPlaying = false
        ballsSpawned = 0
        catches = 0
        activeObjects.clear()
        updateUI()
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { 
            stopGame()
            finish() 
        }
        
        binding.btnStart.setOnClickListener {
            startGame()
        }
    }
    
    private fun startGame() {
        binding.btnStart.isEnabled = false
        binding.startContent.visibility = View.GONE
        binding.tvCountdown.visibility = View.VISIBLE
        
        startCountdown()
    }
    
    private fun startCountdown() {
        val countdownTexts = listOf("3", "2", "1", "GO!")
        var countdownIndex = 0
        
        // Play countdown sound once at the start (it contains 3-2-1-GO)
        soundManager.playCountdown()
        
        fun showNextCountdown() {
            if (countdownIndex >= countdownTexts.size) {
                // Countdown complete - start the game
                binding.startOverlay.visibility = View.GONE
                binding.tvCountdown.visibility = View.GONE
                isPlaying = true
                gameStartTime = System.currentTimeMillis()
                
                // Pick random target color
                targetColor = colors.random()
                binding.tvTargetColor.text = "${targetColor!!.emoji} ${targetColor!!.name}"
                binding.tvTargetColor.setTextColor(targetColor!!.color)
                
                startSpawning()
                startDifficultyIncrease()
                return
            }
            
            binding.tvCountdown.text = countdownTexts[countdownIndex]
            binding.tvCountdown.alpha = 1f
            binding.tvCountdown.scaleX = 0.5f
            binding.tvCountdown.scaleY = 0.5f
            
            // Animate countdown with slight delay before starting
            handler.postDelayed({
                binding.tvCountdown.animate()
                    .scaleX(1.2f)
                    .scaleY(1.2f)
                    .alpha(1f)
                    .setDuration(200)
                    .withEndAction {
                        binding.tvCountdown.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .alpha(0.3f)
                            .setDuration(300)
                            .withEndAction {
                                countdownIndex++
                                handler.postDelayed({ showNextCountdown() }, 200)
                            }
                            .start()
                    }
                    .start()
            }, 150) // Small delay before animation starts
        }
        
        showNextCountdown()
    }
    
    private fun stopGame() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        
        // Remove all active objects
        activeObjects.forEach { data ->
            data.animator.cancel()
            binding.gameArea.removeView(data.view)
        }
        activeObjects.clear()
    }
    
    private fun startSpawning() {
        if (!isPlaying) return
        
        spawnObject()
        
        handler.postDelayed({
            startSpawning()
        }, spawnDelay)
    }
    
    private fun startDifficultyIncrease() {
        if (!isPlaying) return
        
        // Increase difficulty every 5 seconds
        handler.postDelayed({
            if (isPlaying) {
                // Decrease spawn delay (more objects)
                spawnDelay = (spawnDelay * 0.92).toLong().coerceAtLeast(400L)
                // Decrease fall duration (faster falling)
                fallDuration = (fallDuration * 0.95).toLong().coerceAtLeast(1200L)
                currentLevel++
                updateUI()
                
                // Maybe change target color every few levels
                if (currentLevel % 5 == 0) {
                    targetColor = colors.random()
                    binding.tvTargetColor.text = "${targetColor!!.emoji} ${targetColor!!.name}"
                    binding.tvTargetColor.setTextColor(targetColor!!.color)
                }
                
                startDifficultyIncrease()
            }
        }, 5000L)
    }
    
    private fun spawnObject() {
        if (!isPlaying) return
        
        val gameArea = binding.gameArea
        val areaWidth = gameArea.width
        if (areaWidth <= 0) {
            handler.postDelayed({ spawnObject() }, 100)
            return
        }
        
        val objectSize = resources.getDimensionPixelSize(R.dimen.color_ball_size)
        
        // Decide what to spawn: target color (40%), other colors (45%), or bomb (15%)
        val spawnType = Random.nextInt(100)
        
        val objectType: String
        val objectEmoji: String
        
        when {
            spawnType < 40 -> {
                // Target color
                objectType = "target"
                objectEmoji = targetColor!!.emoji
            }
            spawnType < 85 -> {
                // Other color
                objectType = "other"
                val otherColor = colors.filter { it != targetColor }.random()
                objectEmoji = otherColor.emoji
            }
            else -> {
                // Bomb
                objectType = "bomb"
                objectEmoji = "💣"
            }
        }
        
        val objectView = TextView(this).apply {
            text = objectEmoji
            textSize = 44f
            gravity = Gravity.CENTER
            setTextColor(0xFF000000.toInt()) // Black for full color emoji
            
            layoutParams = FrameLayout.LayoutParams(objectSize, objectSize).apply {
                leftMargin = Random.nextInt(areaWidth - objectSize)
                topMargin = -objectSize
            }
        }
        
        binding.gameArea.addView(objectView)
        
        // Track balls spawned (excluding bombs)
        if (objectType != "bomb") {
            ballsSpawned++
        }
        
        // Animate falling
        val animator = animateObject(objectView, objectSize, objectType)
        val objectData = ObjectData(objectView, objectType, animator)
        activeObjects.add(objectData)
        
        objectView.setOnClickListener {
            handleObjectTap(objectData)
        }
    }
    
    private fun animateObject(view: View, objectSize: Int, objectType: String): ValueAnimator {
        val gameAreaHeight = binding.gameArea.height
        
        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fallDuration
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                if (!isPlaying) {
                    cancel()
                    return@addUpdateListener
                }
                
                val progress = animation.animatedValue as Float
                val newY = -objectSize + (gameAreaHeight + objectSize) * progress
                
                (view.layoutParams as FrameLayout.LayoutParams).topMargin = newY.toInt()
                view.requestLayout()
                
                // Check if object reached bottom (missed)
                if (progress >= 0.98f) {
                    val data = activeObjects.find { it.view == view }
                    if (data != null && !data.handled) {
                        handleMissedObject(data)
                    }
                }
            }
        }
        
        animator.start()
        return animator
    }
    
    private fun handleObjectTap(data: ObjectData) {
        if (!isPlaying || data.handled) return
        
        data.handled = true
        data.animator.cancel()
        
        when (data.type) {
            "target" -> {
                // Correct catch!
                catches++
                soundManager.playCorrect()
                showFeedback(data.view, "", R.color.game_green)
            }
            "bomb" -> {
                // Hit a bomb - GAME OVER!
                soundManager.playFail()
                showFeedback(data.view, "💥", R.color.game_red)
                removeObject(data)
                handler.postDelayed({
                    stopGame()
                    showGameOver(hitBomb = true)
                }, 300)
                return
            }
            "other" -> {
                // Wrong color - lose a life
                lives--
                soundManager.playLoseLife()
                showFeedback(data.view, "", R.color.game_red)
            }
        }
        
        removeObject(data)
        updateUI()
        checkGameOver()
    }
    
    private fun handleMissedObject(data: ObjectData) {
        if (!isPlaying || data.handled) return
        
        data.handled = true
        
        // Only count as miss if it was a target color
        if (data.type == "target") {
            lives--
            soundManager.playLoseLife()
            showFeedback(data.view, "Miss!", R.color.game_yellow)
            updateUI()
            checkGameOver()
        }
        
        removeObject(data)
    }
    
    private fun showFeedback(anchorView: View, text: String, colorRes: Int) {
        val params = anchorView.layoutParams as? FrameLayout.LayoutParams ?: return
        
        val feedback = TextView(this).apply {
            this.text = text
            textSize = 20f
            setTextColor(getColor(colorRes))
            typeface = resources.getFont(R.font.poppins_bold)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = params.leftMargin
                topMargin = params.topMargin
            }
        }
        
        binding.gameArea.addView(feedback)
        
        feedback.animate()
            .alpha(0f)
            .translationYBy(-80f)
            .setDuration(600)
            .withEndAction {
                binding.gameArea.removeView(feedback)
            }
            .start()
    }
    
    private fun removeObject(data: ObjectData) {
        data.animator.cancel()
        activeObjects.remove(data)
        
        data.view.animate()
            .alpha(0f)
            .scaleX(0.5f)
            .scaleY(0.5f)
            .setDuration(150)
            .withEndAction {
                binding.gameArea.removeView(data.view)
            }
            .start()
    }
    
    private fun checkGameOver() {
        if (lives <= 0) {
            stopGame()
            showGameOver(hitBomb = false)
        }
    }
    
    private fun updateUI() {
        // Show catches count instead of old score
        binding.tvScore.text = "Catches: $catches"
        binding.tvMisses.text = "❤️".repeat(lives)
    }
    
    private fun showGameOver(hitBomb: Boolean) {
        val finalScore = calculateScore()
        
        // Check if this is a new high score
        val isNewHighScore = finalScore > previousHighScore
        if (isNewHighScore) {
            soundManager.playHighscore()
        } else {
            soundManager.playDefeat()  // Lost all lives - defeat sound
        }
        
        saveScore()
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        val accuracy = if (ballsSpawned > 0) (catches.toDouble() / ballsSpawned * 100).toInt() else 0
        
        if (hitBomb) {
            dialogBinding.tvResultEmoji.text = "💥"
            dialogBinding.tvTitle.text = "BOOM! Hit a bomb!"
        } else {
            dialogBinding.tvResultEmoji.text = when {
                accuracy > 80 -> "🌈"
                accuracy > 50 -> "🎨"
                else -> "👍"
            }
            dialogBinding.tvTitle.text = when {
                accuracy > 80 -> "Color Master!"
                accuracy > 50 -> "Great Catching!"
                else -> "Good Try!"
            }
        }
        
        // Show score even if less than 20 balls (show partial score)
        if (ballsSpawned >= 20) {
            dialogBinding.tvScore.text = "Score: $finalScore"
            // Show badges using helper function
            GameOverHelper.showBadges(dialogBinding, GameType.COLOR_CATCH, finalScore, previousHighScore, this)
        } else {
            // Show partial score for games with less than 20 balls
            val partialScore = calculateScore() // Calculate anyway
            dialogBinding.tvScore.text = "Score: $partialScore (Partial - $ballsSpawned balls)"
            // Don't show badges for partial scores
            dialogBinding.tvNewHighScore.visibility = View.GONE
            dialogBinding.tvPersonalBest.visibility = View.GONE
        }
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Level"
        dialogBinding.tvStat1Value.text = "$currentLevel"
        dialogBinding.tvStat2Label.text = "Accuracy"
        dialogBinding.tvStat2Value.text = "$catches/$ballsSpawned ($accuracy%)"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.COLOR_CATCH)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            setupGame()
            binding.startOverlay.visibility = View.VISIBLE
            binding.startContent.visibility = View.VISIBLE
            binding.btnStart.isEnabled = true
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun calculateScore(): Long {
        // Only calculate if minimum balls spawned
        if (ballsSpawned < 20) return 0L
        
        // New scoring: (catches / ballsSpawned) * level * 1000
        val accuracy = catches.toDouble() / ballsSpawned.toDouble()
        return (accuracy * currentLevel * 1000).toLong()
    }
    
    private fun saveScore() {
        val player = prefsManager.currentPlayer ?: return
        
        // Only save if minimum threshold met
        if (ballsSpawned < 20) {
            android.util.Log.d("ColorGame", "Score not saved: ballsSpawned=$ballsSpawned < 20")
            lifecycleScope.launch {
                firebaseRepo.incrementGamesPlayed(player.id)
            }
            return
        }
        
        val finalScore = calculateScore()
        
        val gameScore = GameScore(
            playerId = player.id,
            playerUsername = player.username,
            gameType = GameType.COLOR_CATCH,
            score = finalScore,
            extras = mapOf("level" to currentLevel, "catches" to catches, "ballsSpawned" to ballsSpawned)
        )
        
        lifecycleScope.launch {
            val saved = firebaseRepo.saveScore(gameScore)
            android.util.Log.d("ColorGame", "Score saved: $saved, catches: $catches/$ballsSpawned, level: $currentLevel, score: $finalScore")
            firebaseRepo.incrementGamesPlayed(player.id)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopGame()
    }
}
