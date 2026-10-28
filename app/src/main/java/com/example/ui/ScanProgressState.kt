package com.example.ui

import android.net.Uri
import com.example.data.local.TransactionType
import com.example.data.rules.DraftTransaction
import java.util.UUID

enum class ScanStep {
    READING_FILE,
    PARSING_DETAILS,
    MATCHING_CATEGORY,
    COMPLETE,
    ERROR
}

data class ScanProgressState(
    val id: String = UUID.randomUUID().toString(),
    val fileNameOrType: String,
    val currentStep: ScanStep,
    val stepDescription: String,
    val resultDrafts: List<DraftTransaction>? = null,
    val errorMessage: String? = null,
    val queuedCount: Int = 0,
    val recoverableUris: List<Uri> = emptyList(),
    val recoverableSamplePreset: Int? = null,
    val recoverableNote: String? = null,
    val isSmartCashLog: Boolean = false,
    val defaultCashType: TransactionType? = null
)
