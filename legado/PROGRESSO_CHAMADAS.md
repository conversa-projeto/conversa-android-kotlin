# 📞 Progresso da Implementação do Fluxo de Chamadas

## ✅ Componentes Implementados

### 1. ChamadaManager (COMPLETO)
**Localização:** `app/src/main/java/com/conversa/conversa/data/chamada/ChamadaManager.kt`

**Funcionalidades:**
- ✅ Conexão TCP com o servidor (porta 9090)
- ✅ Registro do cliente no servidor
- ✅ Captura de áudio do microfone (AudioRecord)
- ✅ Envio de áudio para o servidor
- ✅ Recepção de áudio do servidor
- ✅ Reprodução de áudio (AudioTrack)
- ✅ Mixing de múltiplos streams (para chamadas em grupo)
- ✅ Gerenciamento completo do ciclo de vida da chamada

### 2. WebSocketManager (NOVO - COMPLETO)
**Localização:** `app/src/main/java/com/conversa/conversa/data/websocket/WebSocketManager.kt`

**Funcionalidades:**
- ✅ Conexão e manutenção do WebSocket
- ✅ Autenticação via JWT
- ✅ Processamento de eventos de chamada:
  - Chamada recebida (tipo 51)
  - Chamada finalizada (tipo 52)
  - Usuário recusou (tipo 53)
  - Usuário entrou (tipo 54)
  - Usuário saiu (tipo 55)
- ✅ Reconexão automática (até 5 tentativas)
- ✅ Callbacks configuráveis para eventos

### 3. ChamadaRepository (NOVO - COMPLETO)
**Localização:** `app/src/main/java/com/conversa/conversa/data/repository/ChamadaRepository.kt`

**Funcionalidades:**
- ✅ Integração entre API REST, WebSocket e ChamadaManager
- ✅ Iniciar chamada
- ✅ Aceitar chamada recebida
- ✅ Recusar chamada
- ✅ Finalizar chamada
- ✅ Obter dados da chamada
- ✅ Gerenciamento de estado da chamada atual
- ✅ Callbacks configuráveis

### 4. Modelos de Dados (COMPLETO)
**Localização:** `app/src/main/java/com/conversa/conversa/data/model/Chamada.kt`

**Classes criadas:**
- ✅ IniciarChamadaRequest
- ✅ ChamadaResponse
- ✅ UsuarioChamada
- ✅ ChamadaStatus
- ✅ UsuarioChamadaStatus
- ✅ EstadoChamadaLocal
- ✅ ChamadaIdRequest
- ✅ UsuarioIdDto

### 5. API Endpoints (COMPLETO)
**Localização:** `app/src/main/java/com/conversa/conversa/data/api/ConversaApi.kt`

**Endpoints adicionados:**
- ✅ PUT /chamada/iniciar
- ✅ POST /chamada/entrar
- ✅ POST /chamada/recusar
- ✅ POST /chamada/sair
- ✅ POST /chamada/cancelar
- ✅ POST /chamada/finalizar
- ✅ GET /chamada/dados

---

## 🔄 Próximos Passos

### Fase 1: Activities de Chamada (PENDENTE)

#### 1.1. ChamadaActivity
**Localização:** `app/src/main/java/com/conversa/conversa/ui/chamada/ChamadaActivity.kt`

**Funcionalidades necessárias:**
- Exibir informações da chamada (nome do contato, tempo)
- Botões: Mute, Speaker, Finalizar
- Indicador visual de quem está falando
- Lista de participantes (para chamadas em grupo)
- Timer da chamada
- Estado visual da chamada (conectando, conectado, finalizando)

**Layout necessário:**
- `activity_chamada.xml`

#### 1.2. ChamadaIncomingActivity
**Localização:** `app/src/main/java/com/conversa/conversa/ui/chamada/ChamadaIncomingActivity.kt`

**Funcionalidades necessárias:**
- Exibir quem está chamando
- Botões: Aceitar / Recusar
- Som de toque (opcional)
- Animação visual atraente

**Layout necessário:**
- `activity_chamada_incoming.xml`

### Fase 2: Integração com MainActivity (PENDENTE)

#### 2.1. Inicializar WebSocket no Login
**Arquivo:** `MainActivity.kt`

```kotlin
private lateinit var webSocketManager: WebSocketManager

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    
    // Inicializar WebSocket
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

private fun mostrarTelaChamadaRecebida(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
    val intent = Intent(this, ChamadaIncomingActivity::class.java).apply {
        putExtra("chamada_id", chamadaId)
        putExtra("usuario_id", usuarioId)
        putExtra("usuario_nome", usuarioNome)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    startActivity(intent)
}

override fun onDestroy() {
    super.onDestroy()
    webSocketManager.cleanup()
}
```

#### 2.2. Adicionar Botão de Chamada no ChatActivity
**Arquivo:** `ChatActivity.kt`

