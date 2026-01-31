package com.conversa.conversa.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.conversa.conversa.R
import com.conversa.conversa.data.model.ChamadaStatus
import com.conversa.conversa.data.model.HistoricoChamada
import com.conversa.conversa.data.model.StatusUsuarioHistorico
import com.conversa.conversa.data.model.TipoAcaoChamada
import com.conversa.conversa.databinding.ItemHistoricoChamadaBinding
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoricoChamadasAdapter(
    private val onChamadaClick: (HistoricoChamada) -> Unit
) : ListAdapter<HistoricoChamada, HistoricoChamadasAdapter.ChamadaViewHolder>(ChamadaDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChamadaViewHolder {
        val binding = ItemHistoricoChamadaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChamadaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChamadaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChamadaViewHolder(
        private val binding: ItemHistoricoChamadaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(chamada: HistoricoChamada) {
            binding.apply {
                // Nome do contato
                tvNomeContato.text = chamada.criadoPor

                // Icone de tipo de chamada baseado em status E tipo_chamada
                val tipoAcao = TipoAcaoChamada.fromInt(chamada.tipoAcao)
                val statusChamada = ChamadaStatus.fromInt(chamada.statusChamada)
                val statusUsuario = StatusUsuarioHistorico.fromInt(chamada.statusUsuario)
                val isGrupo = chamada.tipoChamada == 2

                // Icone de tipo de chamada (perdida/realizada/recebida) - independente de grupo
                val iconeRes = when {
                    statusChamada == ChamadaStatus.NAO_ATENDIDA -> R.drawable.ic_call_missed
                    statusUsuario == StatusUsuarioHistorico.RECUSOU -> R.drawable.ic_call_missed
                    statusChamada == ChamadaStatus.CANCELADA -> R.drawable.ic_call_missed
                    statusChamada == ChamadaStatus.RECUSADA -> R.drawable.ic_call_missed
                    tipoAcao == TipoAcaoChamada.REALIZADA -> R.drawable.ic_call_made
                    tipoAcao == TipoAcaoChamada.RECEBIDA -> R.drawable.ic_call_received
                    else -> R.drawable.ic_call
                }
                ivIconeTipo.setImageResource(iconeRes)

                // Avatar diferente para grupo
                if (isGrupo) {
                    ivAvatar.setImageResource(R.drawable.ic_group_call)
                } else {
                    ivAvatar.setImageResource(R.drawable.ic_person)
                }

                // Data/hora usando adicionado_em (ja convertido para local pelo deserializer)
                val dt = chamada.adicionadoEm
                if (dt != null) {
                    val (dataFormatada, horaFormatada) = formatarDataHora(dt)
                    tvDetalhes.text = "$dataFormatada, $horaFormatada"
                    tvHora.text = horaFormatada
                } else {
                    tvDetalhes.text = ""
                    tvHora.text = ""
                }

                // Duracao
                tvDuracao.text = calcularDuracao(chamada.iniciada, chamada.finalizada)

                // Click listener
                root.setOnClickListener {
                    onChamadaClick(chamada)
                }
            }
        }

        private fun formatarDataHora(dt: LocalDateTime): Pair<String, String> {
            val hoje = LocalDateTime.now()

            val dataFormatada = when {
                dt.toLocalDate() == hoje.toLocalDate() -> "Hoje"
                dt.toLocalDate() == hoje.minusDays(1).toLocalDate() -> "Ontem"
                dt.toLocalDate().isAfter(hoje.minusDays(7).toLocalDate()) -> {
                    dt.dayOfWeek.getDisplayName(
                        java.time.format.TextStyle.FULL,
                        Locale("pt", "BR")
                    ).replaceFirstChar { it.uppercase() }
                }
                else -> dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }

            val horaFormatada = dt.format(DateTimeFormatter.ofPattern("HH:mm"))

            return Pair(dataFormatada, horaFormatada)
        }

        private fun calcularDuracao(iniciada: LocalDateTime?, finalizada: LocalDateTime?): String {
            if (iniciada == null || finalizada == null) return ""

            val duracao = Duration.between(iniciada, finalizada)
            val minutos = duracao.toMinutes()
            val segundos = duracao.seconds % 60

            return if (minutos > 0) {
                String.format("%d:%02d", minutos, segundos)
            } else {
                String.format("0:%02d", segundos)
            }
        }
    }

    class ChamadaDiffCallback : DiffUtil.ItemCallback<HistoricoChamada>() {
        override fun areItemsTheSame(oldItem: HistoricoChamada, newItem: HistoricoChamada): Boolean {
            return oldItem.chamadaId == newItem.chamadaId
        }

        override fun areContentsTheSame(oldItem: HistoricoChamada, newItem: HistoricoChamada): Boolean {
            return oldItem == newItem
        }
    }
}
