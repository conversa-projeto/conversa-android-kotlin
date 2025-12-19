package com.conversa.conversa.ui.chamada

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.conversa.conversa.R
import com.conversa.conversa.databinding.ItemParticipanteChamadaBinding
import com.conversa.conversa.databinding.ItemParticipanteGridBinding

class ParticipantesAdapter(
    private val onMuteClick: ((ParticipanteItem) -> Unit)? = null
) : ListAdapter<ParticipanteItem, RecyclerView.ViewHolder>(DiffCallback()) {

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    var isGridMode = false
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_GRID -> {
                val binding = ItemParticipanteGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                GridViewHolder(binding, onMuteClick)
            }
            else -> {
                val binding = ItemParticipanteChamadaBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                ListViewHolder(binding, onMuteClick)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val participante = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(participante)
            is ListViewHolder -> holder.bind(participante)
        }
    }

    class ListViewHolder(
        private val binding: ItemParticipanteChamadaBinding,
        private val onMuteClick: ((ParticipanteItem) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(participante: ParticipanteItem) {
            binding.tvNomeParticipante.text = participante.nome
            binding.tvStatusParticipante.text = participante.status
            
            if (participante.fotoUrl != null) {
                Glide.with(binding.root.context)
                    .load(participante.fotoUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivAvatarParticipante)
            } else {
                binding.ivAvatarParticipante.setImageResource(R.drawable.ic_person)
            }
            
            // Atualiza ícone de volume baseado no estado
            if (participante.mutadoLocalmente) {
                binding.ivStatusParticipante.setImageResource(R.drawable.ic_volume_off)
            } else {
                binding.ivStatusParticipante.setImageResource(R.drawable.ic_volume_up)
            }
            
            // Click listener para mutar/desmutar
            binding.ivStatusParticipante.setOnClickListener {
                onMuteClick?.invoke(participante)
            }
        }
    }

    class GridViewHolder(
        private val binding: ItemParticipanteGridBinding,
        private val onMuteClick: ((ParticipanteItem) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(participante: ParticipanteItem) {
            binding.tvNomeParticipante.text = participante.nome
            binding.tvStatusParticipante.text = participante.status
            
            if (participante.fotoUrl != null) {
                Glide.with(binding.root.context)
                    .load(participante.fotoUrl)
                    .placeholder(R.drawable.ic_person)
                    .error(R.drawable.ic_person)
                    .into(binding.ivAvatarParticipante)
            } else {
                binding.ivAvatarParticipante.setImageResource(R.drawable.ic_person)
            }
            
            // Atualiza ícone de volume baseado no estado
            if (participante.mutadoLocalmente) {
                binding.ivStatusParticipante.setImageResource(R.drawable.ic_volume_off)
            } else {
                binding.ivStatusParticipante.setImageResource(R.drawable.ic_volume_up)
            }
            
            // Click listener para mutar/desmutar
            binding.ivStatusParticipante.setOnClickListener {
                onMuteClick?.invoke(participante)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<ParticipanteItem>() {
        override fun areItemsTheSame(oldItem: ParticipanteItem, newItem: ParticipanteItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ParticipanteItem, newItem: ParticipanteItem): Boolean {
            return oldItem == newItem
        }
    }
}
