package com.sentinelng.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.sentinelng.R
import com.sentinelng.data.LatLng
import com.sentinelng.databinding.ActivitySecurityBinding
import com.sentinelng.ui.adapter.AlertsAdapter
import com.sentinelng.utils.LanguageManager

class SecurityActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySecurityBinding
    private val viewModel: SecurityViewModel by viewModels()
    private lateinit var alertsAdapter: AlertsAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: LatLng? = null

    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                fetchLocation()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySecurityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setTitle(R.string.title_security)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupRecyclerView()
        observeViewModel()

        binding.btnReportIncident.setOnClickListener { showReportDialog() }
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadAlerts() }

        viewModel.loadAlerts()
        checkLocationPermission()
    }

    private fun setupRecyclerView() {
        alertsAdapter = AlertsAdapter()
        binding.rvAlerts.apply {
            layoutManager = LinearLayoutManager(this@SecurityActivity)
            adapter = alertsAdapter
        }
    }

    private fun observeViewModel() {
        viewModel.alerts.observe(this) { alerts ->
            alertsAdapter.submitList(alerts)
            binding.swipeRefresh.isRefreshing = false
            binding.tvNoAlerts.visibility = if (alerts.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { loading ->
            if (loading) binding.progressBar.visibility = View.VISIBLE
            else binding.progressBar.visibility = View.GONE
        }

        viewModel.reportResult.observe(this) { success ->
            success ?: return@observe
            val lang = LanguageManager.getLanguage(this)
            val msg = if (success) LanguageManager.getReportSuccessMessage(lang)
                      else getString(R.string.error_report_failed)
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            viewModel.clearReportResult()
        }

        viewModel.error.observe(this) { err ->
            err ?: return@observe
            Toast.makeText(this, err, Toast.LENGTH_LONG).show()
        }
    }

    private fun showReportDialog() {
        val input = android.widget.EditText(this).apply {
            hint = getString(R.string.hint_describe_incident)
            minLines = 3
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.title_report_incident)
            .setView(input)
            .setPositiveButton(R.string.action_report) { _, _ ->
                val description = input.text.toString().trim()
                if (description.isEmpty()) {
                    Toast.makeText(this, R.string.error_empty_description, Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.reportIncident(null, currentLocation, description)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fetchLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun fetchLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                currentLocation = LatLng(it.latitude, it.longitude)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
