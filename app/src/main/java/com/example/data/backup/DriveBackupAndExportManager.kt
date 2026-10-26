package com.example.data.backup

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.R
import com.example.data.repository.SnapSpendRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val TAG = "DriveBackupManager"
private const val PREFS_NAME = "snapspend_backup_prefs"
private const val KEY_LAST_BACKUP_ATTEMPT = "last_backup_attempt_ms"
private const val KEY_LAST_SUCCESS_BACKUP = "last_successful_backup_ms"
private const val KEY_DRIVE_FILE_ID = "drive_file_id"
private const val KEY_CONNECTED_EMAIL = "connected_google_email"
private const val KEY_PROMPTED_RESTORE_EMAIL = "prompted_restore_for_email"

private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
private const val BACKUP_FILENAME = "snapspend_backup_v1.json"
private const val WEEKLY_WORK_NAME = "snapspend_weekly_drive_backup"
private const val BUDGET_CHANNEL_ID = "snapspend_budget_alerts"

data class BackupStateSnapshot(
    val connectedEmail: String?,
    val lastAttemptMillis: Long,
    val lastSuccessMillis: Long,
    val driveFileId: String?
)

data class DriveBackupFileMetadata(
    val fileId: String,
    val modifiedTimeMillis: Long,
    val formattedDate: String
)

sealed interface DriveAuthResult {
    data class Authorized(val accessToken: String) : DriveAuthResult
    data class NeedsConsentResolution(val intentSender: IntentSender) : DriveAuthResult
    data class Error(val message: String) : DriveAuthResult
}

/**
 * Handles Google Sign-In via Jetpack Credential Manager, Google Drive hidden `appDataFolder`
 * single-file safe overwrite backup, reinstall auto-restore detection, and weekly WorkManager scheduling.
 */
