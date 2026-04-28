package com.sentinelng.data

import android.net.Uri

// ── Enums ──────────────────────────────────────────────────────────────────

enum class SupportedLanguage(val code: String, val displayName: String) {
    ENGLISH("en", "English"),
    HAUSA("ha", "Hausa"),
    YORUBA("yo", "Yoruba"),
    IGBO("ig", "Igbo"),
    PIDGIN("pcm", "Pidgin");

    companion object {
        fun fromCode(code: String): SupportedLanguage =
            values().firstOrNull { it.code == code } ?: ENGLISH
    }
}

enum class NluIntent {
    HEALTH, CROP, SECURITY, OTHER
}

enum class ModelType {
    CROP_DOCTOR, HEALTH_SCAN
}

// ── Inference Results ──────────────────────────────────────────────────────

data class InferenceResult(
    val classIndex: Int,
    val confidence: Float,
    val label: String,
    val advice: String
)

data class NluResult(
    val intent: NluIntent,
    val confidence: Float,
    val rawText: String
)

// ── Chat ───────────────────────────────────────────────────────────────────

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Security / Alerts ─────────────────────────────────────────────────────

data class LatLng(val latitude: Double, val longitude: Double)

data class Alert(
    val id: String,
    val title: String,
    val description: String,
    val severity: AlertSeverity,
    val location: String,
    val timestamp: Long,
    val category: AlertCategory
)

enum class AlertSeverity { LOW, MEDIUM, HIGH, CRITICAL }

enum class AlertCategory { FIRE, FLOOD, HEALTH_OUTBREAK, CROP_DISEASE, SECURITY_THREAT, OTHER }

// ── Repository Interfaces ──────────────────────────────────────────────────

interface SecurityRepository {
    suspend fun getAlerts(): List<Alert>
    suspend fun reportIncident(
        imageUri: Uri?,
        location: LatLng?,
        description: String
    ): Boolean
}
