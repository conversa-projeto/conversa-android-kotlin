# Correção: Ringtone Continua Tocando ao Clicar nos Botões da Tela

## Problema Identificado

**Sintoma**: Quando o dispositivo estava bloqueado e a tela de chamada abria automaticamente (fullscreen intent), ao clicar nos botões "Atender" ou "Recusar" **na própria tela**, o ringtone/vibração continuavam tocando.

**Comportamento Esperado**:
- Clicar em "Atender" → Para ringtone imediatamente
- Clicar em "Recusar" → Para ringtone imediatamente

## Diferença Entre Botões da Notificação vs Botões da Tela

### Botões da Notificação ✅ (Funcionavam Corretamente)

Quando você clica nos botões que aparecem **na notificação**:

**Botão "Atender" da Notificação:**
```kotlin
// SocketService.kt - Botão Atender
val answerIntent = Intent(this, ChamadaActivity::class.java).apply {
    putExtra("auto_answer", true)
    putExtra("from_notification_action", true)  // ← Flag importante
    // ...
}

// ChamadaActivity.kt - onCreate()
if (intent.getBooleanExtra("from_notification_action", false)) {
    pararRingtoneERemoverNotificacao()  // ← Para ringtone!
}
```

**Botão "Recusar" da Notificação:**
```kotlin
// ChamadaActionReceiver.kt
private fun recusarChamada(context: Context, chamadaId: Int) {
    pararRingtone(context)  // ← Para ringtone!
    // ... recusa via API
}
```

### Botões da Tela ❌ (NÃO Paravam o Ringtone)

Quando você clica nos botões que aparecem **dentro da ChamadaActivity**:

**Botão "Atender" da Tela - ANTES:**
```kotlin
override fun onAceitarChamada() {
    (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

    lifecycleScope.launch {
        val result = repository.aceitarChamada(chamadaId)
        // ... abre tela de chamada
    }
}
// ❌ RINGTONE CONTINUA TOCANDO!
```

**Botão "Recusar" da Tela - ANTES:**
```kotlin
override fun onRecusarChamada() {
    (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

    lifecycleScope.launch {
        repository.recusarChamada(chamadaId)
        removerNotificacaoChamada()
        finish()
    }
}
// ❌ RINGTONE CONTINUA TOCANDO!
```

## Causa Raiz

Os callbacks `onAceitarChamada()` e `onRecusarChamada()` (chamados pelos botões da tela) **não estavam parando o ringtone**.

Apenas os botões da notificação tinham lógica para parar o ringtone:
- Botão "Atender" notificação → Tinha `from_notification_action` flag
- Botão "Recusar" notificação → `ChamadaActionReceiver` chamava `pararRingtone()`

## Solução Implementada

Adicionar chamada para `pararRingtoneERemoverNotificacao()` **IMEDIATAMENTE** quando os botões da tela são clicados.

### ChamadaActivity.kt - onAceitarChamada()

**ANTES**:
```kotlin
override fun onAceitarChamada() {
    (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

    lifecycleScope.launch {
        val result = repository.aceitarChamada(chamadaId)
        // ...
    }
}
```

**DEPOIS**:
```kotlin
override fun onAceitarChamada() {
    (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

    // Para ringtone/vibração IMEDIATAMENTE
    pararRingtoneERemoverNotificacao()

    lifecycleScope.launch {
        val result = repository.aceitarChamada(chamadaId)
        // ...
    }
}
```

### ChamadaActivity.kt - onRecusarChamada()

**ANTES**:
```kotlin
override fun onRecusarChamada() {
    (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

    lifecycleScope.launch {
        repository.recusarChamada(chamadaId)
        removerNotificacaoChamada()
        finish()
    }
}
```

**DEPOIS**:
```kotlin
override fun onRecusarChamada() {
    (currentFragment as? IncomingCallFragment)?.desabilitarBotoes()

    // Para ringtone/vibração IMEDIATAMENTE
    pararRingtoneERemoverNotificacao()

    lifecycleScope.launch {
        repository.recusarChamada(chamadaId)
        // Ringtone já foi parado acima
        finish()
    }
}
```

## Como pararRingtoneERemoverNotificacao() Funciona

```kotlin
private fun pararRingtoneERemoverNotificacao() {
    try {
        // 1. Envia comando para SocketService parar ringtone
        val serviceIntent = Intent(this, SocketService::class.java).apply {
            action = "STOP_RINGTONE"
        }
        startService(serviceIntent)

        // 2. Remove notificação de chamada
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(1002) // NOTIFICATION_ID_CHAMADA

        Log.d(TAG, "Ringtone parado e notificação removida")
    } catch (e: Exception) {
        Log.e(TAG, "Erro ao parar ringtone/remover notificação", e)
    }
}
```

