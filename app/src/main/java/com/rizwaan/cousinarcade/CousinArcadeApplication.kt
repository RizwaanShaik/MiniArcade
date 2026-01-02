package com.rizwaan.cousinarcade

import android.app.Application
import com.google.firebase.database.FirebaseDatabase

class CousinArcadeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Enable offline persistence for Realtime Database so games work without internet
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
