package com.sentinelng.data

import retrofit2.http.GET

/**
 * Retrofit API interface for the Sentinel-NG security alerts backend.
 *
 * Base URL: https://nw0.vercel.app/
 * OkHttp handles caching (25 MB, max-stale 1 day) so this endpoint
 * works offline after the first successful fetch.
 */
interface SecurityApiService {

    /**
     * Fetch the list of current security / environmental alerts.
     * Returns [List<AlertDto>] which is mapped to internal [Alert] models
     * in [RealSecurityDataSource].
     */
    @GET("alerts")
    suspend fun getAlerts(): List<AlertDto>
}

/**
 * Raw JSON shape returned by the API.
 * Field names use snake_case to match typical REST conventions;
 * Gson maps them automatically.
 *
 * All fields are nullable so the app won't crash on unexpected API changes.
 * [RealSecurityDataSource.toAlert] fills in sensible defaults.
 */
data class AlertDto(
    val id: String?,
    val title: String?,
    val description: String?,
    val severity: String?,       // "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
    val category: String?,       // "FIRE" | "FLOOD" | "HEALTH_OUTBREAK" | "CROP_DISEASE" | "SECURITY_THREAT" | "OTHER"
    val location: String?,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: Long?         // Unix epoch millis
)
