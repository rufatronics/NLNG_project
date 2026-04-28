package com.sentinelng.data.mock

import android.net.Uri
import com.sentinelng.data.*
import kotlinx.coroutines.delay

/**
 * Mock implementation of SecurityRepository.
 * Replace with real Retrofit/API implementation when backend is ready.
 *
 * FUTURE RETROFIT IMPLEMENTATION (uncomment when backend is available):
 *
 * interface SecurityApiService {
 *     @GET("alerts")
 *     suspend fun getAlerts(): Response<List<AlertDto>>
 *
 *     @Multipart
 *     @POST("incidents")
 *     suspend fun reportIncident(
 *         @Part image: MultipartBody.Part?,
 *         @Part("latitude") latitude: RequestBody,
 *         @Part("longitude") longitude: RequestBody,
 *         @Part("description") description: RequestBody
 *     ): Response<IncidentResponseDto>
 * }
 *
 * val retrofit = Retrofit.Builder()
 *     .baseUrl("https://api.sentinelng.ng/v1/")
 *     .addConverterFactory(GsonConverterFactory.create())
 *     .client(OkHttpClient.Builder()
 *         .addInterceptor(HttpLoggingInterceptor().apply {
 *             level = HttpLoggingInterceptor.Level.BODY
 *         })
 *         .build())
 *     .build()
 */
class MockSecurityRepository : SecurityRepository {

    private val mockAlerts = listOf(
        Alert(
            id = "ALT001",
            title = "Fire Hotspot Detected",
            description = "Satellite imagery shows active fire hotspot in Omo Forest Reserve, Ogun State. " +
                    "Estimated 200 hectares affected. Avoid the area and report any sightings.",
            severity = AlertSeverity.CRITICAL,
            location = "Omo Forest Reserve, Ogun State",
            timestamp = System.currentTimeMillis() - 3_600_000L,
            category = AlertCategory.FIRE
        ),
        Alert(
            id = "ALT002",
            title = "Flood Warning: River Niger",
            description = "Niger Hydrological Services Authority warns of above-normal flooding along " +
                    "River Niger. Communities in Kogi, Anambra, and Bayelsa should take precautions.",
            severity = AlertSeverity.HIGH,
            location = "River Niger Basin – Kogi, Anambra, Bayelsa",
            timestamp = System.currentTimeMillis() - 7_200_000L,
            category = AlertCategory.FLOOD
        ),
        Alert(
            id = "ALT003",
            title = "Cassava Mosaic Disease Outbreak",
            description = "Reports of widespread Cassava Mosaic Disease in Benue and Taraba States. " +
                    "Farmers are advised to remove infected plants and use certified disease-free seedlings.",
            severity = AlertSeverity.HIGH,
            location = "Benue & Taraba States",
            timestamp = System.currentTimeMillis() - 14_400_000L,
            category = AlertCategory.CROP_DISEASE
        ),
        Alert(
            id = "ALT004",
            title = "Cholera Outbreak – Maiduguri",
            description = "NCDC confirms cholera outbreak in Maiduguri, Borno State. " +
                    "43 cases reported. Residents advised to boil water and maintain hygiene.",
            severity = AlertSeverity.HIGH,
            location = "Maiduguri, Borno State",
            timestamp = System.currentTimeMillis() - 21_600_000L,
            category = AlertCategory.HEALTH_OUTBREAK
        ),
        Alert(
            id = "ALT005",
            title = "Herdsmen-Farmer Conflict Alert",
            description = "Security forces report increased tension between herders and farmers in " +
                    "southern Kaduna. Residents should avoid isolated areas and report suspicious activity.",
            severity = AlertSeverity.MEDIUM,
            location = "Southern Kaduna State",
            timestamp = System.currentTimeMillis() - 43_200_000L,
            category = AlertCategory.SECURITY_THREAT
        ),
        Alert(
            id = "ALT006",
            title = "Locust Swarm Advisory",
            description = "FAO Nigeria office warns of potential desert locust swarm approaching " +
                    "Sokoto and Kebbi States from Niger Republic. Farmers should monitor crops.",
            severity = AlertSeverity.MEDIUM,
            location = "Sokoto & Kebbi States",
            timestamp = System.currentTimeMillis() - 86_400_000L,
            category = AlertCategory.CROP_DISEASE
        ),
        Alert(
            id = "ALT007",
            title = "Meningitis Season Alert",
            description = "NCDC issues meningitis alert for the meningitis belt states (Kebbi, Sokoto, " +
                    "Zamfara, Katsina, Kano, Jigawa, Yobe, Borno). Vaccination recommended.",
            severity = AlertSeverity.MEDIUM,
            location = "Northern Nigeria – Meningitis Belt",
            timestamp = System.currentTimeMillis() - 172_800_000L,
            category = AlertCategory.HEALTH_OUTBREAK
        )
    )

    override suspend fun getAlerts(): List<Alert> {
        // Simulate network latency
        delay(800)
        return mockAlerts.sortedByDescending { it.timestamp }
    }

    override suspend fun reportIncident(
        imageUri: Uri?,
        location: LatLng?,
        description: String
    ): Boolean {
        // Simulate upload
        delay(1500)
        // Mock success (90% success rate)
        return Math.random() > 0.1
    }
}
