package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.snapSpendDataStore: DataStore<Preferences> by preferencesDataStore(name = "snapspend_settings")

/**
 * Isolated settings store using Jetpack Preferences DataStore.
 * Completely separate from the SQLite/Room transaction database, JSON backups, CSV exports,
 * and Google Drive backups.
 */
class SnapSpendPreferences(private val context: Context) {

    companion object {
        val KEY_HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
        val KEY_HAS_SEEN_AI_SCAN_NOTICE = booleanPreferencesKey("has_seen_ai_scan_notice")
        val KEY_HAS_PROMPTED_BUDGET_NOTIFICATIONS = booleanPreferencesKey("has_prompted_budget_notifications")
    }

    val hasSeenOnboardingFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_ONBOARDING] ?: false
    }

    suspend fun setHasSeenOnboarding(hasSeen: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_ONBOARDING] = hasSeen
        }
    }

    val hasSeenAiScanNoticeFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_AI_SCAN_NOTICE] ?: false
    }

    suspend fun setHasSeenAiScanNotice(hasSeen: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_HAS_SEEN_AI_SCAN_NOTICE] = hasSeen
        }
    }

    val hasPromptedBudgetNotificationsFlow: Flow<Boolean> = context.snapSpendDataStore.data.map { prefs ->
        prefs[KEY_HAS_PROMPTED_BUDGET_NOTIFICATIONS] ?: false
    }

    suspend fun setHasPromptedBudgetNotifications(hasPrompted: Boolean) {
        context.snapSpendDataStore.edit { prefs ->
            prefs[KEY_HAS_PROMPTED_BUDGET_NOTIFICATIONS] = hasPrompted
        }
    }
}
