package com.conversa.conversa.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conversa.conversa.data.model.Conversa
import com.conversa.conversa.databinding.ItemConversaBinding
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

class ConversasAdapter(
    private val onConversaClick: (Conversa) -> Unit
) : ListAdapter<Conversa, ConversasAdapter.ConversaViewHolder>(ConversaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversaViewHolder {
        val binding = ItemConversaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ConversaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConversaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ConversaViewHolder(
        private val binding: ItemConversaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversa: Conversa) {
            binding.apply {
                // Nome da conversa
                tvNomeConversa.text = conversa.descricao
                
                // Última mensagem
                if (conversa.ultima_mensagem != null) {
                    val mensagem = conversa.ultima_mensagem
                    
                    // Texto da mensagem
                    tvUltimaMensagem.text = conversa.ultima_mensagem_texto;
                    tvHoraMensagem.text = conversa.ultima_mensagem.format(DateTimeFormatter.ofPattern("HH:mm"))
                } else {
                    tvUltimaMensagem.text = ""
                    tvHoraMensagem.text = ""
                }

                // Ícone baseado no tipo
                ivIconeTipo.setImageResource(
                    if (conversa.tipo == 1) {
                        android.R.drawable.ic_menu_share // Ícone de grupo
                    } else {
                        android.R.drawable.ic_menu_myplaces // Ícone de pessoa
                    }
                )
                
                // Click listener
                root.setOnClickListener {
                    onConversaClick(conversa)
                }
            }
        }
        
        private fun formatarHora(dataHora: String?): String {
            if (dataHora == null) return ""
            
            return try {
                // Formato esperado: "2025-01-21T10:30:00"
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dataHora)
                
                // Verifica se é hoje
                val hoje = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val dataMsg = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date)
                
                if (hoje == dataMsg) {
                    outputFormat.format(date)
                } else {
                    val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
                    dateFormat.format(date)
                }
            } catch (e: Exception) {
                ""
            }
        }
    }

    class ConversaDiffCallback : DiffUtil.ItemCallback<Conversa>() {
        override fun areItemsTheSame(oldItem: Conversa, newItem: Conversa): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Conversa, newItem: Conversa): Boolean {
            return oldItem == newItem
        }
    }
}
