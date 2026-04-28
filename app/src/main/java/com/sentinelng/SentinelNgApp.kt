package com.sentinelng

import android.app.Application
import android.util.Log
import com.sentinelng.data.RealSecurityDataSource

/**
 * Application singleton.
 * Initialises the [RealSecurityDataSource] once so the OkHttp cache
 * directory is created at startup and shared across ViewModels.
 */
class SentinelNgApp : Application() {

    /** Shared data source — access from anywhere via [SentinelNgApp.instance]. */
    lateinit var securityDataSource: RealSecurityDataSource
        private set

    override fun onCreate() {
        super.onCreate()
        _instance = this
        securityDataSource = RealSecurityDataSource(this)
        Log.i(TAG, "SentinelNgApp initialised")
    }

    companion object {
        private const val TAG = "SentinelNgApp"
        private var _instance: SentinelNgApp? = null

        /**
         * Convenience accessor.
         * Safe to call after [onCreate] — i.e. from any Activity/ViewModel.
         */
        val instance: SentinelNgApp
            get() = _instance ?: error("SentinelNgApp not yet initialised")
    }
}
