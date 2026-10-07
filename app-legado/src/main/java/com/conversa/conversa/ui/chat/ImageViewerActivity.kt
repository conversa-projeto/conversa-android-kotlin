package com.conversa.conversa.ui.chat

import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.conversa.conversa.R
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityImageViewerBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Activity para visualizar imagens em tela cheia
 */
class ImageViewerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityImageViewerBinding
    private lateinit var userPreferences: UserPreferences
    private var conteudoId: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        userPreferences = UserPreferences(this)
        
        conteudoId = intent.getStringExtra("conteudo_id").toString()
        val nomeImagem = intent.getStringExtra("nome_imagem") ?: "Imagem"
        
        if (conteudoId == "") {
            Toast.makeText(this, "Erro: ID do conteúdo inválido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        setupToolbar(nomeImagem)
        carregarImagem()
    }
    
    private fun setupToolbar(titulo: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = titulo
        }
    }
    
    private fun carregarImagem() {
        lifecycleScope.launch {
            try {
                val apiUrl = intent.getStringExtra("api_url") ?: return@launch
                val authToken = intent.getStringExtra("auth_token") ?: return@launch
                
                val imageUrl = "${apiUrl}anexo?identificador=${conteudoId}"
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    Glide.with(this@ImageViewerActivity)
                        .load(com.bumptech.glide.load.model.GlideUrl(
                            imageUrl,
                            com.bumptech.glide.load.model.LazyHeaders.Builder()
                                .addHeader("Authorization", "Bearer $authToken")
                                .build()
                        ))
                        .placeholder(R.drawable.ic_person)
                        .error(R.drawable.ic_person)
                        .into(binding.imageView)
                    
                    binding.progressBar.visibility = android.view.View.GONE
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    binding.progressBar.visibility = android.view.View.GONE
                    Toast.makeText(
                        this@ImageViewerActivity,
                        "Erro ao carregar imagem: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
