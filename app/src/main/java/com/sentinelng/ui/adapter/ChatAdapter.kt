package com.sentinelng.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.sentinelng.R
import com.sentinelng.data.ChatMessage
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter : ListAdapter<ChatMessage, ChatAdapter.ChatViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val VIEW_TYPE_USER      = 1
        private const val VIEW_TYPE_ASSISTANT = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) = old.id == new.id
            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) = old == new
        }
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val layoutRes = if (viewType == VIEW_TYPE_USER)
            R.layout.item_chat_user else R.layout.item_chat_assistant
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)
        private val tvTime: TextView    = itemView.findViewById(R.id.tv_time)

        fun bind(message: ChatMessage) {
            tvMessage.text = message.text
            tvTime.text    = timeFormat.format(Date(message.timestamp))
        }
    }
}
