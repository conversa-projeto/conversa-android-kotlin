package com.conversa.conversa

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.conversa.conversa.adapter.HistoricoChamadasAdapter
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.ChamadaStatus
import com.conversa.conversa.data.model.HistoricoChamada
import com.conversa.conversa.data.model.StatusUsuarioHistorico
import com.conversa.conversa.data.model.TipoAcaoChamada
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityHistoricoChamadasBinding
import com.conversa.conversa.databinding.DialogDetalhesChamadaBinding
import com.conversa.conversa.ui.chamada.ChamadaNavigator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistoricoChamadasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoricoChamadasBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var adapter: HistoricoChamadasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityHistoricoChamadasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            carregarHistorico()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = HistoricoChamadasAdapter { chamada ->
            mostrarDetalhesChamada(chamada)
        }

        binding.rvHistorico.apply {
            adapter = this@HistoricoChamadasActivity.adapter
            addItemDecoration(
                DividerItemDecoration(this@HistoricoChamadasActivity, DividerItemDecoration.VERTICAL)
            )
        }
    }

    private fun setupListeners() {
        binding.btnTentarNovamente.setOnClickListener {
            lifecycleScope.launch {
                carregarHistorico()
            }
        }
    }

    private suspend fun carregarHistorico() {
        try {
            mostrarLoading(true)

            val token = userPreferences.authToken.first()

            if (token.isNullOrEmpty()) {
                mostrarErro("Token nao encontrado. Faca login novamente.")
                return
            }

            val response = RetrofitClient.api.listarHistoricoChamadas("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                val historico = response.body()!!

                if (historico.isEmpty()) {
                    mostrarVazio()
                } else {
                    mostrarHistorico(historico)
                }
            } else {
                val errorMessage = when (response.code()) {
                    401 -> "Sessao expirada"
                    404 -> "Endpoint nao encontrado"
                    500 -> "Erro no servidor"
                    else -> "Erro ao carregar historico: ${response.code()}"
                }
                mostrarErro(errorMessage)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            mostrarErro("Erro de conexao: ${e.message}")
        } finally {
            mostrarLoading(false)
        }
    }

    private fun mostrarDetalhesChamada(chamada: HistoricoChamada) {
        val dialogBinding = DialogDetalhesChamadaBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.apply {
            // Nome do contato
            tvNomeContato.text = chamada.criadoPor

            // Tipo de chamada (icone e texto)
            val tipoAcao = TipoAcaoChamada.fromInt(chamada.tipoAcao)
            val statusChamada = ChamadaStatus.fromInt(chamada.statusChamada)
            val statusUsuario = StatusUsuarioHistorico.fromInt(chamada.statusUsuario)
            val isGrupo = chamada.tipoChamada == 2

            // Avatar diferente para grupo
            if (isGrupo) {
                ivAvatar.setImageResource(R.drawable.ic_group_call)
            } else {
                ivAvatar.setImageResource(R.drawable.ic_person)
            }

            val (iconeRes, tipoChamadaTexto) = when {
                statusChamada == ChamadaStatus.NAO_ATENDIDA -> {
                    Pair(R.drawable.ic_call_missed, "Chamada perdida")
                }
                statusUsuario == StatusUsuarioHistorico.RECUSOU -> {
                    Pair(R.drawable.ic_call_missed, "Chamada recusada")
                }
                statusChamada == ChamadaStatus.CANCELADA -> {
                    Pair(R.drawable.ic_call_missed, "Chamada cancelada")
                }
                statusChamada == ChamadaStatus.RECUSADA -> {
                    Pair(R.drawable.ic_call_missed, "Chamada recusada")
                }
                isGrupo && tipoAcao == TipoAcaoChamada.REALIZADA -> {
                    Pair(R.drawable.ic_group_call, "Chamada em grupo realizada")
                }
                isGrupo && tipoAcao == TipoAcaoChamada.RECEBIDA -> {
                    Pair(R.drawable.ic_group_call, "Chamada em grupo recebida")
                }
                isGrupo -> {
                    Pair(R.drawable.ic_group_call, "Chamada em grupo")
                }
                tipoAcao == TipoAcaoChamada.REALIZADA -> {
                    Pair(R.drawable.ic_call_made, "Chamada realizada")
                }
                tipoAcao == TipoAcaoChamada.RECEBIDA -> {
                    Pair(R.drawable.ic_call_received, "Chamada recebida")
                }
                else -> {
                    Pair(R.drawable.ic_call, "Chamada")
                }
            }
            ivIconeTipo.setImageResource(iconeRes)
            tvTipoChamada.text = tipoChamadaTexto

            // Status da chamada
            val statusTexto = when (statusChamada) {
                ChamadaStatus.PENDENTE -> "Pendente"
                ChamadaStatus.RECUSADA -> "Recusada"
                ChamadaStatus.EM_ANDAMENTO -> "Em andamento"
                ChamadaStatus.ENCERRADA -> "Encerrada"
                ChamadaStatus.NAO_ATENDIDA -> "Nao atendida"
                ChamadaStatus.CANCELADA -> "Cancelada"
                else -> "Desconhecido"
            }
            tvStatusChamada.text = statusTexto

            // Data e hora usando adicionado_em
            val dataHora = chamada.adicionadoEm
            if (dataHora != null) {
                tvDataHora.text = formatarDataHoraCompleta(dataHora)
            } else {
                tvDataHora.text = "-"
            }

            // Duracao
            val duracao = calcularDuracaoCompleta(chamada.iniciada, chamada.finalizada)
            if (duracao != null) {
                layoutDuracao.visibility = View.VISIBLE
                tvDuracao.text = duracao
            } else {
                layoutDuracao.visibility = View.GONE
            }

            // Botao Fechar
            btnFechar.setOnClickListener {
                dialog.dismiss()
            }

            // Botao Ligar
            btnLigar.setOnClickListener {
                dialog.dismiss()
                ChamadaNavigator.iniciarChamada(
                    context = this@HistoricoChamadasActivity,
                    usuarioId = chamada.criadoPorId,
                    nomeExibicao = chamada.criadoPor
                )
            }
        }

        dialog.show()
    }

    private fun formatarDataHoraCompleta(dt: LocalDateTime): String {
        return dt.format(
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'as' HH:mm", Locale("pt", "BR"))
        )
    }

    private fun calcularDuracaoCompleta(iniciada: LocalDateTime?, finalizada: LocalDateTime?): String? {
        if (iniciada == null || finalizada == null) return null

        val duracao = Duration.between(iniciada, finalizada)
        val horas = duracao.toHours()
        val minutos = duracao.toMinutes() % 60
        val segundos = duracao.seconds % 60

        return when {
            horas > 0 -> "$horas hora${if (horas > 1) "s" else ""}, $minutos minuto${if (minutos != 1L) "s" else ""} e $segundos segundo${if (segundos != 1L) "s" else ""}"
            minutos > 0 -> "$minutos minuto${if (minutos != 1L) "s" else ""} e $segundos segundo${if (segundos != 1L) "s" else ""}"
            else -> "$segundos segundo${if (segundos != 1L) "s" else ""}"
        }
    }

    private fun mostrarLoading(mostrar: Boolean) {
        binding.apply {
            progressBar.visibility = if (mostrar) View.VISIBLE else View.GONE
            rvHistorico.visibility = if (mostrar) View.GONE else View.VISIBLE
            layoutVazio.visibility = View.GONE
            layoutErro.visibility = View.GONE
        }
    }

    private fun mostrarHistorico(historico: List<HistoricoChamada>) {
        binding.apply {
            progressBar.visibility = View.GONE
            rvHistorico.visibility = View.VISIBLE
            layoutVazio.visibility = View.GONE
            layoutErro.visibility = View.GONE
        }

        adapter.submitList(historico)
    }

    private fun mostrarVazio() {
        binding.apply {
            progressBar.visibility = View.GONE
            rvHistorico.visibility = View.GONE
            layoutVazio.visibility = View.VISIBLE
            layoutErro.visibility = View.GONE
        }
    }

    private fun mostrarErro(mensagem: String) {
        binding.apply {
            progressBar.visibility = View.GONE
            rvHistorico.visibility = View.GONE
            layoutVazio.visibility = View.GONE
            layoutErro.visibility = View.VISIBLE
            tvMensagemErro.text = mensagem
        }
    }
}
