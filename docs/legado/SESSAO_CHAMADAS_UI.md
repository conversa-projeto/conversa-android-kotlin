# 📞 Implementação de Chamadas - Resumo da Sessão

## ✅ O que foi Implementado

### 1. Recursos Visuais (Drawables)
- ✅ `ic_call.xml` - Ícone de telefone
- ✅ `ic_call_end.xml` - Ícone de desligar (vermelho)
- ✅ `ic_mic_on.xml` - Ícone de microfone ligado
- ✅ `ic_mic_off.xml` - Ícone de microfone desligado (vermelho)
- ✅ `ic_volume_up.xml` - Ícone de alto-falante ligado
- ✅ `ic_volume_off.xml` - Ícone de alto-falante desligado
- ✅ `bg_btn_accept_call.xml` - Background verde para aceitar
- ✅ `bg_btn_reject_call.xml` - Background vermelho para recusar
- ✅ `bg_btn_call_control.xml` - Background cinza para controles

### 2. Layouts
- ✅ `activity_chamada_incoming.xml` - Tela de chamada recebida
  - Avatar do contato
  - Nome e status
  - Botões Aceitar/Recusar
  
- ✅ `activity_chamada.xml` - Tela de chamada ativa
  - Avatar do contato
  - Nome e timer
  - Botões Mute/Speaker
  - Botão Encerrar

### 3. Strings
- ✅ Todas as strings necessárias em `strings.xml`
  - Títulos e labels
  - Mensagens de erro
  - Textos dos botões

### 4. Activities

#### ChamadaIncomingActivity
**Funcionalidades implementadas:**
- ✅ Recebe intent com dados da chamada (ID, usuário, nome)
- ✅ Exibe informações do chamador
- ✅ Verifica permissão de áudio antes de aceitar
- ✅ Integra com ChamadaRepository para aceitar/recusar
- ✅ Navega para ChamadaActivity ao aceitar
- ✅ Bloqueia botão "voltar" (força decisão)

#### ChamadaActivity
**Funcionalidades implementadas:**
- ✅ Gerencia chamada ativa
- ✅ Timer da chamada (contador de tempo)
- ✅ Toggle Mute (com atualização visual)
- ✅ Toggle Speaker (com AudioManager)
- ✅ Botão encerrar chamada
- ✅ Callbacks para eventos da chamada
- ✅ Bloqueia botão "voltar"
- ✅ Restaura configurações de áudio ao sair

### 5. AndroidManifest
- ✅ Permissões adicionadas:
  - `MODIFY_AUDIO_SETTINGS`
  - `POST_NOTIFICATIONS`
- ✅ Activities registradas:
  - `ChamadaIncomingActivity` com flags especiais
  - `ChamadaActivity` com orientação portrait

---

## 🔄 Próximos Passos Críticos

### Fase 1: Integração com ChatActivity (URGENTE)

Adicionar botão de chamada na tela de chat:

**Arquivo:** `ChatActivity.kt`

```kotlin
// No layout activity_chat.xml, adicionar:
<ImageButton
    android:id="@+id/btnLigar"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:src="@drawable/ic_call"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="@string/iniciar_chamada" />

// No código Kotlin:
private fun setupChamadaButton() {
    binding.btnLigar.setOnClickListener {
        iniciarChamada()
    }
}

private fun iniciarChamada() {
    lifecycleScope.launch {
        try {
            val destinatarioId = conversaAtual.destinatarioId ?: return@launch
            
            // Verificar permissão de áudio
            if (ContextCompat.checkSelfPermission(
                    this@ChatActivity,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this@ChatActivity,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    REQUEST_RECORD_AUDIO
                )
                return@launch
            }
            
            val repository = ChamadaRepository(
                context = this@ChatActivity,
                api = RetrofitClient.api,
                chamadaManager = ChamadaManager(this@ChatActivity),
                webSocketManager = mainActivity.webSocketManager, // Precisa passar referência
                userPreferences = userPreferences
            )
            
            val result = repository.iniciarChamada(listOf(destinatarioId))
            
            result.onSuccess { chamada ->
                val intent = Intent(this@ChatActivity, ChamadaActivity::class.java).apply {
                    putExtra(ChamadaActivity.EXTRA_CHAMADA_ID, chamada.id)
                    putExtra(ChamadaActivity.EXTRA_USUARIO_NOME, conversaAtual.nome)
                    putExtra(ChamadaActivity.EXTRA_IS_INICIADOR, true)
                }
                startActivity(intent)
            }
            
            result.onFailure { erro ->
                Toast.makeText(
                    this@ChatActivity,
                    "Erro ao iniciar chamada: ${erro.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar chamada", e)
        }
    }
}
```

### Fase 2: Integração com MainActivity (URGENTE)

Inicializar WebSocket e tratar chamadas recebidas:

**Arquivo:** `MainActivity.kt`

```kotlin
class MainActivity : AppCompatActivity() {
    
    lateinit var webSocketManager: WebSocketManager
    private lateinit var userPreferences: UserPreferences
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        userPreferences = UserPreferences(this)
        webSocketManager = WebSocketManager(this)
        
        // Configurar callback de chamada recebida
        webSocketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
            mostrarTelaChamadaRecebida(chamadaId, usuarioId, usuarioNome)
        }
        
        // Conectar ao WebSocket após login
        lifecycleScope.launch {
            val token = userPreferences.getToken().firstOrNull()
            val apiUrl = userPreferences.getApiUrl().firstOrNull()
            
            if (token != null && apiUrl != null) {
                val wsUrl = apiUrl.replace("http", "ws")
                webSocketManager.conectar(wsUrl, token)
            }
        }
    }
    
    private fun mostrarTelaChamadaRecebida(
        chamadaId: Int, 
        usuarioId: Int, 
        usuarioNome: String
    ) {
        val intent = Intent(this, ChamadaIncomingActivity::class.java).apply {
            putExtra(ChamadaIncomingActivity.EXTRA_CHAMADA_ID, chamadaId)
            putExtra(ChamadaIncomingActivity.EXTRA_USUARIO_ID, usuarioId)
            putExtra(ChamadaIncomingActivity.EXTRA_USUARIO_NOME, usuarioNome)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        webSocketManager.desconectar()
    }
}
```

