import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.seudominio.conversa.R
import com.seudominio.conversa.databinding.ActivityChatBinding
import com.seudominio.conversa.domain.model.Mensagem
import com.seudominio.conversa.domain.model.TipoConteudo
import com.seudominio.conversa.presentation.chat.adapters.MensagensAdapter
import com.seudominio.conversa.utils.hideKeyboard
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var mensagensAdapter: MensagensAdapter

    private var currentPhotoPath: String? = null

    companion object {
        const val EXTRA_CONVERSA_ID = "conversa_id"
        private const val REQUEST_CAMERA_PERMISSION = 100
        private const val REQUEST_STORAGE_PERMISSION = 101
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { enviarImagem(it) }
    }

    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoPath?.let { path ->
                val uri = Uri.fromFile(File(path))
                enviarImagem(uri)
            }
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { enviarArquivo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val conversaId = intent.getIntExtra(EXTRA_CONVERSA_ID, -1)
        if (conversaId == -1) {
            finish()
            return
        }

        setupToolbar()
        setupRecyclerView()
        setupListeners()
        observeViewModel()

        viewModel.iniciarChat(conversaId)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
    }

    private fun setupRecyclerView() {
        mensagensAdapter = MensagensAdapter(
            onImageClick = { mensagem ->
                // TODO: Abrir visualizador de imagem
            },
            onFileClick = { mensagem ->
                // TODO: Abrir/baixar arquivo
            },
            onMessageLongClick = { mensagem ->
                mostrarOpcoesMsg(mensagem)
            }
        )

        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true

        binding.recyclerViewMessages.apply {
            adapter = mensagensAdapter
            this.layoutManager = layoutManager
        }
    }

    private fun setupListeners() {
        binding.buttonSend.setOnClickListener {
            enviarMensagemTexto()
        }

        binding.buttonAttachment.setOnClickListener {
            mostrarOpcoesAnexo()
        }

        binding.editTextMessage.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.recyclerViewMessages.scrollToPosition(
                    mensagensAdapter.itemCount - 1
                )
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.mensagens.collect { mensagens ->
                mensagensAdapter.submitList(mensagens) {
                    if (mensagens.isNotEmpty()) {
                        binding.recyclerViewMessages.scrollToPosition(
                            mensagens.size - 1
                        )
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.conversa.collect { conversa ->
                conversa?.let {
                    supportActionBar?.title = it.nome ?: "Chat"
                    // TODO: Mostrar foto de perfil e status
                }
            }
        }

        lifecycleScope.launch {
            viewModel.usuarioDigitando.collect { digitando ->
                binding.textViewTyping.visibility =
                    if (digitando) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.enviandoMensagem.collect { enviando ->
                binding.progressBar.visibility =
                    if (enviando) View.VISIBLE else View.GONE
                binding.buttonSend.isEnabled = !enviando
            }
        }

        lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    AlertDialog.Builder(this@ChatActivity)
                        .setTitle("Erro")
                        .setMessage(it)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    private fun enviarMensagemTexto() {
        val texto = binding.editTextMessage.text.toString().trim()
        if (texto.isNotEmpty()) {
            viewModel.enviarMensagem(texto, TipoConteudo.TEXTO)
            binding.editTextMessage.text?.clear()
            hideKeyboard()
        }
    }

    private fun mostrarOpcoesAnexo() {
        val opcoes = arrayOf(
            "Câmera",
            "Galeria",
            "Arquivo"
        )

        AlertDialog.Builder(this)
            .setTitle("Enviar anexo")
            .setItems(opcoes) { _, which ->
                when (which) {
                    0 -> abrirCamera()
                    1 -> abrirGaleria()
                    2 -> abrirSeletorArquivos()
                }
            }
            .show()
    }

    private fun abrirCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CAMERA_PERMISSION
            )
            return
        }

        val photoFile = createImageFile()
        photoFile?.let {
            currentPhotoPath = it.absolutePath
            val photoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                it
            )
            takePictureLauncher.launch(photoUri)
        }
    }

    private fun abrirGaleria() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
            return
        }

        pickImageLauncher.launch("image/*")
    }

    private fun abrirSeletorArquivos() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_STORAGE_PERMISSION
            )
            return
        }

        pickFileLauncher.launch("*/*")
    }

    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }

    private fun enviarImagem(uri: Uri) {
        viewModel.enviarArquivo(uri, TipoConteudo.IMAGEM)
    }

    private fun enviarArquivo(uri: Uri) {
        viewModel.enviarArquivo(uri, TipoConteudo.ARQUIVO)
    }

    private fun mostrarOpcoesMsg(mensagem: Mensagem) {
        val opcoes = mutableListOf("Copiar", "Encaminhar")

        if (mensagem.isMinha) {
            opcoes.add("Excluir para mim")
            opcoes.add("Excluir para todos")
        }

        AlertDialog.Builder(this)
            .setItems(opcoes.toTypedArray()) { _, which ->
                when (opcoes[which]) {
                    "Copiar" -> copiarMensagem(mensagem)
                    "Encaminhar" -> encaminharMensagem(mensagem)
                    "Excluir para mim" -> excluirMensagem(mensagem, false)
                    "Excluir para todos" -> excluirMensagem(mensagem, true)
                }
            }
            .show()
    }

    private fun copiarMensagem(mensagem: Mensagem) {
        // TODO: Implementar cópia para clipboard
    }

    private fun encaminharMensagem(mensagem: Mensagem) {
        // TODO: Implementar encaminhamento
    }

    private fun excluirMensagem(mensagem: Mensagem, paraTodos: Boolean) {
        val titulo = if (paraTodos) "Excluir para todos?" else "Excluir para mim?"

        AlertDialog.Builder(this)
            .setTitle(titulo)
            .setMessage("Esta ação não pode ser desfeita")
            .setPositiveButton("Excluir") { _, _ ->
                viewModel.excluirMensagem(mensagem.id, paraTodos)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_info -> {
                // TODO: Abrir informações da conversa
                true
            }
            R.id.action_search -> {
                // TODO: Pesquisar mensagens
                true
            }
            R.id.action_clear -> {
                confirmarLimparChat()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun confirmarLimparChat() {
        AlertDialog.Builder(this)
            .setTitle("Limpar conversa")
            .setMessage("Deseja excluir todas as mensagens?")
            .setPositiveButton("Limpar") { _, _ ->
                viewModel.limparConversa()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_CAMERA_PERMISSION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    abrirCamera()
                }
            }
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Usuário deverá clicar novamente no botão
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        viewModel.onCleared()
    }
}