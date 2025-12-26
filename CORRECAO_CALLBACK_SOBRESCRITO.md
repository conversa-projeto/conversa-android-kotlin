# Correção: Callback de Chamada Sobrescrito

## Problema Identificado

**Sintoma**: Apenas a primeira chamada recebida exibia notificação. Chamadas subsequentes não exibiam notificação alguma.

## Análise dos Logs

### Primeira Chamada (568) - FUNCIONOU ✅
```
19:23:48.838 SocketService: 🔔 [CALLBACK] onChamadaRecebida CHAMADO
19:23:48.841 SocketService: 🔔 Estado: isDeviceLocked=false
19:23:48.841 SocketService: 🔓 FLUXO: Dispositivo DESBLOQUEADO - Mostrando notificação
19:23:48.855 NotificationManager: notify(1002, ...) ← Notificação exibida!
```

### Segunda Chamada (569) - FALHOU ❌
```
19:24:00.919 SocketManager: Mensagem recebida: {"tipo":51,"chamada_id":569}
19:24:00.921 SocketManager: Chamada recebida: 569 de Desconhecido
19:24:00.921 ChamadaRepository: Socket: Chamada recebida 569 de Desconhecido
                                 ↑ AQUI! Log do ChamadaRepository, não do SocketService!
```

**Nenhum log do SocketService foi gerado!** O callback estava apontando para `ChamadaRepository` ao invés de `SocketService`.

## Causa Raiz

### Conflito de Callbacks

O `SocketManager` tem uma variável **`onChamadaRecebida`** que pode ter apenas **UM** callback por vez. Dois lugares estavam tentando registrar callbacks:

1. **SocketService.kt** (linha 397):
```kotlin
private fun inicializarSocket(...) {
    socketManager = SocketManager(this)

    socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
        // Lógica de notificação baseada em lock state
        if (isDeviceLocked) {
            mostrarNotificacaoComFullscreen(...)
        } else {
            mostrarNotificacaoComBotoes(...)
        }
    }
}
```

2. **ChamadaRepository.kt** (linha 201):
```kotlin
private fun setupSocketEventListeners() {
    socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
        // SOBRESCREVE o callback do SocketService!
        scope.launch {
            val resultado = obterDadosChamada(chamadaId)
            // ...
        }
    }
}
```

### Sequência do Problema

```
1. App inicia
   → SocketService.inicializarSocket() registra callback ✅

2. Primeira chamada chega (568)
   → Callback do SocketService é chamado ✅
   → Notificação exibida ✅

3. Usuário clica "Atender"
   → ChamadaActivity abre
   → ChamadaRepository é inicializado
   → setupSocketEventListeners() SOBRESCREVE o callback do SocketService ❌

4. Chamada termina
   → ChamadaRepository é limpo
   → Callback do SocketService NÃO VOLTA (foi perdido) ❌

5. Segunda chamada chega (569)
   → NÃO HÁ CALLBACK registrado (foi perdido)
   → Nenhuma notificação é exibida ❌
```

## Solução Implementada

### Abordagem

**Apenas SocketService deve gerenciar callbacks de chamadas recebidas.**

O `SocketService` é um serviço de longa duração que permanece ativo mesmo quando activities são destruídas. É o local correto para gerenciar notificações de chamadas.

### Mudança no ChamadaRepository.kt

**ANTES** (linha 201):
```kotlin
private fun setupSocketEventListeners() {
    socketManager.onChamadaRecebida = { chamadaId, usuarioId, usuarioNome ->
        Log.d(TAG, "Socket: Chamada recebida $chamadaId de $usuarioNome")
        // ... código que buscava dados da chamada
    }

    socketManager.onChamadaFinalizada = { ... }
}
```

**DEPOIS**:
```kotlin
private fun setupSocketEventListeners() {
    // REMOVIDO: Não deve sobrescrever o callback do SocketService!
    // O SocketService é quem gerencia as notificações de chamadas recebidas
    // baseado no estado do dispositivo (bloqueado/desbloqueado).
    //
    // [callback comentado - ver código]

    socketManager.onChamadaFinalizada = { ... }
}
```

### Por que Isso Resolve?

Agora existe **APENAS UM** callback registrado para `onChamadaRecebida`:
- ✅ SocketService registra o callback na inicialização
- ✅ Callback permanece ativo durante toda a vida do serviço
- ✅ ChamadaRepository NÃO sobrescreve mais o callback
- ✅ Todas as chamadas subsequentes funcionam

## Comportamento Após a Correção

```
Chamada Recebida
    ↓
SocketManager.onChamadaRecebida dispara
    ↓
SocketService callback (SEMPRE ativo)
    ↓
Verifica: Dispositivo bloqueado?
    ↓
┌─────────────────────┬─────────────────────┐
│ SIM (Bloqueado)     │ NÃO (Desbloqueado)  │
├─────────────────────┼─────────────────────┤
│ Notificação COM     │ Notificação COM     │
│ fullscreen          │ botões              │
│ (abre automático)   │ (usuário clica)     │
└─────────────────────┴─────────────────────┘
```

## Arquivos Modificados

### ChamadaRepository.kt
- **Linhas 199-232**: Callback `onChamadaRecebida` comentado para não sobrescrever o do SocketService
- O callback `onChamadaFinalizada` permanece ativo (não conflita)

## Teste Recomendado

1. **Primeira Chamada**:
   - Receber chamada → ✅ Deve exibir notificação com botões
   - Clicar "Atender" → ✅ Deve abrir tela
   - Finalizar chamada → ✅ Deve voltar à tela principal

2. **Segunda Chamada** (cenário que falhava):
   - Receber nova chamada → ✅ Deve exibir notificação com botões
   - Clicar "Atender" → ✅ Deve abrir tela
   - Finalizar chamada → ✅ Deve voltar à tela principal

3. **Terceira, Quarta, N-ésima Chamada**:
   - ✅ Deve funcionar igual às anteriores

4. **Dispositivo Bloqueado**:
   - Receber chamada → ✅ Deve abrir tela automático

## Lições Aprendidas

1. **Callbacks Singleton**: Quando uma variável callback pode ter apenas um listener, certifique-se de que apenas UM componente o registra.

2. **Services vs Activities**: Services são melhores para callbacks de longa duração porque sobrevivem ao ciclo de vida das activities.

3. **Debugging**: Procure por múltiplas atribuições (`=`) à mesma variável callback em diferentes arquivos.

4. **Logs Detalhados**: Os logs mostraram claramente que o callback mudou de `SocketService` para `ChamadaRepository`.

## Data da Correção
2025-12-26
