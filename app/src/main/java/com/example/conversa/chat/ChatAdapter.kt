package com.example.conversa.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.model.Message
import com.example.conversa.chat.ViewHolderMessageReceived
import com.example.conversa.chat.ViewHolderMessageSent
import android.util.Log // Para logs

class ChatAdapter(
    private val messages: MutableList<Message>,
    private val currentUserId: Int // Passar o ID do usuário logado para o adaptador
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TAG = "ChatAdapter"

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
    }

    // Atualiza a lista de mensagens e notifica o RecyclerView
    fun updateMessages(newMessages: List<Message>) {
        // Log.d(TAG, "Atualizando mensagens no ChatAdapter. Novas mensagens: ${newMessages.size}")
        // if (newMessages.isNotEmpty()) {
        //     Log.d(TAG, "Primeira nova mensagem: ${newMessages.first().content}, isMine: ${newMessages.first().isMine}")
        // }

        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged() // Notifica que todo o dataset mudou
        // Para uma atualização mais eficiente, considere DiffUtil:
        // https://developer.android.com/reference/androidx/recyclerview/widget/DiffUtil
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        // Se o remetente da mensagem é o usuário logado, é uma mensagem enviada
        return if (message.senderId == currentUserId) {
            VIEW_TYPE_SENT
        } else {
            VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val view = inflater.inflate(R.layout.item_message_sent, parent, false)
                ViewHolderMessageSent(view)
            }
            VIEW_TYPE_RECEIVED -> {
                val view = inflater.inflate(R.layout.item_message_received, parent, false)
                ViewHolderMessageReceived(view)
            }
            else -> throw IllegalArgumentException("ViewType desconhecido: $viewType")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder.itemViewType) {
            VIEW_TYPE_SENT -> (holder as ViewHolderMessageSent).bind(message)
            VIEW_TYPE_RECEIVED -> (holder as ViewHolderMessageReceived).bind(message)
        }
    }

    override fun getItemCount(): Int {
        return messages.size
    }
}