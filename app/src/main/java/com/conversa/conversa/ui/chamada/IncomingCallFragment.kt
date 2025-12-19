package com.conversa.conversa.ui.chamada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.conversa.conversa.databinding.FragmentIncomingCallBinding

class IncomingCallFragment : Fragment() {

    private var _binding: FragmentIncomingCallBinding? = null
    private val binding get() = _binding!!

    private var listener: IncomingCallListener? = null

    interface IncomingCallListener {
        fun onAceitarChamada()
        fun onRecusarChamada()
        fun getNomeContato(): String
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIncomingCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listener = activity as? IncomingCallListener

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.tvNomeContato.text = listener?.getNomeContato() ?: "Contato"
    }

    private fun setupListeners() {
        binding.btnAceitar.setOnClickListener {
            listener?.onAceitarChamada()
        }

        binding.btnRecusar.setOnClickListener {
            listener?.onRecusarChamada()
        }
    }

    fun desabilitarBotoes() {
        binding.btnAceitar.isEnabled = false
        binding.btnRecusar.isEnabled = false
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
