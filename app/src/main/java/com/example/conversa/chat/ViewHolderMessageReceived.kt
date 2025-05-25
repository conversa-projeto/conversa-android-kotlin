package com.example.conversa.chat

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ViewHolderMessageReceived(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val textViewSenderName: TextView = itemView.findViewById(R.id.textViewSenderNameReceived)
    private val textViewMessageContent: TextView = itemView.findViewById(R.id.textViewMessageContentReceived)
    private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestampReceived)

    fun bind(message: Message) {
        textViewSenderName.text = message.senderName // Exibe o nome do remetente
        textViewMessageContent.text = message.content
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        textViewTimestamp.text = dateFormat.format(Date(message.timestamp))
    }
}