package com.conversa.conversa.ui.chamada

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.conversa.conversa.databinding.ActivityChamadaIncomingBinding

/**
 * Activity simplificada que apenas redireciona para ChamadaActivity
 * Mantida para compatibilidade com notificações e sistema de chamadas
 */
class ChamadaIncomingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChamadaIncomingBinding

    companion object {
        private const val TAG = "ChamadaIncomingActivity"
        
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
        val chamadaId = intent.getIntExtra(EXTRA_CHAMADA_ID, 0)
        val usuarioId = intent.getIntExtra(EXTRA_USUARIO_ID, 0)
        val usuarioNome = intent.getStringExtra(EXTRA_USUARIO_NOME) ?: "Contato"

        // Redireciona para ChamadaActivity com flag isIncoming=true
        val intentChamada = Intent(this, ChamadaActivity::class.java).apply {
            putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, usuarioNome)
            putExtra(ChamadaActivity.EXTRA_IS_INCOMING, true) // MARCA COMO INCOMING
        }
        
        startActivity(intentChamada)
        finish() // Fecha essa Activity imediatamente
    }

    override fun onBackPressed() {
        // Não permite voltar
    }
}
