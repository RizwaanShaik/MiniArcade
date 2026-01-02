package com.rizwaan.miniarcade.ui

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.rizwaan.miniarcade.R
import com.rizwaan.miniarcade.data.WordDictionary
import com.rizwaan.miniarcade.data.local.PreferencesManager
import com.rizwaan.miniarcade.data.repository.FirebaseRepository
import com.rizwaan.miniarcade.databinding.ActivityWelcomeBinding
import kotlinx.coroutines.launch

class WelcomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBinding
    private lateinit var prefsManager: PreferencesManager
    private lateinit var firebaseRepo: FirebaseRepository

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
        if (prefsManager.isLoggedIn() && firebaseRepo.isLoggedIn()) {
            navigateToHome()
            return
        }
        
        setupInputValidation()
        setupClickListeners()
        animateEntrance()
    }
    
    private fun animateEntrance() {
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
        
        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = 30f
        binding.tvTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(200)
            .setDuration(400)
            .start()
        
        binding.tvSubtitle.alpha = 0f
        binding.tvSubtitle.animate()
            .alpha(1f)
            .setStartDelay(400)
            .setDuration(400)
            .start()
    }
    
    private fun setupInputValidation() {
        val validateInputs = {
            val emailOrUsername = binding.etEmail.text?.toString()?.trim() ?: ""
            val password = binding.etPassword.text?.toString() ?: ""
            
            // Allow either email format OR username (3+ chars, alphanumeric/underscore)
            val isValidEmailOrUsername = Patterns.EMAIL_ADDRESS.matcher(emailOrUsername).matches() ||
                    (emailOrUsername.length >= 3 && emailOrUsername.matches(Regex("^[a-zA-Z0-9_]+$")))
            val isValid = isValidEmailOrUsername && password.length >= 6
            
            binding.btnLogin.isEnabled = isValid
            binding.btnLogin.alpha = if (isValid) 1f else 0.5f
        }
        
        binding.etEmail.addTextChangedListener { 
            binding.tvError.visibility = View.GONE
            validateInputs()
        }
        
        binding.etPassword.addTextChangedListener {
            binding.tvError.visibility = View.GONE
            validateInputs()
        }
        
        binding.btnLogin.alpha = 0.5f
        binding.btnLogin.isEnabled = false
    }
    
    private fun setupClickListeners() {
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        
        binding.tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        
        binding.tvForgotPassword.setOnClickListener {
            showForgotPasswordDialog()
        }
    }
    
    private fun performLogin() {
        val emailOrUsername = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        
        val isEmail = Patterns.EMAIL_ADDRESS.matcher(emailOrUsername).matches()
        val isUsername = emailOrUsername.length >= 3 && emailOrUsername.matches(Regex("^[a-zA-Z0-9_]+$"))
        
        if (!isEmail && !isUsername) {
            showError("Please enter a valid email or username")
            return
        }
        
        if (password.length < 6) {
            showError("Password must be at least 6 characters")
            return
        }
        
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val player = if (isEmail) {
                    // Direct email login
                    firebaseRepo.loginWithEmail(emailOrUsername, password)
                } else {
                    // Username login - first get email from username, then login
                    firebaseRepo.loginWithUsername(emailOrUsername.lowercase(), password)
                }
                
                if (player != null) {
                    prefsManager.currentPlayer = player
                    navigateToHome()
                } else {
                    showLoading(false)
                    showError("Invalid credentials. Check your email/username and password.")
                }
            } catch (e: Exception) {
                showLoading(false)
                val errorMessage = when {
                    e.message?.contains("no user record") == true -> 
                        "No account found with this email/username"
                    e.message?.contains("password is invalid") == true -> 
                        "Incorrect password"
                    e.message?.contains("network") == true -> 
                        "Network error. Check your internet connection."
                    else -> "Login failed. Please try again."
                }
                showError(errorMessage)
            }
        }
    }
    
    private fun showForgotPasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val tilEmail = dialogView.findViewById<TextInputLayout>(R.id.tilEmail)
        val etEmail = dialogView.findViewById<TextInputEditText>(R.id.etEmail)
        
        // Pre-fill with email from login form
        val currentEmail = binding.etEmail.text.toString().trim()
        if (currentEmail.isNotEmpty()) {
            etEmail.setText(currentEmail)
        }
        
        AlertDialog.Builder(this, R.style.Theme_MiniArcade_Dialog)
            .setTitle("Reset Password")
            .setMessage("Enter your email address and we'll send you a link to reset your password.")
            .setView(dialogView)
            .setPositiveButton("Send Reset Link") { dialog, _ ->
                val email = etEmail.text.toString().trim()
                if (Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    sendPasswordResetEmail(email)
                } else {
                    Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun sendPasswordResetEmail(email: String) {
        showLoading(true)
        
        lifecycleScope.launch {
            val success = firebaseRepo.sendPasswordResetEmail(email)
            showLoading(false)
            
            if (success) {
                Toast.makeText(
                    this@WelcomeActivity, 
                    "Password reset email sent! Check your inbox.", 
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@WelcomeActivity, 
                    "Failed to send reset email. Check if the email is correct.", 
                    Toast.LENGTH_SHORT
                ).show()
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
        binding.btnLogin.isEnabled = !show
    }
    
    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }
}
