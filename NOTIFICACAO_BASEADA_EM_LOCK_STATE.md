# Notificação Baseada no Estado de Bloqueio do Dispositivo

## Objetivo

Implementar comportamento inteligente de notificações de chamada baseado no estado de bloqueio do dispositivo:

- **Dispositivo DESBLOQUEADO** → Notificação com botões (usuário decide se atende)
- **Dispositivo BLOQUEADO** → Tela de chamada abre automático (fullscreen intent)

## Como Funciona

### Detecção do Estado de Bloqueio

Usamos o `KeyguardManager` do Android para verificar se o dispositivo está bloqueado:

```kotlin
val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
val isDeviceLocked = keyguardManager.isKeyguardLocked
```

### Fluxo de Decisão

```
Chamada Recebida
    ↓
Verifica: Dispositivo está bloqueado?
    ↓
┌──────────────────────┬──────────────────────┐
│ SIM (Bloqueado)      │ NÃO (Desbloqueado)   │
├──────────────────────┼──────────────────────┤
│ mostrarNotificacao   │ mostrarNotificacao   │
│ ComFullscreen()      │ ComBotoes()          │
│                      │                      │
│ ✅ COM fullscreen    │ ❌ SEM fullscreen    │
│ ✅ COM botões        │ ✅ COM botões        │
│ → Abre automático   │ → Usuário clica      │
└──────────────────────┴──────────────────────┘
```

## Implementação

### 1. SocketService.kt - Lógica de Decisão (linha 397)

```kotlin
socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
    // Verifica se dispositivo está bloqueado
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    val isDeviceLocked = keyguardManager.isKeyguardLocked

    if (isDeviceLocked) {
        // Dispositivo BLOQUEADO: abre tela automático
        mostrarNotificacaoComFullscreen(chamadaId, usuarioId, usuarioNome)
    } else {
        // Dispositivo DESBLOQUEADO: apenas notificação com botões
        mostrarNotificacaoComBotoes(chamadaId, usuarioId, usuarioNome)
    }
}
```

### 2. mostrarNotificacaoComBotoes() - Dispositivo Desbloqueado

**Características:**
- ❌ **SEM** `setFullScreenIntent()` - não abre automático
- ✅ **COM** botões "Atender" e "Recusar"
- ✅ Usuário pode clicar na notificação para abrir a tela
- ✅ Ringtone toca

**Código:**
```kotlin
private fun mostrarNotificacaoComBotoes(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
    ringtoneManager.iniciar()

    val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
        .setContentTitle("📞 Chamada de $usuarioNome")
        .setContentIntent(pendingIntent)  // Abre ao clicar na notificação
        .addAction(ic_call_end, "Recusar", declinePendingIntent)
        .addAction(ic_call, "Atender", answerPendingIntent)
        // SEM .setFullScreenIntent() ← IMPORTANTE!
        .build()
}
```

### 3. mostrarNotificacaoComFullscreen() - Dispositivo Bloqueado

**Características:**
- ✅ **COM** `setFullScreenIntent(pendingIntent, true)` - abre automático
- ✅ **COM** botões "Atender" e "Recusar" (aparecem na notificação)
- ✅ Tela de chamada abre mesmo com dispositivo bloqueado
- ✅ Ringtone toca

**Código:**
```kotlin
private fun mostrarNotificacaoComFullscreen(chamadaId: Int, usuarioId: Int, usuarioNome: String) {
    ringtoneManager.iniciar()

    val notification = NotificationCompat.Builder(this, CHANNEL_ID_CHAMADAS)
        .setContentTitle("Chamada de $usuarioNome")
        .setContentIntent(pendingIntent)
        .addAction(ic_call_end, "Recusar", declinePendingIntent)
        .addAction(ic_call, "Atender", answerPendingIntent)
        .setFullScreenIntent(pendingIntent, true)  ← Abre automático!
        .build()
}
```

## Comportamento dos Botões

