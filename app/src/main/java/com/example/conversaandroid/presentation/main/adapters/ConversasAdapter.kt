import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.seudominio.conversa.R
import com.seudominio.conversa.databinding.ItemConversaBinding
import com.seudominio.conversa.domain.model.Conversa
import java.text.SimpleDateFormat
import java.util.*

class ConversasAdapter(
    private val onConversaClick: (Conversa) -> Unit,
    private val onConversaLongClick: (Conversa) -> Unit
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
            binding.textViewNome.text = conversa.nome ?: "Sem nome"
            binding.textViewUltimaMensagem.text = conversa.ultimaMensagem ?: "Nenhuma mensagem"

            // Formatar hora
            conversa.ultimaMensagemEm?.let { timestamp ->
                val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.textViewHora.text = sdf.format(Date(timestamp))
            }

            // Badge de mensagens não lidas
            if (conversa.mensagensNaoLidas > 0) {
                binding.textViewBadge.text = conversa.mensagensNaoLidas.toString()
                binding.textViewBadge.visibility = android.view.View.VISIBLE
            } else {
                binding.textViewBadge.visibility = android.view.View.GONE
            }

            // Foto de perfil
            Glide.with(binding.root.context)
                .load(conversa.foto)
                .placeholder(R.drawable.ic_person)
                .circleCrop()
                .into(binding.imageViewFoto)

            // Cliques
            binding.root.setOnClickListener {
                onConversaClick(conversa)
            }

            binding.root.setOnLongClickListener {
                onConversaLongClick(conversa)
                true
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