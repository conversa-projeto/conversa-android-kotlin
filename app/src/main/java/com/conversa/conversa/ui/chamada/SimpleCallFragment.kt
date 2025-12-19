package com.conversa.conversa.ui.chamada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.conversa.conversa.R
import com.conversa.conversa.databinding.FragmentSimpleCallBinding

class SimpleCallFragment : Fragment() {

    private var _binding: FragmentSimpleCallBinding? = null
    private val binding get() = _binding!!

    private var listener: SimpleCallListener? = null

    interface SimpleCallListener {
        fun onEncerrarChamada()
        fun onToggleMute()
        fun onToggleSpeaker()
        fun getNomeContato(): String
        fun getTimerText(): String
        fun isMuted(): Boolean
        fun isSpeakerOn(): Boolean
        fun isChamadaConectada(): Boolean
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSimpleCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listener = activity as? SimpleCallListener

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.tvNomeContato.text = listener?.getNomeContato() ?: "Contato"
        atualizarTimer()
        atualizarBotoes()
    }

    private fun setupListeners() {
        binding.btnEncerrarChamada.setOnClickListener {
            listener?.onEncerrarChamada()
        }

        binding.btnMute.setOnClickListener {
            listener?.onToggleMute()
            atualizarBotaoMute()
        }

        binding.btnSpeaker.setOnClickListener {
            listener?.onToggleSpeaker()
            atualizarBotaoSpeaker()
        }
    }

    fun atualizarTimer() {
        binding.tvTimer.text = listener?.getTimerText() ?: "00:00"
    }

    fun atualizarBotoes() {
        val habilitado = listener?.isChamadaConectada() ?: false

        binding.btnMute.isEnabled = habilitado
        binding.btnSpeaker.isEnabled = habilitado

        binding.btnMute.alpha = if (habilitado) 1.0f else 0.5f
        binding.btnSpeaker.alpha = if (habilitado) 1.0f else 0.5f

        atualizarBotaoMute()
        atualizarBotaoSpeaker()
    }

    private fun atualizarBotaoMute() {
        val isMuted = listener?.isMuted() ?: false
        if (isMuted) {
            binding.btnMute.setImageResource(R.drawable.ic_mic_off)
            binding.tvMuteLabel.text = "Mudo"
        } else {
            binding.btnMute.setImageResource(R.drawable.ic_mic_on)
            binding.tvMuteLabel.text = "Mudo"
        }
    }

    private fun atualizarBotaoSpeaker() {
        val isSpeakerOn = listener?.isSpeakerOn() ?: false
        if (isSpeakerOn) {
            binding.btnSpeaker.setImageResource(R.drawable.ic_volume_up)
            binding.tvSpeakerLabel.text = getString(R.string.alto_falante)
        } else {
            binding.btnSpeaker.setImageResource(R.drawable.ic_volume_off)
            binding.tvSpeakerLabel.text = getString(R.string.alto_falante)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
