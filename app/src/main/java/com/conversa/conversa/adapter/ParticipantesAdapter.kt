package com.conversa.conversa.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conversa.conversa.databinding.ItemParticipanteBinding
import com.conversa.conversa.data.model.ParticipanteItem
import com.conversa.conversa.data.model.UsuarioChamadaStatus

class ParticipantesAdapter : ListAdapter<ParticipanteItem, ParticipantesAdapter.ViewHolder>(ParticipanteDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemParticipanteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemParticipanteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(participante: ParticipanteItem) {
            binding.tvNomeParticipante.text = participante.nome
            
            val statusText = when (participante.status) {
                UsuarioChamadaStatus.PENDENTE -> "Chamando..."
                UsuarioChamadaStatus.ENTROU -> "Conectado"
                UsuarioChamadaStatus.SAIU -> "Desconectado"
                UsuarioChamadaStatus.RECUSADO -> "Recusou"
            }
            binding.tvStatusParticipante.text = statusText
        }
    }

    class ParticipanteDiffCallback : DiffUtil.ItemCallback<ParticipanteItem>() {
        override fun areItemsTheSame(oldItem: ParticipanteItem, newItem: ParticipanteItem): Boolean {
            return oldItem.usuarioId == newItem.usuarioId
        }

        override fun areContentsTheSame(oldItem: ParticipanteItem, newItem: ParticipanteItem): Boolean {
            return oldItem == newItem
        }
    }
}
