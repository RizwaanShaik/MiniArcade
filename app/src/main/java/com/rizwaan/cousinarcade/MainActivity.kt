package com.rizwaan.cousinarcade

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.rizwaan.cousinarcade.data.local.PreferencesManager
import com.rizwaan.cousinarcade.ui.HomeActivity
import com.rizwaan.cousinarcade.ui.WelcomeActivity

class MainActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefsManager = PreferencesManager(this)
        
        // Check if user is already logged in
        val intent = if (prefsManager.isLoggedIn()) {
            Intent(this, HomeActivity::class.java)
        } else {
            Intent(this, WelcomeActivity::class.java)
        }
        
        startActivity(intent)
        finish()
    }
}
