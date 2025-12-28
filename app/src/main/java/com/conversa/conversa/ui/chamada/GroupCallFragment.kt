package com.conversa.conversa.ui.chamada

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.conversa.conversa.R
import com.conversa.conversa.databinding.FragmentGroupCallBinding

class GroupCallFragment : Fragment() {

    private var _binding: FragmentGroupCallBinding? = null
    private val binding get() = _binding!!

    private var listener: GroupCallListener? = null
    private lateinit var participantesAdapter: ParticipantesAdapter
    private var isGridMode: Boolean = false

    interface GroupCallListener {
        fun onEncerrarChamada()
        fun onToggleMute()
        fun onToggleSpeaker()
        fun getNomeGrupo(): String
        fun getTimerText(): String
        fun isMuted(): Boolean
        fun isSpeakerOn(): Boolean
        fun isChamadaConectada(): Boolean
        fun getParticipantes(): List<ParticipanteItem>
        fun onMutarParticipante(participante: ParticipanteItem)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupCallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listener = activity as? GroupCallListener

        setupRecyclerView()
        setupUI()
        setupListeners()
    }

    private fun setupRecyclerView() {
        participantesAdapter = ParticipantesAdapter { participante ->
            listener?.onMutarParticipante(participante)
        }
        binding.rvParticipantes.adapter = participantesAdapter
        binding.rvParticipantes.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupUI() {
        binding.tvNomeGrupo.text = listener?.getNomeGrupo() ?: "Grupo"
        atualizarTimer()
        atualizarBotoes()
        atualizarListaParticipantes()
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

        binding.btnToggleView.setOnClickListener {
            toggleViewMode()
        }
    }

    private fun toggleViewMode() {
        isGridMode = !isGridMode
        participantesAdapter.isGridMode = isGridMode

        if (isGridMode) {
            binding.rvParticipantes.layoutManager = GridLayoutManager(requireContext(), 2)
            binding.btnToggleView.setImageResource(R.drawable.ic_view_list)
        } else {
            binding.rvParticipantes.layoutManager = LinearLayoutManager(requireContext())
            binding.btnToggleView.setImageResource(R.drawable.ic_view_grid)
        }
    }

    fun atualizarNome() {
        binding.tvNomeGrupo.text = listener?.getNomeGrupo() ?: "Grupo"
    }

    fun atualizarTimer() {
        binding.tvTimer.text = listener?.getTimerText() ?: "00:00"
    }

    fun atualizarListaParticipantes() {
        val participantes = listener?.getParticipantes() ?: emptyList()
        participantesAdapter.submitList(participantes)
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
