# 📞 Implementação de Chamadas de Áudio - Guia Completo

## ✅ Componentes Implementados

### 1. ChamadaManager.kt
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

**Configurações de Áudio:**
- Sample Rate: 44.100 Hz
- Formato: PCM 16-bit
- Canais: Mono
- Buffer: 2048 bytes (~23ms)

### 2. Modelos de Dados (Chamada.kt)
**Localização:** `app/src/main/java/com/conversa/conversa/data/model/Chamada.kt`

**Classes Criadas:**
- `IniciarChamadaRequest` - Request para iniciar chamada
- `ChamadaResponse` - Dados completos da chamada
- `UsuarioChamada` - Informações do usuário na chamada
- `ChamadaStatus` - Enum com estados da chamada
- `UsuarioChamadaStatus` - Enum com estados do usuário
- `EstadoChamadaLocal` - Estados locais do cliente

### 3. Endpoints da API
**Arquivo Atualizado:** `ConversaApi.kt`

**Endpoints Adicionados:**
- `PUT /chamada/iniciar` - Inicia nova chamada
- `POST /chamada/entrar` - Aceita chamada
- `POST /chamada/recusar` - Recusa chamada
- `POST /chamada/sair` - Sai da chamada
- `POST /chamada/cancelar` - Cancela chamada
- `POST /chamada/finalizar` - Finaliza chamada
- `GET /chamada/dados` - Obtém dados da chamada

---

## 🚀 Próximos Passos para Completar a Implementação

### Fase 1: WebSocket para Sinalização
**Criar:** `WebSocketManager.kt`

```kotlin
class WebSocketManager(context: Context) {
    // Tipos de evento WebSocket
    companion object {
        const val TYPE_CHAMADA_RECEBIDA = 51
        const val TYPE_CHAMADA_FINALIZADA = 52
        const val TYPE_USUARIO_RECUSOU = 53
        const val TYPE_USUARIO_ENTROU = 54
        const val TYPE_USUARIO_SAIU = 55
    }
    
    fun conectar(wsUrl: String, token: String)
    fun enviarMensagem(message: String)
    
    var onChamadaRecebida: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onChamadaFinalizada: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioEntrou: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
    var onUsuarioSaiu: ((chamadaId: Int, usuarioId: Int) -> Unit)? = null
}
```

### Fase 2: Repository para Chamadas
**Criar:** `ChamadaRepository.kt`

```kotlin
class ChamadaRepository(
    private val api: ConversaApi,
    private val chamadaManager: ChamadaManager,
    private val webSocketManager: WebSocketManager,
    private val userPreferences: UserPreferences
) {
    suspend fun iniciarChamada(destinatariosIds: List<Int>)
    suspend fun aceitarChamada(chamadaId: Int)
    suspend fun recusarChamada(chamadaId: Int)
    suspend fun finalizarChamada()
}
```

### Fase 3: Activity de Chamada Ativa
**Criar:** `ChamadaActivity.kt`

Tela que mostra:
- Nome do(s) participante(s)
- Tempo de chamada
- Botões: Mute, Speaker, Finalizar
- Indicador de quem está falando
- Lista de participantes (para chamadas em grupo)

### Fase 4: Activity de Chamada Recebida
**Criar:** `ChamadaIncomingActivity.kt`

Tela que mostra:
- Quem está chamando
- Botões: Aceitar / Recusar
- Som de toque

### Fase 5: Permissões
Adicionar no `AndroidManifest.xml`:

```xml
<!-- Já existe -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!-- Adicionar se necessário -->
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Solicitar permissões em runtime:
```kotlin
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
```

---

## 📖 Como Usar o ChamadaManager

### Exemplo Básico

```kotlin
// 1. Criar instância
val chamadaManager = ChamadaManager(context)

// 2. Configurar callbacks
chamadaManager.onConexaoEstabelecida = {
    Log.d("Chamada", "Conectado!")
}

chamadaManager.onConexaoFalhou = { erro ->
    Log.e("Chamada", "Falha: $erro")
}

chamadaManager.onChamadaFinalizada = {
    Log.d("Chamada", "Chamada encerrada")
}

// 3. Iniciar chamada
lifecycleScope.launch {
    val sucesso = chamadaManager.iniciarChamada(
        serverHost = "seu-servidor.com",
        serverPort = 9090,
        chamadaId = 123,
        usuarioId = 1
    )
    
    if (sucesso) {
        // Chamada iniciada com sucesso
    }
}

// 4. Finalizar chamada
chamadaManager.finalizarChamada()

// 5. Limpar recursos (no onDestroy)
override fun onDestroy() {
    super.onDestroy()
    chamadaManager.cleanup()
}
```

### Fluxo Completo de Chamada 1:1

```kotlin
// USUÁRIO A: Inicia chamada
lifecycleScope.launch {
    // 1. Chama API REST para criar chamada
    val response = api.iniciarChamada(
        token = "Bearer $token",
        request = IniciarChamadaRequest(
            tipo = 1, // 1:1
            usuarios = listOf(
                UsuarioIdDto(usuarioAId),
                UsuarioIdDto(usuarioBId)
            )
        )
    )
    
    if (response.isSuccessful) {
        val chamada = response.body()!!
        
        // 2. Conecta ao servidor TCP e inicia áudio
        chamadaManager.iniciarChamada(
            serverHost = "servidor.com",
            serverPort = 9090,
            chamadaId = chamada.id,
            usuarioId = usuarioAId
        )
        
        // 3. Servidor notifica USUÁRIO B via WebSocket
    }
}

