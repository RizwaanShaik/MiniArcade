package com.rizwaan.miniarcade.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityRegisterBinding
import com.rizwaan.miniarcade.ui.adapters.AvatarAdapter
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository
    private lateinit var avatarAdapter: AvatarAdapter
    
    private var selectedAvatar = "🎮"
    
    private val avatars = listOf(
        "🎮", "🕹️", "👾", "🎯", "🏆", "⭐", "🌟", "💫",
        "🦊", "🐱", "🐶", "🐼", "🦁", "🐯", "🐸", "🦋",
        "🚀", "🎨", "🎪", "🎭", "🎬", "🎤", "🎸", "🎹"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        
        prefsManager = PreferencesManager(this)
        firebaseRepo = FirebaseRepository()
        
        setupAvatarSelector()
        setupInputValidation()
        setupClickListeners()
    }
    
    private fun setupAvatarSelector() {
        avatarAdapter = AvatarAdapter(avatars) { avatar ->
            selectedAvatar = avatar
            avatarAdapter.setSelected(avatar)
        }
        
        binding.rvAvatars.apply {
            layoutManager = LinearLayoutManager(this@RegisterActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = avatarAdapter
        }
        
        avatarAdapter.setSelected(selectedAvatar)
    }
    
    private fun setupInputValidation() {
        val validateInputs = {
            val username = binding.etUsername.text?.toString()?.trim() ?: ""
            val email = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            val confirmPassword = binding.etConfirmPassword.text?.toString() ?: ""
            
            val isValid = username.length >= 3 &&
                    Patterns.EMAIL_ADDRESS.matcher(email).matches() &&
                    password.length >= 6 &&
                    password == confirmPassword
            
            binding.btnCreateAccount.isEnabled = isValid
            binding.btnCreateAccount.alpha = if (isValid) 1f else 0.5f
        }
        
        binding.etUsername.addTextChangedListener { 
            binding.tvError.visibility = View.GONE
            validateInputs()
        }
        binding.etEmail.addTextChangedListener { 
            binding.tvError.visibility = View.GONE
            validateInputs()
        }
        binding.etPassword.addTextChangedListener { 
            binding.tvError.visibility = View.GONE
            validateInputs()
        }
        binding.etConfirmPassword.addTextChangedListener { 
            binding.tvError.visibility = View.GONE
            validateInputs()
        }
        
        binding.btnCreateAccount.alpha = 0.5f
        binding.btnCreateAccount.isEnabled = false
    }
    
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        binding.tvAlreadyHaveAccount.setOnClickListener {
            finish() // Go back to login
        }
        
        binding.btnCreateAccount.setOnClickListener {
            validateAndRegister()
        }
    }
    
    private fun validateAndRegister() {
        val username = binding.etUsername.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()
        
        // Validate username
        if (username.length < 3) {
            showError("Username must be at least 3 characters")
            return
        }
        
        if (!username.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            showError("Username can only contain letters, numbers, and underscore")
            return
        }
        
        // Validate email
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showError("Please enter a valid email address")
            return
        }
        
        // Validate password
        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return
        }
        
        // Validate confirm password
        if (password != confirmPassword) {
            showError("Passwords do not match")
            return
        }
        
        // All validations passed, create account
        createAccount(username, email, password)
    }
    
    private fun createAccount(username: String, email: String, password: String) {
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                // First check if username is available
                val isUsernameAvailable = firebaseRepo.isUsernameAvailable(username)
                if (!isUsernameAvailable) {
                    showLoading(false)
                    showError("This username is already taken. Try another one!")
                    return@launch
                }
                
                // Create user with Firebase Auth
                val player = firebaseRepo.registerWithEmail(email, password, username, selectedAvatar)
                
                if (player != null) {
                    prefsManager.currentPlayer = player
                    navigateToHome()
                } else {
                    showLoading(false)
                    showError("Failed to create account. Please try again.")
                }
                
            } catch (e: Exception) {
                showLoading(false)
                val errorMessage = when {
                    e.message?.contains("email address is already in use") == true -> 
                        "This email is already registered. Try logging in!"
                    e.message?.contains("network") == true -> 
                        "Network error. Check your internet connection."
                    else -> e.message ?: "Registration failed. Please try again."
                }
                showError(errorMessage)
            }
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
        binding.btnCreateAccount.isEnabled = !show
    }
    
    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

