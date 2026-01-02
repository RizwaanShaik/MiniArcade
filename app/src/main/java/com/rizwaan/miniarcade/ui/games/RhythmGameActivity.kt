package com.rizwaan.miniarcade.ui.games

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
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
import com.rizwaan.miniarcade.databinding.ActivityRhythmGameBinding
import com.rizwaan.miniarcade.databinding.DialogGameOverBinding
import com.rizwaan.miniarcade.util.SoundManager
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

class RhythmGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRhythmGameBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var gestureDetector: GestureDetector
    private lateinit var soundManager: SoundManager
    
    private val handler = Handler(Looper.getMainLooper())
    
    private var isPlaying = false
    private var score = 0L
    private var combo = 0
    private var maxCombo = 0
    private var lives = 3
    private var gameStartTime = 0L
    
    // Difficulty - starts easy, gets harder over time
    private var spawnInterval = 1500L  // Time between spawns
    private var fallDuration = 3500L   // Time for shape to fall
    
    private val activeShapes = mutableListOf<FallingShape>()
    
    data class FallingShape(
        val view: TextView,
        val direction: Direction,
        val animator: ValueAnimator,
        var handled: Boolean = false
    )
    
    enum class Direction(val symbol: String) {
        LEFT("◀"),
        UP("▲"),
        DOWN("▼"),
        RIGHT("▶")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityRhythmGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        prefsManager = PreferencesManager(this)
        soundManager = SoundManager.getInstance(this)
        firebaseRepo = FirebaseRepository()
        
        setupGestureDetector()
        setupGame()
        setupClickListeners()
    }
    
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80
            private val SWIPE_VELOCITY_THRESHOLD = 80
            
            override fun onDown(e: MotionEvent): Boolean = true
            
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                val direction = when {
                    abs(diffX) > abs(diffY) && abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD -> {
                        if (diffX > 0) Direction.RIGHT else Direction.LEFT
                    }
                    abs(diffY) > abs(diffX) && abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD -> {
                        if (diffY > 0) Direction.DOWN else Direction.UP
                    }
                    else -> null
                }
                
                direction?.let { handleSwipe(it) }
                return direction != null
            }
        })
    }
    
    private fun setupGame() {
        score = 0
        combo = 0
        maxCombo = 0
        lives = 3
        spawnInterval = 1500L
        fallDuration = 3500L
        isPlaying = false
        activeShapes.clear()
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
        
        // Set up touch listener on game area for swipes
        binding.gameArea.setOnTouchListener { _, event ->
            if (isPlaying) {
                gestureDetector.onTouchEvent(event)
            }
            true
        }
    }
    
    private fun startGame() {
        binding.startOverlay.visibility = View.GONE
        isPlaying = true
        gameStartTime = System.currentTimeMillis()
        
        startSpawning()
        startDifficultyIncrease()
    }
    
    private fun stopGame() {
        isPlaying = false
        handler.removeCallbacksAndMessages(null)
        
        // Remove all active shapes
        activeShapes.forEach { shape ->
            shape.animator.cancel()
            binding.gameArea.removeView(shape.view)
        }
        activeShapes.clear()
    }
    
    private fun startSpawning() {
        if (!isPlaying) return
        
        spawnShape()
        
        handler.postDelayed({
            startSpawning()
        }, spawnInterval)
    }
    
    private fun startDifficultyIncrease() {
        if (!isPlaying) return
        
        // Gradually increase difficulty every 3 seconds
        handler.postDelayed({
            if (isPlaying) {
                // Slowly decrease spawn interval (more shapes)
                spawnInterval = (spawnInterval * 0.97).toLong().coerceAtLeast(600L)
                // Slowly decrease fall duration (faster falling)
                fallDuration = (fallDuration * 0.98).toLong().coerceAtLeast(1500L)
                
                startDifficultyIncrease()
            }
        }, 3000L)
    }
    
    private fun spawnShape() {
        if (!isPlaying) return
        
        val gameArea = binding.gameArea
        val areaWidth = gameArea.width
        if (areaWidth <= 0) {
            handler.postDelayed({ spawnShape() }, 100)
            return
        }
        
        // After score 100, occasionally spawn 2 shapes at once
        val shapesToSpawn = if (score >= 100 && Random.nextFloat() < 0.3f) 2 else 1
        
        repeat(shapesToSpawn) { index ->
            handler.postDelayed({
                if (isPlaying) spawnSingleShape(gameArea, areaWidth)
            }, index * 200L) // Small delay between multiple shapes
        }
    }
    
    private fun spawnSingleShape(gameArea: FrameLayout, areaWidth: Int) {
        if (!isPlaying) return
        
        val shapeSize = resources.getDimensionPixelSize(R.dimen.rhythm_shape_size)
        val direction = Direction.entries[Random.nextInt(Direction.entries.size)]
        
        // Position shape randomly within game area
        val maxX = areaWidth - shapeSize
        val startX = Random.nextInt(maxX.coerceAtLeast(1))
        
        // Choose color based on direction for variety and visibility
        val arrowColor = when (direction) {
            Direction.LEFT -> 0xFFFF6B6B.toInt()   // Red
            Direction.UP -> 0xFF4ECB71.toInt()     // Green
            Direction.DOWN -> 0xFF54A0FF.toInt()   // Blue
            Direction.RIGHT -> 0xFFFFD93D.toInt()  // Yellow
        }
        
        val shapeView = TextView(this).apply {
            text = direction.symbol
            textSize = 56f  // Bigger arrows
            gravity = Gravity.CENTER
            setTextColor(arrowColor)
            setShadowLayer(8f, 0f, 0f, 0x88000000.toInt()) // Shadow for depth
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            
            layoutParams = FrameLayout.LayoutParams(shapeSize, shapeSize).apply {
                leftMargin = startX
                topMargin = -shapeSize
            }
        }
        
        gameArea.addView(shapeView)
        
        // Animate falling
        val animator = createFallAnimation(shapeView, shapeSize, direction)
        val fallingShape = FallingShape(shapeView, direction, animator)
        activeShapes.add(fallingShape)
        
        animator.start()
    }
    
    private fun createFallAnimation(view: View, shapeSize: Int, direction: Direction): ValueAnimator {
        val gameAreaHeight = binding.gameArea.height
        
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = fallDuration
            interpolator = LinearInterpolator()
            
            addUpdateListener { animation ->
                if (!isPlaying) {
                    cancel()
                    return@addUpdateListener
                }
                
                val progress = animation.animatedValue as Float
                val newY = -shapeSize + (gameAreaHeight + shapeSize) * progress
                
                (view.layoutParams as FrameLayout.LayoutParams).topMargin = newY.toInt()
                view.requestLayout()
                
                // Check if shape reached bottom without being swiped
                if (progress >= 0.95f) {
                    val shape = activeShapes.find { it.view == view }
                    if (shape != null && !shape.handled) {
                        handleMissedShape(shape)
                    }
                }
            }
        }
    }
    
    private fun handleSwipe(swipeDirection: Direction) {
        if (!isPlaying) return
        
        // Find the lowest (closest to bottom) unhandled shape matching the swipe direction
        val targetShape = activeShapes
            .filter { !it.handled && it.direction == swipeDirection }
            .maxByOrNull { 
                (it.view.layoutParams as? FrameLayout.LayoutParams)?.topMargin ?: 0 
            }
        
        if (targetShape != null) {
            // Correct swipe!
            handleCorrectSwipe(targetShape)
        } else {
            // Wrong swipe - check if there are any shapes at all
            val anyUnhandled = activeShapes.any { !it.handled }
            if (anyUnhandled) {
                // There are shapes but wrong direction
                handleWrongSwipe()
            }
        }
    }
    
    private fun handleCorrectSwipe(shape: FallingShape) {
        shape.handled = true
        shape.animator.cancel()
        soundManager.playCorrect()
        
        combo++
        maxCombo = maxOf(maxCombo, combo)
        score += 10L
        
        // Show feedback
        showFeedback(shape.view, "✓", R.color.game_green)
        
        // Remove shape with animation
        removeShape(shape)
        updateUI()
    }
    
    private fun handleWrongSwipe() {
        combo = 0
        lives--
        soundManager.playWrong()
        
        // Flash feedback
        binding.tvLives.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(100)
            .withEndAction {
                binding.tvLives.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
        
        updateUI()
        checkGameOver()
    }
    
    private fun handleMissedShape(shape: FallingShape) {
        if (!isPlaying || shape.handled) return
        
        shape.handled = true
        combo = 0
        lives--
        
        showFeedback(shape.view, "✗", R.color.game_red)
        removeShape(shape)
        updateUI()
        checkGameOver()
    }
    
    private fun showFeedback(anchorView: View, text: String, colorRes: Int) {
        val params = anchorView.layoutParams as? FrameLayout.LayoutParams ?: return
        
        val feedback = TextView(this).apply {
            this.text = text
            textSize = 24f
            setTextColor(getColor(colorRes))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = params.leftMargin + 10
                topMargin = params.topMargin
            }
        }
        
        binding.gameArea.addView(feedback)
        
        feedback.animate()
            .alpha(0f)
            .translationYBy(-60f)
            .setDuration(400)
            .withEndAction {
                binding.gameArea.removeView(feedback)
            }
            .start()
    }
    
    private fun removeShape(shape: FallingShape) {
        shape.animator.cancel()
        activeShapes.remove(shape)
        
        shape.view.animate()
            .alpha(0f)
            .scaleX(0.3f)
            .scaleY(0.3f)
            .setDuration(150)
            .withEndAction {
                binding.gameArea.removeView(shape.view)
            }
            .start()
    }
    
    private fun checkGameOver() {
        if (lives <= 0) {
            stopGame()
            showGameOver()
        }
    }
    
    private fun updateUI() {
        binding.tvScore.text = getString(R.string.score, score.toInt())
        binding.tvCombo.text = if (combo > 1) "🔥 x$combo" else ""
        binding.tvLives.text = "❤️".repeat(lives.coerceAtLeast(0))
    }
    
    private fun showGameOver() {
        saveScore(score)
        
        val dialogBinding = DialogGameOverBinding.inflate(layoutInflater)
        
        dialogBinding.tvResultEmoji.text = when {
            score > 500 -> "🎵"
            score > 200 -> "🎶"
            else -> "👍"
        }
        
        dialogBinding.tvTitle.text = when {
            score > 500 -> "Rhythm Master!"
            score > 200 -> "Great Moves!"
            else -> "Good Try!"
        }
        
        dialogBinding.tvScore.text = "Score: $score"
        
        dialogBinding.statsLayout.visibility = View.VISIBLE
        dialogBinding.tvStat1Label.text = "Max Combo"
        dialogBinding.tvStat1Value.text = "x$maxCombo"
        dialogBinding.tvStat2Label.text = "Correct Swipes"
        dialogBinding.tvStat2Value.text = "${score / 10}"
        
        // Load top 3 leaderboard
        GameOverHelper.loadLeaderboard(dialogBinding, GameType.RHYTHM_TAP)
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade)
            .setView(dialogBinding.root)
            .setCancelable(false)
            .create()
        
        dialogBinding.btnPlayAgain.setOnClickListener {
            dialog.dismiss()
            setupGame()
            binding.startOverlay.visibility = View.VISIBLE
        }
        
        dialogBinding.btnMenu.setOnClickListener {
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun saveScore(score: Long) {
        val player = prefsManager.currentPlayer ?: return
        
        val gameScore = GameScore(
            playerId = player.id,
            playerNickname = player.nickname,
            gameType = GameType.RHYTHM_TAP,
            score = score,
            extras = mapOf("maxCombo" to maxCombo)
        )
        
        lifecycleScope.launch {
            firebaseRepo.saveScore(gameScore)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopGame()
    }
}
