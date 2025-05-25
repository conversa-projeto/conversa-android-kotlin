// com.example.conversa.main/MainActivity.kt
package com.example.conversa.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat // Importe WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.adapter.ContactAdapter
import com.example.conversa.adapter.ConversationAdapter
import com.example.conversa.chat.ChatActivity
import com.example.conversa.model.Contact
import com.example.conversa.model.Conversation
import com.example.conversa.utils.MainViewModelFactory

class MainActivity : AppCompatActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var recyclerViewConversations: RecyclerView
    private lateinit var conversationAdapter: ConversationAdapter
    private val conversations = mutableListOf<Conversation>()

    private lateinit var recyclerViewContacts: RecyclerView
    private lateinit var contactAdapter: ContactAdapter
    private val contacts = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // IMPORTANTE: Definir antes de setContentView para que o layout se estenda
        // Isso permite que o app desenhe sob a barra de status e barra de navegação
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Aplicar insets de topo (status bar) à Toolbar
        // E insets de fundo (navigation bar) ao RecyclerView de contatos
        val rootView = findViewById<View>(android.R.id.content) // Obtém a view raiz do conteúdo da Activity
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Aplica padding ao topo da Toolbar para a barra de status
            toolbar.setPadding(
                toolbar.paddingLeft,
                systemBarsInsets.top + toolbar.paddingTop, // Adiciona o padding da barra de status
                toolbar.paddingRight,
                toolbar.paddingBottom
            )

            // Opcional: Se o RecyclerView de contatos estiver sendo coberto pela barra de navegação inferior,
            // aplique padding inferior a ele.
            // recyclerViewContacts.setPadding(
            //     recyclerViewContacts.paddingLeft,
            //     recyclerViewContacts.paddingTop,
            //     recyclerViewContacts.paddingRight,
            //     systemBarsInsets.bottom + recyclerViewContacts.paddingBottom
            // )

            // Retorna os insets para que outras views possam consumi-los se necessário
            insets
        }


        // Inicializa o ViewModel usando o Factory
        val factory = MainViewModelFactory(applicationContext)
        mainViewModel = ViewModelProvider(this, factory).get(MainViewModel::class.java)

        // Define o título da Toolbar com o nome do usuário logado
        supportActionBar?.title = "Bem vindo, ${mainViewModel.getUserName()}"

        // --- Configuração do RecyclerView de Conversas ---
        recyclerViewConversations = findViewById(R.id.recyclerViewConversations)
        recyclerViewConversations.layoutManager = LinearLayoutManager(this)
        conversationAdapter = ConversationAdapter(conversations) { conversation ->
            // Lógica para clicar em uma conversa existente
            navigateToChat(conversation.id, conversation.nome)
        }
        recyclerViewConversations.adapter = conversationAdapter

        // --- Configuração do RecyclerView de Contatos ---
        recyclerViewContacts = findViewById(R.id.recyclerViewContacts)
        recyclerViewContacts.layoutManager = LinearLayoutManager(this)
        contactAdapter = ContactAdapter(contacts) { contact ->
            // Lógica para clicar em um contato para iniciar uma nova conversa
            mainViewModel.createNewConversation(contact)
        }
        recyclerViewContacts.adapter = contactAdapter

        // --- Observadores do ViewModel ---
        mainViewModel.conversations.observe(this) { fetchedConversations ->
            conversations.clear()
            conversations.addAll(fetchedConversations)
            conversationAdapter.notifyDataSetChanged()
        }

        mainViewModel.contacts.observe(this) { fetchedContacts ->
            contacts.clear()
            contacts.addAll(fetchedContacts)
            contactAdapter.notifyDataSetChanged()
        }

        mainViewModel.uiState.observe(this) { state ->
            when (state) {
                MainViewModel.MainUiState.Loading -> {
                    // Opcional: Mostrar um ProgressBar
                }
                MainViewModel.MainUiState.Success -> {
                    // Opcional: Esconder ProgressBar
                }
                is MainViewModel.MainUiState.Error -> {
                    Toast.makeText(this, "Erro: ${state.message}", Toast.LENGTH_LONG).show()
                }
                MainViewModel.MainUiState.Initial -> { /* Não faz nada */ }
            }
        }

        mainViewModel.navigateToChat.observe(this) { pair ->
            pair?.let { (chatId, contactName) ->
                navigateToChat(chatId, contactName)
                mainViewModel.doneNavigatingToChat()
            }
        }
    }

    private fun navigateToChat(chatId: Int, chatName: String) {
        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("chatId", chatId)
            putExtra("contactName", chatName)
        }
        startActivity(intent)
    }
}