package com.sentinelng.data

import android.content.Context
import android.util.Log
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

// TODO: replace with actual URL when backend changes
private const val BASE_URL = "https://nw0.vercel.app/"
private const val CACHE_DIR_NAME = "sentinel_http_cache"
private const val CACHE_SIZE_BYTES = 25L * 1024 * 1024  // 25 MB
private const val MAX_STALE_DAYS = 1L                   // serve stale for up to 1 day offline
private const val CONNECT_TIMEOUT_SEC = 15L
private const val READ_TIMEOUT_SEC = 30L

/**
 * Real network data source for security alerts.
 *
 * Uses Retrofit + OkHttp with a disk cache so alerts remain viewable
 * when the phone has no internet connection (served from cache for up
 * to [MAX_STALE_DAYS] day after the last successful fetch).
 *
 * Offline behaviour:
 *  - Online  → fetch fresh data, store in cache, return to caller.
 *  - Offline → OkHttp's FORCE_CACHE interceptor returns the most recent
 *              cached response if it is within the max-stale window.
 *              If no cache exists yet, throws an IOException which the
 *              repository converts into a user-visible error.
 */
class RealSecurityDataSource(context: Context) {

    private val appContext = context.applicationContext
    private val api: SecurityApiService by lazy { buildRetrofit().create(SecurityApiService::class.java) }

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Fetch alerts from [BASE_URL].  Throws on network failure when no
     * cached data is available.
     */
    suspend fun getAlerts(): List<Alert> {
        val dtos = api.getAlerts()
        Log.d(TAG, "Fetched ${dtos.size} alerts from API")
        return dtos.mapNotNull { it.toAlert() }
    }

    // ── Retrofit / OkHttp setup ────────────────────────────────────────────

    private fun buildRetrofit(): Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(buildOkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private fun buildOkHttpClient(): OkHttpClient {
        val cacheDir = File(appContext.cacheDir, CACHE_DIR_NAME)
        val cache = Cache(cacheDir, CACHE_SIZE_BYTES)

        val logging = HttpLoggingInterceptor { Log.d(TAG, it) }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        return OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            // Online: tell the server we accept responses up to 60 s old
            .addNetworkInterceptor(onlineInterceptor())
            // Offline: if no network, force OkHttp to use cache (stale up to 1 day)
            .addInterceptor(offlineFallbackInterceptor())
            .addInterceptor(logging)
            .build()
    }

    /**
     * Applied when a real network connection is available.
     * Adds a Cache-Control header so OkHttp caches the server response.
     */
    private fun onlineInterceptor() = Interceptor { chain ->
        val response: Response = chain.proceed(chain.request())
        val cacheControl = CacheControl.Builder()
            .maxAge(60, TimeUnit.SECONDS)   // treat response as fresh for 60 s
            .build()
        response.newBuilder()
            .header("Cache-Control", cacheControl.toString())
            .removeHeader("Pragma")         // older servers send no-cache via Pragma
            .build()
    }

    /**
     * Applied before the network call.
     * When the device is offline, rewrites the request to allow stale cache.
     */
    private fun offlineFallbackInterceptor() = Interceptor { chain ->
        var request = chain.request()
        if (!isNetworkAvailable()) {
            Log.d(TAG, "No network – serving from cache (max-stale ${MAX_STALE_DAYS}d)")
            val cacheControl = CacheControl.Builder()
                .onlyIfCached()
                .maxStale(MAX_STALE_DAYS.toInt(), TimeUnit.DAYS)
                .build()
            request = request.newBuilder()
                .cacheControl(cacheControl)
                .build()
        }
        chain.proceed(request)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ── DTO → Domain mapping ───────────────────────────────────────────────

    private fun AlertDto.toAlert(): Alert? {
        // id is required; discard malformed entries
        val safeId = id?.takeIf { it.isNotBlank() } ?: return null

        val sev = runCatching { AlertSeverity.valueOf(severity?.uppercase() ?: "") }
            .getOrDefault(AlertSeverity.MEDIUM)

        val cat = runCatching { AlertCategory.valueOf(category?.uppercase() ?: "") }
            .getOrDefault(AlertCategory.OTHER)

        return Alert(
            id          = safeId,
            title       = title       ?: "Untitled Alert",
            description = description ?: "",
            severity    = sev,
            location    = location    ?: "Nigeria",
            timestamp   = timestamp   ?: System.currentTimeMillis(),
            category    = cat
        )
    }

    companion object {
        private const val TAG = "RealSecurityDataSource"
    }
}