// USUÁRIO B: Recebe notificação WebSocket
webSocketManager.onChamadaRecebida = { chamadaId, usuarioId ->
    // 1. Mostra tela de chamada recebida
    val intent = Intent(context, ChamadaIncomingActivity::class.java)
    intent.putExtra("chamada_id", chamadaId)
    intent.putExtra("usuario_id", usuarioId)
    startActivity(intent)
}

// USUÁRIO B: Aceita chamada
lifecycleScope.launch {
    // 1. Chama API REST
    api.entrarChamada(
        token = "Bearer $token",
        request = ChamadaIdRequest(chamadaId)
    )
    
    // 2. Conecta ao servidor TCP
    chamadaManager.iniciarChamada(
        serverHost = "servidor.com",
        serverPort = 9090,
        chamadaId = chamadaId,
        usuarioId = usuarioBId
    )
    
    // 3. Servidor notifica USUÁRIO A via WebSocket
}

// USUÁRIO A: Recebe notificação que B entrou
webSocketManager.onUsuarioEntrou = { chamadaId, usuarioId ->
    // Atualiza UI: "Conectado"
}

// AMBOS: Áudio flui automaticamente
// - ChamadaManager captura do microfone
// - Envia para servidor via TCP
// - Servidor retransmite para outro participante
// - ChamadaManager recebe e reproduz

// QUALQUER UM: Finaliza chamada
lifecycleScope.launch {
    // 1. Finaliza localmente
    chamadaManager.finalizarChamada()
    
    // 2. Notifica servidor via API REST
    api.finalizarChamada(
        token = "Bearer $token",
        request = ChamadaIdRequest(chamadaId)
    )
    
    // 3. Servidor notifica outro participante via WebSocket
}
```

---

## 🎛️ Configurações e Otimizações

### AudioRecord Settings
```kotlin
// Já configurado no ChamadaManager
AudioSource.MIC          // Microfone padrão
SAMPLE_RATE = 44100     // Taxa de amostragem
CHANNEL_IN_MONO         // Mono
ENCODING_PCM_16BIT      // 16-bit PCM
```

### AudioTrack Settings
```kotlin
// Já configurado no ChamadaManager
STREAM_VOICE_CALL       // Otimizado para voz
SAMPLE_RATE = 44100
CHANNEL_OUT_MONO
ENCODING_PCM_16BIT
MODE_STREAM             // Streaming mode
```

### Mixing Algorithm (Grupos)
```kotlin
// Já implementado no ChamadaManager.mixar()
// Para cada frame:
// 1. Soma samples de todos os participantes
// 2. Aplica clipping: coerceIn(Short.MIN_VALUE, Short.MAX_VALUE)
// 3. Retorna áudio mixado
```

### Buffer Management
```kotlin
// Configuração atual
BUFFER_SIZE = 2048 bytes (~23ms)

// Para ajustar latência vs. qualidade:
// - Menor buffer = menor latência, maior risco de dropout
// - Maior buffer = maior latência, mais estável
```

---

## 🐛 Troubleshooting

### Problema: Sem áudio
**Verificar:**
1. Permissão RECORD_AUDIO concedida
2. Servidor TCP acessível na porta 9090
3. Chamada foi iniciada com sucesso
4. AudioRecord e AudioTrack iniciados

### Problema: Eco/Feedback
**Solução:**
- Usar fone de ouvido
- Implementar cancelamento de eco (AEC)

### Problema: Latência alta
**Soluções:**
- Reduzir BUFFER_SIZE (com cuidado)
- Verificar latência de rede
- Usar codec com compressão (futuro)

### Problema: Áudio cortado
**Soluções:**
- Aumentar BUFFER_SIZE
- Verificar uso de CPU
- Implementar jitter buffer

---

## 📝 TODO: Melhorias Futuras

### Prioridade Alta
- [ ] Implementar WebSocketManager
- [ ] Criar ChamadaRepository
- [ ] Criar telas de chamada (Activity)
- [ ] Adicionar notificações de chamada
- [ ] Implementar som de toque

### Prioridade Média
- [ ] Adicionar controle de volume por participante
- [ ] Implementar indicador visual de quem está falando
- [ ] Adicionar histórico de chamadas
- [ ] Implementar jitter buffer
- [ ] Otimizar consumo de bateria

### Prioridade Baixa
- [ ] Suporte a codec Opus (compressão)
- [ ] Cancelamento de eco (AEC)
- [ ] Supressão de ruído
- [ ] Modo paisagem para chamadas
- [ ] Chamadas com vídeo (futuro)

---

## 📚 Referências

- [Documentação Técnica Completa](DOCUMENTACAO_TECNICA.md)
- [Fluxo de Chamadas](DOCUMENTACAO_FLUXO_CHAMADAS.md)
- [Android AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)
- [Android AudioTrack](https://developer.android.com/reference/android/media/AudioTrack)

---

## ✨ Status da Implementação

| Componente | Status | Pronto para Uso |
|------------|--------|-----------------|
| ChamadaManager | ✅ Completo | Sim |
| Modelos de Dados | ✅ Completo | Sim |
| API Endpoints | ✅ Completo | Sim |
| WebSocket Manager | ❌ Pendente | Não |
| Chamada Repository | ❌ Pendente | Não |
| Tela de Chamada Ativa | ❌ Pendente | Não |
| Tela de Chamada Recebida | ❌ Pendente | Não |
| Permissões | ⚠️ Parcial | Verificar |
| Notificações | ❌ Pendente | Não |

---

**Última Atualização:** 01/11/2025
**Versão:** 1.0
**Autor:** Sistema de Documentação Conversa
