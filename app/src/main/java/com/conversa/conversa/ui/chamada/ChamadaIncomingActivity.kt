package com.conversa.conversa.ui.chamada

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.R
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.chamada.ChamadaManager
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.data.repository.ChamadaRepository
import com.conversa.conversa.databinding.ActivityChamadaIncomingBinding
import kotlinx.coroutines.launch

class ChamadaIncomingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChamadaIncomingBinding
    private lateinit var repository: ChamadaRepository
    private lateinit var userPreferences: UserPreferences
    
    private var chamadaId: Int = 0
    private var usuarioId: Int = 0
    private var usuarioNome: String = ""

    companion object {
        private const val TAG = "ChamadaIncomingActivity"
        private const val REQUEST_RECORD_AUDIO = 200
        
        const val EXTRA_CHAMADA_ID = "chamada_id"
        const val EXTRA_USUARIO_ID = "usuario_id"
        const val EXTRA_USUARIO_NOME = "usuario_nome"
        
        // Singleton para compartilhar SocketManager com Service
        var sharedSocketManager: com.conversa.conversa.data.socket.SocketManager? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChamadaIncomingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obter dados do intent
        chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, 0)
        usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, 0)
        usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: "Contato"

        if (chamadaId == 0) {
            Toast.makeText(this, "Erro: chamada inválida", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Inicializar componentes
        userPreferences = UserPreferences(this)
        
        // Usa o SocketManager do Service (compartilhado)
        val socketManager = sharedSocketManager
        
        if (socketManager == null) {
            Log.e(TAG, "SocketManager não disponível - Service não iniciado?")
            Toast.makeText(this, "Erro: serviço não disponível", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        repository = ChamadaRepository(
            context = this@ChamadaIncomingActivity,
            api = RetrofitClient.api,
            chamadaManager = ChamadaManager(this@ChamadaIncomingActivity),
            socketManager = socketManager,
            userPreferences = userPreferences
        )

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        binding.tvNomeContato.text = usuarioNome
        binding.tvStatus.text = getString(R.string.chamada_recebida)
    }

    private fun setupListeners() {
        binding.btnAceitar.setOnClickListener {
            verificarPermissoesEAceitar()
        }

        binding.btnRecusar.setOnClickListener {
            recusarChamada()
        }
    }

    private fun verificarPermissoesEAceitar() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        } else {
            aceitarChamada()
        }
    }

    private fun aceitarChamada() {
        lifecycleScope.launch {
            try {
                binding.tvStatus.text = getString(R.string.conectando)
                binding.btnAceitar.isEnabled = false
                binding.btnRecusar.isEnabled = false

                val result = repository.aceitarChamada(chamadaId)

                result.onSuccess {
                    // Navegar para tela de chamada ativa
                    val intent = android.content.Intent(
                        this@ChamadaIncomingActivity,
                        ChamadaActivity::class.java
                    ).apply {
                        putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
                        putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
                        putExtra(ChamadaActivity.EXTRA_IS_INICIADOR, false)
                    }
                    startActivity(intent)
                    finish()
                }

                result.onFailure { erro ->
                    Log.e(TAG, "Erro ao aceitar chamada", erro)
                    Toast.makeText(
                        this@ChamadaIncomingActivity,
                        getString(R.string.erro_aceitar_chamada),
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnAceitar.isEnabled = true
                    binding.btnRecusar.isEnabled = true
                    binding.tvStatus.text = getString(R.string.chamada_recebida)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao aceitar chamada", e)
                Toast.makeText(
                    this@ChamadaIncomingActivity,
                    getString(R.string.erro_aceitar_chamada),
                    Toast.LENGTH_SHORT
                ).show()
                binding.btnAceitar.isEnabled = true
                binding.btnRecusar.isEnabled = true
            }
        }
    }

    private fun recusarChamada() {
        lifecycleScope.launch {
            try {
                binding.btnAceitar.isEnabled = false
                binding.btnRecusar.isEnabled = false

                val result = repository.recusarChamada(chamadaId)

                result.onSuccess {
                    finish()
                }

                result.onFailure { erro ->
                    Log.e(TAG, "Erro ao recusar chamada", erro)
                    Toast.makeText(
                        this@ChamadaIncomingActivity,
                        getString(R.string.erro_recusar_chamada),
                        Toast.LENGTH_SHORT
                    ).show()
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao recusar chamada", e)
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if (grantResults.isNotEmpty() && 
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    aceitarChamada()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.permissao_audio_necessaria),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        // Não permite voltar - precisa aceitar ou recusar
    }
}
