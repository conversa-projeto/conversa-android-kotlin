package com.conversa.conversa.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conversa.conversa.R
import com.conversa.conversa.data.model.Conteudo
import com.conversa.conversa.data.model.Mensagem
import com.conversa.conversa.databinding.ItemMensagemRecebidaBinding
import com.conversa.conversa.databinding.ItemMensagemEnviadaBinding
import java.time.format.DateTimeFormatter

class MensagensAdapter(
    private val usuarioId: Int,
    private val isGrupo: Boolean,
    private val onDownloadClick: (conteudoId: Int, nomeArquivo: String, extensao: String) -> Unit
) : ListAdapter<Mensagem, RecyclerView.ViewHolder>(MensagemDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_ENVIADA = 1
        private const val VIEW_TYPE_RECEBIDA = 2
    }

    override fun getItemViewType(position: Int): Int {
        val mensagem = getItem(position)
        return if (mensagem.usuarioId == usuarioId) {
            VIEW_TYPE_ENVIADA
        } else {
            VIEW_TYPE_RECEBIDA
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_ENVIADA -> {
                val binding = ItemMensagemEnviadaBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                MensagemEnviadaViewHolder(binding)
            }
            else -> {
                val binding = ItemMensagemRecebidaBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                MensagemRecebidaViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val mensagem = getItem(position)
        when (holder) {
            is MensagemEnviadaViewHolder -> holder.bind(mensagem)
            is MensagemRecebidaViewHolder -> holder.bind(mensagem)
        }
    }

    /**
     * ViewHolder para mensagens enviadas (lado direito)
     */
    inner class MensagemEnviadaViewHolder(
        private val binding: ItemMensagemEnviadaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(mensagem: Mensagem) {
            // Pega o primeiro conteúdo de texto (se existir)
            val conteudoTexto = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_TEXTO }
            
            if (conteudoTexto != null && conteudoTexto.conteudo.isNotEmpty()) {
                binding.tvMensagem.text = conteudoTexto.conteudo
                binding.tvMensagem.visibility = View.VISIBLE
            } else {
                binding.tvMensagem.visibility = View.GONE
            }
            
            // Verifica se há anexos (arquivo, imagem, áudio)
            val anexo = mensagem.conteudos.firstOrNull { 
                it.tipo in listOf(Conteudo.TIPO_IMAGEM, Conteudo.TIPO_ARQUIVO, Conteudo.TIPO_AUDIO)
            }
            
            if (anexo != null) {
                configurarAnexo(
                    binding.layoutAnexo,
                    binding.tvNomeArquivo,
                    binding.btnBaixarAnexo,
                    anexo
                )
            } else {
                binding.layoutAnexo.visibility = View.GONE
            }
            
            binding.tvHora.text = formatarHora(mensagem.inserida?.toString())
            
            // Indicadores de status
            when {
                mensagem.visualizada -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_double_check_blue)
                    binding.ivStatus.visibility = View.VISIBLE
                }
                mensagem.recebida -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_double_check)
                    binding.ivStatus.visibility = View.VISIBLE
                }
                else -> {
                    binding.ivStatus.setImageResource(R.drawable.ic_check)
                    binding.ivStatus.visibility = View.VISIBLE
                }
            }
        }
    }

    /**
     * ViewHolder para mensagens recebidas (lado esquerdo)
     */
    inner class MensagemRecebidaViewHolder(
        private val binding: ItemMensagemRecebidaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(mensagem: Mensagem) {
            // Pega o primeiro conteúdo de texto (se existir)
            val conteudoTexto = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_TEXTO }
            
            // Só exibe nome do remetente se for grupo
            if (isGrupo) {
                binding.tvRemetente.text = mensagem.remetente
                binding.tvRemetente.visibility = View.VISIBLE
            } else {
                binding.tvRemetente.visibility = View.GONE
            }
            
            if (conteudoTexto != null && conteudoTexto.conteudo.isNotEmpty()) {
                binding.tvMensagem.text = conteudoTexto.conteudo
                binding.tvMensagem.visibility = View.VISIBLE
            } else {
                binding.tvMensagem.visibility = View.GONE
            }
            
            // Verifica se há anexos (arquivo, imagem, áudio)
            val anexo = mensagem.conteudos.firstOrNull { 
                it.tipo in listOf(Conteudo.TIPO_IMAGEM, Conteudo.TIPO_ARQUIVO, Conteudo.TIPO_AUDIO)
            }
            
            if (anexo != null) {
                configurarAnexo(
                    binding.layoutAnexo,
                    binding.tvNomeArquivo,
                    binding.btnBaixarAnexo,
                    anexo
                )
            } else {
                binding.layoutAnexo.visibility = View.GONE
            }
            
            binding.tvHora.text = formatarHora(mensagem.inserida?.toString())
        }
    }

    /**
     * Configura a exibição de um anexo
     */
    private fun configurarAnexo(
        layoutAnexo: View,
        tvNomeArquivo: View,
        btnBaixar: View,
        anexo: Conteudo
    ) {
        layoutAnexo.visibility = View.VISIBLE
        
        val nomeCompleto = if (anexo.nome != null && anexo.extensao != null) {
            "${anexo.nome}.${anexo.extensao}"
        } else {
            when (anexo.tipo) {
                Conteudo.TIPO_IMAGEM -> "imagem.jpg"
                Conteudo.TIPO_AUDIO -> "audio.mp3"
                else -> "arquivo.bin"
            }
        }
        
        (tvNomeArquivo as? android.widget.TextView)?.text = nomeCompleto
        
        btnBaixar.setOnClickListener {
            val nome = anexo.nome ?: "arquivo_${anexo.id}"
            val extensao = anexo.extensao ?: "bin"
            onDownloadClick(anexo.id, nome, extensao)
        }
    }

    /**
     * Formata a data/hora para exibição (ex: "14:30")
     */
    private fun formatarHora(dataHora: String?): String {
        if (dataHora == null) return ""
        
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            val dateTime = java.time.LocalDateTime.parse(dataHora, formatter)
            dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * DiffUtil para comparar mensagens
 */
class MensagemDiffCallback : DiffUtil.ItemCallback<Mensagem>() {
    override fun areItemsTheSame(oldItem: Mensagem, newItem: Mensagem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Mensagem, newItem: Mensagem): Boolean {
        return oldItem == newItem
    }
}