class DriveBackupAndExportManager(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun getBackupState(): BackupStateSnapshot {
        val firebaseEmail = Firebase.auth.currentUser?.email
        val savedEmail = prefs.getString(KEY_CONNECTED_EMAIL, null)
        return BackupStateSnapshot(
            connectedEmail = firebaseEmail ?: savedEmail,
            lastAttemptMillis = prefs.getLong(KEY_LAST_BACKUP_ATTEMPT, 0L),
            lastSuccessMillis = prefs.getLong(KEY_LAST_SUCCESS_BACKUP, 0L),
            driveFileId = prefs.getString(KEY_DRIVE_FILE_ID, null)
        )
    }

    fun shouldPromptAutoRestoreForCurrentAccount(): Boolean {
        val email = getBackupState().connectedEmail ?: return false
        val alreadyPrompted = prefs.getString(KEY_PROMPTED_RESTORE_EMAIL, null)
        return alreadyPrompted != email
    }

    fun markRestorePromptShownForCurrentAccount() {
        val email = getBackupState().connectedEmail ?: return
        prefs.edit().putString(KEY_PROMPTED_RESTORE_EMAIL, email).apply()
    }

    /**
     * Interactive Google Sign-In via Credential Manager (`GetSignInWithGoogleOption` — 1 option per request).
     */
    suspend fun signInWithGoogleInteractive(activity: Activity): Result<String> {
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Google Sign-In client ID missing in configuration."))
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        return try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdCred.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val email = authResult.user?.email ?: googleIdCred.id
                prefs.edit().putString(KEY_CONNECTED_EMAIL, email).apply()
                scheduleWeeklyBackupWork(context)
                Result.success(email)
            } else {
                Result.failure(IllegalStateException("Unexpected Google credential type."))
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    /**
     * Silent auto sign-in via Credential Manager (`GetGoogleIdOption` — 1 option per request).
     */
    suspend fun attemptSilentSignIn(): String? {
        Firebase.auth.currentUser?.email?.let { email ->
            prefs.edit().putString(KEY_CONNECTED_EMAIL, email).apply()
            return email
        }
        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            return null
        }
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

        return try {
            val result = credentialManager.getCredential(context, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdCred = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdCred.idToken, null)
                val authResult = Firebase.auth.signInWithCredential(authCredential).await()
                val email = authResult.user?.email ?: googleIdCred.id
                prefs.edit().putString(KEY_CONNECTED_EMAIL, email).apply()
                email
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun disconnectGoogleAccount() {
        Firebase.auth.signOut()
        prefs.edit()
            .remove(KEY_CONNECTED_EMAIL)
            .remove(KEY_PROMPTED_RESTORE_EMAIL)
            .apply()
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear credential state", e)
        }
    }

    /**
     * Requests an OAuth2 access token with the narrow `drive.appdata` hidden folder scope.
     */
    suspend fun authorizeDriveAppData(): DriveAuthResult {
        return try {
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA_SCOPE)))
                .build()
            val authResult = Identity.getAuthorizationClient(context).authorize(authRequest).await()
            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    DriveAuthResult.NeedsConsentResolution(pendingIntent.intentSender)
                } else {
                    DriveAuthResult.Error("Google Drive permission requires user confirmation.")
                }
            } else {
                val token = authResult.accessToken
                if (!token.isNullOrBlank()) {
                    DriveAuthResult.Authorized(token)
                } else {
                    DriveAuthResult.Error("Did not receive a Drive access token. Please try again.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Drive authorization failed", e)
            DriveAuthResult.Error(
                e.localizedMessage ?: "Could not authorize Google Drive hidden folder."
            )
        }
    }

    /**
     * Performs the Safe Single-File Overwrite backup to Google Drive's hidden `appDataFolder`:
     * 1. Lists existing backup files in `appDataFolder`.
     * 2. Uploads the new backup file FIRST.
     * 3. Only if the new upload succeeds (HTTP 200 + valid new fileId) does it delete the old file(s).
     *    If the upload fails (e.g. no internet), the previous backup remains untouched!
     */
    suspend fun performSafeOverwriteDriveBackup(
        accessToken: String,
        repository: SnapSpendRepository
    ): Result<BackupStateSnapshot> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_BACKUP_ATTEMPT, now).apply()

        try {
            val backupJson = repository.exportFullBackupJson()

            // Step 1: Query existing files in appDataFolder before uploading
            val existingFileIds = listAppDataFolderFileIds(accessToken)

            // Step 2: Upload new backup file FIRST
            val metadataJson = JSONObject().apply {
                put("name", BACKUP_FILENAME)
                put("parents", JSONArray().put("appDataFolder"))
                put("mimeType", "application/json")
            }.toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .addPart(backupJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val uploadReq = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime")
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            val newFileId = httpClient.newCall(uploadReq).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Drive upload failed (HTTP ${response.code}). Your previous backup was kept untouched.")
                }
                val json = JSONObject(bodyStr)
                json.optString("id").takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Drive did not return a file ID. Previous backup kept safe.")
            }

            // Step 3: Safe overwrite — delete old backup files ONLY after new upload succeeded
            for (oldId in existingFileIds) {
                if (oldId != newFileId) {
                    runCatching { deleteDriveFile(accessToken, oldId) }
                }
            }

            val completedTime = System.currentTimeMillis()
            prefs.edit()
                .putLong(KEY_LAST_SUCCESS_BACKUP, completedTime)
                .putString(KEY_DRIVE_FILE_ID, newFileId)
                .apply()

            Result.success(getBackupState())
        } catch (e: Exception) {
            Log.e(TAG, "Safe overwrite Drive backup failed", e)
            Result.failure(e)
        }
    }

    /**
     * Looks inside Google Drive's hidden `appDataFolder` for an existing backup file.
     * Used both for manual "Restore from Drive" and automatic reinstall detection.
     */
    suspend fun findLatestDriveBackupMetadata(accessToken: String): Result<DriveBackupFileMetadata?> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&OrderBy=modifiedTime desc&pageSize=5&fields=files(id,name,modifiedTime)"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Could not check Drive backup folder (HTTP ${response.code}).")
                    }
                    val files = JSONObject(bodyStr).optJSONArray("files") ?: JSONArray()
                    if (files.length() == 0) {
                        return@withContext Result.success(null)
                    }
                    val latest = files.getJSONObject(0)
                    val id = latest.getString("id")
                    val modIso = latest.optString("modifiedTime", "")
                    val modMillis = parseRfc3339Millis(modIso)
                    val formatted = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(Date(modMillis))
                    Result.success(
                        DriveBackupFileMetadata(
                            fileId = id,
                            modifiedTimeMillis = modMillis,
                            formattedDate = formatted
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query Drive backup metadata", e)
                Result.failure(e)
            }
        }

    /**
     * Downloads the backup JSON content from Google Drive `appDataFolder`.
     */
    suspend fun downloadDriveBackupJson(accessToken: String, fileId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get()
                    .build()

                httpClient.newCall(req).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (!response.isSuccessful || bodyStr.isBlank()) {
                        throw IllegalStateException("Failed to download backup file from Drive (HTTP ${response.code}).")
                    }
                    Result.success(bodyStr)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download Drive backup", e)
                Result.failure(e)
            }
        }

    private fun listAppDataFolderFileIds(accessToken: String): List<String> {
        val url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)"
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        return httpClient.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val bodyStr = response.body?.string().orEmpty()
            val files = JSONObject(bodyStr).optJSONArray("files") ?: return emptyList()
            (0 until files.length()).mapNotNull { i ->
                files.getJSONObject(i).optString("id").takeIf { it.isNotBlank() }
            }
        }
    }

    private fun deleteDriveFile(accessToken: String, fileId: String) {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId")
            .addHeader("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        httpClient.newCall(req).execute().close()
    }

    private fun parseRfc3339Millis(iso: String): Long {
        if (iso.isBlank()) return System.currentTimeMillis()
        return try {
            val cleaned = iso.substringBefore(".").substringBefore("Z")
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            fmt.parse(cleaned)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    companion object {
        fun scheduleWeeklyBackupWork(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val weeklyRequest = PeriodicWorkRequestBuilder<WeeklyBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                WEEKLY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                weeklyRequest
            )
        }

        fun sendBudgetWarningNotification(
            context: Context,
            categoryName: String,
            usagePercent: Int,
            spentFormatted: String,
            limitFormatted: String
        ) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    BUDGET_CHANNEL_ID,
                    "Budget alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when category spending reaches 80% or 100% of your monthly limit"
                }
                nm.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val title = if (usagePercent >= 100) {
                "Budget limit reached: $categoryName ($usagePercent%)"
            } else {
                "Heads up: $categoryName hit $usagePercent% of budget"
            }
            val body = "You've spent $spentFormatted out of your $limitFormatted monthly limit."

            val openIntent = Intent(context, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("TARGET_TAB", "CATEGORIES")
                putExtra("TARGET_CATEGORY", categoryName)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                categoryName.hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, BUDGET_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(categoryName.hashCode(), notification)
        }
    }
}
