package com.rizwaan.miniarcade.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.WordDictionary
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.models.GameType
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityHomeBinding
import com.rizwaan.miniarcade.databinding.DialogPlayerStatsBinding
import com.rizwaan.miniarcade.ui.adapters.GameAdapter
import com.rizwaan.miniarcade.ui.games.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var gameAdapter: GameAdapter
    private val firebaseRepository = FirebaseRepository()
    
    // Easter egg: Swipe pattern detection (any 4 unique directions)
    private lateinit var gestureDetector: GestureDetector
    private val allDirections = setOf("UP", "DOWN", "LEFT", "RIGHT")
    private val currentPattern = mutableListOf<String>()
    private var lastSwipeTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Apply padding only to content, not the gradient background
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Apply top padding only to header content (not the gradient behind it)
            binding.headerLayout.setPadding(
                dpToPx(20), 
                systemBars.top + dpToPx(16), 
                dpToPx(20), 
                dpToPx(16)
            )
            
            // Apply bottom margin to logout button container area
            val params = binding.btnLogout.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = systemBars.bottom + dpToPx(16)
            binding.btnLogout.layoutParams = params
            
            insets
        }
        
        prefsManager = PreferencesManager(this)
        
        // Initialize word dictionary (in case user was already logged in)
        WordDictionary.initialize(this)
        
        setupHeader()
        setupGamesGrid()
        setupLeaderboardButton()
        setupLogoutButton()
        setupEasterEgg()
        animateEntrance()
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun animateEntrance() {
        // Animate avatar
        binding.tvAvatar.alpha = 0f
        binding.tvAvatar.scaleX = 0.5f
        binding.tvAvatar.scaleY = 0.5f
        binding.tvAvatar.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .start()
        
        // Animate greeting
        binding.tvGreeting.alpha = 0f
        binding.tvGreeting.translationX = -30f
        binding.tvGreeting.animate()
            .alpha(1f)
            .translationX(0f)
            .setStartDelay(150)
            .setDuration(350)
            .start()
        
        // Animate leaderboard button
        binding.btnLeaderboard.alpha = 0f
        binding.btnLeaderboard.scaleX = 0.5f
        binding.btnLeaderboard.scaleY = 0.5f
        binding.btnLeaderboard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(200)
            .setDuration(400)
            .setInterpolator(OvershootInterpolator())
            .start()
        
        // Animate logout button
        binding.btnLogout.alpha = 0f
        binding.btnLogout.translationY = 50f
        binding.btnLogout.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(300)
            .setDuration(400)
            .start()
    }
    
    private fun setupHeader() {
        val player = prefsManager.currentPlayer
        if (player != null) {
            binding.tvAvatar.text = player.avatarEmoji
            binding.tvGreeting.text = getString(R.string.hi_player, player.username.replaceFirstChar { it.uppercase() })
        }
        
        // Avatar click shows player stats
        binding.tvAvatar.setOnClickListener { view ->
            view.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(80)
                .withEndAction {
                    view.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                    showPlayerStatsDialog()
                }
                .start()
        }
    }
    
    private fun showPlayerStatsDialog() {
        val player = prefsManager.currentPlayer ?: return
        
        val dialogBinding = DialogPlayerStatsBinding.inflate(layoutInflater)
        
        dialogBinding.tvAvatar.text = player.avatarEmoji
        dialogBinding.tvPlayerName.text = player.username.replaceFirstChar { it.uppercase() }
        
        // Set default values
        dialogBinding.tvReactionScore.text = "-"
        dialogBinding.tvMemoryScore.text = "-"
        dialogBinding.tvPatternScore.text = "-"
        dialogBinding.tvColorScore.text = "-"
        dialogBinding.tvWordScore.text = "-"
        dialogBinding.tvRhythmScore.text = "-"
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_MiniArcade_Dialog)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setDimAmount(0.7f)
        }
        
        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }
        
        dialog.show()
        
        // Load scores from Firebase (directly from player record)
        if (firebaseRepository.isAvailable) {
            CoroutineScope(Dispatchers.Main).launch {
                android.util.Log.d("HomeActivity", "Loading scores for player: ${player.username} (id: ${player.id})")
                
                // Get fresh player data from Firebase
                val freshPlayer = firebaseRepository.getPlayerScores(player.id)
                
                if (freshPlayer != null) {
                    android.util.Log.d("HomeActivity", "Player scores loaded: reaction=${freshPlayer.reactionTime}, memory=${freshPlayer.memoryFlip}, pattern=${freshPlayer.patternSnap}")
                    
                    // Display scores (0 means no score yet, show "-")
                    dialogBinding.tvReactionScore.text = if (freshPlayer.reactionTime > 0) "${freshPlayer.reactionTime}ms" else "-"
                    dialogBinding.tvMemoryScore.text = if (freshPlayer.memoryFlip > 0) "${freshPlayer.memoryFlip}" else "-"
                    dialogBinding.tvPatternScore.text = if (freshPlayer.patternSnap > 0) "${freshPlayer.patternSnap}" else "-"
                    dialogBinding.tvColorScore.text = if (freshPlayer.colorCatch > 0) "${freshPlayer.colorCatch}" else "-"
                    dialogBinding.tvWordScore.text = if (freshPlayer.wordScramble > 0) "${freshPlayer.wordScramble}" else "-"
                    dialogBinding.tvRhythmScore.text = if (freshPlayer.rhythmTap > 0) "${freshPlayer.rhythmTap}" else "-"
                    
                    // Update local prefs with fresh data
                    prefsManager.currentPlayer = freshPlayer
                } else {
                    android.util.Log.e("HomeActivity", "Failed to load player scores")
                }
            }
        }
    }
    
    private fun setupLogoutButton() {
        binding.btnLogout.setOnClickListener {
            it.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(80)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .start()
                    showLogoutDialog()
                }
                .start()
        }
    }
    
    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this, R.style.Theme_MiniArcade_Dialog)
            .setTitle("Logout?")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun logout() {
        // Sign out from Firebase Auth
        firebaseRepository.signOut()
        // Clear local preferences
        prefsManager.logout()
        // Navigate to welcome screen
        val intent = Intent(this, WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
    
    private fun setupGamesGrid() {
        val games = GameType.entries.toList()
        
        gameAdapter = GameAdapter(games) { gameType ->
            navigateToGame(gameType)
        }
        
        binding.rvGames.apply {
            layoutManager = GridLayoutManager(this@HomeActivity, 2)
            adapter = gameAdapter
            isNestedScrollingEnabled = true
        }
    }
    
    private fun setupLeaderboardButton() {
        binding.btnLeaderboard.setOnClickListener {
            it.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(80)
                .withEndAction {
                    it.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .withEndAction {
                            startActivity(Intent(this, LeaderboardActivity::class.java))
                            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                        }
                        .start()
                }
                .start()
        }
    }
    
    private fun navigateToGame(gameType: GameType) {
        val intent = when (gameType) {
            GameType.REACTION_TIME -> Intent(this, ReactionGameActivity::class.java)
            GameType.MEMORY_FLIP -> Intent(this, MemoryGameActivity::class.java)
            GameType.PATTERN_SNAP -> Intent(this, PatternGameActivity::class.java)
            GameType.COLOR_CATCH -> Intent(this, ColorGameActivity::class.java)
            GameType.WORD_SCRAMBLE -> Intent(this, WordGameActivity::class.java)
            GameType.RHYTHM_TAP -> Intent(this, RhythmGameActivity::class.java)
        }
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
    
    private fun setupEasterEgg() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100
            
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
                    kotlin.math.abs(diffX) > kotlin.math.abs(diffY) -> {
                        if (kotlin.math.abs(diffX) > SWIPE_THRESHOLD && kotlin.math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffX > 0) "RIGHT" else "LEFT"
                        } else null
                    }
                    else -> {
                        if (kotlin.math.abs(diffY) > SWIPE_THRESHOLD && kotlin.math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                            if (diffY > 0) "DOWN" else "UP"
                        } else null
                    }
                }
                
                direction?.let { handleSecretSwipe(it) }
                return direction != null
            }
        })
    }
    
    private fun handleSecretSwipe(direction: String) {
        val currentTime = System.currentTimeMillis()
        
        // Reset pattern if too much time passed (3 seconds)
        if (currentTime - lastSwipeTime > 3000) {
            currentPattern.clear()
        }
        lastSwipeTime = currentTime
        
        currentPattern.add(direction)
        
        // Check if pattern matches (any 4 unique directions)
        if (currentPattern.size == 4) {
            if (currentPattern.toSet() == allDirections) {
                showEasterEggDialog()
            }
            currentPattern.clear()
        } else if (currentPattern.size > 4) {
            currentPattern.clear()
        }
    }
    
    private fun showEasterEggDialog() {
        MaterialAlertDialogBuilder(this, R.style.Theme_MiniArcade_Dialog)
            .setTitle("🕵️ Secret Found!")
            .setMessage(
                "Aha! You found the secret!\n\n" +
                "No Cheating Ayaan, Aleena, Aaliya, Sana and Farah! \n\n" +
                "Play fair, have fun, and may the best cousin win! 🏆\n\n" +
                "Highest Scorer wins a secret reward\n\n" +
                "Made by Rizwaan Bhaiya"
            )
            .setPositiveButton("I Promise! 🤞") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }
}
