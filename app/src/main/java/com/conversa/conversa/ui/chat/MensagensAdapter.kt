package com.conversa.conversa.ui.chat

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.Button
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.conversa.conversa.R
import com.conversa.conversa.data.model.Conteudo
import com.conversa.conversa.data.model.Mensagem
import com.conversa.conversa.databinding.ItemMensagemRecebidaBinding
import com.conversa.conversa.databinding.ItemMensagemEnviadaBinding
import java.time.format.DateTimeFormatter

class MensagensAdapter(
    private val usuarioId: Int,
    private val isGrupo: Boolean,
    private val apiUrl: String,
    private val authToken: String,
    private val audioPlayerHelper: AudioPlayerHelper,
    private val onDownloadClick: (conteudoId: String, nomeArquivo: String, extensao: String) -> Unit
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

            // Verifica se há imagem
            val imagem = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_IMAGEM }
            if (imagem != null) {
                configurarImagem(binding.ivImagem, imagem)
            } else {
                binding.ivImagem.visibility = View.GONE
            }

            // Verifica se há áudio
            val audio = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_AUDIO }
            if (audio != null) {
                configurarAudio(
                    binding.layoutAudio,
                    binding.btnPlayPause,
                    binding.tvDuracaoAudio,
                    binding.tvTamanhoAudio,              // NOVO
                    binding.layoutAudioPlayback,         // NOVO
                    binding.seekBarAudio,                // NOVO
                    binding.tvTempoAtual,                // NOVO
                    binding.tvTempoTotal,                // NOVO
                    binding.layoutDownloadProgress,
                    binding.progressBarDownload,
                    binding.tvDownloadStatus,
                    binding.layoutDownloadError,
                    binding.tvDownloadError,
                    binding.btnRetryDownload,
                    audio
                )
            } else {
                binding.layoutAudio.visibility = View.GONE
            }

            // Verifica se há anexo (arquivo)
            val anexo = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_ARQUIVO }
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

            // Verifica se há imagem
            val imagem = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_IMAGEM }
            if (imagem != null) {
                configurarImagem(binding.ivImagem, imagem)
            } else {
                binding.ivImagem.visibility = View.GONE
            }

            // Verifica se há áudio
            val audio = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_AUDIO }
            if (audio != null) {
                configurarAudio(
                    binding.layoutAudio,
                    binding.btnPlayPause,
                    binding.tvDuracaoAudio,
                    binding.tvTamanhoAudio,              // NOVO
                    binding.layoutAudioPlayback,         // NOVO
                    binding.seekBarAudio,                // NOVO
                    binding.tvTempoAtual,                // NOVO
                    binding.tvTempoTotal,                // NOVO
                    binding.layoutDownloadProgress,
                    binding.progressBarDownload,
                    binding.tvDownloadStatus,
                    binding.layoutDownloadError,
                    binding.tvDownloadError,
                    binding.btnRetryDownload,
                    audio
                )
            } else {
                binding.layoutAudio.visibility = View.GONE
            }

            // Verifica se há anexo (arquivo)
            val anexo = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_ARQUIVO }
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
     * Configura a exibição de uma imagem
     */
    private fun configurarImagem(imageView: android.widget.ImageView, imagem: Conteudo) {
        imageView.visibility = View.VISIBLE

        val imageUrl = "${apiUrl}anexo?identificador=${imagem.conteudo}"

        // Carrega thumbnail da imagem COM AUTENTICAÇÃO
        Glide.with(imageView.context)
            .load(com.bumptech.glide.load.model.GlideUrl(
                imageUrl,
                com.bumptech.glide.load.model.LazyHeaders.Builder()
                    .addHeader("Authorization", "Bearer $authToken")
                    .build()
            ))
            .placeholder(R.drawable.ic_person)
            .error(R.drawable.ic_person)
            .centerCrop()
            .into(imageView)

        // Ao clicar, abre em tela cheia
        imageView.setOnClickListener {
            val intent = Intent(imageView.context, ImageViewerActivity::class.java).apply {
                putExtra("conteudo_id", imagem.conteudo)
                putExtra("nome_imagem", imagem.nome ?: "Imagem")
                putExtra("api_url", apiUrl)
                putExtra("auth_token", authToken)
            }
            imageView.context.startActivity(intent)
        }
    }

    /**
     * Configura a exibição de um áudio
     */
    private fun configurarAudio(
        layoutAudio: View,
        btnPlayPause: ImageButton,
        tvDuracao: TextView,
        tvTamanho: TextView,                    // NOVO
        layoutAudioPlayback: View,              // NOVO
        seekBarAudio: android.widget.SeekBar,   // NOVO
        tvTempoAtual: TextView,                 // NOVO
        tvTempoTotal: TextView,                 // NOVO
        layoutDownloadProgress: View,
        progressBarDownload: ProgressBar,
        tvDownloadStatus: TextView,
        layoutDownloadError: View,
        tvDownloadError: TextView,
        btnRetryDownload: Button,
        audio: Conteudo
    ) {
        layoutAudio.visibility = View.VISIBLE

        val audioUrl = "${apiUrl}anexo?identificador=${audio.conteudo}"

        // Remove callback anterior (se existir) para evitar múltiplos callbacks
        audioPlayerHelper.unregisterStateCallback(audio.conteudo)

        // Obtém o estado atual primeiro e configura a UI
        val currentState = audioPlayerHelper.getAudioState(audio.conteudo)
        atualizarUIAudio(
            currentState,
            btnPlayPause,
            tvDuracao,
            tvTamanho,
            layoutAudioPlayback,
            seekBarAudio,
            tvTempoAtual,
            tvTempoTotal,
            layoutDownloadProgress,
            progressBarDownload,
            tvDownloadStatus,
            layoutDownloadError,
            tvDownloadError,
            layoutAudio,
            audio
        )

        // Registra callback para atualizar UI quando o estado mudar
        audioPlayerHelper.registerStateCallback(audio.conteudo) { state ->
            atualizarUIAudio(
                state,  // USA O ESTADO ATUALIZADO DO CALLBACK
                btnPlayPause,
                tvDuracao,
                tvTamanho,
                layoutAudioPlayback,
                seekBarAudio,
                tvTempoAtual,
                tvTempoTotal,
                layoutDownloadProgress,
                progressBarDownload,
                tvDownloadStatus,
                layoutDownloadError,
                tvDownloadError,
                layoutAudio,
                audio
            )
        }

        // Inicia o download do áudio (se ainda não foi iniciado)
        audioPlayerHelper.downloadAudio(audio.conteudo, audioUrl, authToken)

        btnPlayPause.setOnClickListener {
            val currentState = audioPlayerHelper.getAudioState(audio.conteudo)

            if (currentState.downloadState != AudioDownloadState.READY) {
                android.widget.Toast.makeText(
                    it.context,
                    "Aguarde o download terminar",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Se já está tocando, pausa
            val isCurrentlyPlaying = audioPlayerHelper.isPlaying() &&
                    audioPlayerHelper.getCurrentAudioId() == audio.conteudo

            if (isCurrentlyPlaying) {
                audioPlayerHelper.pauseAudio()
                btnPlayPause.setImageResource(R.drawable.ic_play)
                return@setOnClickListener
            }

            // Inicia reprodução
            audioPlayerHelper.playAudio(
                identificador = audio.conteudo,
                audioUrl = audioUrl,
                authToken = authToken,
                onComplete = {
                    // Atualiza UI quando terminar
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                    seekBarAudio.progress = 0
                },
                onError = { error ->
                    android.widget.Toast.makeText(
                        it.context,
                        error,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )

            // Mostra SeekBar
            layoutAudioPlayback.visibility = View.VISIBLE
            tvTempoTotal.text = audioPlayerHelper.formatDuration(currentState.duration)
            btnPlayPause.setImageResource(R.drawable.ic_pause)

            // Inicia atualização do SeekBar
            iniciarAtualizacaoSeekBar(
                audio.conteudo,
                seekBarAudio,
                tvTempoAtual
            )
        }

// Configura SeekBar para permitir seek manual
        seekBarAudio.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val duration = audioPlayerHelper.getDuration()
                    val newPosition = (duration * progress) / 100
                    tvTempoAtual.text = audioPlayerHelper.formatDuration(newPosition.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Usuário começou a arrastar
            }

            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                // Usuário soltou - aplica a nova posição
                seekBar?.let {
                    val duration = audioPlayerHelper.getDuration()
                    val newPosition = (duration * it.progress) / 100
                    audioPlayerHelper.seekTo(newPosition)
                }
            }
        })

        // Configura botão de retry
        btnRetryDownload.setOnClickListener {
            audioPlayerHelper.retryDownload(audio.conteudo, audioUrl, authToken)
        }
    }

    /**
     * Atualiza o SeekBar enquanto o áudio está tocando
     */
    private fun iniciarAtualizacaoSeekBar(
        audioId: String,
        seekBar: android.widget.SeekBar,
        tvTempoAtual: TextView
    ) {
        // Handler para atualizar a cada 100ms
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (audioPlayerHelper.isPlaying() &&
                    audioPlayerHelper.getCurrentAudioId() == audioId) {

                    val currentPos = audioPlayerHelper.getCurrentPosition()
                    val duration = audioPlayerHelper.getDuration()

                    if (duration > 0) {
                        val progress = (currentPos * 100) / duration
                        seekBar.progress = progress
                        tvTempoAtual.text = audioPlayerHelper.formatDuration(currentPos.toLong())
                    }

                    handler.postDelayed(this, 100)
                }
            }
        }
        handler.post(runnable)
    }

    /**
     * Atualiza a UI do áudio baseado no estado
     */
    private fun atualizarUIAudio(
        state: AudioState,
        btnPlayPause: ImageButton,
        tvDuracao: TextView,
        tvTamanho: TextView,                    // NOVO
        layoutAudioPlayback: View,              // NOVO
        seekBarAudio: android.widget.SeekBar,   // NOVO
        tvTempoAtual: TextView,                 // NOVO
        tvTempoTotal: TextView,                 // NOVO
        layoutDownloadProgress: View,
        progressBarDownload: ProgressBar,
        tvDownloadStatus: TextView,
        layoutDownloadError: View,
        tvDownloadError: TextView,
        layoutAudio: View,
        audio: Conteudo
    ) {
        layoutAudioPlayback.visibility = View.VISIBLE
        when (state.downloadState) {
            AudioDownloadState.NOT_STARTED -> {
                // Estado inicial - oculta tudo
                btnPlayPause.isEnabled = false
                btnPlayPause.setImageResource(R.drawable.ic_play)
                tvDuracao.text = "Aguardando..."
                layoutDownloadProgress.visibility = View.GONE
                layoutDownloadError.visibility = View.GONE
            }

            AudioDownloadState.DOWNLOADING -> {
                // Mostra progresso do download
                btnPlayPause.isEnabled = false
                btnPlayPause.setImageResource(R.drawable.ic_play)
                layoutDownloadProgress.visibility = View.VISIBLE
                layoutDownloadError.visibility = View.GONE

                progressBarDownload.progress = state.progress
                tvDownloadStatus.text = "Baixando... ${state.progress}%"
            }

            AudioDownloadState.READY -> {
                // Áudio pronto para tocar
                btnPlayPause.isEnabled = true
                layoutDownloadProgress.visibility = View.GONE
                layoutDownloadError.visibility = View.GONE

                // Mostra tamanho do arquivo
                tvTamanho.text = audioPlayerHelper.formatFileSize(state.fileSize)
                tvTamanho.visibility = View.VISIBLE

                // Atualiza duração
                tvDuracao.text = audioPlayerHelper.formatDuration(state.duration)

                // Verifica se este áudio está tocando
                val isPlaying = audioPlayerHelper.isPlaying() &&
                        audioPlayerHelper.getCurrentAudioId() == audio.conteudo

                if (isPlaying) {
                    // Mostra SeekBar se estiver tocando
                    tvTempoTotal.text = audioPlayerHelper.formatDuration(state.duration)
                    btnPlayPause.setImageResource(R.drawable.ic_pause)
                } else {
                    btnPlayPause.setImageResource(R.drawable.ic_play)
                }
            }

            AudioDownloadState.ERROR -> {
                // Mostra erro e botão de retry
                btnPlayPause.isEnabled = false
                btnPlayPause.setImageResource(R.drawable.ic_play)
                tvDuracao.text = "Erro"
                layoutDownloadProgress.visibility = View.GONE
                layoutDownloadError.visibility = View.VISIBLE

                tvDownloadError.text = state.errorMessage ?: "Erro ao baixar áudio"
            }
        }

        // Força recálculo do layout para ajustar o tamanho
        layoutAudio.post {
            layoutAudio.requestLayout()
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
            "arquivo.bin"
        }

        (tvNomeArquivo as? android.widget.TextView)?.text = nomeCompleto

        btnBaixar.setOnClickListener {
            val nome = anexo.nome ?: "arquivo_${anexo.id}"
            val extensao = anexo.extensao ?: "bin"
            onDownloadClick(anexo.conteudo, nome, extensao)
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

    /**
     * Limpa callbacks quando o adapter é destruído
     */
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        // Remove callbacks de áudio quando a view é reciclada
        when (holder) {
            is MensagemEnviadaViewHolder -> {
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val mensagem = getItem(position)
                    val audio = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_AUDIO }
                    audio?.let {
                        audioPlayerHelper.unregisterStateCallback(it.conteudo)
                    }
                }
            }
            is MensagemRecebidaViewHolder -> {
                val position = holder.adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val mensagem = getItem(position)
                    val audio = mensagem.conteudos.firstOrNull { it.tipo == Conteudo.TIPO_AUDIO }
                    audio?.let {
                        audioPlayerHelper.unregisterStateCallback(it.conteudo)
                    }
                }
            }
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