### Botão "Atender"
```kotlin
val answerIntent = Intent(this, ChamadaActivity::class.java).apply {
    putExtra("auto_answer", true)
    putExtra("from_notification_action", true)  // Para ringtone
    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
}
val answerPendingIntent = PendingIntent.getActivity(...)
```

- Abre `ChamadaActivity`
- Para o ringtone automaticamente (via `from_notification_action`)
- Compatível com Android 14+ (usa `PendingIntent.getActivity()` direto)

### Botão "Recusar"
```kotlin
val declineIntent = Intent(this, ChamadaActionReceiver::class.java).apply {
    action = ChamadaActionReceiver.ACTION_DECLINE
    putExtra(ChamadaActionReceiver.EXTRA_CHAMADA_ID, chamadaId)
}
val declinePendingIntent = PendingIntent.getBroadcast(...)
```

- Envia broadcast para `ChamadaActionReceiver`
- Recusa a chamada via API
- Para ringtone
- Remove notificação

## Mudanças no MainActivity

O callback `onChamadaRecebida()` do `CallListener` **NÃO É MAIS USADO**.

**Antes:**
```kotlin
override fun onChamadaRecebida(...) {
    socketService?.mostrarNotificacaoChamadaParaAppAberto(...)  // ❌ Removido
}
```

**Depois:**
```kotlin
override fun onChamadaRecebida(...) {
    // Este callback não é mais chamado
    // Lógica movida para SocketService
}
```

**Por quê?**
Toda a decisão agora é feita no `SocketService` baseada no estado do dispositivo, não no estado do app (foreground/background). Isso é mais preciso e garante o comportamento correto.

## Cenários de Teste

### ✅ Cenário 1: Dispositivo Desbloqueado, App Aberto
- Recebe chamada
- Aparece notificação com botões
- **NÃO** abre tela automático
- Usuário clica "Atender" → abre tela
- Usuário clica "Recusar" → recusa chamada

### ✅ Cenário 2: Dispositivo Desbloqueado, App Minimizado
- Recebe chamada
- Aparece notificação com botões
- **NÃO** abre tela automático
- Comportamento idêntico ao cenário 1

### ✅ Cenário 3: Dispositivo Bloqueado (Tela de Lock)
- Recebe chamada
- **ABRE** tela de chamada automático
- Botões também aparecem na notificação (caso usuário volte)
- Ringtone toca

### ✅ Cenário 4: Dispositivo Desbloqueado, Usuário Clica na Notificação
- Clica na **área da notificação** (não nos botões)
- Abre `ChamadaActivity` normalmente
- Ringtone continua até atender/recusar

## Arquivos Modificados

### SocketService.kt
- **Linha 4**: Adicionado import `android.app.KeyguardManager`
- **Linhas 397-416**: Nova lógica baseada em `isKeyguardLocked`
- **Linha 224**: Renomeado `mostrarNotificacaoChamadaParaAppAberto()` → `mostrarNotificacaoComBotoes()`
- **Linha 478**: Renomeado `mostrarNotificacaoChamada()` → `mostrarNotificacaoComFullscreen()`

### MainActivity.kt
- **Linhas 378-383**: Esvaziado `onChamadaRecebida()` com comentário explicativo

## Permissões Necessárias

Não requer permissões adicionais. `KeyguardManager.isKeyguardLocked` é acessível sem permissões especiais.

## Vantagens da Implementação

1. ✅ **UX Melhorada**: No dispositivo desbloqueado, usuário tem controle (não é interrompido)
2. ✅ **Comportamento Natural**: No dispositivo bloqueado, funciona como chamadas normais do telefone
3. ✅ **Consistente**: Sempre mostra botões em ambos os casos
4. ✅ **Simples**: Decisão baseada em um único flag (`isKeyguardLocked`)
5. ✅ **Android 14+ Compatível**: Usa `PendingIntent.getActivity()` direto (não trampolining)

## Data da Implementação
2025-12-26
