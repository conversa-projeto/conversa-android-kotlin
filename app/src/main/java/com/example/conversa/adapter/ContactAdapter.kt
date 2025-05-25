// com.example.conversa.adapter/ContactAdapter.kt
package com.example.conversa.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView // Se você usar o avatar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.model.Contact // Importa sua classe de modelo Contact

// Importe Glide ou Picasso se for carregar imagens (ex: import com.bumptech.glide.Glide)

class ContactAdapter(
    private val contacts: List<Contact>, // A lista de contatos a ser exibida
    private val onItemClick: (Contact) -> Unit // Uma função lambda para lidar com cliques nos itens
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    // ViewHolder: Responsável por manter as referências das Views de cada item da lista
    inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.textViewContactName)
        // Se você adicionou o ImageView no item_contact.xml, descomente a linha abaixo:
        // val avatarImageView: ImageView = itemView.findViewById(R.id.imageViewContactAvatar)
    }

    // onCreateViewHolder: Infla o layout do item e cria um ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false) // Infla o layout item_contact.xml
        return ContactViewHolder(itemView)
    }

    // onBindViewHolder: Preenche os dados de um item específico no ViewHolder
    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val currentContact = contacts[position] // Pega o objeto Contact na posição atual

        holder.nameTextView.text = currentContact.nome // Define o nome do contato

        // Se você tiver um avatar:
        /*
        if (currentContact.avatarUrl != null && currentContact.avatarUrl.isNotEmpty()) {
            // Use uma biblioteca como Glide para carregar a imagem da URL
            Glide.with(holder.itemView.context)
                .load(currentContact.avatarUrl)
                .placeholder(R.drawable.ic_default_avatar) // Imagem placeholder enquanto carrega
                .error(R.drawable.ic_default_avatar) // Imagem de erro
                .into(holder.avatarImageView)
        } else {
            holder.avatarImageView.setImageResource(R.drawable.ic_default_avatar) // Define avatar padrão
        }
        */

        // Define o listener de clique para o item inteiro
        holder.itemView.setOnClickListener {
            onItemClick(currentContact) // Chama a função lambda passada no construtor
        }
    }

    // getItemCount: Retorna o número total de itens na lista
    override fun getItemCount() = contacts.size
}