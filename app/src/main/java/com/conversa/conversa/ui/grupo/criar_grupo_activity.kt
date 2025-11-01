package com.conversa.conversa

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.conversa.conversa.adapter.ContatosSelecionaveisAdapter
import com.conversa.conversa.data.api.RetrofitClient
import com.conversa.conversa.data.model.Contato
import com.conversa.conversa.data.preferences.UserPreferences
import com.conversa.conversa.databinding.ActivityCriarGrupoBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CriarGrupoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCriarGrupoBinding
    private lateinit var userPreferences: UserPreferences
    private lateinit var adapter: ContatosSelecionaveisAdapter
    private var listaContatos: List<Contato> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCriarGrupoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userPreferences = UserPreferences(this)

        setupToolbar()
        setupRecyclerView()
        setupListeners()

        lifecycleScope.launch {
            carregarContatos()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContatosSelecionaveisAdapter { quantidade ->
            binding.tvContador.text = "$quantidade selecionados"
            binding.btnAvancar.isEnabled = quantidade > 0
        }

        binding.rvContatos.adapter = adapter
    }

    private fun setupListeners() {
        binding.etPesquisar.addTextChangedListener { text ->
            adapter.filtrar(text.toString())
        }

        binding.btnCancelar.setOnClickListener {
            finish()
        }

        binding.btnAvancar.setOnClickListener {
            val contatosSelecionados = adapter.getContatosSelecionados()

            if (contatosSelecionados.isEmpty()) {
                Toast.makeText(this, "Selecione pelo menos 1 contato", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Passa os IDs dos contatos selecionados
            val intent = Intent(this, DetalhesGrupoActivity::class.java)
            intent.putIntegerArrayListExtra("contatos_ids", ArrayList(contatosSelecionados.map { it.id }))
            startActivity(intent)
            finish()
        }
    }

    private suspend fun carregarContatos() {
        try {
            mostrarLoading(true)

            val token = userPreferences.authToken.first()

            if (token.isNullOrEmpty()) {
                mostrarErro()
                return
            }

            val response = RetrofitClient.api.listarContatos("Bearer $token")

            if (response.isSuccessful && response.body() != null) {
                listaContatos = response.body()!!

                if (listaContatos.isEmpty()) {
                    mostrarVazio()
                } else {
                    mostrarContatos()
                }
            } else {
                mostrarErro()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            mostrarErro()
        }
    }

    private fun mostrarLoading(mostrar: Boolean) {
        binding.progressBar.visibility = if (mostrar) View.VISIBLE else View.GONE
        binding.rvContatos.visibility = View.GONE
        binding.tvVazio.visibility = View.GONE
        binding.tvErro.visibility = View.GONE
    }

    private fun mostrarContatos() {
        binding.progressBar.visibility = View.GONE
        binding.rvContatos.visibility = View.VISIBLE
        binding.tvVazio.visibility = View.GONE
        binding.tvErro.visibility = View.GONE

        adapter.setListaCompleta(listaContatos)
    }

    private fun mostrarVazio() {
        binding.progressBar.visibility = View.GONE
        binding.rvContatos.visibility = View.GONE
        binding.tvVazio.visibility = View.VISIBLE
        binding.tvErro.visibility = View.GONE
    }

    private fun mostrarErro() {
        binding.progressBar.visibility = View.GONE
        binding.rvContatos.visibility = View.GONE
        binding.tvVazio.visibility = View.GONE
        binding.tvErro.visibility = View.VISIBLE
    }
}