package com.example.data.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.SnapSpendDatabase
import com.example.data.repository.SnapSpendRepository

private const val TAG = "WeeklyBackupWorker"

/**
 * Background CoroutineWorker that runs approximately once a week when connected to the internet.
 * Uploads a fresh single-file backup to Google Drive's hidden `appDataFolder` using safe overwrite
 * (new file uploaded first, old file deleted only after upload succeeds).
 */
class WeeklyBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val backupManager = DriveBackupAndExportManager(applicationContext)
        val state = backupManager.getBackupState()
        if (state.connectedEmail.isNullOrBlank()) {
            Log.d(TAG, "No Google account connected yet; skipping weekly Drive backup.")
            return Result.success()
        }

        return when (val auth = backupManager.authorizeDriveAppData()) {
            is DriveAuthResult.Authorized -> {
                val repo = SnapSpendRepository(SnapSpendDatabase.getInstance(applicationContext))
                val backupRes = backupManager.performSafeOverwriteDriveBackup(auth.accessToken, repo)
                if (backupRes.isSuccess) {
                    Log.d(TAG, "Weekly Drive hidden folder backup succeeded.")
                    Result.success()
                } else {
                    Log.w(TAG, "Weekly Drive backup failed; retrying later.")
                    Result.retry()
                }
            }
            else -> {
                Log.w(TAG, "Drive authorization requires interactive consent; skipping background run.")
                Result.success()
            }
        }
    }
}