### Fluxo de Parada do Ringtone

```
1. ChamadaActivity chama pararRingtoneERemoverNotificacao()
       ↓
2. Envia Intent com action="STOP_RINGTONE" para SocketService
       ↓
3. SocketService.onStartCommand() recebe o intent
       ↓
4. SocketService chama pararRingtone()
       ↓
5. SocketService chama ringtoneManager.parar()
       ↓
6. CallRingtoneManager para ringtone e vibração
       ↓
7. Notificação é removida ✅
```

## Comportamento Após a Correção

### Cenário 1: Dispositivo Bloqueado → Fullscreen Abre → Clica "Atender"

```
1. Chamada chega, dispositivo bloqueado
2. Ringtone/vibração iniciam 🔊
3. Fullscreen intent dispara
4. ChamadaActivity abre (sem auto-answer)
5. Usuário vê tela com botões tocando ringtone 🔊
6. Usuário clica "Atender"
7. onAceitarChamada() chamado
8. pararRingtoneERemoverNotificacao() executado
9. Ringtone/vibração PARAM IMEDIATAMENTE ✅
10. Chamada é aceita
11. Tela de chamada em andamento aparece
```

### Cenário 2: Dispositivo Bloqueado → Fullscreen Abre → Clica "Recusar"

```
1. Chamada chega, dispositivo bloqueado
2. Ringtone/vibração iniciam 🔊
3. Fullscreen intent dispara
4. ChamadaActivity abre (sem auto-answer)
5. Usuário vê tela com botões tocando ringtone 🔊
6. Usuário clica "Recusar"
7. onRecusarChamada() chamado
8. pararRingtoneERemoverNotificacao() executado
9. Ringtone/vibração PARAM IMEDIATAMENTE ✅
10. Chamada é recusada via API
11. Activity fecha
```

### Cenário 3: Dispositivo Desbloqueado → Notificação → Clica Botão "Atender"

```
1. Chamada chega, dispositivo desbloqueado
2. Notificação aparece com botões
3. Ringtone/vibração iniciam 🔊
4. Usuário clica botão "Atender" DA NOTIFICAÇÃO
5. ChamadaActivity abre com from_notification_action=true
6. onCreate() para ringtone via pararRingtoneERemoverNotificacao()
7. auto_answer=true → Aceita automático
8. Tela de chamada em andamento aparece ✅
```

## Resumo das Correções

| Ação | Origem | Método de Parada | Status |
|------|--------|------------------|--------|
| **Atender** via notificação | SocketService | onCreate() + `from_notification_action` flag | ✅ Já funcionava |
| **Recusar** via notificação | ChamadaActionReceiver | `pararRingtone()` | ✅ Já funcionava |
| **Atender** via tela | ChamadaActivity | `pararRingtoneERemoverNotificacao()` | ✅ **ADICIONADO AGORA** |
| **Recusar** via tela | ChamadaActivity | `pararRingtoneERemoverNotificacao()` | ✅ **ADICIONADO AGORA** |

## Arquivos Modificados

### ChamadaActivity.kt
- **Linha 420**: Adicionado `pararRingtoneERemoverNotificacao()` em `onAceitarChamada()`
- **Linha 454**: Adicionado `pararRingtoneERemoverNotificacao()` em `onRecusarChamada()`
- **Linha 459**: Removido `removerNotificacaoChamada()` duplicado (já feito em `pararRingtoneERemoverNotificacao()`)

## Teste Recomendado

### Teste 1: Botões da Tela - Atender
1. Bloqueie o dispositivo
2. Receba uma chamada
3. Tela abre automaticamente com ringtone tocando
4. Clique no botão "Atender" **NA TELA**
5. ✅ Ringtone deve parar IMEDIATAMENTE
6. ✅ Vibração deve parar IMEDIATAMENTE
7. ✅ Chamada deve ser aceita

### Teste 2: Botões da Tela - Recusar
1. Bloqueie o dispositivo
2. Receba uma chamada
3. Tela abre automaticamente com ringtone tocando
4. Clique no botão "Recusar" **NA TELA**
5. ✅ Ringtone deve parar IMEDIATAMENTE
6. ✅ Vibração deve parar IMEDIATAMENTE
7. ✅ Tela deve fechar
8. ✅ Chamada deve ser recusada

### Teste 3: Botões da Notificação (Garantir que não quebrou)
1. Dispositivo desbloqueado
2. Receba uma chamada
3. Notificação aparece
4. Clique no botão "Atender" **DA NOTIFICAÇÃO**
5. ✅ Deve funcionar normalmente

## Data da Correção
2025-12-26
