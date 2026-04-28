package com.sentinelng.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sentinelng.R
import com.sentinelng.data.Alert
import com.sentinelng.data.AlertCategory
import com.sentinelng.data.AlertSeverity
import java.text.SimpleDateFormat
import java.util.*

class AlertsAdapter : ListAdapter<Alert, AlertsAdapter.AlertViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Alert>() {
            override fun areItemsTheSame(a: Alert, b: Alert) = a.id == b.id
            override fun areContentsTheSame(a: Alert, b: Alert) = a == b
        }
        private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon:       ImageView = itemView.findViewById(R.id.iv_alert_icon)
        private val tvTitle:      TextView  = itemView.findViewById(R.id.tv_alert_title)
        private val tvDesc:       TextView  = itemView.findViewById(R.id.tv_alert_description)
        private val tvLocation:   TextView  = itemView.findViewById(R.id.tv_alert_location)
        private val tvTime:       TextView  = itemView.findViewById(R.id.tv_alert_time)
        private val tvSeverity:   TextView  = itemView.findViewById(R.id.tv_severity)
        private val severityBar:  View      = itemView.findViewById(R.id.view_severity_bar)

        fun bind(alert: Alert) {
            tvTitle.text    = alert.title
            tvDesc.text     = alert.description
            tvLocation.text = alert.location
            tvTime.text     = dateFormat.format(Date(alert.timestamp))
            tvSeverity.text = alert.severity.name

            // Severity colour
            val colour = when (alert.severity) {
                AlertSeverity.CRITICAL -> ContextCompat.getColor(itemView.context, R.color.severity_critical)
                AlertSeverity.HIGH     -> ContextCompat.getColor(itemView.context, R.color.severity_high)
                AlertSeverity.MEDIUM   -> ContextCompat.getColor(itemView.context, R.color.severity_medium)
                AlertSeverity.LOW      -> ContextCompat.getColor(itemView.context, R.color.severity_low)
            }
            severityBar.setBackgroundColor(colour)
            tvSeverity.setTextColor(colour)

            // Category icon
            val iconRes = when (alert.category) {
                AlertCategory.FIRE             -> R.drawable.ic_fire
                AlertCategory.FLOOD            -> R.drawable.ic_flood
                AlertCategory.HEALTH_OUTBREAK  -> R.drawable.ic_health
                AlertCategory.CROP_DISEASE     -> R.drawable.ic_crop
                AlertCategory.SECURITY_THREAT  -> R.drawable.ic_security
                AlertCategory.OTHER            -> R.drawable.ic_alert
            }
            ivIcon.setImageResource(iconRes)
        }
    }
}
