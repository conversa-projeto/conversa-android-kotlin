package com.conversa.conversa.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.databinding.ItemContatoBinding

/**
 * Adapter para lista simples de contatos (sem seleção)
 * Usado para conversas 1:1
 */
class ContatosAdapter(
    private val onContatoClick: (Contato) -> Unit
) : ListAdapter<Contato, ContatosAdapter.ContatoViewHolder>(ContatoDiffCallback()) {

    private var listaCompleta: List<Contato> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContatoViewHolder {
        val binding = ItemContatoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContatoViewHolder(binding, onContatoClick)
    }

    override fun onBindViewHolder(holder: ContatoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setListaCompleta(lista: List<Contato>) {
        listaCompleta = lista
        submitList(lista)
    }

    fun filtrar(query: String) {
        val listaFiltrada = if (query.isEmpty()) {
            listaCompleta
        } else {
            listaCompleta.filter { contato ->
                contato.nome.contains(query, ignoreCase = true) ||
                        contato.login.contains(query, ignoreCase = true)
            }
        }
        submitList(listaFiltrada)
    }

    class ContatoViewHolder(
        private val binding: ItemContatoBinding,
        private val onContatoClick: (Contato) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contato: Contato) {
            binding.apply {
                tvNomeContato.text = contato.nome
                tvLoginContato.text = "@${contato.login}"

                // Primeira letra do nome como avatar
                tvIconeContato.text = contato.nome.firstOrNull()?.toString()?.uppercase() ?: "?"

                root.setOnClickListener {
                    onContatoClick(contato)
                }
            }
        }
    }

    class ContatoDiffCallback : DiffUtil.ItemCallback<Contato>() {
        override fun areItemsTheSame(oldItem: Contato, newItem: Contato): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Contato, newItem: Contato): Boolean {
            return oldItem == newItem
        }
    }
}
