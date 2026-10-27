package com.example.util

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp

private const val TAG = "AppCheckInit"

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
        // App Check enforcement disabled for local debug APK builds as requested.
        Log.d(TAG, "Firebase App Check enforcement disabled.")
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize FirebaseApp", e)
    }
}
