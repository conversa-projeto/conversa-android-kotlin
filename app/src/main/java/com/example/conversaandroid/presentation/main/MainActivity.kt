import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.seudominio.conversa.R
import com.seudominio.conversa.databinding.ActivityMainBinding
import com.seudominio.conversa.domain.model.Conversa
import com.seudominio.conversa.presentation.chat.ChatActivity
import com.seudominio.conversa.presentation.login.LoginActivity
import com.seudominio.conversa.presentation.main.adapters.ConversasAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var conversasAdapter: ConversasAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        // Carregar conversas
        viewModel.carregarConversas()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Conversas"
    }

    private fun setupRecyclerView() {
        conversasAdapter = ConversasAdapter(
            onConversaClick = { conversa ->
                abrirChat(conversa)
            },
            onConversaLongClick = { conversa ->
                mostrarOpcoesConversa(conversa)
            }
        )

        binding.recyclerViewConversas.apply {
            adapter = conversasAdapter
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
    }

    private fun setupListeners() {
        binding.fabNovaConversa.setOnClickListener {
            mostrarDialogoNovaConversa()
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.carregarConversas()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.conversas.collect { conversas ->
                conversasAdapter.submitList(conversas)
                binding.swipeRefreshLayout.isRefreshing = false

                if (conversas.isEmpty()) {
                    binding.textViewEmpty.visibility = View.VISIBLE
                    binding.recyclerViewConversas.visibility = View.GONE
                } else {
                    binding.textViewEmpty.visibility = View.GONE
                    binding.recyclerViewConversas.visibility = View.VISIBLE
                }
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Erro")
                        .setMessage(it)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun abrirChat(conversa: Conversa) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_CONVERSA_ID, conversa.id)
        }
        startActivity(intent)
    }

    private fun mostrarOpcoesConversa(conversa: Conversa) {
        val opcoes = arrayOf(
            "Abrir",
            "Silenciar",
            "Arquivar",
            "Excluir"
        )

        AlertDialog.Builder(this)
            .setTitle(conversa.nome ?: "Conversa")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> abrirChat(conversa)
                    1 -> silenciarConversa(conversa)
                    2 -> arquivarConversa(conversa)
                    3 -> confirmarExclusaoConversa(conversa)
                }
            }
            .show()
    }

    private fun silenciarConversa(conversa: Conversa) {
        viewModel.silenciarConversa(conversa.id)
    }

    private fun arquivarConversa(conversa: Conversa) {
        viewModel.arquivarConversa(conversa.id)
    }

    private fun confirmarExclusaoConversa(conversa: Conversa) {
        AlertDialog.Builder(this)
            .setTitle("Excluir conversa")
            .setMessage("Tem certeza que deseja excluir esta conversa?")
            .setPositiveButton("Excluir") { _, _ ->
                viewModel.excluirConversa(conversa.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoNovaConversa() {
        // TODO: Implementar dialog para criar nova conversa
        // Pode ser uma lista de contatos ou campo para buscar usuários
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_perfil -> {
                // TODO: Abrir tela de perfil
                true
            }
            R.id.action_configuracoes -> {
                // TODO: Abrir configurações
                true
            }
            R.id.action_sair -> {
                confirmarSair()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmarSair() {
        AlertDialog.Builder(this)
            .setTitle("Sair")
            .setMessage("Deseja realmente sair?")
            .setPositiveButton("Sair") { _, _ ->
                viewModel.logout()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}