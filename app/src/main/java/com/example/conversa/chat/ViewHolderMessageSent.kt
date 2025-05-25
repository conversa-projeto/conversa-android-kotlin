package com.example.conversa.chat

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewHolderMessageSent(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val textViewMessageContent: TextView = itemView.findViewById(R.id.textViewMessageContentSent)
    private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestampSent)

    fun bind(message: Message) {
        textViewMessageContent.text = message.content
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        textViewTimestamp.text = dateFormat.format(Date(message.timestamp))
    }
}