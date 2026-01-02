package com.rizwaan.miniarcade

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class MiniArcadeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable offline persistence for Realtime Database so games work without internet
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
