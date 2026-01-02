package com.rizwaan.cousinarcade.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.activity.enableEdgeToEdge
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.rizwaan.cousinarcade.R
import com.rizwaan.cousinarcade.data.WordDictionary
import com.rizwaan.cousinarcade.data.local.PreferencesManager
import com.rizwaan.cousinarcade.data.models.GameType
import com.rizwaan.cousinarcade.data.repository.FirebaseRepository
import com.rizwaan.cousinarcade.databinding.ActivityHomeBinding
import com.rizwaan.cousinarcade.databinding.DialogPlayerStatsBinding
import com.rizwaan.cousinarcade.ui.adapters.GameAdapter
import com.rizwaan.cousinarcade.ui.games.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var gameAdapter: GameAdapter
    private val firebaseRepository = FirebaseRepository()

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
            binding.tvGreeting.text = getString(R.string.hi_player, player.nickname.replaceFirstChar { it.uppercase() })
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
        dialogBinding.tvPlayerName.text = player.nickname.replaceFirstChar { it.uppercase() }
        
        // Set default values
        dialogBinding.tvReactionScore.text = "-"
        dialogBinding.tvMemoryScore.text = "-"
        dialogBinding.tvPatternScore.text = "-"
        dialogBinding.tvColorScore.text = "-"
        dialogBinding.tvWordScore.text = "-"
        dialogBinding.tvRhythmScore.text = "-"
        
        val dialog = AlertDialog.Builder(this, R.style.Theme_CousinArcade_Dialog)
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
        
        // Load scores from Firebase
        if (firebaseRepository.isAvailable) {
            CoroutineScope(Dispatchers.Main).launch {
                GameType.entries.forEach { gameType ->
                    val scores = firebaseRepository.getLeaderboard(gameType, 100).firstOrNull() ?: emptyList()
                    val playerScore = scores.find { it.playerNickname.equals(player.nickname, ignoreCase = true) }
                    
                    if (playerScore != null) {
                        when (gameType) {
                            GameType.REACTION_TIME -> dialogBinding.tvReactionScore.text = "${playerScore.score}ms"
                            GameType.MEMORY_FLIP -> dialogBinding.tvMemoryScore.text = "${playerScore.score} moves"
                            GameType.PATTERN_SNAP -> dialogBinding.tvPatternScore.text = "${playerScore.score}"
                            GameType.COLOR_CATCH -> dialogBinding.tvColorScore.text = "${playerScore.score}"
                            GameType.WORD_SCRAMBLE -> dialogBinding.tvWordScore.text = "${playerScore.score}"
                            GameType.RHYTHM_TAP -> dialogBinding.tvRhythmScore.text = "${playerScore.score}"
                        }
                    }
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
        MaterialAlertDialogBuilder(this, R.style.Theme_CousinArcade_Dialog)
            .setTitle("Switch Player? 👋")
            .setMessage("Are you sure you want to logout and switch to another player?")
            .setPositiveButton("Logout") { _, _ ->
                logout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun logout() {
        prefsManager.logout()
        startActivity(Intent(this, WelcomeActivity::class.java))
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
}
