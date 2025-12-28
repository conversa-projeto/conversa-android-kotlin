package com.conversa.conversa.ui.chamada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

class IncomingCallFragment : Fragment() {

    private var listener: IncomingCallListener? = null
    private var nomeExibicao: String = "Contato"
    private var descricaoExibicao: String = "Chamada Recebida"

    interface IncomingCallListener {
        fun onAceitarChamada()
        fun onRecusarChamada()
        fun getNomeContato(): String
    }

    companion object {
        private const val ARG_NOME = "nome_exibicao"
        private const val ARG_DESCRICAO = "descricao_exibicao"

        fun newInstance(nome: String, descricao: String): IncomingCallFragment {
            return IncomingCallFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NOME, nome)
                    putString(ARG_DESCRICAO, descricao)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        listener = activity as? IncomingCallListener

        // Lê os argumentos passados
        nomeExibicao = arguments?.getString(ARG_NOME) ?: listener?.getNomeContato() ?: "Contato"
        descricaoExibicao = arguments?.getString(ARG_DESCRICAO) ?: "Chamada Recebida"

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                IncomingCallScreen(
                    callerName = nomeExibicao,
                    callerDescription = descricaoExibicao,
                    onAccept = {
                        listener?.onAceitarChamada()
                    },
                    onDecline = {
                        listener?.onRecusarChamada()
                    }
                )
            }
        }
    }

    fun atualizarNome() {
        // O Compose irá recompor automaticamente quando o listener retornar um novo valor
        // Não é mais necessário atualizar manualmente como no View binding
    }
}
