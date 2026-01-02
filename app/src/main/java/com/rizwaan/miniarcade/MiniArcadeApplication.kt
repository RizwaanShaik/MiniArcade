package com.rizwaan.miniarcade

import android.app.Application
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class MiniArcadeApplication : Application() {
    
    companion object {
        private const val DATABASE_URL = "https://miniarcade-rushmalai-default-rtdb.asia-southeast1.firebasedatabase.app/"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase Database with persistence ONCE at app startup
        try {
            val database = FirebaseDatabase.getInstance(DATABASE_URL)
            database.setPersistenceEnabled(true)
            Log.d("MiniArcadeApp", "Firebase persistence enabled")
        } catch (e: Exception) {
            Log.e("MiniArcadeApp", "Firebase init error: ${e.message}")
        }
    }
}
