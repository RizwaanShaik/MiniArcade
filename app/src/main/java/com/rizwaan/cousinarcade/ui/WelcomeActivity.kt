package com.rizwaan.cousinarcade.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rizwaan.cousinarcade.R
import com.rizwaan.cousinarcade.data.WordDictionary
import com.rizwaan.cousinarcade.data.local.PreferencesManager
import com.rizwaan.cousinarcade.data.models.Player
import com.rizwaan.cousinarcade.data.repository.FirebaseRepository
import com.rizwaan.cousinarcade.databinding.ActivityWelcomeBinding
import com.rizwaan.cousinarcade.ui.adapters.AvatarAdapter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var avatarAdapter: AvatarAdapter
    
    private var selectedAvatar = "🎮"
    private var isFirebaseAvailable = false
    
    private val avatars = listOf(
        "🎮", "🕹️", "👾", "🎯", "🏆", "⭐", "🌟", "💫",
        "🦊", "🐱", "🐶", "🐼", "🦁", "🐯", "🐸", "🦋",
        "🚀", "🎨", "🎪", "🎭", "🎬", "🎤", "🎸", "🎹"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        prefsManager = PreferencesManager(this)
        firebaseRepo = FirebaseRepository()
        
        // Initialize word dictionary for Word Scramble game
        WordDictionary.initialize(this)
        
        // Check if already logged in
        if (prefsManager.isLoggedIn()) {
            navigateToHome()
            return
        }
        
        setupAvatarSelector()
        setupNicknameInput()
        setupLetsPlayButton()
        animateEntrance()
        
        // Try to authenticate with Firebase (with timeout)
        checkFirebaseAvailability()
    }
    
    private fun animateEntrance() {
        // Animate logo
        binding.tvLogo.alpha = 0f
        binding.tvLogo.scaleX = 0.5f
        binding.tvLogo.scaleY = 0.5f
        binding.tvLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(600)
            .setInterpolator(OvershootInterpolator())
            .start()
        
        // Animate title
        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = 30f
        binding.tvTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(200)
            .setDuration(400)
            .start()
        
        // Animate subtitle
        binding.tvSubtitle.alpha = 0f
        binding.tvSubtitle.animate()
            .alpha(1f)
            .setStartDelay(400)
            .setDuration(400)
            .start()
    }
    
    private fun checkFirebaseAvailability() {
        lifecycleScope.launch {
            try {
                val result = withTimeoutOrNull(3000L) {
                    firebaseRepo.ensureAuthenticated()
                }
                isFirebaseAvailable = result == true
                Log.d("WelcomeActivity", "Firebase available: $isFirebaseAvailable")
            } catch (e: Exception) {
                Log.e("WelcomeActivity", "Firebase check failed", e)
                isFirebaseAvailable = false
            }
        }
    }
    
    private fun setupAvatarSelector() {
        avatarAdapter = AvatarAdapter(avatars) { avatar ->
            selectedAvatar = avatar
            avatarAdapter.setSelected(avatar)
        }
        
        binding.rvAvatars.apply {
            layoutManager = LinearLayoutManager(this@WelcomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = avatarAdapter
        }
        
        avatarAdapter.setSelected(selectedAvatar)
    }
    
    private fun setupNicknameInput() {
        binding.etNickname.addTextChangedListener { text ->
            binding.tvError.visibility = View.GONE
            binding.btnLetsPlay.isEnabled = text?.length ?: 0 >= 3
            binding.btnLetsPlay.alpha = if (text?.length ?: 0 >= 3) 1f else 0.5f
        }
        
        binding.btnLetsPlay.alpha = 0.5f
    }
    
    private fun setupLetsPlayButton() {
        binding.btnLetsPlay.isEnabled = false
        
        binding.btnLetsPlay.setOnClickListener {
            // Button press animation
            binding.btnLetsPlay.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(80)
                .withEndAction {
                    binding.btnLetsPlay.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(80)
                        .withEndAction {
                            validateAndProceed()
                        }
                        .start()
                }
                .start()
        }
    }
    
    private fun validateAndProceed() {
        val nickname = binding.etNickname.text.toString().trim()
        
        when {
            nickname.length < 3 -> {
                showError(getString(R.string.nickname_too_short))
            }
            !nickname.matches(Regex("^[a-zA-Z0-9]+$")) -> {
                showError(getString(R.string.nickname_invalid))
            }
            else -> {
                createPlayerAndProceed(nickname)
            }
        }
    }
    
    private fun createPlayerAndProceed(nickname: String) {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                if (isFirebaseAvailable) {
                    // Try Firebase first
                    val result = withTimeoutOrNull(5000L) {
                        tryFirebaseLogin(nickname)
                    }
                    
                    if (result == true) {
                        return@launch // Successfully logged in via Firebase
                    }
                }
                
                // Fallback to offline mode
                createOfflinePlayer(nickname)
                
            } catch (e: Exception) {
                Log.e("WelcomeActivity", "Login error", e)
                // Fallback to offline
                createOfflinePlayer(nickname)
            }
        }
    }
    
    private suspend fun tryFirebaseLogin(nickname: String): Boolean {
        // Check if nickname is available
        val isAvailable = firebaseRepo.isNicknameAvailable(nickname)
        
        if (isAvailable) {
            // Create new player
            val player = firebaseRepo.createPlayer(nickname, selectedAvatar)
            if (player != null) {
                prefsManager.currentPlayer = player
                navigateToHome()
                return true
            }
        } else {
            // Nickname taken - check if it's returning player
            val existingPlayer = firebaseRepo.getPlayerByNickname(nickname)
            if (existingPlayer != null) {
                // Welcome back! Update avatar if changed
                val updatedPlayer = if (existingPlayer.avatarEmoji != selectedAvatar) {
                    firebaseRepo.updatePlayerAvatar(existingPlayer.id, selectedAvatar)
                    existingPlayer.copy(avatarEmoji = selectedAvatar)
                } else {
                    existingPlayer
                }
                prefsManager.currentPlayer = updatedPlayer
                navigateToHome()
                return true
            } else {
                showLoading(false)
                showError(getString(R.string.nickname_taken))
                return true // Handled, don't fallback
            }
        }
        
        return false
    }
    
    private fun createOfflinePlayer(nickname: String) {
        // Create player locally
        val player = Player(
            id = UUID.randomUUID().toString(),
            nickname = nickname.lowercase(),
            avatarEmoji = selectedAvatar,
            createdAt = System.currentTimeMillis()
        )
        
        prefsManager.currentPlayer = player
        
        runOnUiThread {
            // Don't show offline message - just proceed silently
            navigateToHome()
        }
    }
    
    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
        
        // Shake animation
        binding.tvError.translationX = -10f
        binding.tvError.animate()
            .translationX(10f)
            .setDuration(50)
            .withEndAction {
                binding.tvError.animate()
                    .translationX(-5f)
                    .setDuration(50)
                    .withEndAction {
                        binding.tvError.animate()
                            .translationX(0f)
                            .setDuration(50)
                            .start()
                    }
                    .start()
            }
            .start()
    }
    
    private fun showLoading(show: Boolean) {
        binding.loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        binding.btnLetsPlay.isEnabled = !show
        binding.etNickname.isEnabled = !show
    }
    
    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
