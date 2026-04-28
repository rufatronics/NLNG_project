package com.sentinelng.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.sentinelng.SentinelNgApp
import com.sentinelng.data.Alert
import com.sentinelng.data.LatLng
import com.sentinelng.data.mock.MockSecurityRepository
import kotlinx.coroutines.launch
import java.io.IOException

class SecurityViewModel(application: Application) : AndroidViewModel(application) {

    // ── Real data source (nw0.vercel.app with HTTP cache) ──────────────────
    private val dataSource = SentinelNgApp.instance.securityDataSource

    // ── Fallback mock (used when API returns nothing AND no cache exists) ──
    private val mockRepo = MockSecurityRepository()

    private val _alerts     = MutableLiveData<List<Alert>>()
    val alerts: LiveData<List<Alert>> = _alerts

    private val _isLoading  = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _reportResult = MutableLiveData<Boolean?>()
    val reportResult: LiveData<Boolean?> = _reportResult

    private val _error      = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    // ── Load alerts ────────────────────────────────────────────────────────

    /**
     * Fetches alerts from https://nw0.vercel.app/alerts.
     *
     * - Online  → returns fresh data and stores it in the 25 MB OkHttp cache.
     * - Offline → OkHttp serves the cached response (up to 1 day old).
     * - No cache + offline → falls back to built-in mock data so the screen
     *   is never empty on first launch without internet.
     */
    fun loadAlerts() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value     = null
            try {
                val alerts = dataSource.getAlerts()
                if (alerts.isNotEmpty()) {
                    _alerts.value = alerts
                } else {
                    // Empty list from API – show mock so screen isn't blank
                    _alerts.value = mockRepo.getAlerts()
                }
            } catch (e: IOException) {
                // Network + no cache  →  show mock + explain
                _error.value = "No internet connection. Showing sample alerts."
                _alerts.value = mockRepo.getAlerts()
            } catch (e: Exception) {
                _error.value = "Failed to load alerts: ${e.message}"
                _alerts.value = mockRepo.getAlerts()
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ── Report incident (mock – endpoint not yet live) ─────────────────────

    fun reportIncident(imageUri: Uri?, location: LatLng?, description: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val success = mockRepo.reportIncident(imageUri, location, description)
                _reportResult.value = success
            } catch (e: Exception) {
                _reportResult.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearReportResult() { _reportResult.value = null }
}