### Fase 3: Melhorias na UI

#### 3.1. Adicionar Botão no Layout do Chat
**Arquivo:** `activity_chat.xml`

```xml
<!-- Adicionar ao lado do botão de enviar mensagem -->
<ImageButton
    android:id="@+id/btnLigar"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:layout_marginEnd="8dp"
    android:src="@drawable/ic_call"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="@string/iniciar_chamada"
    app:tint="@color/colorPrimary" />
```

#### 3.2. Vibração ao Receber Chamada
**Adicionar em ChamadaIncomingActivity:**

```kotlin
private fun vibrarTelefone() {
    val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 500, 250, 500),
                0
            )
        )
    } else {
        vibrator.vibrate(longArrayOf(0, 500, 250, 500), 0)
    }
}

override fun onDestroy() {
    super.onDestroy()
    val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
    vibrator.cancel()
}
```

#### 3.3. Som de Toque (Opcional)
**Adicionar MediaPlayer para tocar ringtone:**

```kotlin
private var mediaPlayer: MediaPlayer? = null

private fun tocarRingtone() {
    try {
        val notification = RingtoneManager.getDefaultUri(
            RingtoneManager.TYPE_RINGTONE
        )
        mediaPlayer = MediaPlayer.create(this, notification).apply {
            isLooping = true
            start()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Erro ao tocar ringtone", e)
    }
}

override fun onDestroy() {
    super.onDestroy()
    mediaPlayer?.stop()
    mediaPlayer?.release()
    mediaPlayer = null
}
```

---

## 🐛 Possíveis Problemas e Soluções

### 1. Erro de Compilação - ViewBinding
**Problema:** `ActivityChamadaBinding` não encontrado

**Solução:** Verificar se ViewBinding está habilitado em `build.gradle`:
```gradle
android {
    buildFeatures {
        viewBinding true
    }
}
```

### 2. Permissões não Funcionam
**Problema:** Permissão RECORD_AUDIO negada

**Solução:** 
- Verificar se permissão está no AndroidManifest
- Verificar se está solicitando em runtime
- Testar em dispositivo físico (emulador pode ter problemas)

### 3. WebSocket não Conecta
**Problema:** Chamadas recebidas não aparecem

**Solução:**
- Verificar se WebSocketManager está inicializado no MainActivity
- Verificar URL do WebSocket (deve ser ws:// não http://)
- Verificar se token JWT está válido
- Ver logs do WebSocketManager

### 4. Áudio não Funciona
**Problema:** Não consegue ouvir ou falar

**Solução:**
- Verificar se ChamadaManager está sendo inicializado corretamente
- Verificar se servidor TCP está acessível (porta 9090)
- Ver logs do ChamadaManager
- Testar em dispositivo físico real

---

## 📋 Checklist de Teste

### Teste 1: Iniciar Chamada
- [ ] Abrir chat com contato
- [ ] Clicar em botão de ligar
- [ ] Verificar se solicita permissão de áudio
- [ ] Verificar se API é chamada
- [ ] Verificar se abre tela de chamada
- [ ] Verificar se outro usuário recebe notificação

### Teste 2: Receber Chamada
- [ ] Receber chamada de outro usuário
- [ ] Verificar se tela de chamada aparece
- [ ] Verificar nome do contato
- [ ] Clicar em aceitar
- [ ] Verificar se solicita permissão
- [ ] Verificar se abre tela de chamada ativa

### Teste 3: Chamada Ativa
- [ ] Verificar se timer inicia
- [ ] Testar botão mute (deve desligar mic)
- [ ] Testar botão speaker (deve alternar áudio)
- [ ] Testar botão encerrar
- [ ] Verificar se chamada é encerrada no servidor

### Teste 4: Fluxo Completo
- [ ] A liga para B
- [ ] B recebe notificação
- [ ] B aceita
- [ ] Ambos conversam
- [ ] A encerra
- [ ] B é notificado do encerramento
- [ ] Ambos voltam ao chat

---

## 📊 Status Atual

| Componente | Status | Pronto para Teste |
|------------|--------|-------------------|
| Layouts | ✅ Completo | Sim |
| Drawables | ✅ Completo | Sim |
| Strings | ✅ Completo | Sim |
| ChamadaIncomingActivity | ✅ Completo | Sim |
| ChamadaActivity | ✅ Completo | Sim |
| AndroidManifest | ✅ Atualizado | Sim |
| Integração ChatActivity | ⏳ Pendente | Não |
| Integração MainActivity | ⏳ Pendente | Não |
| WebSocket no MainActivity | ⏳ Pendente | Não |

---

## 🎯 Próxima Sessão

Na próxima sessão de desenvolvimento, focar em:

1. **Adicionar botão de chamada no ChatActivity**
2. **Inicializar WebSocket no MainActivity**
3. **Testar fluxo completo de chamada**
4. **Corrigir bugs encontrados**
5. **Adicionar melhorias na UI (vibração, som)**

---

**Data:** 01/11/2025 - 23:30  
**Status:** Interface de chamadas implementada - Aguardando integração  
**Próximo passo:** Integrar com MainActivity e ChatActivity