```kotlin
private fun setupChamadaButton() {
    binding.btnLigar.setOnClickListener {
        iniciarChamada()
    }
}

private fun iniciarChamada() {
    lifecycleScope.launch {
        try {
            val destinatarioId = intent.getIntExtra("destinatario_id", 0)
            if (destinatarioId == 0) return@launch
            
            val repository = ChamadaRepository(
                context = this@ChatActivity,
                api = RetrofitClient.api,
                chamadaManager = ChamadaManager(this@ChatActivity),
                webSocketManager = webSocketManager,
                userPreferences = userPreferences
            )
            
            val result = repository.iniciarChamada(listOf(destinatarioId))
            
            result.onSuccess { chamada ->
                // Abrir tela de chamada
                val intent = Intent(this@ChatActivity, ChamadaActivity::class.java).apply {
                    putExtra("chamada_id", chamada.id)
                    putExtra("is_iniciador", true)
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

### Fase 3: Permissões e Notificações (PENDENTE)

#### 3.1. Adicionar Permissões no AndroidManifest.xml
```xml
<!-- Já existentes -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Adicionar se necessário -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

#### 3.2. Solicitar Permissões em Runtime
```kotlin
private fun verificarPermissoes() {
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
    }
}
```

### Fase 4: Service de Background (OPCIONAL)

Para chamadas funcionarem com app em background:

**Arquivo:** `ChamadaService.kt`

```kotlin
class ChamadaService : Service() {
    private lateinit var webSocketManager: WebSocketManager
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Manter WebSocket vivo em background
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }
    
    private fun createNotification(): Notification {
        // Criar notificação de foreground service
    }
}
```

---

## 📋 Checklist de Implementação

### ✅ Fase Concluída: Infraestrutura Base
- [x] ChamadaManager (captura, envio, recepção, reprodução de áudio)
- [x] WebSocketManager (sinalização de eventos)
- [x] ChamadaRepository (integração de componentes)
- [x] Modelos de dados completos
- [x] Endpoints da API

### ⏳ Fase Atual: Interface de Usuário
- [ ] Layout: activity_chamada.xml
- [ ] Layout: activity_chamada_incoming.xml
- [ ] ChamadaActivity (tela de chamada ativa)
- [ ] ChamadaIncomingActivity (tela de chamada recebida)
- [ ] Ícones e recursos visuais

### 🔜 Próximas Fases
- [ ] Integração com MainActivity
- [ ] Botão de chamada no ChatActivity
- [ ] Permissões em runtime
- [ ] Notificações de chamada
- [ ] Testes end-to-end

---

## 🎯 Como Testar

### Teste Básico de Chamada 1:1

1. **Preparação:**
   - Dois dispositivos Android com o app instalado
   - Ambos conectados ao mesmo servidor
   - Ambos com usuários diferentes logados

2. **Fluxo de Teste:**
   ```
   Dispositivo A                    Dispositivo B
   ─────────────                    ─────────────
   1. Abre chat com B
   2. Clica "Ligar"
   3. API cria chamada
   4. Conecta TCP
   5. Tela "Chamando..."          6. Recebe notificação WS
                                   7. Tela "Recebendo chamada"
                                   8. Clica "Aceitar"
                                   9. Conecta TCP
   10. WS notifica "Entrou"
   11. Áudio conectado            12. Áudio conectado
   12. Fala no microfone    ───>  13. Ouve no speaker
   13. Ouve no speaker      <───  14. Fala no microfone
   15. Clica "Desligar"
   16. API finaliza
   17. Desconecta TCP             18. WS notifica "Finalizada"
                                   19. Desconecta TCP
   ```

3. **Pontos de Verificação:**
   - ✓ Conexão TCP estabelecida
   - ✓ Registro do cliente no servidor
   - ✓ Áudio capturado do microfone
   - ✓ Áudio enviado via TCP
   - ✓ Áudio recebido via TCP
   - ✓ Áudio reproduzido no speaker
   - ✓ WebSocket notifica eventos
   - ✓ Chamada finaliza corretamente

---

## 🐛 Troubleshooting

### Problema: Áudio não funciona
**Verificar:**
1. Permissão RECORD_AUDIO concedida
2. Servidor TCP acessível na porta 9090
3. Firewall não bloqueando porta 9090
4. AudioRecord e AudioTrack inicializados

### Problema: WebSocket não conecta
**Verificar:**
1. URL do WebSocket correta (ws:// não wss://)
2. Token JWT válido
3. Servidor WebSocket rodando
4. Logs do WebSocketManager

### Problema: Chamada não inicia
**Verificar:**
1. API respondendo corretamente
2. IDs dos usuários corretos
3. Token JWT válido
4. Logs do ChamadaRepository

---

## 📚 Referências Técnicas

- **Documentação Oficial:** `documentacao-oficial/DOCUMENTACAO_TECNICA.md`
- **Fluxo de Chamadas:** `DOCUMENTACAO_FLUXO_CHAMADAS.md`
- **Implementação de Chamadas:** `IMPLEMENTACAO_CHAMADAS.md`

---

**Última Atualização:** 01/11/2025 - 22:45  
**Status:** Infraestrutura completa, aguardando UI
