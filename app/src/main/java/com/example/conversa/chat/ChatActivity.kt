package com.example.conversa.chat

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.conversa.R
import com.example.conversa.model.Message // Importe o modelo de domínio Message
import com.example.conversa.utils.ChatViewModelFactory

class ChatActivity : AppCompatActivity() {

    private val TAG = "ChatActivity"

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var chatAdapter: ChatAdapter // Declaração do adaptador
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSendMessage: ImageButton
    private lateinit var buttonSendAudio: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Habilitar o botão de "voltar" na Toolbar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                systemBarsInsets.top,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        val chatId = intent.getIntExtra("chatId", -1)
        val contactName = intent.getStringExtra("contactName")

        if (chatId == -1 || contactName.isNullOrBlank()) {
            Toast.makeText(this, "Erro: Dados da conversa não encontrados.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        supportActionBar?.title = contactName

        val factory = ChatViewModelFactory(applicationContext, chatId, contactName)
        chatViewModel = ViewModelProvider(this, factory).get(ChatViewModel::class.java)
        chatViewModel.setChatInfo(chatId, contactName)

        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSendMessage = findViewById(R.id.buttonSendMessage)
        buttonSendAudio = findViewById(R.id.buttonSendAudio)

        recyclerViewMessages.layoutManager = LinearLayoutManager(this)

        // INICIALIZAÇÃO CORRETA DO ADAPTER:
        chatAdapter = ChatAdapter(mutableListOf(), chatViewModel.currentUserId) // Passe o ID do usuário logado
        recyclerViewMessages.adapter = chatAdapter

        chatViewModel.messages.observe(this) { messages ->
            Log.d(TAG, "LiveData 'messages' observado em ChatActivity. Total: ${messages.size} mensagens.")
            if (messages.isNotEmpty()) {
                Log.d(TAG, "Primeira mensagem no LiveData (Activity): ID=${messages.first().id}, Conteúdo='${messages.first().content}', isMine=${messages.first().isMine}")
            }
            // Atualiza o adaptador com as novas mensagens
            chatAdapter.updateMessages(messages)
            // Rola para a última mensagem, se houver
            if (messages.isNotEmpty()) {
                recyclerViewMessages.scrollToPosition(messages.size - 1)
            }
        }

        chatViewModel.uiState.observe(this) { state ->
            when (state) {
                ChatViewModel.ChatUiState.Loading -> {
                    // Opcional: mostrar um ProgressBar
                    Log.d(TAG, "UI State: Loading")
                }
                ChatViewModel.ChatUiState.MessageSent -> {
                    editTextMessage.text.clear()
                    Log.d(TAG, "UI State: MessageSent")
                    // A mensagem já foi adicionada localmente, e loadMessages será chamado em breve
                }
                is ChatViewModel.ChatUiState.Error -> {
                    Toast.makeText(this, "Erro: ${state.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "UI State: Error: ${state.message}")
                }
                ChatViewModel.ChatUiState.Initial, ChatViewModel.ChatUiState.Success -> {
                    // Opcional: esconder ProgressBar
                    Log.d(TAG, "UI State: Initial/Success")
                }
            }
        }

        buttonSendMessage.setOnClickListener {
            val messageContent = editTextMessage.text.toString()
            chatViewModel.sendMessage(messageContent)
        }

        buttonSendAudio.setOnClickListener {
            Toast.makeText(this, "Funcionalidade de áudio a ser implementada!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}