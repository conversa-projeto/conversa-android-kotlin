package com.conversa.conversa.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.databinding.ItemContatoSelecionavelBinding

/**
 * Adapter para lista de contatos com seleção múltipla
 * Usado para criar grupos
 */
class ContatosSelecionaveisAdapter(
    private val onSelecaoChanged: (Int) -> Unit
) : ListAdapter<Contato, ContatosSelecionaveisAdapter.ContatoViewHolder>(ContatoDiffCallback()) {

    private var listaCompleta: List<Contato> = emptyList()
    private val contatosSelecionados = mutableSetOf<Int>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContatoViewHolder {
        val binding = ItemContatoSelecionavelBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContatoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContatoViewHolder, position: Int) {
        holder.bind(getItem(position), contatosSelecionados.contains(getItem(position).id))
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

    fun getContatosSelecionados(): List<Contato> {
        return listaCompleta.filter { contatosSelecionados.contains(it.id) }
    }

    fun limparSelecao() {
        contatosSelecionados.clear()
        notifyDataSetChanged()
        onSelecaoChanged(0)
    }

    inner class ContatoViewHolder(
        private val binding: ItemContatoSelecionavelBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(contato: Contato, isSelected: Boolean) {
            binding.apply {
                tvNomeContato.text = contato.nome
                tvLoginContato.text = "@${contato.login}"
                cbSelecionar.isChecked = isSelected

                // Primeira letra do nome como avatar
                tvIconeContato.text = contato.nome.firstOrNull()?.toString()?.uppercase() ?: "?"

                // Toggle seleção ao clicar no item ou checkbox
                val toggleListener = {
                    if (contatosSelecionados.contains(contato.id)) {
                        contatosSelecionados.remove(contato.id)
                    } else {
                        contatosSelecionados.add(contato.id)
                    }
                    notifyItemChanged(adapterPosition)
                    onSelecaoChanged(contatosSelecionados.size)
                }

                root.setOnClickListener { toggleListener() }
                cbSelecionar.setOnClickListener { toggleListener() }
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
