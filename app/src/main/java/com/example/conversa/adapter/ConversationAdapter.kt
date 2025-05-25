// com.example.conversa.adapter/ConversationAdapter.kt
package com.example.conversa.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.model.Conversation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ConversationAdapter(
    private val conversations: List<Conversation>,
    private val onItemClick: (Conversation) -> Unit
) : RecyclerView.Adapter<ConversationAdapter.ConversationViewHolder>() {

    inner class ConversationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewConversationName)
        val lastMessageTextView: TextView = itemView.findViewById(R.id.textViewLastMessage)
        val lastMessageTimeTextView: TextView = itemView.findViewById(R.id.textViewLastMessageTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        val currentConversation = conversations[position]

        holder.nameTextView.text = currentConversation.nome // Usa o campo 'nome' para o título da conversa

        holder.lastMessageTextView.text = currentConversation.ultimaMensagemTexto ?: "Nenhuma mensagem."

        currentConversation.ultimaMensagemTimestamp?.let { timestamp ->
            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            // Assumindo que o timestamp da API é UTC (com 'Z' no final)
            // Se não for 'Z', ajuste o padrão "yyyy-MM-dd'T'HH:mm:ss.SSS"
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
            isoFormat.timeZone = TimeZone.getTimeZone("UTC")
            try {
                val date = isoFormat.parse(timestamp)
                if (date != null) {
                    holder.lastMessageTimeTextView.text = dateFormat.format(date)
                } else {
                    holder.lastMessageTimeTextView.text = ""
                }
            } catch (e: Exception) {
                holder.lastMessageTimeTextView.text = "" // Em caso de erro de parsing
            }
        } ?: run {
            holder.lastMessageTimeTextView.text = ""
        }

        holder.itemView.setOnClickListener {
            onItemClick(currentConversation)
        }
    }

    override fun getItemCount() = conversations.size
}
